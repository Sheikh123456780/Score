package com.Score;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import black.android.app.BRActivityThread;
import black.android.os.BRUserHandle;
import me.weishu.reflection.Reflection;

import com.Score.app.BActivityThread;
import com.Score.app.LauncherActivity;
import com.Score.app.configuration.AppLifecycleCallback;
import com.Score.app.configuration.ClientConfiguration;
import com.Score.core.GmsCore;
import com.Score.core.LicenseManager;
import com.Score.core.NativeCore;
import com.Score.core.env.BEnvironment;
import com.Score.core.system.DaemonService;
import com.Score.core.system.ServiceManager;
import com.Score.core.system.user.BUserHandle;
import com.Score.core.system.user.BUserInfo;
import com.Score.entity.pm.InstallOption;
import com.Score.entity.pm.InstallResult;
import com.Score.entity.pm.InstalledModule;
import com.Score.fake.delegate.ContentProviderDelegate;
import com.Score.fake.frameworks.BActivityManager;
import com.Score.fake.frameworks.BJobManager;
import com.Score.fake.frameworks.BPackageManager;
import com.Score.fake.frameworks.BStorageManager;
import com.Score.fake.frameworks.BUserManager;
import com.Score.fake.frameworks.BXposedManager;
import com.Score.fake.hook.HookManager;
import com.Score.proxy.ProxyManifest;
import com.Score.utils.FileUtils;
import com.Score.utils.ShellUtils;
import com.Score.utils.Slog;
import com.Score.utils.compat.BuildCompat;
import com.Score.utils.compat.BundleCompat;
import com.Score.utils.compat.XposedParserCompat;
import com.Score.utils.provider.ProviderCall;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
@SuppressLint({"StaticFieldLeak", "NewApi"})
public class ScoreCore extends ClientConfiguration {
    public static final String TAG = "ScoreCore";

    private static final ScoreCore sScoreCore = new ScoreCore();
    private static Context sContext;
    private static native boolean nativeCheckValidation();
    static {
        try {
            System.loadLibrary("shayk");
            Log.i(TAG, "✅ Native library loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "❌ Failed to load native library: " + e.getMessage());
        }
    }
    private ProcessType mProcessType;
    private final Map<String, IBinder> mServices = new HashMap<>();
    private Thread.UncaughtExceptionHandler mExceptionHandler;
    private ClientConfiguration mClientConfiguration;
    private final List<AppLifecycleCallback> mAppLifecycleCallbacks = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int mHostUid = Process.myUid();
    private final int mHostUserId = BRUserHandle.get().myUserId();

    // ========== LICENSE PROTECTION ==========
    private static String LICENSE_KEY = null;
    private static volatile boolean licenseChecked = false;
    private static volatile boolean isLicensed = false;
    // =======================================

    public static ScoreCore get() {
        return sScoreCore;
    }

    public Handler getHandler() {
        return mHandler;
    }

    public static PackageManager getPackageManager() {
        return sContext.getPackageManager();
    }

    public static String getHostPkg() {
        return get().getHostPackageName();
    }

    public static int getHostUid() {
        return get().mHostUid;
    }

    public static int getHostUserId() {
        return get().mHostUserId;
    }

    public static Context getContext() {
        return sContext;
    }

    public Thread.UncaughtExceptionHandler getExceptionHandler() {
        return mExceptionHandler;
    }

    public void setExceptionHandler(Thread.UncaughtExceptionHandler exceptionHandler) {
        mExceptionHandler = exceptionHandler;
    }

    // ========== PUBLIC LICENSE API ==========
    
    // ========== PUBLIC LICENSE API ==========
public static void setLicenseKey(String key) {
    LICENSE_KEY = key;
    Log.i(TAG, "License key set: " + (key != null ? "***" : "null"));
}

public static String getLicenseKey() {
    return LICENSE_KEY;
}

public static long getLicenseDaysLeft() {
    return LicenseManager.Z9();  // ← CHANGED
}

public static boolean isLicenseValid() {
    return LicenseManager.Z6();  // ← CHANGED (was isLicensed)
}
// =======================================
    // =======================================

    public void doAttachBaseContext(Context context, ClientConfiguration clientConfiguration) {
        Log.e(TAG, "★★★ doAttachBaseContext START ★★★");
        
        if (clientConfiguration == null) {
            throw new IllegalArgumentException("ClientConfiguration is null!");
        }
        
        Reflection.unseal(context);
        sContext = context;
        mClientConfiguration = clientConfiguration;
        initNotificationManager();

        String processName = getProcessName(getContext());
        if (processName.equals(ScoreCore.getHostPkg())) {
            mProcessType = ProcessType.Main;
            startLogcat();
        } else if (processName.endsWith(getContext().getString(R.string.black_box_service_name))) {
            mProcessType = ProcessType.Server;
        } else {
            mProcessType = ProcessType.BAppClient;
        }
        if (ScoreCore.get().isBlackProcess()) {
            BEnvironment.load();
        }
        if (isServerProcess()) {
            if (clientConfiguration.isEnableDaemonService()) {
                Intent intent = new Intent();
                intent.setClass(getContext(), DaemonService.class);
                if (BuildCompat.isOreo()) {
                    getContext().startForegroundService(intent);
                } else {
                    getContext().startService(intent);
                }
            }
        }
        
        HookManager.get().init();
        Log.e(TAG, "★★★ doAttachBaseContext END ★★★");
    }

public void doCreate() {
    // No license check here - already done in launchApk()
    
    if (isBlackProcess()) {
        ContentProviderDelegate.init();
    }
    if (!isServerProcess()) {
        ServiceManager.initBlackManager();
    }
    copyRawToInternal(getContext());
}
    
    private void showToastAndExit(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, "❌ " + message, Toast.LENGTH_LONG).show();
            new Handler().postDelayed(() -> {
                Process.killProcess(Process.myPid());
                System.exit(0);
            }, 3000);
        });
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

    // ========== ORIGINAL METHODS ==========

    public static Object mainThread() {
        return BRActivityThread.get().currentActivityThread();
    }

    public void startActivity(Intent intent, int userId) {
        if (mClientConfiguration.isEnableLauncherActivity()) {
            LauncherActivity.launch(intent, userId);
        } else {
            getBActivityManager().startActivity(intent, userId);
        }
    }

    public static BJobManager getBJobManager() {
        return BJobManager.get();
    }

    public static BPackageManager getBPackageManager() {
        return BPackageManager.get();
    }

    public static BActivityManager getBActivityManager() {
        return BActivityManager.get();
    }

    public static BStorageManager getBStorageManager() {
        return BStorageManager.get();
    }

    // ========== LAUNCH APK WITH UNBREAKABLE LICENSE CHECK ==========
