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
    private long lastRedirectTime = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            NativeKioskManager.init(getApplicationContext());
            Log.d(TAG, "Kiosk Accessibility Service connected.");
        } catch (Throwable t) {
            Log.e(TAG, "Error in onServiceConnected", t);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event == null) return;

            if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return;
            }

            NativeKioskManager.init(getApplicationContext());
            if (!NativeKioskManager.nativeIsKioskActive()) {
                return;
            }

            CharSequence packageNameChar = event.getPackageName();
            if (packageNameChar == null) return;
            String packageName = packageNameChar.toString();

            // Native C++ check: is this package permitted while kiosk is running?
            if (!NativeKioskManager.nativeIsPackageAllowed(packageName)) {
                Log.w(TAG, "Unauthorized app detected in kiosk mode: " + packageName);
                blockAndRedirect(packageName);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unhandled error in onAccessibilityEvent", t);
        }
    }

    private synchronized void blockAndRedirect(String attemptedPackage) {
        long now = System.currentTimeMillis();
        // Prevent redirect loop and spamming startActivity
        if (now - lastRedirectTime < 1200) {
            return;
        }
        lastRedirectTime = now;

        if (now - lastWarningTime > 2500) {
            lastWarningTime = now;
            handler.post(() -> {
                try {
                    Toast.makeText(
                            getApplicationContext(),
                            "Kiosk Mode Aktif! Akses diblokir. Masukkan PIN untuk keluar.",
                            Toast.LENGTH_SHORT
                    ).show();
                } catch (Throwable ignored) {}
            });
        }

        String targetPackage = NativeKioskManager.nativeGetTargetPackage();
        if (targetPackage != null && !targetPackage.isEmpty() && !targetPackage.equals(attemptedPackage)) {
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
                if (launchIntent != null) {
                    launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    );
                    startActivity(launchIntent);
                }
            } catch (Throwable e) {
                Log.e(TAG, "Error redirecting to target kiosk app: " + e.getMessage());
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Kiosk Accessibility Service interrupted.");
    }
}
