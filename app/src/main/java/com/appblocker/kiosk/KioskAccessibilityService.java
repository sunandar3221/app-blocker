package com.appblocker.kiosk;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
    private final Set<String> launcherPackages = new HashSet<>();
    private BroadcastReceiver screenReceiver;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            NativeKioskManager.init(getApplicationContext());
            loadLauncherPackages();
            registerScreenReceiver();
            Log.d(TAG, "Kiosk Accessibility Service connected.");

            // Crucial: If phone was rebooted while Kiosk was active, resume immediately!
            if (NativeKioskManager.nativeIsKioskActive()) {
                Log.w(TAG, "Kiosk mode is ACTIVE upon service connection. Resuming kiosk enforcement...");
                resumeKioskOnBoot("service_connected");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onServiceConnected", t);
        }
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
                            Log.d(TAG, "Screen unlocked / ON after boot while kiosk active. Enforcing kiosk.");
                            resumeKioskOnBoot(action);
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenReceiver, filter);
        } catch (Throwable t) {
            Log.e(TAG, "Error registering screenReceiver", t);
        }
    }

    private void resumeKioskOnBoot(String reason) {
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

        // 2. Force target kiosk application to front with safe delayed retries
        handler.postDelayed(() -> blockAndRedirect("boot_resume_" + reason), 200);
        handler.postDelayed(() -> blockAndRedirect("boot_retry_" + reason), 1000);
    }

    private void loadLauncherPackages() {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            // Query with flag 0 to find ALL OEM launchers without omission
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

            // 1. Allow our own app (UnlockActivity, Pin Dialogs, MainActivity)
            if (packageName.equals(getPackageName())) {
                return;
            }

            // 2. Allow soft keyboards / input methods so typing PIN or app text is never interrupted
            if (isInputMethod(packageName)) {
                return;
            }

            // 3. Catch SystemUI attempts (Notification Shade, Quick Settings, Recents Overview)
            if (packageName.equals("com.android.systemui")) {
                CharSequence className = event.getClassName();
                String cls = (className != null) ? className.toString().toLowerCase() : "";
                // If user is pulling down notifications or opening recents panel
                if (cls.contains("panel") || cls.contains("shade") || cls.contains("recents") || cls.contains("overview") || cls.contains("qs")) {
                    Log.w(TAG, "SystemUI notification/recents panel detected. Collapsing...");
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    try {
                        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                    } catch (Throwable ignored) {}
                    blockAndRedirect(packageName);
                    return;
                }
                // Allow standard status bar rendering when not opening panels
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

    private synchronized void blockAndRedirect(String attemptedPackage) {
        long now = System.currentTimeMillis();
        // Prevent rapid intent thrashing while maintaining instantaneous blocking
        if (now - lastRedirectTime < 150) {
            return;
        }
        lastRedirectTime = now;

        if (now - lastWarningTime > 2500) {
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

        // Close any opened system dialogs or notification shades
        try {
            sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        } catch (Throwable ignored) {}

        String targetPackage = NativeKioskManager.nativeGetTargetPackage();
        if (targetPackage != null && !targetPackage.isEmpty() && !targetPackage.equals(attemptedPackage)) {
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
                if (launchIntent != null) {
                    launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    );
                    startActivity(launchIntent);
                }
            } catch (Throwable e) {
                Log.e(TAG, "Error redirecting to target kiosk app: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
