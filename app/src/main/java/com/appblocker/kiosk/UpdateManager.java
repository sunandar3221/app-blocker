package com.appblocker.kiosk;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String GITHUB_REPO = "sunandar3221/app-blocker";
    private static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String UPDATE_CHANNEL_ID = "kiosk_app_updates";
    private static File pendingApkFile = null;

    public static class ReleaseInfo {
        public String tagName;
        public String title;
        public String changelog;
        public String downloadUrl;
        public long fileSize;

        public ReleaseInfo(String tagName, String title, String changelog, String downloadUrl, long fileSize) {
            this.tagName = tagName;
            this.title = title;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.fileSize = fileSize;
        }
    }

    public interface UpdateCheckCallback {
        void onUpdateAvailable(ReleaseInfo release);
        void onNoUpdate();
        void onError(String message);
    }

    /**
     * Check GitHub Releases for updates in a background thread.
     */
    public static void checkForUpdates(Activity activity, boolean isManualCheck) {
        new Thread(() -> {
            try {
                ReleaseInfo release = fetchLatestRelease();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (activity == null || activity.isFinishing()) return;

                    if (release != null && isNewerVersion(BuildConfig.VERSION_NAME, release.tagName)) {
                        notifyUpdateAvailable(activity, release);
                        showUpdateDialog(activity, release);
                    } else {
                        if (isManualCheck) {
                            Toast.makeText(
                                activity,
                                "Aplikasi sudah menggunakan versi terbaru (v" + BuildConfig.VERSION_NAME + ")",
                                Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "Error checking for updates: " + t.getMessage(), t);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isManualCheck && activity != null && !activity.isFinishing()) {
                        Toast.makeText(activity, "Gagal memeriksa pembaruan: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private static ReleaseInfo fetchLatestRelease() throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("User-Agent", "AppBlocker-Android-Client");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("GitHub API HTTP response code: " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();

        JSONObject json = new JSONObject(response.toString());
        String tagName = json.optString("tag_name", "");
        String title = json.optString("name", tagName);
        String changelog = json.optString("body", "");

        String downloadUrl = null;
        long fileSize = 0;

        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String assetName = asset.optString("name", "");
                if (assetName.endsWith(".apk")) {
                    downloadUrl = asset.optString("browser_download_url", "");
                    fileSize = asset.optLong("size", 0);
                    break;
                }
            }
        }

        if (downloadUrl == null || downloadUrl.isEmpty()) {
            return null;
        }

        return new ReleaseInfo(tagName, title, changelog, downloadUrl, fileSize);
    }

    public static boolean isNewerVersion(String currentVersion, String latestVersion) {
        if (latestVersion == null || latestVersion.isEmpty()) return false;
        try {
            String curClean = currentVersion.replaceFirst("^[vV]", "").trim();
            String latClean = latestVersion.replaceFirst("^[vV]", "").trim();

            String[] curParts = curClean.split("[^0-9]+");
            String[] latParts = latClean.split("[^0-9]+");

            int maxLen = Math.max(curParts.length, latParts.length);
            for (int i = 0; i < maxLen; i++) {
                int curVal = (i < curParts.length && !curParts[i].isEmpty()) ? Integer.parseInt(curParts[i]) : 0;
                int latVal = (i < latParts.length && !latParts[i].isEmpty()) ? Integer.parseInt(latParts[i]) : 0;

                if (latVal > curVal) return true;
                if (latVal < curVal) return false;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void showUpdateDialog(Activity activity, ReleaseInfo release) {
        if (activity == null || activity.isFinishing()) return;

        String message = "Versi baru " + release.tagName + " telah tersedia di GitHub Releases.\n\n"
                + (release.changelog != null && !release.changelog.isEmpty() ? release.changelog : "Pembaruan kestabilan dan fitur terbaru.")
                + "\n\nApakah Anda ingin mengunduh dan memperbarui sekarang?";

        new AlertDialog.Builder(activity)
                .setTitle("🚀 Pembaruan Tersedia (" + release.tagName + ")")
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton("Perbarui Sekarang", (dialog, which) -> {
                    startDownloadAndInstall(activity, release);
                })
                .setNegativeButton("Nanti", (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static void notifyUpdateAvailable(Context context, ReleaseInfo release) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        UPDATE_CHANNEL_ID,
                        "Pembaruan Aplikasi",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Notifikasi pembaruan rilis App Blocker");
                nm.createNotificationChannel(channel);
            }

            Intent openAppIntent = new Intent(context, MainActivity.class);
            openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    102,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_shield)
                    .setContentTitle("Pembaruan Tersedia: " + release.tagName)
                    .setContentText("Versi baru siap diunduh. Ketuk untuk membuka pembaruan.")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            nm.notify(2001, builder.build());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to send update notification", t);
        }
    }

    private static void startDownloadAndInstall(Activity activity, ReleaseInfo release) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Mengunduh Pembaruan");
        progressDialog.setMessage("Menghubungkan ke GitHub...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            File apkFile = null;
            try {
                File dir = activity.getExternalCacheDir();
                if (dir == null) dir = activity.getCacheDir();
                apkFile = new File(dir, "AppBlocker-update.apk");

                if (apkFile.exists()) {
                    apkFile.delete();
                }

                URL url = new URL(release.downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                // Handle HTTP redirects (GitHub Releases redirect to AWS S3 CDN)
                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.connect();
                }

                long fileLength = conn.getContentLength();
                if (fileLength <= 0 && release.fileSize > 0) {
                    fileLength = release.fileSize;
                }

                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(apkFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                long lastProgressUpdate = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);

                    if (fileLength > 0) {
                        int progress = (int) ((total * 100) / fileLength);
                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdate > 100) {
                            lastProgressUpdate = now;
                            final long curTotal = total;
                            final long maxLen = fileLength;
                            activity.runOnUiThread(() -> {
                                progressDialog.setProgress(progress);
                                progressDialog.setMessage(String.format("Mengunduh: %.1f MB / %.1f MB",
                                        curTotal / (1024.0 * 1024.0), maxLen / (1024.0 * 1024.0)));
                            });
                        }
                    }
                }

                output.flush();
                output.close();
                input.close();
                conn.disconnect();

                final File finalApk = apkFile;
                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    installApk(activity, finalApk);
                });

            } catch (Throwable t) {
                Log.e(TAG, "Error downloading APK: " + t.getMessage(), t);
                if (apkFile != null && apkFile.exists()) {
                    apkFile.delete();
                }
                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity, "Gagal mengunduh APK: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void installApk(Activity activity, File apkFile) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "File installer tidak ditemukan.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Check unknown sources installation permission on Android 8.0+ (Oreo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    pendingApkFile = apkFile;
                    Toast.makeText(activity, "Aktifkan izin 'Izinkan dari sumber ini', lalu installer akan otomatis terbuka.", Toast.LENGTH_LONG).show();
                    Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    permissionIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(permissionIntent);
                    return;
                }
            }

            Uri apkUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
            Log.d(TAG, "Launched APK package installer successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "Error launching package installer: " + t.getMessage(), t);
            Toast.makeText(activity, "Gagal membuka installer APK: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Called when returning from unknown sources settings screen.
     * Automatically resumes APK installation without losing the installer GUI!
     */
    public static void resumePendingInstall(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (pendingApkFile != null && pendingApkFile.exists()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.getPackageManager().canRequestPackageInstalls()) {
                File apk = pendingApkFile;
                pendingApkFile = null;
                Log.d(TAG, "Resuming pending APK install after permission granted.");
                installApk(activity, apk);
            }
        }
    }
}
