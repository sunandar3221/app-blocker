🔒 App Blocker

Turn any Android device into a simple, focused Kiosk.

App Blocker adalah aplikasi Android yang memungkinkan kamu mengunci perangkat ke satu aplikasi pilihan menggunakan Kiosk Mode.

Cocok untuk tablet kasir, perangkat sekolah, digital signage, perangkat operasional, testing device, atau perangkat apa pun yang perlu membatasi akses pengguna ke aplikasi tertentu.

«🛡️ One device. One app. Full focus.»

---

✨ Highlights

Fitur| Deskripsi
🔒 Kiosk Mode| Mengunci perangkat agar tetap berada di aplikasi yang dipilih
📱 App Selector| Pilih aplikasi target langsung dari daftar aplikasi yang terinstal
🔑 PIN Protection| PIN diperlukan untuk keluar dari Kiosk Mode
♿ Accessibility Protection| Mendeteksi perpindahan aplikasi dan mencoba mengembalikan fokus ke aplikasi Kiosk
🛡️ Device Admin| Menambahkan lapisan perlindungan administratif
🫧 Floating Exit Button| Tombol melayang untuk membuka menu keluar
⚙️ Native C++ Core| Core security dan kiosk logic menggunakan C++17 + Android NDK
🔐 Salted SHA-256| PIN disimpan dalam bentuk hash dengan salt
🤖 GitHub Actions| Build APK otomatis melalui CI

---

🎯 Cara Kerjanya

Konsep App Blocker sebenarnya cukup sederhana:

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
│   ┌──────────────┐   │
│   │ Target App   │   │
│   │              │   │
│   │   Running    │   │
│   │              │   │
│   └──────────────┘   │
└──────────┬───────────┘
           │
           │ User mencoba
           │ keluar / pindah app
           ▼
┌──────────────────────┐
│ Accessibility Service│
│     detects it       │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Kembali ke Target App│
└──────────────────────┘

             🚪
        Exit Kiosk
             │
             ▼
       ┌───────────┐
       │ Enter PIN │
       └─────┬─────┘
             │
       ┌─────▼─────┐
       │ Valid PIN?│
       └──┬─────┬──┘
          YES    NO
           │      │
           ▼      ▼
         Exit   Stay 🔒

---

🚀 Quick Start

1. Clone Repository

git clone <your-repository-url>
cd app-blocker

2. Build Project

Project menggunakan Android NDK + CMake, jadi pastikan environment Android development kamu sudah tersedia.

Struktur native module berada di:

app/src/main/cpp/

Kemudian lakukan build menggunakan Android Studio atau Gradle.

---

🤖 Build Otomatis dengan GitHub Actions

Kalau kamu tidak ingin build APK secara manual, project ini juga menyediakan workflow GitHub Actions.

Yang kamu butuhkan:

- Git
- GitHub account
- GitHub CLI ("gh")

Cek GitHub CLI:

gh --version

Login jika belum:

gh auth login

---

📦 Push Project ke GitHub

Dari folder project:

git init
git branch -M main
git add .
git commit -m "feat: initial commit for App Blocker"

Buat repository sekaligus push:

gh repo create app-blocker --public --source=. --remote=origin --push

GitHub Actions kemudian akan menjalankan workflow:

.github/workflows/build.yml

---

👀 Monitor Build

Lihat daftar workflow:

gh run list --workflow=build.yml

Pantau build secara langsung:

gh run watch

Jika build berhasil, kamu akan mendapatkan artifact APK.

---

📥 Download APK

Download artifact:

gh run download -n AppBlocker-debug-apk

APK kemudian siap dipindahkan dan diinstal ke perangkat Android.

---

📱 Setup Kiosk Mode

Setelah APK terpasang, ikuti alur berikut:

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
      🔒
   KIOSK ACTIVE

Ketika Kiosk Mode aktif, App Blocker akan mencoba memastikan perangkat tetap berada di aplikasi target.

Untuk keluar:

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

Digunakan untuk mendeteksi perubahan aplikasi/window yang sedang aktif.

Ketika Kiosk Mode aktif, service ini membantu mencegah pengguna berpindah ke aplikasi yang tidak diizinkan.

---

🫧 Display Over Other Apps

Permission:

SYSTEM_ALERT_WINDOW

Diperlukan untuk menampilkan floating exit button dan elemen UI tertentu di atas aplikasi target.

---

🧠 Security

App Blocker menggunakan native module untuk menangani beberapa bagian dari core logic.

Java / Android
      │
      │ JNI
      ▼
┌─────────────────────┐
│     Native C++     │
│                     │
│  Kiosk State        │
│  App Whitelist      │
│  PIN Verification   │
│  SHA-256 Hashing    │
│                     │
└─────────────────────┘

🔑 PIN Hashing

PIN tidak digunakan sebagai plaintext untuk proses validasi.

Konsep sederhananya:

PIN
 │
 +── Salt
 │
 ▼
SHA-256
 │
 ▼
Stored Hash

Native security module ditulis menggunakan:

- C++17
- Android NDK
- CMake
- JNI
- SHA-256

«⚠️ Hashing bukan berarti PIN bisa dipulihkan kembali. Pastikan PIN tetap disimpan dengan aman dan jangan membagikannya kepada pengguna yang tidak berwenang.»

---

