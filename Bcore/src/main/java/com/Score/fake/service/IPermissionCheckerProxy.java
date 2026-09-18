package com.Score.fake.service;

import android.os.IBinder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import com.Score.ScoreCore;
import com.Score.app.BActivityThread;
import com.Score.fake.hook.BinderInvocationStub;
import com.Score.fake.hook.MethodHook;
import com.Score.fake.hook.ProxyMethod;
import com.Score.utils.Slog;

/**
 * Bridges the framework PermissionChecker service for virtual apps.
 *
 * Android 10+ can perform microphone checks through PermissionChecker using an
 * AttributionSource (package + uid). The real process belongs to the host app,
 * while the attribution source identifies the virtual package. Mapping that
 * source back to the host lets Android evaluate the host's real RECORD_AUDIO
 * grant and AppOp without granting anything to an uninstalled package.
 */
public class IPermissionCheckerProxy extends BinderInvocationStub {
    private static final String SERVICE = "permission_checker";
    private static final String TAG = "IPermissionCheckerProxy";

    public IPermissionCheckerProxy() {
        super(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(SERVICE);
            if (binder == null) return null;
            Class<?> stub = Class.forName("android.permission.IPermissionChecker$Stub");
            Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
            asInterface.setAccessible(true);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            Slog.d(TAG, "IPermissionChecker unavailable: " + e);
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        if (proxyInvocation == null) return;
        try {
            // PermissionChecker keeps the binder proxy in a static field on
            // releases where the service is accessed through PermissionChecker.
            Class<?> checker = Class.forName("android.content.PermissionChecker");
            Field field = checker.getDeclaredField("sService");
            field.setAccessible(true);
            field.set(null, proxyInvocation);
        } catch (Throwable e) {
            Slog.d(TAG, "Could not replace PermissionChecker.sService: " + e);
        }
        replaceSystemService(SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("checkPermission")
    public static class CheckPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 1 && args[0] instanceof String) {
                String permission = (String) args[0];
                Object state = args[1];
                if (VirtualPermissionCompat.isRuntimePermission(permission)
                        && state != null
                        && remapAttributionSource(state)) {
                    Slog.d(TAG, "Mapped virtual attribution to host for " + permission);
                }
            }
            return method.invoke(who, args);
        }

        private static boolean remapAttributionSource(Object state) {
            try {
                Class<?> cls = state.getClass();
                Field uidField = findField(cls, "uid");
                Field pkgField = findField(cls, "packageName");
                if (uidField == null || pkgField == null) return false;
                uidField.setAccessible(true);
                pkgField.setAccessible(true);
                int uid = uidField.getInt(state);
                String pkg = (String) pkgField.get(state);
                if (!VirtualPermissionCompat.isVirtualUid(uid)
                        && !VirtualPermissionCompat.isVirtualPackage(pkg)) {
                    return false;
                }
                uidField.setInt(state, ScoreCore.getHostUid());
                pkgField.set(state, ScoreCore.getHostPkg());
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static Field findField(Class<?> cls, String name) {
            Class<?> c = cls;
            while (c != null) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            return null;
        }
    }
}