public boolean launchApk(String packageName, int userId) {
    Log.e(TAG, "★★★ launchApk: " + packageName);
    
    Context ctx = getContext();
    if (ctx == null) {
        Log.e(TAG, "❌ Context is null!");
        return false;
    }
    
    if (LICENSE_KEY == null || LICENSE_KEY.isEmpty()) {
        showToast("License key missing!");
        return false;
    }
    
    // ========== SERVER-SIDE VALIDATION ==========
    final boolean[] result = {false};
    final String[] errorMessage = {""};
    final Object lock = new Object();
    
    LicenseManager.X1(ctx, LICENSE_KEY, (valid, error, days) -> {
    result[0] = valid;
    errorMessage[0] = error;
    
        synchronized (lock) {
            lock.notify();
        }
        if (!valid) {
            showToast(error);
        } else {
            Log.i(TAG, "✅ License valid! Days left: " + days);
        }
    });
    
    // Wait max 5 seconds
    synchronized (lock) {
        try { 
            lock.wait(5000); 
        } catch (InterruptedException e) {
            Log.e(TAG, "Validation interrupted");
        }
    }
    
    if (!result[0]) {
        Log.e(TAG, "❌ License invalid: " + errorMessage[0]);
        return false;
    }
    // ==========================================
    
    // ========== UNBREAKABLE NATIVE CHECK ==========
    try {
        if (!nativeCheckValidation()) {
            Log.e(TAG, "❌ Native validation failed!");
            // App crashes in native code - never reaches here
            return false;
        }
    } catch (UnsatisfiedLinkError e) {
        Log.e(TAG, "❌ Native library not loaded!");
        showToast("SDK Error: Native library missing!");
        return false;
    } catch (Exception e) {
        Log.e(TAG, "❌ Native check error: " + e.getMessage());
        return false;
    }
    // =============================================
    
    onBeforeMainLaunchApk(packageName, userId);
    
    Intent launchIntentForPackage = getBPackageManager().getLaunchIntentForPackage(packageName, userId);
    if (launchIntentForPackage == null) {
        return false;
    }
    startActivity(launchIntentForPackage, userId);
    
    bypass();
    
    return true;
}

    public boolean isInstalled(String packageName, int userId) {
        return getBPackageManager().isInstalled(packageName, userId);
    }

    public void uninstallPackageAsUser(String packageName, int userId) {
        getBPackageManager().uninstallPackageAsUser(packageName, userId);
    }

    public void uninstallPackage(String packageName) {
        getBPackageManager().uninstallPackage(packageName);
    }

    public InstallResult installPackageAsUser(String packageName, int userId) {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(packageName, 0);
            return getBPackageManager().installPackageAsUser(packageInfo.applicationInfo.sourceDir, InstallOption.installBySystem(), userId);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return new InstallResult().installError(e.getMessage());
        }
    }

    public InstallResult installPackageAsUser(File apk, int userId) {
        return getBPackageManager().installPackageAsUser(apk.getAbsolutePath(), InstallOption.installByStorage(), userId);
    }

    public InstallResult installPackageAsUser(Uri apk, int userId) {
        return getBPackageManager().installPackageAsUser(apk.toString(), InstallOption.installByStorage().makeUriFile(), userId);
    }

    public InstallResult installXPModule(File apk) {
        return getBPackageManager().installPackageAsUser(apk.getAbsolutePath(), InstallOption.installByStorage().makeXposed(), BUserHandle.USER_XPOSED);
    }

    public InstallResult installXPModule(Uri apk) {
        return getBPackageManager().installPackageAsUser(apk.toString(), InstallOption.installByStorage().makeXposed().makeUriFile(), BUserHandle.USER_XPOSED);
    }

    public InstallResult installXPModule(String packageName) {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(packageName, 0);
            String path = packageInfo.applicationInfo.sourceDir;
            return getBPackageManager().installPackageAsUser(path, InstallOption.installBySystem().makeXposed(), BUserHandle.USER_XPOSED);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return new InstallResult().installError(e.getMessage());
        }
    }

    public void uninstallXPModule(String packageName) {
        uninstallPackage(packageName);
    }

    public boolean isXPEnable() {
        return BXposedManager.get().isXPEnable();
    }

    public void setXPEnable(boolean enable) {
        BXposedManager.get().setXPEnable(enable);
    }

    public boolean isXposedModule(File file) {
        return XposedParserCompat.isXPModule(file.getAbsolutePath());
    }

    public boolean isInstalledXposedModule(String packageName) {
        return isInstalled(packageName, BUserHandle.USER_XPOSED);
    }

    public boolean isModuleEnable(String packageName) {
        return BXposedManager.get().isModuleEnable(packageName);
    }

    public void setModuleEnable(String packageName, boolean enable) {
        BXposedManager.get().setModuleEnable(packageName, enable);
    }

    public List<InstalledModule> getInstalledXPModules() {
        return BXposedManager.get().getInstalledModules();
    }

    public List<ApplicationInfo> getInstalledApplications(int flags, int userId) {
        return getBPackageManager().getInstalledApplications(flags, userId);
    }

    public List<PackageInfo> getInstalledPackages(int flags, int userId) {
        return getBPackageManager().getInstalledPackages(flags, userId);
    }

    public void clearPackage(String packageName, int userId) {
        BPackageManager.get().clearPackage(packageName, userId);
    }

    public void stopPackage(String packageName, int userId) {
        BPackageManager.get().stopPackage(packageName, userId);
    }

    public List<BUserInfo> getUsers() {
        return BUserManager.get().getUsers();
    }

    public BUserInfo createUser(int userId) {
        return BUserManager.get().createUser(userId);
    }

    public void deleteUser(int userId) {
        BUserManager.get().deleteUser(userId);
    }

    public List<AppLifecycleCallback> getAppLifecycleCallbacks() {
        return mAppLifecycleCallbacks;
    }

    public void removeAppLifecycleCallback(AppLifecycleCallback appLifecycleCallback) {
        mAppLifecycleCallbacks.remove(appLifecycleCallback);
    }

    public void addAppLifecycleCallback(AppLifecycleCallback appLifecycleCallback) {
        mAppLifecycleCallbacks.add(appLifecycleCallback);
    }

    public boolean isSupportGms() {
        return GmsCore.isSupportGms();
    }

    public boolean isInstallGms(int userId) {
        return GmsCore.isInstalledGoogleService(userId);
    }

    public InstallResult installGms(int userId) {
        return GmsCore.installGApps(userId);
    }

    public boolean uninstallGms(int userId) {
        GmsCore.uninstallGApps(userId);
        return !GmsCore.isInstalledGoogleService(userId);
    }

    public IBinder getService(String name) {
        IBinder binder = mServices.get(name);
        if (binder != null && binder.isBinderAlive()) {
            return binder;
        }
        Bundle bundle = new Bundle();
        bundle.putString("_B_|_server_name_", name);
        Bundle vm = ProviderCall.callSafely(ProxyManifest.getBindProvider(), "VM", null, bundle);
        binder = BundleCompat.getBinder(vm, "_B_|_server_");
        Slog.d(TAG, "getService: " + name + ", " + binder);
        mServices.put(name, binder);
        return binder;
    }

    private enum ProcessType {
        Server,
        BAppClient,
        Main,
    }

    public boolean isBlackProcess() {
        return mProcessType == ProcessType.BAppClient;
    }

    public boolean isMainProcess() {
        return mProcessType == ProcessType.Main;
    }

    public boolean isServerProcess() {
        return mProcessType == ProcessType.Server;
    }

    @Override
    public boolean isHideRoot() {
        return mClientConfiguration.isHideRoot();
    }

    @Override
    public boolean isHideXposed() {
        return mClientConfiguration.isHideXposed();
    }

    @Override
    public String getHostPackageName() {
        return mClientConfiguration.getHostPackageName();
    }

    @Override
    public boolean requestInstallPackage(File file, int userId) {
        return mClientConfiguration.requestInstallPackage(file, userId);
    }

    private void startLogcat() {
        new Thread(() -> {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), getContext().getPackageName() + "_logcat.txt");
            FileUtils.deleteDir(file);
            ShellUtils.execCommand("logcat -c", false);
            ShellUtils.execCommand("logcat -f " + file.getAbsolutePath(), false);
        }).start();
    }

    private static String getProcessName(Context context) {
        int myPid = Process.myPid();
        String processName = null;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.pid == myPid) {
                processName = info.processName;
                break;
            }
        }
        if (processName == null) {
            throw new RuntimeException("processName = null");
        }
        return processName;
    }

    public static boolean is64Bit() {
        if (BuildCompat.isM()) {
            return Process.is64Bit();
        } else {
            return Build.CPU_ABI.equals("arm64-v8a");
        }
    }

    private void initNotificationManager() {
        NotificationManager nm = (NotificationManager) ScoreCore.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String CHANNEL_ONE_ID = ScoreCore.getContext().getPackageName() + ".blackbox_core";
        String CHANNEL_ONE_NAME = "blackbox_core";
        if (BuildCompat.isOreo()) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ONE_ID, CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(notificationChannel);
        }
    }

    public void onBeforeMainLaunchApk(String packageName, int userid) {
        for (AppLifecycleCallback appLifecycleCallback : ScoreCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.beforeMainLaunchApk(packageName, userid);
        }
    }

    public void onBeforeMainApplicationAttach(Application app, Context context) {
        for (AppLifecycleCallback appLifecycleCallback : ScoreCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.beforeMainApplicationAttach(app, context);
        }
    }

    public void onAfterMainApplicationAttach(Application app, Context context) {
        for (AppLifecycleCallback appLifecycleCallback : ScoreCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.afterMainApplicationAttach(app, context);
        }
    }

    public void onBeforeMainActivityOnCreate(android.app.Activity activity) {
        for (AppLifecycleCallback appLifecycleCallback : ScoreCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.beforeMainActivityOnCreate(activity);
        }
    }

    public void onAfterMainActivityOnCreate(android.app.Activity activity) {
        for (AppLifecycleCallback appLifecycleCallback : ScoreCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.afterMainActivityOnCreate(activity);
        }
    }

    private void copyRawToInternal(Context context) {
        String fileName = "temp";
        File dataDir;

        try {
            dataDir = context.getDataDir();
        } catch (NoSuchMethodError e) {
            dataDir = context.getFilesDir().getParentFile();
        }

        File outDir = new File(dataDir, "SdCard/cache");
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        File outFile = new File(outDir, fileName);

        if (!outFile.exists()) {
            try (InputStream in = context.getResources().openRawResource(R.raw.temp);
                 OutputStream out = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();

                outFile.setExecutable(true, false);
                outFile.setReadable(true, false);
                outFile.setWritable(true, false);
            } catch (Exception e) {
                Log.e("CopyFile", "Error copying file: " + e.getMessage());
            }
        }
    }

    void runant(final String nf) {
        excpp("/SdCard/cache/" + nf);
    }

    private void ExecuteElf(String shell) {
        try {
            Runtime.getRuntime().exec(shell);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void excpp(String path) {
        try {
            String fullPath = sContext.getDataDir() + path;
            ExecuteElf("chmod 777 " + fullPath);
            ExecuteElf(fullPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void bypass() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            runant("temp 992");
            handler.postDelayed(() -> {
                runant("temp 992");
                handler.postDelayed(() -> {
                    runant("temp 992");
                }, 38000);
            }, 30000);
        }, 15000);
    }
}