🏗️ Project Structure

App Blocker/
│
├── .github/
│   └── workflows/
│       └── build.yml
│           └── GitHub Actions CI
│
├── app/
│   │
│   ├── CMakeLists.txt
│   │   └── NDK / CMake configuration
│   │
│   ├── build.gradle
│   │
│   └── src/main/
│       │
│       ├── cpp/
│       │   │
│       │   ├── sha256.hpp
│       │   ├── sha256.cpp
│       │   │   └── SHA-256 implementation
│       │   │
│       │   ├── kiosk_core.hpp
│       │   ├── kiosk_core.cpp
│       │   │   └── Kiosk state & whitelist logic
│       │   │
│       │   └── app_blocker.cpp
│       │       └── JNI bridge
│       │
│       ├── java/com/appblocker/kiosk/
│       │   │
│       │   ├── MainActivity.java
│       │   │   └── Main screen & settings
│       │   │
│       │   ├── UnlockActivity.java
│       │   │   └── PIN unlock screen
│       │   │
│       │   ├── NativeKioskManager.java
│       │   │   └── Java ↔ C++ interface
│       │   │
│       │   ├── KioskAccessibilityService.java
│       │   │   └── Accessibility monitoring
│       │   │
│       │   ├── KioskDeviceAdminReceiver.java
│       │   │   └── Device Admin receiver
│       │   │
│       │   ├── FloatingExitService.java
│       │   │   └── Floating exit button
│       │   │
│       │   ├── AppInfo.java
│       │   │   └── Application model
│       │   │
│       │   └── AppAdapter.java
│       │       └── Application list adapter
│       │
│       └── res/
│           └── Android resources
│
└── build.gradle
    └── Root project configuration

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

⚠️ Important Notes

App Blocker menggunakan permission Android yang cukup kuat, terutama:

- Accessibility Service
- Device Admin
- Display Over Other Apps

Gunakan aplikasi ini hanya pada perangkat yang memang kamu miliki atau memiliki izin untuk mengelolanya.

Sebelum digunakan pada perangkat produksi, sangat disarankan untuk melakukan testing terlebih dahulu.

Android Compatibility

Perilaku Kiosk Mode dapat berbeda tergantung:

- Versi Android
- Manufacturer / OEM
- Kebijakan keamanan perangkat
- Implementasi Accessibility Service
- Device management policy

Dengan kata lain, hasil pada satu perangkat belum tentu identik dengan perangkat lainnya.

---

🧪 Development

Untuk melakukan development, kamu dapat menggunakan Android Studio dengan Android SDK, NDK, dan CMake yang sesuai dengan konfigurasi project.

Bagian native berada di:

app/src/main/cpp/

Sedangkan Android application layer berada di:

app/src/main/java/com/appblocker/kiosk/

Jika ingin mengubah logika Kiosk, bagian utama yang perlu diperhatikan adalah:

kiosk_core.cpp
kiosk_core.hpp

Jika ingin mengubah komunikasi antara Java dan native layer:

app_blocker.cpp
NativeKioskManager.java

---

🐛 Troubleshooting

Kiosk Mode tidak mengunci aplikasi

Pastikan:

- Accessibility Service sudah aktif.
- Permission yang diperlukan sudah diberikan.
- Aplikasi target masih terinstal.
- Perangkat tidak memiliki policy OEM yang mengganggu Accessibility Service.

---

Floating button tidak muncul

Periksa permission:

Display over other apps

Pastikan App Blocker diizinkan untuk menampilkan overlay.

---

Tidak bisa keluar dari Kiosk Mode

Pastikan kamu menggunakan PIN yang benar.

Jika sedang melakukan development/testing, jangan mengaktifkan Kiosk Mode pada perangkat utama sebelum memastikan mekanisme exit sudah bekerja dengan baik.

---

🤝 Contributing

Pull Request, bug report, dan improvement sangat dipersilakan! ❤️

Kalau menemukan bug:

1. Cek apakah issue tersebut sudah pernah dilaporkan.
2. Buat issue baru jika belum ada.
3. Sertakan versi Android dan informasi perangkat jika memungkinkan.
4. Jelaskan langkah untuk mereproduksi masalah.

Untuk kontribusi kode:

git checkout -b feature/my-feature

Lakukan perubahan, kemudian buat Pull Request.

---

📄 License

App Blocker dirilis di bawah:

GNU General Public License v3.0

GPL-3.0

Kamu bebas untuk:

- ✅ Menggunakan software
- ✅ Mempelajari source code
- ✅ Memodifikasi software
- ✅ Membagikan software
- ✅ Membagikan versi yang telah dimodifikasi

Dengan syarat distribusi tetap mengikuti ketentuan GNU GPL v3.0, termasuk kewajiban terkait source code dan lisensi.

Lihat file ""LICENSE"" (LICENSE) untuk teks lengkap lisensi.

SPDX-License-Identifier: "GPL-3.0-only"

---

⭐ Support the Project

Kalau project ini berguna buat kamu, jangan lupa kasih ⭐ Star di GitHub!

Star kecil dari kamu membantu project ini lebih mudah ditemukan oleh developer lain. ❤️

---

<div align="center">🔒 App Blocker

Simple Kiosk Mode for Android

Made with ☕ Java + ⚙️ C++ + ❤️

GPL-3.0

</div>