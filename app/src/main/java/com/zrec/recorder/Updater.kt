package com.zrec.recorder

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the public release feed, downloads a newer APK, and hands it to the
 * system installer. Android never installs silently for a sideloaded app, so
 * the final step is always one confirmation tap by the user.
 */
object Updater {

    private const val OWNER = "Zdevlops"
    private const val REPO = "zrec"
    private const val LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val USER_AGENT = "Zrec-Updater"

    /** apk file inside cacheDir; ApkProvider serves exactly this file */
    const val APK_NAME = "update.apk"

    data class Release(val version: String, val apkUrl: String, val code: Long = 0L)

    private val main = Handler(Looper.getMainLooper())

    /** Asks GitHub for the newest release. The callback runs on the main thread. */
    fun check(context: Context, onResult: (Release?) -> Unit) {
        val app = context.applicationContext
        Thread {
            val remote = runCatching { fetchLatest() }.getOrNull()
            val info = runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0)
            }.getOrNull()
            val localName = info?.versionName.orEmpty()
            val localCode = if (info == null) 0L else localVersionCode(info)
            val newer = remote != null && isNewer(remote, localName, localCode)
            main.post { onResult(if (newer) remote else null) }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun localVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    /** Downloads then opens the installer. Progress is reported on the main thread. */
    fun downloadAndInstall(context: Context, release: Release, onState: (Int?) -> Unit) {
        val app = context.applicationContext
        onState(R.string.update_downloading)
        Thread {
            val ok = runCatching { download(app, release.apkUrl) }.getOrDefault(false)
            main.post {
                if (ok) {
                    onState(null)
                    launchInstaller(app, release.code)
                } else {
                    onState(R.string.update_failed)
                }
            }
        }.start()
    }

    private fun fetchLatest(): Release {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            val assets = json.optJSONArray("assets")
            var url = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        url = a.optString("browser_download_url")
                        break
                    }
                }
            }
            if (tag.isBlank() || url.isBlank()) error("no apk asset in latest release")
            return Release(tag, url, versionCodeIn(json.optString("body")))
        } finally {
            conn.disconnect()
        }
    }

    /**
     * The release body carries `<!-- zrec:versionCode=8 -->`. Comparing build
     * numbers is the only thing that works here: the release tag stays v1.0.0
     * while the build keeps going up, so semver alone would never report an
     * update again.
     */
    private fun versionCodeIn(body: String): Long =
        CODE_IN_BODY.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

    private fun download(context: Context, url: String): Boolean {
        val out = File(context.cacheDir, APK_NAME)
        if (out.exists()) out.delete()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (conn.responseCode !in 200..299) return false
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
        return out.length() > 0
    }

    private fun launchInstaller(context: Context, code: Long) {
        // Remember what was asked for. The installer reports nothing back, so this
        // is the only way to tell afterwards that the new build really landed.
        Prefs(context).pendingUpdateCode = code
        val uri = Uri.parse("content://${context.packageName}.apk/$APK_NAME")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * True when the remote release should replace what is installed.
     *
     * Build numbers win whenever the release advertises one, because versionName
     * is pinned to 1.0.0 by request and would otherwise look "not newer" forever.
     */
    fun isNewer(remote: Release, localName: String, localCode: Long): Boolean {
        if (remote.code > 0L) return remote.code > localCode
        return isNewer(remote.version, localName)
    }

    /** Compares dotted versions such as "v1.2.0" and "1.1.0". */
    fun isNewer(remote: String, local: String): Boolean {
        val a = numbers(remote)
        val b = numbers(local)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split('.')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    private val CODE_IN_BODY = Regex("""zrec:versionCode=(\d+)""")
}
