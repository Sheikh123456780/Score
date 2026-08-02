package top.niunaijun.blackbox.core.env;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.FileUtils;

@Obfuscate
public class BEnvironment {
    
    private static final String TAG = "BEnvironment";
    
    // ========== DYNAMIC PACKAGE DETECTION ==========
    private static String getHostPackageName() {
        return BlackBoxCore.getHostPkg();
    }
    
    private static File getHostDataDir() {
        return BlackBoxCore.getContext().getDataDir();
    }
    
    // ========== FIXED: Dynamic root paths ==========
    private static File sVirtualRoot;
    private static File sExternalVirtualRoot;
    
    private static void initPaths() {
        if (sVirtualRoot != null) return;
        
        // Use the host app's data directory dynamically
        File hostDataDir = getHostDataDir();
        sVirtualRoot = new File(hostDataDir.getParent(), "SdCard");
        
        if (Build.VERSION.SDK_INT >= 30) {
            sExternalVirtualRoot = new File("/storage/emulated/0/SdCard");
        } else {
            sExternalVirtualRoot = new File("/sdcard/SdCard");
        }
        
        Log.d(TAG, "VirtualRoot: " + sVirtualRoot.getAbsolutePath());
        Log.d(TAG, "ExternalVirtualRoot: " + sExternalVirtualRoot.getAbsolutePath());
    }
    
