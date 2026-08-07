package com.Score.fake.service;

import android.content.pm.PackageManager;

import black.android.app.BRActivityThread;
import black.android.app.BRContextImpl;
import black.android.os.BRServiceManager;
import black.android.permission.BRIPermissionManagerStub;
import com.Score.ScoreCore;
import com.Score.fake.hook.BinderInvocationStub;
import com.Score.fake.service.base.PkgMethodProxy;
import com.Score.fake.service.base.ValueMethodProxy;
import com.Score.utils.Reflector;
import com.Score.utils.compat.BuildCompat;
import android.os.Build;
/**
 * Created by BlackBox on 2022/3/2.
 */
public class IPermissionManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IPermissionManagerProxy";

    private static final String P = "permissionmgr";

    public IPermissionManagerProxy() {
        super(BRServiceManager.get().getService(P));
    }

    @Override
    protected Object getWho() {
        return BRIPermissionManagerStub.get().asInterface(BRServiceManager.get().getService(P));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("permissionmgr");
        BRActivityThread.getWithException()._set_sPermissionManager(proxyInvocation);
        Object systemContext = BRActivityThread.get(ScoreCore.mainThread()).getSystemContext();
        PackageManager packageManager = BRContextImpl.get(systemContext).mPackageManager();
        if (packageManager != null) {
            try {
                Reflector.on("android.app.ApplicationPackageManager").field("mPermissionManager").set(packageManager, proxyInvocation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("addPermissionAsync", true));
        addMethodHook(new ValueMethodProxy("addPermission", true));
        addMethodHook(new ValueMethodProxy("performDexOpt", true));
        addMethodHook(new ValueMethodProxy("performDexOptIfNeeded", false));
        addMethodHook(new ValueMethodProxy("performDexOptSecondary", true));
        addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("checkDeviceIdentifierAccess", false));
        addMethodHook(new PkgMethodProxy("shouldShowRequestPermissionRationale"));
        
        if (BuildCompat.isOreo()) {
            addMethodHook(new ValueMethodProxy("notifyDexLoad", 0));
            addMethodHook(new ValueMethodProxy("notifyPackageUse", 0));
            addMethodHook(new ValueMethodProxy("setInstantAppCookie", false));
            addMethodHook(new ValueMethodProxy("isInstantApp", false));
        }
        
        // Android 10+ (API 29+) - New permission methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addMethodHook(new ValueMethodProxy("getAppOpPermissionPackages", null));
            addMethodHook(new ValueMethodProxy("setAutoRevokeWhitelisted", false));
        }
        
        // Android 11+ (API 30+) - New permission methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", null));
            addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", null));
            addMethodHook(new ValueMethodProxy("revokePostNotificationPermissionWithoutKillForTest", null));
        }
        
        // Android 12+ (API 31+) - New permission methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addMethodHook(new ValueMethodProxy("getAutoRevokeWhitelisted", false));
            addMethodHook(new ValueMethodProxy("isAutoRevokeWhitelisted", false));
        }
        
        // Android 13+ (API 33+) - New permission methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addMethodHook(new ValueMethodProxy("getNotificationPermissionState", null));
            addMethodHook(new ValueMethodProxy("checkPermissionAndStartUsingSourceAppId", null));
        }
        
        // Android 14+ (API 34+) - New permission methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            addMethodHook(new ValueMethodProxy("checkAllowBackgroundRestrictions", false));
            addMethodHook(new ValueMethodProxy("getForegroundServicePermissions", null));
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

}
