<img src="docs/banner.svg" alt="Zrec — screen recorder for Android" width="100%">

<p align="center">
  <a href="https://github.com/Zdevlops/zrec/releases/latest">
    <img alt="Download APK" src="https://img.shields.io/badge/Download%20APK-v1.1.1-F4F1EC?style=for-the-badge&labelColor=121110">
  </a>
</p>

Screen recorder Android tanpa iklan, tanpa watermark, dan tanpa akun. Dibuat native dengan Kotlin dan framework Android saja, jadi APK-nya cuma sekitar 100 KB.

Satu file, tidak ada tambahan lain. Ambil di [Releases](../../releases/latest).

> [!WARNING]
> **MAKS FPS TETAP BERGANTUNG KEMAMPUAN ASLI DEVICE.**
> Angka di bawah itu batas atas dari Zrec, bukan janji. Kalau encoder HP cuma sanggup 1080p60, ya 60 fps yang muncul dan yang terekam. Zrec membaca kemampuan encoder lewat `MediaCodecList`, jadi yang ditawarkan hanya yang benar-benar bisa dijalankan.

## Features

| Feature | Description |
|---|---|
| **Screen recording** | MediaProjection langsung ke hardware encoder, tanpa re-encode |
| **Floating control** | Bola yang bisa digeser di luar aplikasi: record, stop, pause, screenshot, dan ganti sumber audio |
| **Close by drag** | Geser bola ke target di tengah bawah untuk menutupnya, tanpa masuk ke aplikasi |
| **Hide while recording** | Bola tampil 2,5 detik saat rekaman mulai, lalu hilang supaya video bersih |
| **Summon by volume** | Tekan volume bawah dua kali untuk memunculkan bola lagi selama 5 detik. Kalau tombol volume sedang mengatur stream lain, tombol **Show control** di notifikasi tetap bisa dipakai |
| **Shake to stop** | Goyang dua kali saat merekam, dengan getar pendek sebagai tanda goyangan diterima |
| **Screenshot** | Bisa saat merekam atau tanpa merekam, tersimpan ke `Pictures/Zrec` |
| **Audio** | Off, Mic, Internal, atau Mic + internal. Kalau sumbernya ditolak HP, rekaman lanjut tanpa suara |
| **Auto split** | Rekaman dipecah otomatis tiap 5 menit di titik keyframe, jadi satu file bermasalah cuma menghilangkan satu potongan |
| **Quick Settings tile** | Tile di panel pengaturan cepat: tap saat merekam untuk stop, tap saat idle untuk membuka aplikasi |
| **Recent recordings** | Lima rekaman terakhir tampil di tab Record, tap untuk memutar. Setelah stop, notifikasi juga bisa membuka filenya |
| **Rotation handling** | Android 14+ hanya mengizinkan satu sesi tangkap per izin layar, jadi rekaman tetap hidup dengan orientasi awal saat ponsel diputar |
| **Honest options** | Resolusi dan frame rate difilter dari kemampuan encoder HP, bukan daftar karangan |

Hasil rekaman masuk ke `Movies/Zrec`, screenshot ke `Pictures/Zrec`.

## Quality limits

Frame rate dibatasi per resolusi supaya file tidak meledak dan encoder tidak tersendat. Batas HP tetap berlaku dan selalu menang.

| Resolution | Max frame rate |
|---|---|
| 720p | 144 fps |
| 1080p | 120 fps |
| 1440p | 90 fps |
| 4K | 60 fps |

Resolusi terendah 720p. Di bawah itu teks di layar berhenti terbaca setelah dikompres.

## Auto update

Zrec memeriksa rilis baru saat aplikasi dibuka. Kalau ada yang lebih baru, muncul baris **Version x.y.z available** di atas tombol rekam. Tap **Update**, APK diunduh, lalu installer Android terbuka.

Hasil pengecekan disimpan 30 menit. Tap berulang dalam rentang itu memakai jawaban yang sama, supaya batas 60 permintaan per jam dari GitHub tidak habis. Kalau jaringan gagal padahal masih ada jawaban tersimpan, jawaban lama yang dipakai, bukan pesan error.

Yang dibandingkan adalah **build number**, bukan nama versi. Nama versi muncul di tag rilis (`v1.1.0`), tapi angka yang menentukan update adalah build number di halaman rilis, ditulis sebagai komentar `<!-- zrec:versionCode=N -->`. Dengan begitu rilis yang di-tag ulang atau APK yang diunggah ulang tetap terdeteksi.

