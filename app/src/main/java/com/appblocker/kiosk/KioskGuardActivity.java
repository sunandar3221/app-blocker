package com.appblocker.kiosk;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class KioskGuardActivity extends AppCompatActivity {

    private static final String TAG = "KioskGuardActivity";
    public static final String EXTRA_AUTO_PIN = "extra_auto_pin";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvGuardAppName;
    private Button btnReturnToApp;
    private Button btnPinScreen;
    private Button btnExitKiosk;
    private long lastLaunchTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk_guard);

        tvGuardAppName = findViewById(R.id.tvGuardAppName);
        btnReturnToApp = findViewById(R.id.btnReturnToApp);
        btnPinScreen = findViewById(R.id.btnPinScreen);
        btnExitKiosk = findViewById(R.id.btnExitKiosk);

        NativeKioskManager.init(getApplicationContext());
        if (!NativeKioskManager.nativeIsKioskActive()) {
            finish();
            return;
        }

        updateAppLabel();

        btnReturnToApp.setOnClickListener(v -> launchTargetApp(true));

        btnPinScreen.setOnClickListener(v -> enableScreenPinning());

        btnExitKiosk.setOnClickListener(v -> {
            Intent unlockIntent = new Intent(this, UnlockActivity.class);
            unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(unlockIntent);
        });

        // Auto pin immediately via Lock Task Mode
        enableScreenPinning();

        // Launch target app initially
        handler.postDelayed(() -> launchTargetApp(false), 250);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NativeKioskManager.init(getApplicationContext());
        if (!NativeKioskManager.nativeIsKioskActive()) {
            finish();
            return;
        }

        updateAppLabel();

        // Re-enforce LockTaskMode
        enableScreenPinning();

        // If user landed here because target app was finished/closed (e.g. back spam),
        // immediately bounce back to target app after brief delay
        long now = System.currentTimeMillis();
        if (now - lastLaunchTime > 1000) {
            handler.postDelayed(() -> launchTargetApp(false), 300);
        }
    }

    private void updateAppLabel() {
        String targetPackage = NativeKioskManager.nativeGetTargetPackage();
        if (targetPackage != null && !targetPackage.isEmpty()) {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(targetPackage, 0);
                CharSequence label = pm.getApplicationLabel(ai);
                tvGuardAppName.setText("Aplikasi terkunci: " + label + " (" + targetPackage + ")");
            } catch (Throwable ignored) {
                tvGuardAppName.setText("Aplikasi terkunci: " + targetPackage);
            }
        }
    }

    private void launchTargetApp(boolean force) {
        if (!NativeKioskManager.nativeIsKioskActive()) {
            finish();
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && (now - lastLaunchTime < 800)) {
            return;
        }
        lastLaunchTime = now;

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
            } catch (Throwable e) {
                Log.e(TAG, "Error launching target: " + e.getMessage());
            }
        }
    }

    public void enableScreenPinning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                android.content.ComponentName admin = new android.content.ComponentName(this, KioskDeviceAdminReceiver.class);
                String target = NativeKioskManager.nativeGetTargetPackage();
                if (dpm != null && dpm.isDeviceOwnerApp(getPackageName()) && target != null && !target.isEmpty()) {
                    dpm.setLockTaskPackages(admin, new String[]{ getPackageName(), target });
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        dpm.setLockTaskFeatures(admin, android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
                    }
                }
                startLockTask();
            } catch (Throwable t) {
                Log.e(TAG, "Failed to startLockTask: " + t.getMessage(), t);
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Intercept back button: DO NOT allow falling back to home launcher!
        // Instead, bring locked app back to front
        launchTargetApp(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            launchTargetApp(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
