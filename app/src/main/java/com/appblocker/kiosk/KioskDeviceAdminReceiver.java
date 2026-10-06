package com.appblocker.kiosk;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import androidx.annotation.NonNull;

public class KioskDeviceAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "Izin Device Admin App Blocker aktif.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(@NonNull Context context, @NonNull Intent intent) {
        NativeKioskManager.init(context);
        if (NativeKioskManager.nativeIsKioskActive()) {
            return "Peringatan: Kiosk Mode sedang aktif! Anda harus menonaktifkan Kiosk Mode dengan PIN terlebih dahulu.";
        }
        return "Apakah Anda yakin ingin menonaktifkan Device Admin App Blocker?";
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Izin Device Admin App Blocker dinonaktifkan.", Toast.LENGTH_SHORT).show();
    }
}
