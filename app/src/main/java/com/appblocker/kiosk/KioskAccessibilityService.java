package com.appblocker.kiosk;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KioskAccessibilityService extends AccessibilityService {

    private static final String TAG = "KioskAccessibility";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastWarningTime = 0;
    private long lastRedirectTime = 0;
    private long lastBootResumeTime = 0;
    private long lastBackPressTime = 0;
    private final Set<String> launcherPackages = new HashSet<>();
    private BroadcastReceiver screenReceiver;
    private static volatile KioskAccessibilityService sInstance = null;

    private final Runnable pendingRedirectRunnable = new Runnable() {
        @Override
        public void run() {
            executeRedirect();
        }
    };

    public static boolean isRunning() {
        return sInstance != null;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        try {
            NativeKioskManager.init(getApplicationContext());
            if (!NativeKioskManager.nativeIsKioskActive()) {
                return super.onKeyEvent(event);
            }

            int keyCode = event.getKeyCode();
            int action = event.getAction();

            // 1. Consume Home and App Switch (Recents) entirely so user cannot exit or trigger pinning
            if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                Log.d(TAG, "Suppressed kiosk exit key: " + keyCode);
                return true;
            }

            // 2. Suppress rapid Back key spam (< 350ms) to prevent target app from crashing/exiting
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (action == KeyEvent.ACTION_DOWN) {
                    long now = System.currentTimeMillis();
                    if (now - lastBackPressTime < 350) {
                        Log.d(TAG, "Suppressed rapid back key spam");
                        return true;
                    }
                    lastBackPressTime = now;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error handling key event", t);
        }
        return super.onKeyEvent(event);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        try {
            NativeKioskManager.init(getApplicationContext());
            loadLauncherPackages();
            registerScreenReceiver();
            Log.d(TAG, "Kiosk Accessibility Service connected.");

            // If phone was rebooted while Kiosk was active, resume gently!
            if (NativeKioskManager.nativeIsKioskActive()) {
                Log.w(TAG, "Kiosk mode is ACTIVE upon service connection. Resuming kiosk enforcement...");
                resumeKioskOnBoot("service_connected");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onServiceConnected", t);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sInstance = null;
        Log.w(TAG, "Accessibility service unbound by system!");
        return super.onUnbind(intent);
    }

    private void registerScreenReceiver() {
        if (screenReceiver != null) return;
        try {
            screenReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) return;
                    String action = intent.getAction();
                    if (Intent.ACTION_USER_PRESENT.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) {
                        NativeKioskManager.init(getApplicationContext());
                        if (NativeKioskManager.nativeIsKioskActive()) {
                            Log.d(TAG, "Screen unlocked / ON while kiosk active. Resuming kiosk gently.");
                            resumeKioskOnBoot(action);
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(screenReceiver, filter);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error registering screenReceiver", t);
        }
    }

    private void resumeKioskOnBoot(String reason) {
        long now = System.currentTimeMillis();
        // Prevent duplicate rapid resume calls within 4 seconds
        if (now - lastBootResumeTime < 4000) {
            return;
        }
        lastBootResumeTime = now;

        // 1. Ensure FloatingExitService is running
        try {
            Intent serviceIntent = new Intent(this, FloatingExitService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error starting FloatingExitService on boot resume", t);
        }

        // 2. Launch target application without crashing or destroying running task
        String targetPackage = NativeKioskManager.nativeGetTargetPackage();
        if (targetPackage != null && !targetPackage.isEmpty()) {
            handler.postDelayed(() -> {
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
                } catch (Throwable e) {
                    Log.e(TAG, "Error launching target on boot resume: " + e.getMessage());
                }
            }, 800);
        }
    }

    private void loadLauncherPackages() {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
            launcherPackages.clear();
            for (ResolveInfo ri : resolveInfos) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    launcherPackages.add(ri.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean isLauncherPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        if (launcherPackages.isEmpty()) {
            loadLauncherPackages();
        }
        if (launcherPackages.contains(packageName)) return true;
        String lower = packageName.toLowerCase();
        return lower.contains("launcher") ||
               lower.contains("home") ||
               lower.contains("quickstep") ||
               lower.contains("nexuslauncher") ||
               lower.contains("trebuchet") ||
               lower.equals("com.sec.android.app.launcher") ||
               lower.equals("com.miui.home") ||
               lower.equals("com.mi.android.globallauncher") ||
               lower.equals("com.oppo.launcher") ||
               lower.equals("com.coloros.home") ||
               lower.equals("com.bbk.launcher2") ||
               lower.equals("com.vivo.upslide") ||
               lower.equals("com.asus.launcher") ||
               lower.equals("com.motorola.launcher3") ||
               lower.equals("com.teslacoilsw.launcher");
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

            // 0. If current window is ALREADY the target app, do nothing and cancel pending redirect!
            String targetPackage = NativeKioskManager.nativeGetTargetPackage();
            if (targetPackage != null && !targetPackage.isEmpty() && packageName.equals(targetPackage)) {
                handler.removeCallbacks(pendingRedirectRunnable);
                return;
            }

            // 1. Allow our own app (UnlockActivity, Pin Dialogs, MainActivity)
            if (packageName.equals(getPackageName())) {
                return;
            }

            // 2. Allow soft keyboards / input methods so typing PIN or app text is never interrupted
            if (isInputMethod(packageName)) {
                return;
            }

            // 3. Catch SystemUI attempts (Notification Shade, Quick Settings, Recents Overview, Screen Pinning)
            if (packageName.equals("com.android.systemui")) {
                CharSequence className = event.getClassName();
                String cls = (className != null) ? className.toString().toLowerCase() : "";
                if (cls.contains("panel") || cls.contains("shade") || cls.contains("recents") || cls.contains("overview") || cls.contains("qs")) {
                    Log.w(TAG, "SystemUI notification/recents panel detected. Collapsing...");
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    return;
                }
                if (cls.contains("pinning") || cls.contains("screenpinning")) {
                    Log.w(TAG, "Screen pinning prompt detected in SystemUI. Redirecting to target...");
                    blockAndRedirect(packageName);
                    return;
                }
                return;
            }

            // 4. Catch Home Launchers immediately (gesture navigation / home button / recent apps)
            if (isLauncherPackage(packageName)) {
                Log.w(TAG, "Launcher detected in kiosk mode: " + packageName);
                blockAndRedirect(packageName);
                return;
            }

            // 5. Native C++ check: is this package permitted while kiosk is running?
            if (NativeKioskManager.nativeIsPackageAllowed(packageName)) {
                return;
            }

            Log.w(TAG, "Unauthorized app detected in kiosk mode: " + packageName);
            blockAndRedirect(packageName);
        } catch (Throwable t) {
            Log.e(TAG, "Unhandled error in onAccessibilityEvent", t);
        }
    }

    private boolean isInputMethod(String packageName) {
        if (packageName == null || packageName.isEmpty()) return true;
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                List<InputMethodInfo> imes = imm.getEnabledInputMethodList();
                if (imes != null) {
                    for (InputMethodInfo imi : imes) {
                        if (packageName.equals(imi.getPackageName())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        String lower = packageName.toLowerCase();
        return lower.contains("inputmethod") ||
               lower.contains("keyboard") ||
               lower.contains("honeyboard") ||
               lower.contains("swiftkey") ||
               lower.contains("ime") ||
               lower.contains("latin") ||
               lower.contains("autofill") ||
               lower.contains("touchtype");
    }

    private boolean isTargetAppForeground(String targetPackage) {
        if (targetPackage == null || targetPackage.isEmpty()) return false;
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                if (tasks != null && !tasks.isEmpty()) {
                    ComponentName topActivity = tasks.get(0).topActivity;
                    if (topActivity != null && targetPackage.equals(topActivity.getPackageName())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private synchronized void blockAndRedirect(String attemptedPackage) {
        long now = System.currentTimeMillis();

        if (now - lastWarningTime > 3000) {
            lastWarningTime = now;
            handler.post(() -> {
                try {
                    Toast.makeText(
                            getApplicationContext(),
                            "Kiosk Mode Aktif! Masukkan PIN untuk keluar.",
                            Toast.LENGTH_SHORT
                    ).show();
                } catch (Throwable ignored) {}
            });
        }

        // Cancel previous pending redirect to debounce multiple rapid events
        handler.removeCallbacks(pendingRedirectRunnable);

        long elapsed = now - lastRedirectTime;
        if (elapsed < 600) {
            // Post delayed so only the final debounce trigger executes
            handler.postDelayed(pendingRedirectRunnable, 600 - elapsed);
            return;
        }

        executeRedirect();
    }

    private synchronized void executeRedirect() {
        lastRedirectTime = System.currentTimeMillis();
        String targetPackage = NativeKioskManager.nativeGetTargetPackage();
        if (targetPackage == null || targetPackage.isEmpty()) {
            return;
        }

        // Don't thrash intent if target is already top of stack
        if (isTargetAppForeground(targetPackage)) {
            Log.d(TAG, "Target app is already in foreground, skipping redirect intent.");
            return;
        }

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
        } catch (Throwable e) {
            Log.e(TAG, "Error redirecting to target kiosk app: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        handler.removeCallbacks(pendingRedirectRunnable);
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
                screenReceiver = null;
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Kiosk Accessibility Service interrupted.");
    }
}
