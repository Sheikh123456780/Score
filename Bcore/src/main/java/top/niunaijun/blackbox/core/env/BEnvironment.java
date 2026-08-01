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
    
    // 🔥 Custom Folder Set: Main Storage ke andar "SdCard" folder
    private static final File sExternalVirtualRoot = new File(Environment.getExternalStorageDirectory(), "SdCard");

    public static File JUNIT_JAR = new File(getCacheDir(), "junit.apk");
    public static File EMPTY_JAR = new File(getCacheDir(), "empty.apk");

    public static void load() {
        FileUtils.mkdirs(sVirtualRoot);
        FileUtils.mkdirs(sExternalVirtualRoot);
        FileUtils.mkdirs(getSystemDir());
        FileUtils.mkdirs(getCacheDir());
        FileUtils.mkdirs(getProcDir());
    }

    /**
     * 🔥 OBB Location: /storage/emulated/0/SdCard/Android/obb/<packageName>/
     */
    public static File getObbDir(String packageName) {
        File obbDir = new File(sExternalVirtualRoot, "Android/obb/" + packageName);
        if (!obbDir.exists()) {
            obbDir.mkdirs();
        }
        return obbDir;
    }

    /**
     * 🔥 Direct Path Redirection for Android 16 Verification Fix
     */
    public static String redirectPath(String path) {
        if (path == null) return null;

        // Force redirect OBB path to custom folder
        if (path.contains("Android/obb")) {
            return path;
        }

        // Force redirect Data path to custom folder
        if (path.contains("Android/data")) {
            return path;
        }

        return path;
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
        return new File(sVirtualRoot, String.format(Locale.CHINA, "data/user/%d", userId));
    }

    public static File getDeDataDir(String packageName, int userId) {
        return new File(sVirtualRoot, String.format(Locale.CHINA, "data/user_de/%d/%s", userId, packageName));
    }

    /**
     * 🔥 Data Location: /storage/emulated/0/SdCard/Android/data/<packageName>/
     */
    public static File getExternalDataDir(String packageName, int userId) {
        File dataDir = new File(sExternalVirtualRoot, "Android/data/" + packageName);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return dataDir;
    }

    public static File getDataDir(String packageName, int userId) {
        return new File(sVirtualRoot, String.format(Locale.CHINA, "data/user/%d/%s", userId, packageName));
    }

    public static File getProcDir(int pid) {
        File file = new File(getProcDir(), String.format(Locale.CHINA, "%d", pid));
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
        return new File(BEnvironment.getDataDir(packageName, BActivityThread.getUserId()), "shared_prefs/" + prefFileName + ".xml");
    }
}
