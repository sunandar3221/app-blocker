package com.appblocker.kiosk;

import android.content.Context;
import android.util.Log;

public class NativeKioskManager {
    private static final String TAG = "NativeKioskManager";
    private static boolean isLoaded = false;

    static {
        try {
            System.loadLibrary("appblocker");
            isLoaded = true;
            Log.d(TAG, "Native library libappblocker loaded successfully.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library libappblocker: " + e.getMessage());
        }
    }

    public static void init(Context context) {
        if (isLoaded && context != null) {
            String filesDir = context.getFilesDir().getAbsolutePath();
            nativeInit(filesDir);
        }
    }

    // Native C++ declarations
    public static native boolean nativeInit(String storageDir);
    public static native boolean nativeHasPin();
    public static native boolean nativeSetPin(String pin);
    public static native boolean nativeVerifyPin(String pin);
    public static native boolean nativeStartKiosk(String packageName);
    public static native boolean nativeStopKiosk(String pin);
    public static native boolean nativeIsKioskActive();
    public static native String nativeGetTargetPackage();
    public static native boolean nativeIsPackageAllowed(String packageName);
}
