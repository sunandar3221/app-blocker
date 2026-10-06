#include "kiosk_core.hpp"
#include "sha256.hpp"
#include <fstream>
#include <sstream>
#include <android/log.h>

#define TAG "KioskCoreNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace appblocker {

KioskCore& KioskCore::getInstance() {
    static KioskCore instance;
    return instance;
}

KioskCore::KioskCore() : isKioskActive_(false) {
    // Whitelist core Android framework and critical system UI components
    systemAllowedPackages_.insert("android");
    systemAllowedPackages_.insert("com.android.systemui");
    systemAllowedPackages_.insert("com.appblocker.kiosk");
    systemAllowedPackages_.insert("com.google.android.permissioncontroller");
    systemAllowedPackages_.insert("com.android.permissioncontroller");
    systemAllowedPackages_.insert("com.google.android.packageinstaller");
    systemAllowedPackages_.insert("com.android.packageinstaller");
    systemAllowedPackages_.insert("com.google.android.gms");
    systemAllowedPackages_.insert("android.ext.services");

    // Standard keyboards and OEM input methods
    systemAllowedPackages_.insert("com.google.android.inputmethod.latin");
    systemAllowedPackages_.insert("com.sec.android.inputmethod");
    systemAllowedPackages_.insert("com.sec.android.inputmethod.i18n");
    systemAllowedPackages_.insert("com.samsung.android.honeyboard");
    systemAllowedPackages_.insert("com.touchtype.swiftkey");
    systemAllowedPackages_.insert("com.android.inputmethod.latin");
    systemAllowedPackages_.insert("com.baidu.input_yousa");
    systemAllowedPackages_.insert("com.coloros.keyboard");
    systemAllowedPackages_.insert("com.oppo.keyboard");
    systemAllowedPackages_.insert("com.vivo.ime");
    systemAllowedPackages_.insert("com.miui.virtualsim");
    systemAllowedPackages_.insert("com.facemoji.lite.xiaomi");
}

void KioskCore::initialize(const std::string& storageDir) {
    std::lock_guard<std::mutex> lock(mutex_);
    storageDir_ = storageDir;
    loadPinHash();
    loadState();
    LOGD("Initialized KioskCore with storage path: %s (active: %d, target: %s)",
         storageDir_.c_str(), isKioskActive_ ? 1 : 0, targetPackage_.c_str());
}

std::string KioskCore::getPinFilePath() const {
    if (storageDir_.empty()) {
        return "kiosk_pin.bin";
    }
    return storageDir_ + "/kiosk_pin.bin";
}

std::string KioskCore::getStateFilePath() const {
    if (storageDir_.empty()) {
        return "kiosk_state.bin";
    }
    return storageDir_ + "/kiosk_state.bin";
}

void KioskCore::loadPinHash() {
    std::string path = getPinFilePath();
    std::ifstream file(path);
    if (file.is_open()) {
        std::getline(file, storedPinHash_);
        file.close();
        LOGD("Pin hash loaded successfully.");
    } else {
        storedPinHash_.clear();
        LOGD("No stored pin hash found.");
    }
}

void KioskCore::savePinHash(const std::string& hash) {
    std::string path = getPinFilePath();
    std::ofstream file(path, std::ios::trunc);
    if (file.is_open()) {
        file << hash;
        file.close();
        storedPinHash_ = hash;
        LOGD("Pin hash saved successfully to %s", path.c_str());
    } else {
        LOGE("Failed to write pin hash to %s", path.c_str());
    }
}

void KioskCore::loadState() {
    std::string path = getStateFilePath();
    std::ifstream file(path);
    if (file.is_open()) {
        std::string activeStr;
        if (std::getline(file, activeStr)) {
            isKioskActive_ = (activeStr == "1");
        }
        std::getline(file, targetPackage_);
        file.close();
        LOGD("Loaded kiosk state: active=%d, target=%s", isKioskActive_ ? 1 : 0, targetPackage_.c_str());
    } else {
        isKioskActive_ = false;
        targetPackage_.clear();
    }
}

