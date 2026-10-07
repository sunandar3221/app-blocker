🔒 App Blocker

Turn any Android device into a simple, focused Kiosk.

App Blocker adalah aplikasi Android yang memungkinkan kamu mengunci perangkat ke satu aplikasi pilihan menggunakan Kiosk Mode.

Cocok untuk:

- 📱 Tablet kasir
- 🏫 Perangkat sekolah
- 🏭 Perangkat operasional
- 🖥️ Digital signage
- 🧪 Perangkat testing
- 🔐 Perangkat bersama yang perlu dibatasi aksesnya

«🛡️ One device. One app. Full focus.»

---

✨ Features

Feature| Description
🔒 Kiosk Mode| Mengunci perangkat agar tetap berada di aplikasi yang dipilih
📱 App Selector| Memilih aplikasi target dari aplikasi yang terinstal
🔑 PIN Protection| PIN diperlukan untuk keluar dari Kiosk Mode
♿ Accessibility Protection| Mendeteksi perpindahan aplikasi saat Kiosk aktif
🛡️ Device Admin| Menambahkan lapisan perlindungan administratif
🫧 Floating Exit Button| Tombol melayang untuk membuka menu keluar
⚙️ Native C++ Core| Core logic menggunakan C++17 dan Android NDK
🔐 Salted SHA-256| PIN diproses menggunakan salted SHA-256
🤖 GitHub Actions| Build APK otomatis melalui CI

---

🎯 How It Works

Secara sederhana, alurnya seperti ini:

┌──────────────────────┐
│     App Blocker      │
│                      │
│  1. Buat PIN         │
│  2. Pilih aplikasi   │
│  3. Aktifkan Kiosk   │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│     Kiosk Mode 🔒    │
│                      │
│    ┌────────────┐    │
│    │ Target App │    │
│    │   Running  │    │
│    └────────────┘    │
└──────────┬───────────┘
           │
           │ User mencoba
           │ berpindah aplikasi
           ▼
┌──────────────────────┐
│ Accessibility Service│
│      detects it      │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Kembali ke Target App│
└──────────────────────┘

Untuk keluar dari Kiosk Mode:

Tap Floating Exit Button
          │
          ▼
      Enter PIN
          │
     ┌────┴────┐
     ▼         ▼
  Correct    Incorrect
     │         │
     ▼         ▼
   Exit     Stay Locked 🔒

---

🚀 Quick Start

1. Clone Repository

git clone <your-repository-url>
cd app-blocker

2. Open with Android Studio

Buka folder project menggunakan Android Studio.

Pastikan environment berikut tersedia:

- Android SDK
- Android NDK
- CMake
- JDK yang sesuai dengan konfigurasi Gradle project

Native C++ source berada di:

app/src/main/cpp/

---

🤖 Build with GitHub Actions

Project ini sudah dilengkapi dengan GitHub Actions untuk melakukan build APK secara otomatis.

Requirements

Pastikan kamu sudah memiliki:

- Git
- GitHub account
- GitHub CLI ("gh")

Cek GitHub CLI:

gh --version

Jika belum login:

gh auth login

---

1. Push Project ke GitHub

Dari folder project:

git init
git branch -M main
git add .
git commit -m "feat: initial commit for App Blocker"

Kemudian buat repository dan push:

gh repo create app-blocker --public --source=. --remote=origin --push

GitHub Actions akan menjalankan workflow:

.github/workflows/build.yml

---

2. Monitor Build

Lihat daftar workflow:

gh run list --workflow=build.yml

Untuk mengikuti proses build secara langsung:

gh run watch

Tunggu sampai workflow selesai dengan status success ✅.

---

3. Download APK

Setelah build berhasil:

gh run download -n AppBlocker-debug-apk

APK hasil build akan tersedia di folder hasil download.

---

📱 Setup Kiosk Mode

Setelah APK terpasang, ikuti langkah berikut:

Install APK
    │
    ▼
Open App Blocker
    │
    ▼
Create Security PIN
    │
    ▼
Grant Required Permissions
    │
    ▼
