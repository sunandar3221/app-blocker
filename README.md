# App Blocker (Kiosk Mode Android App)

Aplikasi Android untuk mengunci perangkat dalam **Kiosk Mode** pada aplikasi tertentu yang dipilih oleh pengguna. Ditenagai oleh modul keamanan native **C++ (NDK)** untuk enkripsi, hashing PIN ber-salt (SHA-256), dan validasi status kiosk.

---

## ✨ Fitur Utama

1. **Pemilihan Aplikasi Target Kiosk Mode**:
   - Memilih satu aplikasi apapun yang terinstal di perangkat untuk dijadikan aplikasi Kiosk.
2. **Pengamanan PIN Pertama Kali**:
   - Saat pertama kali membuka aplikasi, pengguna diminta untuk mengatur PIN pengaman rahasia.
3. **Keluaran Terproteksi Password/PIN**:
   - Setelah masuk Kiosk Mode, pengguna **wajib** memasukkan PIN untuk dapat keluar. Tanpa PIN yang valid, Kiosk Mode tidak dapat dihentikan.
4. **Proteksi Aksesibilitas (Accessibility Service)**:
   - Memantau jendela aplikasi secara real-time. Jika pengguna mencoba berpindah aplikasi, membuka pengaturan, notifikasi, atau kembali ke home screen, aplikasi akan secara otomatis mengembalikan fokus ke aplikasi Kiosk yang diizinkan.
5. **Proteksi Device Admin**:
   - Mencegah manipulasi paksa dan memperkuat hak istimewa perlindungan sistem Kiosk.
6. **Overlay Tombol Keluar Cepat**:
   - Floating bubble overlay di layar yang dapat diketuk kapan saja untuk memunculkan dialog verifikasi PIN keluar Kiosk.
7. **Modul Native C++**:
   - Logika inti penguncian, verifikasi SHA-256 tersalt, dan filter paket aplikasi ditulis menggunakan **C++17** dan dikompilasi menggunakan **Android NDK**.

---

## 🔒 Izin Sistem yang Diperlukan

1. **Device Admin (`DeviceAdminReceiver`)**:
   - Memberikan otoritas administratif perangkat agar sistem penguncian terlindungi.
2. **Aksesibilitas (`AccessibilityService`)**:
   - Mendeteksi perpindahan window/aplikasi dan secara instan memblokir aplikasi yang tidak diizinkan saat Kiosk aktif.
3. **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)**:
   - Menampilkan tombol melayang (floating exit trigger) dan dialog verifikasi PIN di atas aplikasi target.

---

## 🛠️ Struktur Proyek

```
App Blocker/
├── .github/
│   └── workflows/
│       └── build.yml             # GitHub Actions CI/CD Build APK
├── app/
│   ├── CMakeLists.txt            # NDK CMake Configuration
│   ├── build.gradle              # App build configuration
│   └── src/main/
│       ├── cpp/                  # Native C++ Source Code
│       │   ├── sha256.hpp        # Implementasi SHA-256 Header
│       │   ├── sha256.cpp        # Implementasi SHA-256 Native
│       │   ├── kiosk_core.hpp    # Core Kiosk Logic & Whitelist
│       │   ├── kiosk_core.cpp    # State Machine & Security Logic
│       │   └── app_blocker.cpp   # JNI Bridge Functions
│       ├── java/com/appblocker/kiosk/
│       │   ├── NativeKioskManager.java       # Native JNI Loader
│       │   ├── MainActivity.java             # Layar Pengaturan & Izin
│       │   ├── UnlockActivity.java           # Layar PIN Keluar Kiosk
│       │   ├── KioskAccessibilityService.java# Layar Pemblokir Aksesibilitas
│       │   ├── KioskDeviceAdminReceiver.java # Device Admin Receiver
│       │   ├── FloatingExitService.java      # Service Tombol Melayang
│       │   ├── AppInfo.java                  # Model Item Aplikasi
│       │   └── AppAdapter.java               # Adapter Daftar Aplikasi
│       └── res/                              # UI Layouts & Resources
└── build.gradle
```

---

## 🚀 Build Otomatis di GitHub Menggunakan GitHub CLI

Sesuai instruksi, proses build dan setup dijalankan sepenuhnya di **GitHub Actions** menggunakan GitHub CLI (`gh`).

### 1. Inisialisasi dan Push ke GitHub:
```bash
git init
git branch -M main
git add .
git commit -m "feat: initial commit for App Blocker with native C++ NDK kiosk engine"
gh repo create app-blocker --public --source=. --remote=origin --push
```

### 2. Memantau Status Build di GitHub:
```bash
gh run list --workflow=build.yml
gh run watch
```

### 3. Mengunduh APK Hasil Build:
```bash
gh run download -n AppBlocker-debug-apk
```
APK siap diinstal ke perangkat Android Anda.
