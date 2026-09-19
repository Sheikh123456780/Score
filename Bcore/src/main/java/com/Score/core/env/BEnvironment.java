package com.Score.core.env;

import com.Score.app.BActivityThread;
import com.Score.ScoreCore;
import com.Score.utils.FileUtils;

import java.io.File;
import java.util.Locale;

/** Canonical virtual storage layout used by hCore. */
public final class BEnvironment {
    private BEnvironment() {
    }

    /** The virtual app root lives beside the host app's files/cache directories. */
    private static final File sVirtualRoot = new File(
            ScoreCore.getContext().getCacheDir().getParent(), "");

    /** Shared virtual external-storage root used by hCore. */
    private static final File sExternalVirtualRoot = new File("/sdcard/SdCard");

    public static File ObbDirectory(int userId) {
        return new File(String.format(Locale.CHINA,
                "/storage/emulated/%d/SdCard/", userId));
    }

    public static File getAccountsConf() {
        return new File(getSystemDir(), "accounts.conf");
    }

    public static File getAppDir(String packageName) {
        return new File(sVirtualRoot, "data/app/" + packageName);
    }

    public static File getAppLibDir(String packageName) {
        return new File(getAppDir(packageName), "lib");
    }

    public static File getAppRootDir() {
        return getAppDir("");
    }

    public static File getBaseApkDir(String packageName) {
        return new File(sVirtualRoot, "data/app/" + packageName + "/base.apk");
    }

    public static File getCacheDir() {
        return new File(sVirtualRoot, "cache");
    }

    public static File getDataCacheDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "cache");
    }

    public static File getDataDatabasesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "databases");
    }

    /** hCore intentionally maps hosted app data through the shared virtual root. */
    public static File getDataDir(String packageName, int userId) {
        return sVirtualRoot;
    }

    public static File getDataFilesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "files");
    }

    public static File getDataLibDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "lib");
    }

    public static File getDeDataDir(String packageName, int userId) {
        return new File(sVirtualRoot,
                String.format(Locale.CHINA, "data/user_de/%d/%s", userId, packageName));
    }

    public static File getExternalAndroidRootDir() {
        return getExternalAndroidRootDir(0);
    }

    public static File getExternalAndroidRootDir(int userId) {
        return userId <= 0
                ? new File(ObbDirectory(0), "Android")
                : new File(ObbDirectory(0), String.format(Locale.CHINA,
                        "%d/Android", userId));
    }

    public static File getExternalDataCacheDir(String packageName, int userId) {
        return new File(getExternalDataDir(packageName, userId), "cache");
    }

    public static File getExternalDataDir(String packageName, int userId) {
        return new File(getExternalAndroidRootDir(userId), "data/" + packageName);
    }

    public static File getExternalDataFilesDir(String packageName, int userId) {
        return new File(getExternalDataDir(packageName, userId), "files");
    }

    public static File getExternalObbDir(String packageName) {
        return new File(getExternalObbRootDir(), packageName);
    }

    public static File getExternalObbRootDir() {
        return getExternalObbRootDir(0);
    }

    public static File getExternalObbRootDir(int userId) {
        return new File(getExternalAndroidRootDir(userId), "obb");
    }

    public static File getExternalUserDir(int userId) {
        return userId <= 0
                ? sExternalVirtualRoot
                : new File(sExternalVirtualRoot, String.valueOf(userId));
    }

    public static File getExternalVirtualRoot() {
        return sExternalVirtualRoot;
    }

    public static File getFakeLocationConf() {
        return new File(getSystemDir(), "fake-location.conf");
    }

    public static File getPackageConf(String packageName) {
        return new File(getAppDir(packageName), "package.conf");
    }

    public static File getProcDir() {
        return new File(sVirtualRoot, "proc");
    }

    public static File getProcDir(int pid) {
        File dir = new File(getProcDir(), String.format(Locale.CHINA, "%d", pid));
        FileUtils.mkdirs(dir);
        return dir;
    }

    public static File getSharedUserConf() {
        return new File(getSystemDir(), "shared-user.conf");
    }

    public static File getSystemDir() {
        return new File(sVirtualRoot, "system");
    }

    public static File getUidConf() {
        return new File(getSystemDir(), "uid.conf");
    }

    public static File getUserDir(int userId) {
        return new File(sVirtualRoot,
                String.format(Locale.CHINA, "data/user/%d", userId));
    }

    public static File getUserInfoConf() {
        return new File(getSystemDir(), "user.conf");
    }

    public static File getVirtualRoot() {
        return sVirtualRoot;
    }

    public static File getXPModuleConf() {
        return new File(getSystemDir(), "xposed-module.conf");
    }

    public static File getXSharedPreferences(String packageName, String prefFileName) {
        return new File(getDataDir(packageName, BActivityThread.getUserId()),
                "shared_prefs/" + prefFileName + ".xml");
    }

    public static void load() {
        FileUtils.mkdirs(sVirtualRoot);
        FileUtils.mkdirs(sExternalVirtualRoot);
        FileUtils.mkdirs(ObbDirectory(0));
        FileUtils.mkdirs(getExternalAndroidRootDir(0));
        FileUtils.mkdirs(getExternalObbRootDir(0));
        FileUtils.mkdirs(getSystemDir());
        FileUtils.mkdirs(getCacheDir());
        FileUtils.mkdirs(getProcDir());
    }
}
