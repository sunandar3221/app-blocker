package com.appblocker.kiosk;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
    private final Set<String> launcherPackages = new HashSet<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            NativeKioskManager.init(getApplicationContext());
            loadLauncherPackages();
            Log.d(TAG, "Kiosk Accessibility Service connected.");
        } catch (Throwable t) {
            Log.e(TAG, "Error in onServiceConnected", t);
        }
    }

    private void loadLauncherPackages() {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            launcherPackages.clear();
            for (ResolveInfo ri : resolveInfos) {
                if (ri.activityInfo != null) {
                    launcherPackages.add(ri.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            NativeKioskManager.init(getApplicationContext());
            if (NativeKioskManager.nativeIsKioskActive()) {
                int keyCode = event.getKeyCode();
                // Intercept and consume Home and Recent Apps button presses
                if (keyCode == KeyEvent.KEYCODE_HOME ||
                    keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                    keyCode == KeyEvent.KEYCODE_SEARCH) {
                    Log.d(TAG, "Intercepted navigation key: " + keyCode);
                    // Force target app to front if home/recents was pressed
                    blockAndRedirect("key_navigation");
                    return true; // Consume event completely
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error handling onKeyEvent", t);
        }
        return super.onKeyEvent(event);
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

            // 1. Allow our own app (UnlockActivity, Pin Dialogs)
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
                if (cls.contains("panel") || cls.contains("shade") || cls.contains("recents") || cls.contains("overview")) {
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

            // 4. Catch Home Launchers immediately (gesture navigation / home button)
            if (launcherPackages.contains(packageName) ||
                packageName.contains("launcher") ||
                packageName.contains("home")) {
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
    public void onInterrupt() {
        Log.w(TAG, "Kiosk Accessibility Service interrupted.");
    }
}
