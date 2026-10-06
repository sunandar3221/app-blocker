package com.appblocker.kiosk;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class UnlockActivity extends AppCompatActivity {

    public static volatile boolean isUnlockScreenActive = false;

    private EditText etPin;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock);

        NativeKioskManager.init(getApplicationContext());

        etPin = findViewById(R.id.etUnlockPin);
        tvError = findViewById(R.id.tvUnlockError);
        Button btnUnlock = findViewById(R.id.btnConfirmUnlock);
        Button btnCancel = findViewById(R.id.btnCancelUnlock);

        setupKeypad();

        btnUnlock.setOnClickListener(v -> attemptUnlock());
        btnCancel.setOnClickListener(v -> returnToKioskApp());

        etPin.setOnEditorActionListener((v, actionId, event) -> {
            attemptUnlock();
            return true;
        });
    }

    private void setupKeypad() {
        int[] digitBtnIds = {
            R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3, R.id.btnKey4,
            R.id.btnKey5, R.id.btnKey6, R.id.btnKey7, R.id.btnKey8, R.id.btnKey9
        };

        for (int i = 0; i <= 9; i++) {
            final String digit = String.valueOf(i);
            Button btn = findViewById(digitBtnIds[i]);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    tvError.setText("");
                    etPin.append(digit);
                });
            }
        }

        Button btnClear = findViewById(R.id.btnKeyClear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                etPin.setText("");
                tvError.setText("");
            });
        }

        Button btnBack = findViewById(R.id.btnKeyBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                String cur = etPin.getText().toString();
                if (!cur.isEmpty()) {
                    etPin.setText(cur.substring(0, cur.length() - 1));
                    etPin.setSelection(etPin.getText().length());
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isUnlockScreenActive = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isUnlockScreenActive = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isUnlockScreenActive = false;
    }

    private void attemptUnlock() {
        String enteredPin = etPin.getText().toString().trim();
        if (TextUtils.isEmpty(enteredPin)) {
            tvError.setText("Masukkan PIN terlebih dahulu!");
            return;
        }

        // Native C++ verification and deactivation
        boolean success = NativeKioskManager.nativeStopKiosk(enteredPin);
        if (success) {
            // Stop floating bubble service
            Intent stopFloating = new Intent(this, FloatingExitService.class);
            stopFloating.setAction(FloatingExitService.ACTION_STOP);
            stopService(stopFloating);

            Toast.makeText(this, "Kiosk Mode berhasil dinonaktifkan.", Toast.LENGTH_SHORT).show();

            // Return to main app blocker settings
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(mainIntent);
            finish();
        } else {
            tvError.setText("PIN salah! Tidak dapat keluar dari Kiosk Mode.");
            etPin.setText("");
            etPin.requestFocus();
        }
    }

    private void returnToKioskApp() {
        String targetPackage = NativeKioskManager.nativeGetTargetPackage();
        if (targetPackage != null && !targetPackage.isEmpty()) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launchIntent);
            }
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        // Prevent bypassing lock screen via back button
        returnToKioskApp();
    }
}
