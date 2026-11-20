package com.zrec.recorder

import android.content.Context
import android.content.Intent
import android.net.Uri
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

    private const val OWNER = "pancasilayangpertama-maker"
    private const val REPO = "zrec"
    private const val LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val USER_AGENT = "Zrec-Updater"

    /** apk file inside cacheDir; ApkProvider serves exactly this file */
    const val APK_NAME = "update.apk"

    data class Release(val version: String, val apkUrl: String)

    private val main = Handler(Looper.getMainLooper())

    /** Asks GitHub for the newest release. The callback runs on the main thread. */
    fun check(context: Context, onResult: (Release?) -> Unit) {
        val app = context.applicationContext
        Thread {
            val remote = runCatching { fetchLatest() }.getOrNull()
            val local = runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName
            }.getOrNull().orEmpty()
            val newer = remote != null && isNewer(remote.version, local)
            main.post { onResult(if (newer) remote else null) }
        }.start()
    }

    /** Downloads then opens the installer. Progress is reported on the main thread. */
    fun downloadAndInstall(context: Context, release: Release, onState: (Int?) -> Unit) {
        val app = context.applicationContext
        onState(R.string.update_downloading)
        Thread {
            val ok = runCatching { download(app, release.apkUrl) }.getOrDefault(false)
            main.post {
                if (ok) {
                    onState(null)
                    launchInstaller(app)
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
            return Release(tag, url)
        } finally {
            conn.disconnect()
        }
    }

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

    private fun launchInstaller(context: Context) {
        val uri = Uri.parse("content://${context.packageName}.apk/$APK_NAME")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
}
