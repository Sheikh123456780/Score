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
/** Created by Milk on 4/22/21. * ∧＿∧ (`･ω･∥ 丶 つ０ しーＪ 此处无Bug */
public class BEnvironment {

    private static final File sVirtualRoot = new File(BlackBoxCore.getContext().getCacheDir().getParent(), "localhost");
    
    // Custom Folder Set: Main Storage ke andar "SdCard" folder
    private static final File sExternalVirtualRoot = new File(Environment.getExternalStorageDirectory(), "SdCard");

    public static File JUNIT_JAR = new File(getCacheDir(), "junit.apk");
    public static File EMPTY_JAR = new File(getCacheDir(), "empty.apk");

    public static void load() {
        FileUtils.mkdirs(sVirtualRoot);
        FileUtils.mkdirs(sExternalVirtualRoot);
        FileUtils.mkdirs(getSystemDir());
        FileUtils.mkdirs(getCacheDir());
        FileUtils.mkdirs(getProcDir());
        
        // 🔥 CRITICAL: Create all external Android directories
        File androidRoot = new File(sExternalVirtualRoot, "Android");
        FileUtils.mkdirs(androidRoot);
        FileUtils.mkdirs(new File(androidRoot, "data"));
        FileUtils.mkdirs(new File(androidRoot, "obb"));
        FileUtils.mkdirs(new File(androidRoot, "media"));
        
        // Create user directories
        FileUtils.mkdirs(getUserDir(0));
        FileUtils.mkdirs(getDeDataDir("", 0));
    }

    /**
     * 🔥 FULL Path Redirection - This actually WORKS now!
     */
    public static String redirectPath(String path) {
        if (path == null || path.isEmpty()) return null;

        // Handle absolute paths without scheme
        String cleanPath = path;
        if (cleanPath.startsWith("file://")) {
            cleanPath = cleanPath.substring(7);
        }

        // ============================================================
        // 1. INTERNAL APP DATA REDIRECTION
        //    /data/data/package/ → /data/user/0/com.shayk/localhost/data/user/0/package/
        // ============================================================
        if (cleanPath.startsWith("/data/data/")) {
            String packageName = extractPackageName(cleanPath);
            if (packageName != null && !packageName.isEmpty()) {
                String subPath = cleanPath.substring(("/data/data/" + packageName).length());
                if (subPath.isEmpty()) subPath = "/";
                String redirected = new File(getDataDir(packageName, 0), subPath).getAbsolutePath();
                ensureParentExists(redirected);
                return redirected;
            }
        }

        // ============================================================
        // 2. INTERNAL USER DATA REDIRECTION (Android 10+)
        //    /data/user/0/package/ → /data/user/0/com.shayk/localhost/data/user/0/package/
        // ============================================================
        if (cleanPath.startsWith("/data/user/")) {
            // Extract package name from /data/user/X/package/
            String[] parts = cleanPath.split("/");
            if (parts.length >= 5) {
                String userId = parts[3];
                String packageName = parts[4];
                String subPath = cleanPath.substring(("/data/user/" + userId + "/" + packageName).length());
                if (subPath.isEmpty()) subPath = "/";
                String redirected = new File(getDataDir(packageName, Integer.parseInt(userId)), subPath).getAbsolutePath();
                ensureParentExists(redirected);
                return redirected;
            }
        }

        // ============================================================
        // 3. EXTERNAL STORAGE REDIRECTION
        //    /sdcard/Android/ → /sdcard/SdCard/Android/
        //    /storage/emulated/0/Android/ → /storage/emulated/0/SdCard/Android/
        // ============================================================
        if (cleanPath.contains("Android/obb") || cleanPath.contains("Android/obb/")) {
            // Extract the relative path after Android/
            int obbIndex = cleanPath.indexOf("Android/obb");
            if (obbIndex >= 0) {
                String relativePath = cleanPath.substring(obbIndex);
                String redirected = new File(sExternalVirtualRoot, relativePath).getAbsolutePath();
                ensureParentExists(redirected);
                return redirected;
            }
        }

        if (cleanPath.contains("Android/data") || cleanPath.contains("Android/data/")) {
            int dataIndex = cleanPath.indexOf("Android/data");
            if (dataIndex >= 0) {
                String relativePath = cleanPath.substring(dataIndex);
                String redirected = new File(sExternalVirtualRoot, relativePath).getAbsolutePath();
                ensureParentExists(redirected);
                return redirected;
            }
        }

        // Redirect any /sdcard/Android/ path
        if (cleanPath.startsWith("/sdcard/Android/") || 
            cleanPath.startsWith("/storage/emulated/0/Android/")) {
            int androidIndex = cleanPath.indexOf("Android/");
            if (androidIndex >= 0) {
                String relativePath = cleanPath.substring(androidIndex);
                String redirected = new File(sExternalVirtualRoot, relativePath).getAbsolutePath();
                ensureParentExists(redirected);
                return redirected;
            }
        }

        // ============================================================
        // 4. MEDIA/OTHER EXTERNAL PATHS
        // ============================================================
        if (cleanPath.startsWith("/sdcard/") || cleanPath.startsWith("/storage/emulated/0/")) {
            // Keep media files in the real location, but redirect Android/ to SdCard/
            if (!cleanPath.contains("Android/")) {
                // Non-Android files stay in real storage
                return cleanPath;
            }
            // Android/ paths are handled above
        }

        // ============================================================
        // 5. APP DATA DE (Device Encrypted) PATHS
        // ============================================================
        if (cleanPath.startsWith("/data/user_de/")) {
            String[] parts = cleanPath.split("/");
            if (parts.length >= 5) {
                String userId = parts[3];
                String packageName = parts[4];
                String subPath = cleanPath.substring(("/data/user_de/" + userId + "/" + packageName).length());
                if (subPath.isEmpty()) subPath = "/";
                String redirected = new File(getDeDataDir(packageName, Integer.parseInt(userId)), subPath).getAbsolutePath();
                ensureParentExists(redirected);
                return redirected;
            }
        }

        // ============================================================
        // 6. ROOT PATHS (Keep as-is)
        // ============================================================
        // /system/, /dev/, /proc/, /sys/ should not be redirected
        if (cleanPath.startsWith("/system/") || 
            cleanPath.startsWith("/dev/") || 
            cleanPath.startsWith("/proc/") || 
            cleanPath.startsWith("/sys/") ||
            cleanPath.startsWith("/apex/") ||
            cleanPath.startsWith("/vendor/")) {
            return cleanPath;
        }

        // If path doesn't match any rule, return as-is
        return cleanPath;
    }

