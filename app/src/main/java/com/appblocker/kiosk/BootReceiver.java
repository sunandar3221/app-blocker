package com.appblocker.kiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device booted. Checking kiosk state...");
            NativeKioskManager.init(context);

            if (NativeKioskManager.nativeIsKioskActive()) {
                Log.d(TAG, "Kiosk mode was active before reboot! Re-launching kiosk...");

                // 1. Restart Floating Exit Service
                try {
                    Intent serviceIntent = new Intent(context, FloatingExitService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Error restarting FloatingExitService on boot", t);
                }

                // 2. Launch target kiosk application
                String targetPackage = NativeKioskManager.nativeGetTargetPackage();
                if (targetPackage != null && !targetPackage.isEmpty()) {
                    try {
                        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(targetPackage);
                        if (launchIntent != null) {
                            launchIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            );
                            context.startActivity(launchIntent);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Error launching target package on boot", t);
                    }
                }
            }
        }
    }
}