void KioskCore::saveState() {
    std::string path = getStateFilePath();
    std::ofstream file(path, std::ios::trunc);
    if (file.is_open()) {
        file << (isKioskActive_ ? "1\n" : "0\n");
        file << targetPackage_ << "\n";
        file.close();
        LOGD("Saved kiosk state: active=%d, target=%s", isKioskActive_ ? 1 : 0, targetPackage_.c_str());
    } else {
        LOGE("Failed to save kiosk state to %s", path.c_str());
    }
}

bool KioskCore::hasPin() {
    std::lock_guard<std::mutex> lock(mutex_);
    return !storedPinHash_.empty();
}

bool KioskCore::setPin(const std::string& pin) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (pin.empty()) {
        return false;
    }
    std::string salted = std::string(SALT_PREFIX) + pin;
    std::string hash = SHA256::hash(salted);
    savePinHash(hash);
    return true;
}

bool KioskCore::verifyPin(const std::string& pin) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (storedPinHash_.empty() || pin.empty()) {
        return false;
    }
    std::string salted = std::string(SALT_PREFIX) + pin;
    std::string computedHash = SHA256::hash(salted);
    return (computedHash == storedPinHash_);
}

bool KioskCore::changePin(const std::string& oldPin, const std::string& newPin) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (storedPinHash_.empty() || oldPin.empty() || newPin.empty()) {
        return false;
    }
    // Verify old PIN
    std::string oldSalted = std::string(SALT_PREFIX) + oldPin;
    std::string computedOldHash = SHA256::hash(oldSalted);
    if (computedOldHash != storedPinHash_) {
        LOGD("Failed to change PIN: old PIN mismatch.");
        return false;
    }

    // Save new PIN
    std::string newSalted = std::string(SALT_PREFIX) + newPin;
    std::string newHash = SHA256::hash(newSalted);
    savePinHash(newHash);
    LOGD("PIN changed successfully.");
    return true;
}

bool KioskCore::startKiosk(const std::string& targetPackage) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (targetPackage.empty() || storedPinHash_.empty()) {
        LOGE("Cannot start kiosk: invalid target or no PIN set.");
        return false;
    }
    targetPackage_ = targetPackage;
    isKioskActive_ = true;
    saveState();
    LOGD("Kiosk mode STARTED and PERSISTED for package: %s", targetPackage.c_str());
    return true;
}

bool KioskCore::stopKiosk(const std::string& pin) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (storedPinHash_.empty() || pin.empty()) {
        return false;
    }
    std::string salted = std::string(SALT_PREFIX) + pin;
    std::string computedHash = SHA256::hash(salted);
    if (computedHash == storedPinHash_) {
        isKioskActive_ = false;
        targetPackage_.clear();
        saveState();
        LOGD("Kiosk mode STOPPED successfully with valid PIN.");
        return true;
    }
    LOGD("Failed to stop kiosk: invalid PIN.");
    return false;
}

bool KioskCore::isKioskActive() {
    std::lock_guard<std::mutex> lock(mutex_);
    return isKioskActive_;
}

std::string KioskCore::getTargetPackage() {
    std::lock_guard<std::mutex> lock(mutex_);
    return targetPackage_;
}

void KioskCore::addAllowedPackage(const std::string& packageName) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!packageName.empty()) {
        systemAllowedPackages_.insert(packageName);
        LOGD("Dynamically allowed package added: %s", packageName.c_str());
    }
}

bool KioskCore::isPackageAllowed(const std::string& packageName) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!isKioskActive_) {
        return true;
    }

    if (packageName.empty()) {
        return true;
    }

    if (packageName == targetPackage_) {
        return true;
    }

    if (systemAllowedPackages_.find(packageName) != systemAllowedPackages_.end()) {
        return true;
    }

    // Permit all keyboards, input methods, and autofill components without blocking
    if (packageName.find("inputmethod") != std::string::npos ||
        packageName.find("keyboard") != std::string::npos ||
        packageName.find("honeyboard") != std::string::npos ||
        packageName.find("swiftkey") != std::string::npos ||
        packageName.find("ime") != std::string::npos ||
        packageName.find("latin") != std::string::npos ||
        packageName.find("autofill") != std::string::npos ||
        packageName.find("touchtype") != std::string::npos) {
        return true;
    }

    return false;
}

} // namespace appblocker
