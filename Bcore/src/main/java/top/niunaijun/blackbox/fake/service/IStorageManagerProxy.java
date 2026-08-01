package top.niunaijun.blackbox.fake.service;

import android.os.IInterface;
import android.os.storage.StorageVolume;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import black.android.os.mount.BRIMountServiceStub;
import black.android.os.storage.BRIStorageManagerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * Android 16 Compatible IStorageManagerProxy
 */
public class IStorageManagerProxy extends BinderInvocationStub {

    public IStorageManagerProxy() {
        super(BRServiceManager.get().getService("mount"));
    }

    @Override
    protected Object getWho() {
        IInterface mount;
        if (BuildCompat.isOreo()) {
            mount = BRIStorageManagerStub.get().asInterface(BRServiceManager.get().getService("mount"));
        } else {
            mount = BRIMountServiceStub.get().asInterface(BRServiceManager.get().getService("mount"));
        }
        return mount;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("mount");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("fixupAppDir")
    public static class FixupAppDir extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.e(TAG, "fixupAppDir");
            if (args != null) {
                for (Object o : args) {
                    Slog.e(TAG, "args=" + o);
                }
            }
            return method.invoke(who, args);
        }
    }

    // 🔥 Fixed: Fallback to real system volume list on error or Android 16 verification failure
    @ProxyMethod("getVolumeList")
    public static class GetVolumeList extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                StorageVolume[] volumeList = null;
                if (args == null) {
                    volumeList = BlackBoxCore.getBStorageManager().getVolumeList(BActivityThread.getBUid(), null, 0, BActivityThread.getUserId());
                } else {
                    int uid = (int) args[0];
                    String packageName = (String) args[1];
                    int flags = (int) args[2];
                    volumeList = BlackBoxCore.getBStorageManager().getVolumeList(uid, packageName, flags, BActivityThread.getUserId());
                }

                if (volumeList != null && volumeList.length > 0) {
                    return volumeList;
                }
            } catch (Throwable ignored) {
            }

            // Real host system fallback for direct Android 16 resource verification
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getVolumeVolumes")
    public static class GetVolumeVolumes extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("mkdirs")
    public static class mkdirs extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // Direct system execution for creating OBB/Data directories
            try {
                return method.invoke(who, args);
            } catch (Throwable t) {
                return 0;
            }
        }
    }
}
