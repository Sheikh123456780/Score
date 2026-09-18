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
        addMethodHook(new CheckPermission());
        addMethodHook(new CheckUidPermission());
        addMethodHook(new GrantRuntimePermission());
        addMethodHook(new RevokeRuntimePermission());
        addMethodHook(new PkgMethodProxy("getPermissionFlags"));
        addMethodHook(new PkgMethodProxy("updatePermissionFlags"));
        if (BuildCompat.isOreo()) {
            addMethodHook(new ValueMethodProxy("notifyDexLoad", 0));
            addMethodHook(new ValueMethodProxy("notifyPackageUse", 0));
            addMethodHook(new ValueMethodProxy("setInstantAppCookie", false));
            addMethodHook(new ValueMethodProxy("isInstantApp", false));
        }
    }


    @com.Score.fake.hook.ProxyMethod("checkPermission")
    public static class CheckPermission extends com.Score.fake.hook.MethodHook {
        @Override
        protected Object hook(Object who, java.lang.reflect.Method method, Object[] args) throws Throwable {
            String permission = null;
            String pkg = null;
            if (args != null) {
                for (Object arg : args) {
                    if (!(arg instanceof String)) continue;
                    String value = (String) arg;
                    if (VirtualPermissionCompat.isVirtualPackage(value)) pkg = value;
                    else if (permission == null) permission = value;
                }
            }
            if (pkg != null && permission != null && VirtualPermissionCompat.isRuntimePermission(permission)) {
                return VirtualPermissionCompat.hostHasPermission(permission)
                        ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
            }
            return method.invoke(who, args);
        }
    }

    @com.Score.fake.hook.ProxyMethod("checkUidPermission")
    public static class CheckUidPermission extends com.Score.fake.hook.MethodHook {
        @Override
        protected Object hook(Object who, java.lang.reflect.Method method, Object[] args) throws Throwable {
            String permission = null;
            int uid = -1;
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof String && permission == null) permission = (String) arg;
                    else if (arg instanceof Integer) uid = (Integer) arg;
                }
            }
            if (uid >= 0 && VirtualPermissionCompat.isVirtualUid(uid)
                    && VirtualPermissionCompat.isRuntimePermission(permission)) {
                return VirtualPermissionCompat.hostHasPermission(permission)
                        ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
            }
            return method.invoke(who, args);
        }
    }

    @com.Score.fake.hook.ProxyMethod("grantRuntimePermission")
    public static class GrantRuntimePermission extends com.Score.fake.hook.MethodHook {
        @Override
        protected Object hook(Object who, java.lang.reflect.Method method, Object[] args) throws Throwable {
            String pkg = null;
            String permission = null;
            if (args != null) {
                for (Object arg : args) {
                    if (!(arg instanceof String)) continue;
                    String value = (String) arg;
                    if (VirtualPermissionCompat.isVirtualPackage(value)) pkg = value;
                    else if (permission == null) permission = value;
                }
            }
            if (pkg != null && permission != null && VirtualPermissionCompat.isRuntimePermission(permission)) {
                return null;
            }
            return method.invoke(who, args);
        }
    }

    @com.Score.fake.hook.ProxyMethod("revokeRuntimePermission")
    public static class RevokeRuntimePermission extends com.Score.fake.hook.MethodHook {
        @Override
        protected Object hook(Object who, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof String && VirtualPermissionCompat.isVirtualPackage((String) arg)) {
                        return null;
                    }
                }
            }
            return method.invoke(who, args);
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

}
