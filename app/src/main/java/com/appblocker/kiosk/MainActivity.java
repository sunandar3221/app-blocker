package com.appblocker.kiosk;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AppAdapter.OnAppSelectedListener {

    private static final String TAG = "MainActivity";

    private ComponentName adminComponent;
    private DevicePolicyManager devicePolicyManager;

    private TextView tvStatusAdmin;
    private TextView tvStatusLockTask;
    private TextView tvStatusOverlay;
    private TextView tvStatusBattery;
    private Button btnGrantAdmin;
    private Button btnInfoLockTask;
    private Button btnGrantOverlay;
    private Button btnGrantBattery;

    private RecyclerView rvApps;
    private ProgressBar progressBar;
    private AppAdapter appAdapter;
    private AppInfo selectedApp = null;
    private Button btnStartKiosk;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            NativeKioskManager.init(getApplicationContext());
            // If kiosk is already active, redirect immediately to unlock screen!
            if (NativeKioskManager.nativeIsKioskActive()) {
                Intent unlockIntent = new Intent(this, UnlockActivity.class);
                unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(unlockIntent);
                finish();
                return;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error checking kiosk state in onCreate", t);
        }

        setContentView(R.layout.activity_main);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, KioskDeviceAdminReceiver.class);

        initViews();
        checkFirstTimePin();
        loadInstalledApps();

        // Check for updates on startup
        UpdateManager.checkForUpdates(this, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NativeKioskManager.init(getApplicationContext());
        if (NativeKioskManager.nativeIsKioskActive()) {
            Intent unlockIntent = new Intent(this, UnlockActivity.class);
            unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(unlockIntent);
            finish();
            return;
        }
        updatePermissionsUI();
        UpdateManager.resumePendingInstall(this);
    }

    private void initViews() {
        tvStatusAdmin = findViewById(R.id.tvStatusAdmin);
        tvStatusLockTask = findViewById(R.id.tvStatusLockTask);
        tvStatusOverlay = findViewById(R.id.tvStatusOverlay);
        tvStatusBattery = findViewById(R.id.tvStatusBattery);

        btnGrantAdmin = findViewById(R.id.btnGrantAdmin);
        btnInfoLockTask = findViewById(R.id.btnInfoLockTask);
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay);
        btnGrantBattery = findViewById(R.id.btnGrantBattery);

        rvApps = findViewById(R.id.rvApps);
        progressBar = findViewById(R.id.progressBar);
        btnStartKiosk = findViewById(R.id.btnStartKiosk);
        etSearch = findViewById(R.id.etSearch);

        rvApps.setLayoutManager(new LinearLayoutManager(this));

        btnGrantAdmin.setOnClickListener(v -> requestDeviceAdmin());
        if (btnInfoLockTask != null) {
            btnInfoLockTask.setOnClickListener(v -> showLockTaskInfoDialog());
        }
        btnGrantOverlay.setOnClickListener(v -> requestOverlay());
        btnGrantBattery.setOnClickListener(v -> requestIgnoreBatteryOptimizations());

        btnStartKiosk.setOnClickListener(v -> startKioskMode());

        Button btnOpenChangePin = findViewById(R.id.btnOpenChangePin);
        if (btnOpenChangePin != null) {
            btnOpenChangePin.setOnClickListener(v -> showChangePinDialog());
        }

        Button btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> UpdateManager.checkForUpdates(this, true));
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (appAdapter != null) {
                    appAdapter.filter(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void checkFirstTimePin() {
        if (!NativeKioskManager.nativeHasPin()) {
            showSetPinDialog(false);
        }
    }

    private void showChangePinDialog() {
        if (!NativeKioskManager.nativeHasPin()) {
            showSetPinDialog(false);
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_change_pin);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCancelable(true);

        EditText etOldPin = dialog.findViewById(R.id.etOldPin);
        EditText etNewPinChange = dialog.findViewById(R.id.etNewPinChange);
        EditText etConfirmPinChange = dialog.findViewById(R.id.etConfirmPinChange);
        TextView tvError = dialog.findViewById(R.id.tvChangePinError);
        Button btnSubmit = dialog.findViewById(R.id.btnSubmitChangePin);
        Button btnCancel = dialog.findViewById(R.id.btnCancelChangePin);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String oldPin = etOldPin.getText().toString().trim();
            String newPin = etNewPinChange.getText().toString().trim();
            String confirmPin = etConfirmPinChange.getText().toString().trim();

            if (TextUtils.isEmpty(oldPin)) {
                tvError.setText("Masukkan PIN lama!");
                return;
            }
            if (TextUtils.isEmpty(newPin)) {
                tvError.setText("Masukkan PIN baru!");
                return;
            }
            if (newPin.length() < 4) {
                tvError.setText("PIN baru minimal 4 angka / karakter!");
                return;
            }
            if (!newPin.equals(confirmPin)) {
                tvError.setText("Konfirmasi PIN baru tidak cocok!");
                return;
            }

            // Native C++ verify old PIN and update to new PIN
            boolean changed = NativeKioskManager.nativeChangePin(oldPin, newPin);
            if (changed) {
                Toast.makeText(this, "PIN berhasil diperbarui!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                tvError.setText("PIN lama salah! Gagal memperbarui PIN.");
                etOldPin.setText("");
                etOldPin.requestFocus();
            }
        });

        dialog.show();
    }

    private void showSetPinDialog(boolean cancelable) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_set_pin);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCancelable(cancelable);

        EditText etNewPin = dialog.findViewById(R.id.etNewPin);
        EditText etConfirmPin = dialog.findViewById(R.id.etConfirmPin);
        TextView tvError = dialog.findViewById(R.id.tvPinError);
        Button btnSavePin = dialog.findViewById(R.id.btnSavePin);

        btnSavePin.setOnClickListener(v -> {
            String pin = etNewPin.getText().toString().trim();
            String confirm = etConfirmPin.getText().toString().trim();

            if (TextUtils.isEmpty(pin)) {
                tvError.setText("PIN tidak boleh kosong!");
                return;
            }
            if (pin.length() < 4) {
                tvError.setText("PIN minimal 4 karakter / angka!");
                return;
            }
            if (!pin.equals(confirm)) {
                tvError.setText("Konfirmasi PIN tidak cocok!");
                return;
            }

            boolean saved = NativeKioskManager.nativeSetPin(pin);
            if (saved) {
                Toast.makeText(this, "PIN berhasil disimpan!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                tvError.setText("Gagal menyimpan PIN secara aman.");
            }
        });

        dialog.show();
    }

    private void updatePermissionsUI() {
        boolean isAdmin = isAdminActive();
        boolean isOverlay = canDrawOverlays();

        if (isAdmin) {
            tvStatusAdmin.setText("✓ Aktif");
            tvStatusAdmin.setTextColor(Color.parseColor("#4CAF50"));
            btnGrantAdmin.setVisibility(View.GONE);
        } else {
            tvStatusAdmin.setText("✗ Belum Aktif");
            tvStatusAdmin.setTextColor(Color.parseColor("#EF4444"));
            btnGrantAdmin.setVisibility(View.VISIBLE);
        }

        boolean isDeviceOwner = (devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(getPackageName()));
        if (isDeviceOwner) {
            tvStatusLockTask.setText("✓ Device Owner (Silent Lock)");
            tvStatusLockTask.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            tvStatusLockTask.setText("✓ Lock Task Siap");
            tvStatusLockTask.setTextColor(Color.parseColor("#4CAF50"));
        }

        if (isOverlay) {
            tvStatusOverlay.setText("✓ Aktif");
            tvStatusOverlay.setTextColor(Color.parseColor("#4CAF50"));
            btnGrantOverlay.setVisibility(View.GONE);
        } else {
            tvStatusOverlay.setText("✗ Belum Aktif");
            tvStatusOverlay.setTextColor(Color.parseColor("#F59E0B"));
            btnGrantOverlay.setVisibility(View.VISIBLE);
        }

        boolean isBatteryIgnored = isIgnoringBatteryOptimizations();
        if (isBatteryIgnored) {
            tvStatusBattery.setText("✓ Aktif");
            tvStatusBattery.setTextColor(Color.parseColor("#4CAF50"));
            btnGrantBattery.setVisibility(View.GONE);
        } else {
            tvStatusBattery.setText("✗ Belum Aktif");
            tvStatusBattery.setTextColor(Color.parseColor("#F59E0B"));
            btnGrantBattery.setVisibility(View.VISIBLE);
        }

        // Accessibility is no longer required - LockTaskMode enforces kiosk mode!
        btnStartKiosk.setEnabled(isAdmin && isOverlay && selectedApp != null);
    }

    private void showLockTaskInfoDialog() {
        boolean isDeviceOwner = (devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(getPackageName()));
        String msg = isDeviceOwner
                ? "Aplikasi sudah menjadi Device Owner! Lock Task Mode akan mengunci layar secara penuh dan senyap tanpa dialog konfirmasi sistem."
                : "Aplikasi menggunakan Lock Task Mode (Screen Pinning resmi Android). Layanan Aksesibilitas TIDAK lagi dibutuhkan.\n\nTips Kiosk Mandiri:\nUntuk mengunci secara 100% otomatis tanpa dialog konfirmasi sistem, jadikan aplikasi Device Owner via ADB dengan perintah:\n\nadb shell dpm set-device-owner com.appblocker.kiosk/.KioskDeviceAdminReceiver";

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Informasi Lock Task Mode")
                .setMessage(msg)
                .setPositiveButton("Mengerti", null)
                .show();
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Throwable e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                } catch (Throwable ignored) {}
            }
        }
    }

    private boolean isAdminActive() {
        return devicePolicyManager != null && devicePolicyManager.isAdminActive(adminComponent);
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Device Admin diperlukan untuk mengamankan dan mengunci Kiosk Mode App Blocker.");
        startActivity(intent);
    }

    private void requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void loadInstalledApps() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                PackageManager pm = getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
                List<AppInfo> apps = new ArrayList<>();
                String myPackage = getPackageName();

                for (ResolveInfo ri : resolveInfos) {
                    if (ri.activityInfo != null) {
                        String pkg = ri.activityInfo.packageName;
                        if (!pkg.equals(myPackage)) {
                            String name = ri.loadLabel(pm).toString();
                            apps.add(new AppInfo(name, pkg, ri.loadIcon(pm)));
                        }
                    }
                }

                Collections.sort(apps, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    appAdapter = new AppAdapter(apps, this);
                    rvApps.setAdapter(appAdapter);
                });
            } catch (Throwable t) {
                Log.e(TAG, "Error loading apps", t);
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        }).start();
    }

    @Override
    public void onAppSelected(AppInfo appInfo) {
        this.selectedApp = appInfo;
        updatePermissionsUI();
    }

    private void startKioskMode() {
        if (!NativeKioskManager.nativeHasPin()) {
            Toast.makeText(this, "Harap buat PIN terlebih dahulu!", Toast.LENGTH_SHORT).show();
            showSetPinDialog(false);
            return;
        }

        if (!isAdminActive()) {
            Toast.makeText(this, "Harap aktifkan izin Device Admin!", Toast.LENGTH_SHORT).show();
            requestDeviceAdmin();
            return;
        }

        if (!canDrawOverlays()) {
            Toast.makeText(this, "Harap aktifkan izin Tampilkan di Atas Aplikasi (Overlay)!", Toast.LENGTH_SHORT).show();
            requestOverlay();
            return;
        }

        if (selectedApp == null) {
            Toast.makeText(this, "Pilih aplikasi yang ingin dikunci!", Toast.LENGTH_SHORT).show();
            return;
        }

        // If app is Device Owner, whitelist target and guard packages for silent LockTaskMode
        try {
            if (devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(getPackageName())) {
                devicePolicyManager.setLockTaskPackages(adminComponent, new String[]{ getPackageName(), selectedApp.getPackageName() });
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    devicePolicyManager.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error configuring lock task packages in DevicePolicyManager", t);
        }

        // Native C++ activates kiosk lock & writes state to disk
        boolean started = NativeKioskManager.nativeStartKiosk(selectedApp.getPackageName());
        if (!started) {
            Toast.makeText(this, "Gagal mengaktifkan Kiosk Mode secara native.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Start floating exit button & foreground service
        try {
            Intent floatingIntent = new Intent(this, FloatingExitService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(floatingIntent);
            } else {
                startService(floatingIntent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error starting FloatingExitService", t);
        }

        // Launch the selected kiosk target app directly!
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(selectedApp.getPackageName());
            if (launchIntent != null) {
                launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                );
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(getPackageName())) {
                    android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
                    options.setLockTaskEnabled(true);
                    startActivity(launchIntent, options.toBundle());
                } else {
                    startActivity(launchIntent);
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error launching target kiosk app", e);
        }

        Toast.makeText(this, "Kiosk Mode aktif untuk " + selectedApp.getAppName(), Toast.LENGTH_LONG).show();
        finish();
    }
}
