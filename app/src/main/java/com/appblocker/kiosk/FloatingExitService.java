package com.appblocker.kiosk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import android.os.Handler;
import android.os.Looper;

public class FloatingExitService extends Service {

    private static final String TAG = "FloatingExitService";
    public static final String ACTION_STOP = "com.appblocker.kiosk.ACTION_STOP";
    private static final String CHANNEL_ID = "kiosk_exit_channel";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private View floatingView;
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private boolean isWatchdogRunning = false;
    private long lastRedirectTime = 0;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isWatchdogRunning) return;
            checkKioskEnforcement();
            watchdogHandler.postDelayed(this, 350);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            NativeKioskManager.init(getApplicationContext());
            createNotificationChannel();
            startAsForeground();
            setupFloatingView();
            startWatchdog();
        } catch (Throwable t) {
            Log.e(TAG, "Error in onCreate", t);
        }
    }

    private void startWatchdog() {
        if (!isWatchdogRunning) {
            isWatchdogRunning = true;
            watchdogHandler.postDelayed(watchdogRunnable, 500);
            Log.d(TAG, "Kiosk watchdog started.");
        }
    }

    private void stopWatchdog() {
        isWatchdogRunning = false;
        watchdogHandler.removeCallbacks(watchdogRunnable);
        Log.d(TAG, "Kiosk watchdog stopped.");
    }

    private void checkKioskEnforcement() {
        try {
            NativeKioskManager.init(getApplicationContext());
            if (!NativeKioskManager.nativeIsKioskActive()) {
                stopSelf();
                return;
            }

            // If user is currently on UnlockActivity keypad, don't interrupt!
            if (UnlockActivity.isUnlockScreenActive) {
                return;
            }

            String targetPackage = NativeKioskManager.nativeGetTargetPackage();
            if (targetPackage == null || targetPackage.isEmpty()) return;

            // Check if target app or unlock screen is in foreground
            boolean isForeground = checkIsForeground(targetPackage);
            if (!isForeground) {
                long now = System.currentTimeMillis();
                if (now - lastRedirectTime > 600) {
                    lastRedirectTime = now;
                    Log.w(TAG, "Screen pinning unpinned or user exited target app! Intercepting with PIN unlock screen...");
                    Intent unlockIntent = new Intent(this, UnlockActivity.class);
                    unlockIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );
                    startActivity(unlockIntent);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in checkKioskEnforcement", t);
        }
    }

    private boolean checkIsForeground(String targetPackage) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                if (procs != null) {
                    for (android.app.ActivityManager.RunningAppProcessInfo p : procs) {
                        if (p.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                            for (String pkg : p.pkgList) {
                                if (pkg.equals(targetPackage) || pkg.equals(getPackageName())) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void startAsForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopWatchdog();
            stopSelf();
            return START_NOT_STICKY;
        }
        setupFloatingView();
        startWatchdog();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Kiosk Exit Service",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Layanan keluar dari Kiosk Mode");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent unlockIntent = new Intent(this, UnlockActivity.class);
        unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                unlockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("App Blocker: Kiosk Mode Aktif")
                .setContentText("Ketuk untuk memasukkan PIN dan keluar dari Kiosk Mode")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_lock, "KELUAR KIOSK", pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX);

        // If kiosk is active, attach full screen intent to automatically bring locked app to front on boot
        String targetPackage = NativeKioskManager.nativeGetTargetPackage();
        if (targetPackage != null && !targetPackage.isEmpty()) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                PendingIntent fsi = PendingIntent.getActivity(
                        this,
                        1099,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                );
                builder.setFullScreenIntent(fsi, true);
            }
        }

        return builder.build();
    }

    private void setupFloatingView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted. Floating bubble cannot be added.");
            return;
        }

        try {
            if (floatingView != null) {
                Log.d(TAG, "Floating view already exists, skipping duplicate addition.");
                return;
            }
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) return;

            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 40;
            params.y = 220;

            floatingView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;
                private long touchStartTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            touchStartTime = System.currentTimeMillis();
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            int dx = (int) (event.getRawX() - initialTouchX);
                            int dy = (int) (event.getRawY() - initialTouchY);
                            params.x = initialX + dx;
                            params.y = initialY + dy;
                            try {
                                windowManager.updateViewLayout(floatingView, params);
                            } catch (Exception ignored) {}
                            return true;

                        case MotionEvent.ACTION_UP:
                            long duration = System.currentTimeMillis() - touchStartTime;
                            int movedX = Math.abs((int) (event.getRawX() - initialTouchX));
                            int movedY = Math.abs((int) (event.getRawY() - initialTouchY));
                            if (duration < 400 && movedX < 25 && movedY < 25) {
                                // Tap detected -> Launch Unlock screen
                                Intent intent = new Intent(FloatingExitService.this, UnlockActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);
                            }
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(floatingView, params);
            Log.d(TAG, "Floating exit bubble added to WindowManager successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to create floating view: " + t.getMessage(), t);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopWatchdog();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
                floatingView = null;
            } catch (Exception ignored) {}
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
