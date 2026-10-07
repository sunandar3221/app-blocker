package com.appblocker.kiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Log.d(TAG, "Received boot event action: " + action);

        Context appContext = context.getApplicationContext();
        NativeKioskManager.init(appContext);

        // Requirement: Only resume kiosk if it was previously active!
        if (!NativeKioskManager.nativeIsKioskActive()) {
            Log.d(TAG, "Kiosk mode was NOT active prior to reboot. No action taken.");
            return;
        }

        Log.w(TAG, "Kiosk mode is ACTIVE! Resuming kiosk enforcement across reboot...");

        // 1. Restart Floating Exit Service (with ongoing notification & exit bubble)
        try {
            Intent serviceIntent = new Intent(appContext, FloatingExitService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent);
            } else {
                appContext.startService(serviceIntent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error starting FloatingExitService on boot", t);
        }

        // 2. Launch locked target application
        String targetPackage = NativeKioskManager.nativeGetTargetPackage();
        if (targetPackage != null && !targetPackage.isEmpty()) {
            launchTargetApp(appContext, targetPackage);
        }
    }

    private void launchTargetApp(Context context, String targetPackage) {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable launchTask = () -> {
            try {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(targetPackage);
                if (launchIntent != null) {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );
                    context.startActivity(launchIntent);
                    Log.d(TAG, "Target kiosk app launched successfully after boot: " + targetPackage);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to launch target app after boot: " + t.getMessage(), t);
            }
        };

        // Delay slightly (600ms) to ensure WindowManager and SystemUI are completely ready
        handler.postDelayed(launchTask, 600);
        // Fallback retry after 2000ms in case system was still initializing
        handler.postDelayed(launchTask, 2000);
    }
}
