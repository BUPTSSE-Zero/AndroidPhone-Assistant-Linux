package com.buptsse.zero.phoneassistant.phoneinfoprovider;

/**
 * Created by buptsse-zero on 11/9/15.
 */
public class AppInfo
{
    private String appName;
    private String appVersion;
    private String appPackageName;
    private byte[] appIconBytes;
    private boolean isSystemApp;
    private long appSize;

    public AppInfo(String appName, String appVersion, String appPackageName, byte[] appIconBytes, boolean isSystemApp, long appSize)
    {
        this.appName = appName;
        this.appVersion = appVersion;
        this.appPackageName = appPackageName;
        this.appIconBytes = appIconBytes;
        this.isSystemApp = isSystemApp;
        this.appSize = appSize;
    }

    public String getAppName() {
        return appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getAppPackageName() {
        return appPackageName;
    }

    public byte[] getAppIconBytes() {
        return appIconBytes;
    }

    public boolean isSystemApp() {
        return isSystemApp;
    }

    public long getAppSize() {
        return appSize;
    }
}