Android tidak mengizinkan aplikasi sideload memasang pembaruan tanpa konfirmasi. Jadi satu tap tetap diperlukan. Itu batas sistem, bukan pilihan Zrec.

Butuh versi tanpa jaringan sama sekali? Pakai [Zrec lite](https://github.com/Zdevlops/zrec-lite).

## Permissions

- **Screen capture** — dialog bawaan Android, muncul setiap kali mulai merekam.
- **Microphone** — hanya kalau audio disetel ke Mic atau Mic + internal.
- **Display over other apps** — hanya kalau floating control dinyalakan.
- **Notifications** — untuk kontrol rekam saat bola sedang disembunyikan.

- **Install unknown apps** — hanya diminta saat kamu menekan Update, supaya Android mau memasang APK baru.

Jaringan dipakai untuk satu hal saja: menanyakan rilis terbaru ke GitHub. Tidak ada analytics, tidak ada akun, tidak ada data yang dikirim.

## Verifikasi unduhan

Setiap APK punya SHA-256 yang dicantumkan di halaman release. Cocokkan sebelum memasang:

```powershell
# Windows
Get-FileHash Zrec-1.1.1.apk -Algorithm SHA256
```

```bash
# Termux / Linux
sha256sum Zrec-1.1.1.apk
```

Rilis terakhir:

| File | Ukuran | Build | SHA-256 |
|---|---|---|---|
| `Zrec-1.1.1.apk` | 105.541 byte | 13 | `9d9de9c045f51279a143e035dc538a63e659d85717e5e9648618c1ae78f36eb2` |

File `.sha256` juga ikut diunggah di setiap release, isinya satu baris siap dipakai `sha256sum -c`.

## Build

Butuh JDK 17 dan Android SDK. Tidak perlu Android Studio.

```bash
git clone https://github.com/Zdevlops/zrec.git
cd zrec
gradle assembleRelease
```

APK keluar di `app/build/outputs/apk/release/app-release.apk`.

Build lokal menghasilkan APK **tanpa tanda tangan**. Kunci rilis tidak ada di repo; CI menyimpannya sebagai secret dan menyuntikkannya saat build. Jadi kalau mau memasang hasil build sendiri, tandatangani dulu dengan keystore milik sendiri.

Repo ini juga punya GitHub Actions: setiap push ke `main` menghasilkan APK sebagai artifact, dan setiap tag `v*` menerbitkan release berisi APK saja.

## Structure

```
app/src/main/java/com/zrec/recorder/
  MainActivity.kt      layar utama dan pengaturan
  RecorderService.kt   foreground service, notifikasi, guncangan, rotasi
  ScreenRecorder.kt    MediaCodec + MediaMuxer, audio, jeda, pemecahan per 5 menit
  OverlayService.kt    floating control
  RecorderTileService.kt  tile pengaturan cepat
  Shot.kt              screenshot sekali jalan
  Caps.kt              pembacaan kemampuan encoder
  Updater.kt           cek rilis, unduh APK, buka installer
  ApkProvider.kt       penyaji file APK ke installer
  Config.kt            preferensi
```

## Known limits

- Floating control tidak bisa terlihat di layar tapi hilang dari rekaman dengan sempurna. Bola dan target tutupnya sekarang ditandai `FLAG_SECURE`, jadi tidak ikut terekam, tapi di sebagian HP areanya bisa muncul sebagai kotak hitam kecil. Karena itu bola tetap disembunyikan selama merekam, dan kontrol pindah ke notifikasi.
- Memanggil bola dengan volume bawah 2x hanya bekerja kalau volume benar-benar turun. Kalau media sudah di angka 0, tidak ada perubahan yang bisa dideteksi. Kontrol rekam tetap ada di notifikasi, dan bola muncul sendiri begitu rekaman selesai.
- Audio internal bisa kosong di aplikasi yang melarang perekaman suaranya. Ini keputusan aplikasi tersebut, bukan bug Zrec.
- Ganti sumber audio saat merekam baru berlaku untuk rekaman berikutnya, karena jalur audio dibuka sekali di awal file.
- Rekaman dipecah tiap 5 menit, jadi rekaman 20 menit menghasilkan empat file, bukan satu file besar.
- Rotasi di tengah rekaman menghasilkan dua file, bukan satu file yang berputar.

## License

[MIT](LICENSE). Pakai, ubah, dan bagikan bebas.