Select Target Application
    │
    ▼
Enable Kiosk Mode
    │
    ▼
🔒 KIOSK ACTIVE

Saat Kiosk Mode aktif, App Blocker akan mencoba memastikan perangkat tetap berada di aplikasi target.

Untuk keluar:

Floating Exit Button
        │
        ▼
     Enter PIN
        │
   ┌────┴────┐
   ▼         ▼
 Correct   Incorrect
   │         │
   ▼         ▼
 Exit      Stay Locked 🔒

---

🔐 Required Permissions

App Blocker membutuhkan beberapa permission khusus Android.

🛡️ Device Admin

Component:

DeviceAdminReceiver

Digunakan untuk memberikan kemampuan administratif tertentu yang mendukung perlindungan perangkat.

---

♿ Accessibility Service

Component:

AccessibilityService

Digunakan untuk mendeteksi aplikasi atau window yang sedang aktif.

Ketika Kiosk Mode aktif, service ini membantu mencegah pengguna berpindah ke aplikasi yang tidak diizinkan.

---

🫧 Display Over Other Apps

Permission:

SYSTEM_ALERT_WINDOW

Digunakan untuk menampilkan floating exit button dan elemen UI tertentu di atas aplikasi target.

---

🧠 Security Architecture

App Blocker menggunakan native C++ module untuk menangani beberapa bagian dari core logic.

┌─────────────────────────┐
│     Android / Java      │
│                         │
│  UI / Services / Apps   │
└────────────┬────────────┘
             │
             │ JNI
             ▼
┌─────────────────────────┐
│       Native C++        │
│                         │
│  Kiosk State            │
│  App Whitelist          │
│  PIN Verification       │
│  SHA-256 Hashing        │
└─────────────────────────┘

🔑 PIN Hashing

PIN tidak disimpan sebagai plaintext.

Secara sederhana:

PIN
 │
 +── Salt
 │
 ▼
SHA-256
 │
 ▼
Stored Hash

Native security module menggunakan:

- C++17
- Android NDK
- CMake
- JNI
- SHA-256

«⚠️ Hashing bukan berarti PIN dapat dipulihkan kembali. Pastikan PIN tetap diingat dan jangan membagikannya kepada orang yang tidak berwenang.»

---

🏗️ Project Structure

App Blocker/
│
├── .github/
│   └── workflows/
│       └── build.yml
│           # GitHub Actions CI
│
├── app/
│   │
│   ├── CMakeLists.txt
│   │   # NDK / CMake configuration
│   │
│   ├── build.gradle
│   │   # Android module configuration
│   │
│   └── src/main/
│       │
│       ├── cpp/
│       │   # Native C++ source
│       │
│       │   ├── sha256.hpp
│       │   ├── sha256.cpp
│       │   │   # SHA-256 implementation
│       │   │
│       │   ├── kiosk_core.hpp
│       │   ├── kiosk_core.cpp
│       │   │   # Kiosk state & whitelist logic
│       │   │
│       │   └── app_blocker.cpp
│       │       # JNI bridge
│       │
│       ├── java/com/appblocker/kiosk/
│       │   │
│       │   ├── MainActivity.java
│       │   │   # Main screen & settings
│       │   │
│       │   ├── UnlockActivity.java
│       │   │   # PIN unlock screen
│       │   │
│       │   ├── NativeKioskManager.java
│       │   │   # Java ↔ C++ interface
│       │   │
│       │   ├── KioskAccessibilityService.java
│       │   │   # Accessibility monitoring
│       │   │
│       │   ├── KioskDeviceAdminReceiver.java
│       │   │   # Device Admin receiver
│       │   │
│       │   ├── FloatingExitService.java
│       │   │   # Floating exit button
│       │   │
│       │   ├── AppInfo.java
│       │   │   # Application model
│       │   │
│       │   └── AppAdapter.java
│       │       # Application list adapter
│       │
│       └── res/
│           # Android resources
│
└── build.gradle
    # Root project configuration

---

🛠️ Tech Stack

