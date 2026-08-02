package top.niunaijun.blackbox.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.TrieTree;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
@SuppressLint("SdCardPath")
public class IOCore {
    public static final String TAG = "IOCore";

    private static final IOCore sIOCore = new IOCore();
    private static final TrieTree mTrieTree = new TrieTree();
    private static final TrieTree sBlackTree = new TrieTree();
    private final Map<String, String> mRedirectMap = new LinkedHashMap<>();

    private static final Map<String, Map<String, String>> sCachePackageRedirect = new HashMap<>();

    public static IOCore get() {
        return sIOCore;
    }

    // ========== FIXED: Create all parent directories ==========
    private static void ensureParentDirs(String path) {
        if (TextUtils.isEmpty(path)) return;
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean created = parent.mkdirs();
            if (created) {
                Log.d(TAG, "Created parent dir: " + parent.getAbsolutePath());
            }
        }
    }

    public void addRedirect(String origPath, String redirectPath) {
        if (TextUtils.isEmpty(origPath) || TextUtils.isEmpty(redirectPath) || mRedirectMap.get(origPath) != null) 
            return;
        
        mTrieTree.add(origPath);
        mRedirectMap.put(origPath, redirectPath);
        
        // FIX: Create ALL parent directories
        ensureParentDirs(redirectPath);
        
        // Create the target directory if it's a directory path
        File redirectFile = new File(redirectPath);
        if (!redirectFile.exists() && !redirectPath.endsWith(".apk") && !redirectPath.endsWith(".so") && !redirectPath.endsWith(".dex")) {
            boolean created = redirectFile.mkdirs();
            if (created) {
                Log.d(TAG, "Created target dir: " + redirectFile.getAbsolutePath());
            }
        }
        
        NativeCore.addIORule(origPath, redirectPath);
    }

    public String redirectPath(String path) {
        if (TextUtils.isEmpty(path)) return path;
        if (path.contains("/blackbox/")) {
            return path;
        }

        String key = mTrieTree.search(path);
        if (!TextUtils.isEmpty(key)) {
            String redirectPath = mRedirectMap.get(key);
            if (redirectPath != null) {
                // Ensure the redirected path directory exists
                ensureParentDirs(redirectPath);
                path = path.replace(key, redirectPath);
            }
        }
        return path;
    }

    public File redirectPath(File path) {
        if (path == null) return null;
        String pathStr = path.getAbsolutePath();
        String redirectPath = redirectPath(pathStr);
        if (pathStr.equals(redirectPath)) {
            return path;
        }
        return new File(redirectPath);
    }

    public String redirectPath(String path, Map<String, String> rule) {
        if (TextUtils.isEmpty(path)) return path;

        String key = mTrieTree.search(path);
        if (!TextUtils.isEmpty(key)) {
            String redirectPath = rule.get(key);
            if (redirectPath != null) {
                ensureParentDirs(redirectPath);
                path = path.replace(key, redirectPath);
            }
        }
        return path;
    }

    public File redirectPath(File path, Map<String, String> rule) {
        if (path == null) return null;
        String pathStr = path.getAbsolutePath();
        return new File(redirectPath(pathStr, rule));
    }

    // ========== FIXED: Dynamic package detection ==========
    public void enableRedirect(Context context) {
        Map<String, String> rule = new LinkedHashMap<>();
        String packageName = context.getPackageName();
        String hostPkg = BlackBoxCore.getHostPkg();

        try {
            ApplicationInfo packageInfo = BlackBoxCore.getBPackageManager()
                    .getApplicationInfo(packageName, PackageManager.GET_META_DATA, BActivityThread.getUserId());
            
            int systemUserId = BlackBoxCore.getHostUserId();
            
            // ========== DYNAMIC PATHS - Uses runtime detection ==========
            String hostDataDir = BlackBoxCore.getContext().getDataDir().getAbsolutePath();
            String hostParentDir = new File(hostDataDir).getParent();
            String virtualDataDir = hostParentDir + "/SdCard/data/user/" + systemUserId;
            
            rule.put(String.format("/data/data/%s/lib", packageName), packageInfo.nativeLibraryDir);
            rule.put(String.format("/data/user/%d/%s/lib", systemUserId, packageName), packageInfo.nativeLibraryDir);
            rule.put(String.format("/data/data/%s", packageName), packageInfo.dataDir);
            rule.put(String.format("/data/user/%d/%s", systemUserId, packageName), packageInfo.dataDir);
            rule.put(String.format("/data/user_de/%d/%s", systemUserId, packageName), 
                    new File(virtualDataDir + "_de", packageName).getAbsolutePath());

            // ========== SDCARD REDIRECTION ==========
            if (BlackBoxCore.getContext().getExternalCacheDir() != null && context.getExternalCacheDir() != null) {
                File external = BEnvironment.getExternalUserDir(BActivityThread.getUserId());
                String externalRoot = external.getAbsolutePath();
                
                // Dynamic sdcard path detection
                File sdcardAndroidFile = new File("/sdcard/Android");
                String androidDir = String.format("/storage/emulated/%d/Android", systemUserId);
                
                if (!sdcardAndroidFile.exists()) {
                    sdcardAndroidFile = new File(androidDir);
                }
                
                if (sdcardAndroidFile.exists()) {
                    File[] childDirs = sdcardAndroidFile.listFiles(pathname -> pathname.isDirectory());
                    if (childDirs != null) {
                        for (File childDir : childDirs) {
                            String name = childDir.getName();
                            Log.d(TAG, "Redirecting /sdcard/Android/" + name + " -> " + externalRoot + "/Android/" + name);
                            rule.put("/sdcard/Android/" + name, externalRoot + "/Android/" + name);
                            rule.put(androidDir + "/" + name, externalRoot + "/Android/" + name);
                        }
                    } else {
                        rule.put("/sdcard/Android", externalRoot + "/Android");
                        rule.put(androidDir, externalRoot + "/Android");
                    }
                } else {
                    rule.put("/sdcard/Android", externalRoot);
                    rule.put(androidDir, externalRoot);
                }
                
                // Additional sdcard redirects
                rule.put("/sdcard", externalRoot);
                rule.put("/storage/emulated/0", externalRoot);
                rule.put("/storage/emulated/" + systemUserId, externalRoot);
            }
            
            if (BlackBoxCore.get().isHideRoot()) {
                hideRoot(rule);
            }
            proc(rule);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Add all rules
        for (String key : rule.keySet()) {
            String value = rule.get(key);
            // Ensure directories exist before adding rule
            ensureParentDirs(value);
            get().addRedirect(key, value);
        }
        
        NativeCore.enableIO();
        Log.d(TAG, "IO redirection enabled for: " + packageName);
    }

    private void hideRoot(Map<String, String> rule) {
        rule.put("/system/app/Superuser.apk", "/system/app/Superuser.apk-fake");
        rule.put("/sbin/su", "/sbin/su-fake");
        rule.put("/system/bin/su", "/system/bin/su-fake");
        rule.put("/system/xbin/su", "/system/xbin/su-fake");
        rule.put("/data/local/xbin/su", "/data/local/xbin/su-fake");
        rule.put("/data/local/bin/su", "/data/local/bin/su-fake");
        rule.put("/system/sd/xbin/su", "/system/sd/xbin/su-fake");
        rule.put("/system/bin/failsafe/su", "/system/bin/failsafe/su-fake");
        rule.put("/data/local/su", "/data/local/su-fake");
        rule.put("/su/bin/su", "/su/bin/su-fake");
    }

    private void proc(Map<String, String> rule) {
        int appPid = BActivityThread.getAppPid();
        int pid = Process.myPid();
        String selfProc = "/proc/self/";
        String proc = "/proc/" + pid + "/";

        String cmdline = new File(BEnvironment.getProcDir(appPid), "cmdline").getAbsolutePath();
        rule.put(proc + "cmdline", cmdline);
        rule.put(selfProc + "cmdline", cmdline);
    }
}
