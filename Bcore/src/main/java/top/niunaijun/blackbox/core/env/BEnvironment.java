package top.niunaijun.blackbox.core.env;

import android.os.Build;
import android.os.Environment;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.FileUtils;

@Obfuscate
public class BEnvironment {

    // ========== EXACTLY LIKE BlackBoxCore ==========
    private static final File sVirtualRoot = new File(BlackBoxCore.getContext().getCacheDir().getParent(), "SdCard");
    
    private static final File sExternalVirtualRoot;
    
    static {
        if (Build.VERSION.SDK_INT >= 30) {
            sExternalVirtualRoot = new File("/storage/emulated/0/SdCard");
        } else {
            sExternalVirtualRoot = new File("/sdcard/SdCard");
        }
    }
    // =================================================

    // JAR files like BlackBoxCore
    public static File JUNIT_JAR = new File(getCacheDir(), "junit.apk");
    public static File EMPTY_JAR = new File(getCacheDir(), "empty.apk");

    public static void load() {
        FileUtils.mkdirs(sVirtualRoot);
        FileUtils.mkdirs(sExternalVirtualRoot);
        FileUtils.mkdirs(getSystemDir());
        FileUtils.mkdirs(getCacheDir());
        FileUtils.mkdirs(getProcDir());
    }

    // ========== ADDED MISSING METHODS ==========
    
    public static File getExternalStorageDirectory() {
        return sExternalVirtualRoot;
    }
    
    public static File getExternalObbDir(String packageName) {
        return new File(sExternalVirtualRoot, "Android/obb/" + packageName);
    }
    
    // Single parameter versions (for compatibility)
    public static File getExternalDataCacheDir(String packageName) {
        return getExternalDataCacheDir(packageName, 0);
    }
    
    public static File getExternalDataFilesDir(String packageName) {
        return getExternalDataFilesDir(packageName, 0);
    }
    
    // ========== ORIGINAL METHODS ==========

    public static File getObbDir(String packageName) {
        return new File(sExternalVirtualRoot, "Android/obb/" + packageName);
    }

    public static File getVirtualRoot() {
        return sVirtualRoot;
    }

    public static File getExternalVirtualRoot() {
        return sExternalVirtualRoot;
    }

    public static File getSystemDir() {
        return new File(sVirtualRoot, "system");
    }

    public static File getProcDir() {
        return new File(sVirtualRoot, "proc");
    }

    public static File getCacheDir() {
        return new File(sVirtualRoot, "cache");
    }

    public static File getUserInfoConf() {
        return new File(getSystemDir(), "user.conf");
    }

    public static File getAccountsConf() {
        return new File(getSystemDir(), "accounts.conf");
    }

    public static File getUidConf() {
        return new File(getSystemDir(), "uid.conf");
    }

    public static File getSharedUserConf() {
        return new File(getSystemDir(), "shared-user.conf");
    }

    public static File getXPModuleConf() {
        return new File(getSystemDir(), "xposed-module.conf");
    }

    public static File getFakeLocationConf() {
        return new File(getSystemDir(), "fake-location.conf");
    }

    public static File getFakeDeviceConf() {
        return new File(getSystemDir(), "fake-device.conf");
    }

    public static File getPackageConf(String packageName) {
        return new File(getAppDir(packageName), "package.conf");
    }

    public static File getExternalUserDir(int userId) {
        return new File(sExternalVirtualRoot, String.format(Locale.US, "%d", userId));
    }

    public static File getUserDir(int userId) {
        return new File(sVirtualRoot, String.format(Locale.US, "data/user/%d", userId));
    }

    public static File getDeDataDir(String packageName, int userId) {
        return new File(sVirtualRoot, String.format(Locale.US, "data/user_de/%d/%s", userId, packageName));
    }

    public static File getExternalDataDir(String packageName, int userId) {
        return new File(getExternalUserDir(userId), String.format(Locale.US, "Android/data/%s", packageName));
    }

    public static File getDataDir(String packageName, int userId) {
        return new File(sVirtualRoot, String.format(Locale.US, "data/user/%d/%s", userId, packageName));
    }

    public static File getProcDir(int pid) {
        File file = new File(getProcDir(), String.format(Locale.US, "%d", pid));
        FileUtils.mkdirs(file);
        return file;
    }

    public static File getExternalDataFilesDir(String packageName, int userId) {
        return new File(getExternalDataDir(packageName, userId), "files");
    }

    public static File getDataFilesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "files");
    }

    public static File getExternalDataCacheDir(String packageName, int userId) {
        return new File(getExternalDataDir(packageName, userId), "cache");
    }

    public static File getDataCacheDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "cache");
    }

    public static File getDataLibDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "lib");
    }

    public static File getDataDatabasesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "databases");
    }

    public static File getAppRootDir() {
        return getAppDir("");
    }

    public static File getAppDir(String packageName) {
        return new File(sVirtualRoot, "data/app/" + packageName);
    }

    public static File getBaseApkDir(String packageName) {
        return new File(sVirtualRoot, "data/app/" + packageName + "/base.apk");
    }

    public static File getAppLibDir(String packageName) {
        return new File(getAppDir(packageName), "lib");
    }

    public static File getXSharedPreferences(String packageName, String prefFileName) {
        return new File(getDataDir(packageName, BActivityThread.getUserId()), "shared_prefs/" + prefFileName + ".xml");
    }
}


    public static File getDataDatabasesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "databases");
    }

    public static File getAppRootDir() {
        return getAppDir("");
    }

    public static File getAppDir(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return new File(sVirtualRoot, "data/app");
        }
        return new File(sVirtualRoot, "data/app/" + packageName);
    }

    public static File getBaseApkDir(String packageName) {
        return new File(sVirtualRoot, "data/app/" + packageName + "/base.apk");
    }

    public static File getAppLibDir(String packageName) {
        return new File(getAppDir(packageName), "lib");
    }

    public static File getXSharedPreferences(String packageName, String prefFileName) {
        return new File(BEnvironment.getDataDir(packageName, BActivityThread.getUserId()), "shared_prefs/" + prefFileName + ".xml");
    }
    
    // ============================================================
    // ADDITIONAL HELPER METHODS FOR COMPATIBILITY
    // ============================================================
    
    public static File getExternalMediaDir(String packageName) {
        File mediaDir = new File(sExternalVirtualRoot, "Android/media/" + packageName);
        if (!mediaDir.exists()) {
            FileUtils.mkdirs(mediaDir);
        }
        return mediaDir;
    }
    
    public static File getExternalObbDir(String packageName) {
        return getObbDir(packageName);
    }
    
    public static String getRedirectedPath(String path) {
        return redirectPath(path);
    }
}
