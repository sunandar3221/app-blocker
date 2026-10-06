package com.appblocker.kiosk;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class UnlockActivity extends AppCompatActivity {

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

        btnUnlock.setOnClickListener(v -> attemptUnlock());

        btnCancel.setOnClickListener(v -> returnToKioskApp());

        etPin.setOnEditorActionListener((v, actionId, event) -> {
            attemptUnlock();
            return true;
        });
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
            startService(stopFloating);

            Toast.makeText(this, "Kiosk Mode dinonaktifkan.", Toast.LENGTH_SHORT).show();

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
