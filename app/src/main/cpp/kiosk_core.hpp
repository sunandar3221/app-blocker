#ifndef KIOSK_CORE_HPP
#define KIOSK_CORE_HPP

#include <string>
#include <unordered_set>
#include <mutex>

namespace appblocker {

class KioskCore {
public:
    static KioskCore& getInstance();

    void initialize(const std::string& storageDir);

    bool hasPin();
    bool setPin(const std::string& pin);
    bool verifyPin(const std::string& pin);

    bool startKiosk(const std::string& targetPackage);
    bool stopKiosk(const std::string& pin);

    bool isKioskActive();
    std::string getTargetPackage();

    bool isPackageAllowed(const std::string& packageName);

private:
    KioskCore();
    ~KioskCore() = default;
    KioskCore(const KioskCore&) = delete;
    KioskCore& operator=(const KioskCore&) = delete;

    std::string getPinFilePath() const;
    std::string getStateFilePath() const;
    void loadPinHash();
    void savePinHash(const std::string& hash);
    void loadState();
    void saveState();

    mutable std::mutex mutex_;
    std::string storageDir_;
    std::string storedPinHash_;
    std::string targetPackage_;
    bool isKioskActive_;
    std::unordered_set<std::string> systemAllowedPackages_;

    static constexpr const char* SALT_PREFIX = "APPBLOCKER_SALT_v1_";
};

} // namespace appblocker

#endif // KIOSK_CORE_HPP