Technology| Purpose
☕ Java| Android application & UI
⚙️ C++17| Native kiosk & security logic
🧰 Android NDK| Native C++ development
🔗 JNI| Java ↔ C++ communication
🔐 SHA-256| PIN hashing
♿ Accessibility Service| Application/window monitoring
🛡️ Device Admin| Device administration
🫧 SYSTEM_ALERT_WINDOW| Floating UI
🤖 GitHub Actions| Automated build
🐙 GitHub CLI| Repository & workflow management

---

🧪 Development

Untuk development, kamu bisa menggunakan Android Studio dengan Android SDK, NDK, dan CMake yang sesuai dengan konfigurasi project.

Android Layer

Source Android berada di:

app/src/main/java/com/appblocker/kiosk/

Native Layer

Source C++ berada di:

app/src/main/cpp/

Core Kiosk logic:

kiosk_core.hpp
kiosk_core.cpp

JNI bridge:

app_blocker.cpp
NativeKioskManager.java

---

🐛 Troubleshooting

Kiosk Mode tidak mengunci aplikasi

Coba periksa:

- Accessibility Service sudah aktif.
- Semua permission yang diperlukan sudah diberikan.
- Aplikasi target masih terinstal.
- Tidak ada policy OEM yang membatasi Accessibility Service.

---

Floating Button tidak muncul

Pastikan permission:

Display over other apps

sudah diaktifkan untuk App Blocker.

---

Tidak bisa keluar dari Kiosk Mode

Pastikan PIN yang dimasukkan benar.

Jika sedang melakukan development atau testing, jangan mengaktifkan Kiosk Mode pada perangkat utama sebelum memastikan mekanisme exit sudah bekerja dengan baik.

---

⚠️ Important Notes

App Blocker menggunakan permission Android yang cukup kuat, terutama:

- Accessibility Service
- Device Admin
- Display Over Other Apps

Gunakan aplikasi ini hanya pada perangkat yang memang kamu miliki atau memiliki izin untuk mengelolanya.

Sangat disarankan untuk melakukan testing terlebih dahulu sebelum digunakan pada perangkat produksi.

Android Compatibility

Perilaku Kiosk Mode dapat berbeda tergantung:

- Versi Android
- Manufacturer / OEM
- Kebijakan keamanan perangkat
- Implementasi Accessibility Service
- Device management policy

Jadi, hasil pada satu perangkat belum tentu sama dengan perangkat lainnya.

---

🤝 Contributing

Pull Request, bug report, dan improvement sangat dipersilakan! ❤️

Reporting a Bug

Sebelum membuat issue:

1. Pastikan issue tersebut belum pernah dilaporkan.
2. Gunakan versi aplikasi terbaru.
3. Sertakan versi Android.
4. Sertakan informasi perangkat jika memungkinkan.
5. Jelaskan langkah untuk mereproduksi masalah.

Creating a Pull Request

Buat branch baru:

git checkout -b feature/my-feature

Lakukan perubahan, commit, kemudian buat Pull Request.

---

📄 License

App Blocker dirilis di bawah:

GNU General Public License v3.0

GPL-3.0-only

Kamu bebas untuk:

- ✅ Menggunakan software
- ✅ Mempelajari source code
- ✅ Memodifikasi software
- ✅ Membagikan software
- ✅ Membagikan versi yang telah dimodifikasi

Distribusi versi yang dimodifikasi tetap harus mengikuti ketentuan GNU GPL v3.0, termasuk persyaratan terkait source code dan lisensi.

Lihat file ""LICENSE"" (LICENSE) untuk teks lengkap lisensi.

SPDX-License-Identifier: "GPL-3.0-only"

---

⭐ Support the Project

Kalau project ini berguna buat kamu, jangan lupa kasih ⭐ Star di GitHub!

Star dari kamu membantu project ini lebih mudah ditemukan oleh developer lain. ❤️

---

<div align="center">🔒 App Blocker

Simple Kiosk Mode for Android

Made with ☕ Java + ⚙️ C++ + ❤️

Licensed under GPL-3.0-only

</div>