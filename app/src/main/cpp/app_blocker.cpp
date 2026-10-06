#include <jni.h>
#include <string>
#include "kiosk_core.hpp"

static std::string jstringToString(JNIEnv* env, jstring jstr) {
    if (!jstr) {
        return "";
    }
    const char* utf = env->GetStringUTFChars(jstr, nullptr);
    std::string str(utf);
    env->ReleaseStringUTFChars(jstr, utf);
    return str;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_appblocker_kiosk_NativeKioskManager_nativeInit(JNIEnv* env, jclass /* clazz */, jstring storageDir) {
    std::string dir = jstringToString(env, storageDir);
    appblocker::KioskCore::getInstance().initialize(dir);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_appblocker_kiosk_NativeKioskManager_nativeHasPin(JNIEnv* /* env */, jclass /* clazz */) {
    return appblocker::KioskCore::getInstance().hasPin() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_appblocker_kiosk_NativeKioskManager_nativeSetPin(JNIEnv* env, jclass /* clazz */, jstring pin) {
    std::string p = jstringToString(env, pin);
    return appblocker::KioskCore::getInstance().setPin(p) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_appblocker_kiosk_NativeKioskManager_nativeVerifyPin(JNIEnv* env, jclass /* clazz */, jstring pin) {
    std::string p = jstringToString(env, pin);
    return appblocker::KioskCore::getInstance().verifyPin(p) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_appblocker_kiosk_NativeKioskManager_nativeStartKiosk(JNIEnv* env, jclass /* clazz */, jstring packageName) {
    std::string pkg = jstringToString(env, packageName);
    return appblocker::KioskCore::getInstance().startKiosk(pkg) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_appblocker_kiosk_NativeKioskManager_nativeStopKiosk(JNIEnv* env, jclass /* clazz */, jstring pin) {
    std::string p = jstringToString(env, pin);
    return appblocker::KioskCore::getInstance().stopKiosk(p) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_appblocker_kiosk_NativeKioskManager_nativeIsKioskActive(JNIEnv* /* env */, jclass /* clazz */) {
    return appblocker::KioskCore::getInstance().isKioskActive() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_appblocker_kiosk_NativeKioskManager_nativeGetTargetPackage(JNIEnv* env, jclass /* clazz */) {
    std::string pkg = appblocker::KioskCore::getInstance().getTargetPackage();
    return env->NewStringUTF(pkg.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_appblocker_kiosk_NativeKioskManager_nativeIsPackageAllowed(JNIEnv* env, jclass /* clazz */, jstring packageName) {
    std::string pkg = jstringToString(env, packageName);
    return appblocker::KioskCore::getInstance().isPackageAllowed(pkg) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