    // ========== FIXED: Directory creation with parent ==========
    private static File ensureParentDirs(File file) {
        if (file == null) return null;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean created = parent.mkdirs();
            if (created) {
                Log.d(TAG, "Created directory: " + parent.getAbsolutePath());
            }
        }
        return file;
    }
    
    private static File mkdirs(File file) {
        if (file == null) return file;
        if (!file.exists()) {
            boolean created = file.mkdirs();
            if (created) {
                Log.d(TAG, "Created directory: " + file.getAbsolutePath());
            }
        }
        return file;
    }
    
    // JAR files
    public static File JUNIT_JAR = new File(getCacheDir(), "junit.apk");
    public static File EMPTY_JAR = new File(getCacheDir(), "empty.apk");

    public static void load() {
        initPaths();
        
        // Create all root directories
        mkdirs(sVirtualRoot);
        mkdirs(sExternalVirtualRoot);
        mkdirs(getSystemDir());
        mkdirs(getCacheDir());
        mkdirs(getProcDir());
        
        // ========== CRITICAL: Pre-create all needed subdirectories ==========
        mkdirs(new File(sVirtualRoot, "data"));
        mkdirs(new File(sVirtualRoot, "data/user"));
        mkdirs(new File(sVirtualRoot, "data/user_de"));
        mkdirs(new File(sVirtualRoot, "data/app"));
        mkdirs(new File(sVirtualRoot, "data/app-lib"));
        
        // Pre-create directories for common Google services
        String[] commonPackages = {
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.google.android.webview"
        };
        
        for (int userId = 0; userId <= 10; userId++) {
            for (String pkg : commonPackages) {
                mkdirs(new File(sVirtualRoot, String.format("data/user/%d/%s", userId, pkg)));
                mkdirs(new File(sVirtualRoot, String.format("data/user/%d/%s/shared_prefs", userId, pkg)));
                mkdirs(new File(sVirtualRoot, String.format("data/user/%d/%s/databases", userId, pkg)));
                mkdirs(new File(sVirtualRoot, String.format("data/user/%d/%s/cache", userId, pkg)));
                mkdirs(new File(sVirtualRoot, String.format("data/user_de/%d/%s", userId, pkg)));
                mkdirs(new File(sVirtualRoot, String.format("data/user_de/%d/%s/app_chimera", userId, pkg)));
            }
        }
        
        // Create app_webview directories
        String hostPkg = getHostPackageName();
        mkdirs(new File(getHostDataDir(), "app_webview"));
        mkdirs(new File(getHostDataDir(), "app_webview_0"));
        mkdirs(new File(getHostDataDir(), "cache/webview"));
        
        Log.d(TAG, "BEnvironment loaded successfully");
    }

    // ========== GETTER METHODS WITH DIRECTORY CREATION ==========
    
    public static File getVirtualRoot() {
        initPaths();
        return sVirtualRoot;
    }

    public static File getExternalVirtualRoot() {
        initPaths();
        return sExternalVirtualRoot;
    }
    
    public static File getExternalStorageDirectory() {
        return getExternalVirtualRoot();
    }
    
    public static File getExternalObbDir(String packageName) {
        return mkdirs(new File(getExternalVirtualRoot(), "Android/obb/" + packageName));
    }
    
    public static File getObbDir(String packageName) {
        return mkdirs(new File(getExternalVirtualRoot(), "Android/obb/" + packageName));
    }

    public static File getSystemDir() {
        return mkdirs(new File(getVirtualRoot(), "system"));
    }

    public static File getProcDir() {
        return mkdirs(new File(getVirtualRoot(), "proc"));
    }

    public static File getCacheDir() {
        return mkdirs(new File(getVirtualRoot(), "cache"));
    }

    public static File getUserInfoConf() {
        return ensureParentDirs(new File(getSystemDir(), "user.conf"));
    }

    public static File getAccountsConf() {
        return ensureParentDirs(new File(getSystemDir(), "accounts.conf"));
    }

    public static File getUidConf() {
        return ensureParentDirs(new File(getSystemDir(), "uid.conf"));
    }

    public static File getSharedUserConf() {
        return ensureParentDirs(new File(getSystemDir(), "shared-user.conf"));
    }

    public static File getXPModuleConf() {
        return ensureParentDirs(new File(getSystemDir(), "xposed-module.conf"));
    }

    public static File getFakeLocationConf() {
        return ensureParentDirs(new File(getSystemDir(), "fake-location.conf"));
    }

    public static File getFakeDeviceConf() {
        return ensureParentDirs(new File(getSystemDir(), "fake-device.conf"));
    }

    public static File getPackageConf(String packageName) {
        return ensureParentDirs(new File(getAppDir(packageName), "package.conf"));
    }

    public static File getExternalUserDir(int userId) {
        return mkdirs(new File(getExternalVirtualRoot(), String.format(Locale.US, "%d", userId)));
    }

    public static File getUserDir(int userId) {
        return mkdirs(new File(getVirtualRoot(), String.format(Locale.US, "data/user/%d", userId)));
    }

    public static File getDeDataDir(String packageName, int userId) {
        return mkdirs(new File(getVirtualRoot(), String.format(Locale.US, "data/user_de/%d/%s", userId, packageName)));
    }

    public static File getExternalDataDir(String packageName, int userId) {
        return mkdirs(new File(getExternalUserDir(userId), String.format(Locale.US, "Android/data/%s", packageName)));
    }

    public static File getDataDir(String packageName, int userId) {
        return mkdirs(new File(getVirtualRoot(), String.format(Locale.US, "data/user/%d/%s", userId, packageName)));
    }

    public static File getProcDir(int pid) {
        File file = new File(getProcDir(), String.format(Locale.US, "%d", pid));
        return mkdirs(file);
    }

    public static File getExternalDataFilesDir(String packageName, int userId) {
        return mkdirs(new File(getExternalDataDir(packageName, userId), "files"));
    }

    public static File getDataFilesDir(String packageName, int userId) {
        return mkdirs(new File(getDataDir(packageName, userId), "files"));
    }

    public static File getExternalDataCacheDir(String packageName, int userId) {
        return mkdirs(new File(getExternalDataDir(packageName, userId), "cache"));
    }

    public static File getDataCacheDir(String packageName, int userId) {
        return mkdirs(new File(getDataDir(packageName, userId), "cache"));
    }

    public static File getDataLibDir(String packageName, int userId) {
        return mkdirs(new File(getDataDir(packageName, userId), "lib"));
    }

    public static File getDataDatabasesDir(String packageName, int userId) {
        return mkdirs(new File(getDataDir(packageName, userId), "databases"));
    }

    public static File getAppRootDir() {
        return getAppDir("");
    }

    public static File getAppDir(String packageName) {
        return mkdirs(new File(getVirtualRoot(), "data/app/" + packageName));
    }

    public static File getBaseApkDir(String packageName) {
        return ensureParentDirs(new File(getVirtualRoot(), "data/app/" + packageName + "/base.apk"));
    }

    public static File getAppLibDir(String packageName) {
        return mkdirs(new File(getAppDir(packageName), "lib"));
    }

    public static File getXSharedPreferences(String packageName, String prefFileName) {
        return ensureParentDirs(new File(getDataDir(packageName, BActivityThread.getUserId()), "shared_prefs/" + prefFileName + ".xml"));
    }
    
    // ========== Compatibility for single parameter calls ==========
    public static File getExternalDataCacheDir(String packageName) {
        return getExternalDataCacheDir(packageName, 0);
    }
    
    public static File getExternalDataFilesDir(String packageName) {
        return getExternalDataFilesDir(packageName, 0);
    }
}
