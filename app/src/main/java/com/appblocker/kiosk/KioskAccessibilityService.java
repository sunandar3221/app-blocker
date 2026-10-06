package com.appblocker.kiosk;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class KioskAccessibilityService extends AccessibilityService {

    private static final String TAG = "KioskAccessibility";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastWarningTime = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        NativeKioskManager.init(getApplicationContext());
        Log.d(TAG, "Kiosk Accessibility Service connected.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        NativeKioskManager.init(getApplicationContext());
        if (!NativeKioskManager.nativeIsKioskActive()) {
            return;
        }

        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {

            CharSequence packageNameChar = event.getPackageName();
            if (packageNameChar == null) return;

            String packageName = packageNameChar.toString();

            // Native C++ check: is this package permitted while kiosk is running?
            if (!NativeKioskManager.nativeIsPackageAllowed(packageName)) {
                Log.w(TAG, "Unauthorized app detected in kiosk mode: " + packageName);
                blockAndRedirect();
            }
        }
    }

    private void blockAndRedirect() {
        long now = System.currentTimeMillis();
        if (now - lastWarningTime > 2500) {
            lastWarningTime = now;
            handler.post(() -> Toast.makeText(
                    getApplicationContext(),
                    "Kiosk Mode Aktif! Akses aplikasi lain diblokir. Masukkan PIN untuk keluar.",
                    Toast.LENGTH_SHORT
            ).show());
        }

        String targetPackage = NativeKioskManager.nativeGetTargetPackage();
        if (targetPackage != null && !targetPackage.isEmpty()) {
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
                if (launchIntent != null) {
                    launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );
                    startActivity(launchIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error redirecting to target kiosk app: " + e.getMessage());
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Kiosk Accessibility Service interrupted.");
    }
}
