package com.appblocker.kiosk;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private final String appName;
    private final String packageName;
    private final Drawable icon;
    private boolean isSelected;

    public AppInfo(String appName, String packageName, Drawable icon) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSelected = false;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