    /**
     * Helper method to extract package name from a path
     */
    private static String extractPackageName(String path) {
        if (path == null || !path.startsWith("/data/data/")) return null;
        String afterData = path.substring("/data/data/".length());
        int slashIndex = afterData.indexOf('/');
        if (slashIndex > 0) {
            return afterData.substring(0, slashIndex);
        }
        return afterData.isEmpty() ? null : afterData;
    }

    /**
     * Ensure parent directory exists for a file path
     */
    private static void ensureParentExists(String path) {
        if (path == null) return;
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            FileUtils.mkdirs(parent);
        }
    }

    /**
     * OBB Location: /storage/emulated/0/SdCard/Android/obb/<packageName>/
     */
    public static File getObbDir(String packageName) {
        File obbDir = new File(sExternalVirtualRoot, "Android/obb/" + packageName);
        if (!obbDir.exists()) {
            FileUtils.mkdirs(obbDir);
        }
        return obbDir;
    }

    /**
     * Data Location: /storage/emulated/0/SdCard/Android/data/<packageName>/
     */
    public static File getExternalDataDir(String packageName, int userId) {
        File dataDir = new File(sExternalVirtualRoot, "Android/data/" + packageName);
        if (!dataDir.exists()) {
            FileUtils.mkdirs(dataDir);
        }
        return dataDir;
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

    public static File getPackageConf(String packageName) {
        return new File(getAppDir(packageName), "package.conf");
    }

    public static File getExternalUserDir(int userId) {
        return sExternalVirtualRoot;
    }

    public static File getUserDir(int userId) {
        return new File(sVirtualRoot, String.format(Locale.US, "data/user/%d", userId));
    }

    public static File getDeDataDir(String packageName, int userId) {
        if (packageName == null || packageName.isEmpty()) {
            return new File(sVirtualRoot, String.format(Locale.US, "data/user_de/%d", userId));
        }
        return new File(sVirtualRoot, String.format(Locale.US, "data/user_de/%d/%s", userId, packageName));
    }

    public static File getDataDir(String packageName, int userId) {
        if (packageName == null || packageName.isEmpty()) {
            return new File(sVirtualRoot, String.format(Locale.US, "data/user/%d", userId));
        }
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
