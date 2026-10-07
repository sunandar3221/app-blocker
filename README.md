# 🔒 App Blocker

Kunci HP Android kamu hanya ke satu aplikasi pilihan. Cocok untuk fokus belajar atau bekerja, atau saat meminjamkan HP ke anak tanpa khawatir dia membuka aplikasi lain. Keluar dari mode ini hanya bisa lewat PIN rahasia kamu.

Di balik layar, bagian keamanannya ditulis dengan **C++17 (Android NDK)** supaya lebih cepat dan sulit diutak-atik.



![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)



---

## ✨ Apa yang Bisa Dilakukan?

- **Pilih satu aplikasi target**: Pilih aplikasi apa pun yang terpasang di HP untuk dijadikan aplikasi Kiosk.
- **Atur PIN di awal**: Saat pertama kali dibuka, kamu diminta membuat PIN pengaman.
- **Keluar wajib pakai PIN**: Selama Kiosk Mode aktif, tidak ada jalan keluar tanpa PIN yang benar.
- **Pengawasan real-time**: Kalau ada yang mencoba pindah aplikasi, membuka pengaturan, menarik notifikasi, atau kembali ke home, layar langsung dikembalikan ke aplikasi Kiosk.
- **Perlindungan Device Admin**: Mempersulit upaya mencopot paksa atau mengakali sistem penguncian.
- **Tombol keluar melayang**: Gelembung kecil di layar yang bisa diketuk kapan saja untuk memunculkan dialog PIN.
- **Inti keamanan native**: Logika penguncian, hashing PIN (SHA-256 + salt), dan filter paket aplikasi berjalan di modul C++.

---

## 📥 Cara Mendapatkan Aplikasi

Ada dua cara, pilih yang paling nyaman buat kamu:

### Opsi 1: Unduh dari Releases (paling mudah)

1. Buka halaman **[Releases](../../releases)** di repositori ini.
2. Unduh file APK versi terbaru.
3. Pasang di HP Android kamu. Kalau diminta, izinkan pemasangan dari sumber tidak dikenal.

### Opsi 2: Build sendiri

Kalau kamu ingin membangun APK dari kode sumber, ikuti panduan di bagian [Build Otomatis dengan GitHub Actions](#-build-otomatis-dengan-github-actions) di bawah.

---

## 🔑 Izin yang Dibutuhkan

Aplikasi ini meminta tiga izin berikut agar bisa bekerja dengan benar:

| Izin | Fungsinya |
|------|-----------|
| **Device Admin** | Melindungi sistem penguncian agar tidak mudah dimanipulasi. |
| **Aksesibilitas** | Mendeteksi perpindahan aplikasi dan langsung memblokir yang tidak diizinkan. |
| **Tampil di atas aplikasi lain** (`SYSTEM_ALERT_WINDOW`) | Menampilkan tombol melayang dan dialog PIN di atas aplikasi target. |

---

## 🗂️ Struktur Proyek

```
App Blocker/
├── .github/
│   └── workflows/
│       └── build.yml                  # CI/CD: build APK lewat GitHub Actions
├── app/
│   ├── CMakeLists.txt                 # Konfigurasi CMake untuk NDK
│   ├── build.gradle                   # Konfigurasi build modul app
│   └── src/main/
│       ├── cpp/                       # Kode native C++
│       │   ├── sha256.hpp / .cpp      # Implementasi SHA-256
│       │   ├── kiosk_core.hpp / .cpp  # Logika inti Kiosk, whitelist, state machine
│       │   └── app_blocker.cpp        # Jembatan JNI
│       ├── java/com/appblocker/kiosk/
│       │   ├── NativeKioskManager.java        # Pemuat library native (JNI)
│       │   ├── MainActivity.java              # Layar pengaturan & izin
│       │   ├── UnlockActivity.java            # Layar PIN untuk keluar Kiosk
│       │   ├── KioskAccessibilityService.java # Pemblokir via Aksesibilitas
│       │   ├── KioskDeviceAdminReceiver.java  # Receiver Device Admin
│       │   ├── FloatingExitService.java       # Service tombol melayang
│       │   ├── AppInfo.java                   # Model data aplikasi
│       │   └── AppAdapter.java                # Adapter daftar aplikasi
│       └── res/                       # Layout UI & resource
└── build.gradle
```

---

## 🚀 Build Otomatis dengan GitHub Actions

Kamu tidak perlu menyiapkan Android Studio atau NDK di komputer. Seluruh proses build berjalan di **GitHub Actions**, dan semuanya bisa dikendalikan lewat GitHub CLI (`gh`).

### 1. Push proyek ke GitHub

```bash
git init
git branch -M main
git add .
git commit -m "feat: initial commit for App Blocker with native C++ NDK kiosk engine"
gh repo create app-blocker --public --source=. --remote=origin --push
```

### 2. Pantau proses build

```bash
gh run list --workflow=build.yml
gh run watch
```

### 3. Unduh APK hasil build

```bash
gh run download -n AppBlocker-debug-apk
```

Setelah selesai, pasang APK-nya di HP Android kamu dan aplikasi siap dipakai. 🎉

---

## 📄 Lisensi

Proyek ini dirilis di bawah lisensi **GNU General Public License v3.0 (GPL-3.0)**. Kamu bebas memakai, mempelajari, mengubah, dan membagikannya, selama turunannya juga dirilis dengan lisensi yang sama. Detail lengkapnya ada di file [LICENSE](LICENSE).