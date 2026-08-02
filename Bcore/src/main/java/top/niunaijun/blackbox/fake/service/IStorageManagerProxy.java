package top.niunaijun.blackbox.fake.service;

import android.os.IInterface;
import android.os.storage.StorageVolume;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import black.android.os.mount.BRIMountServiceStub;
import black.android.os.storage.BRIStorageManagerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.system.os.BStorageManagerService;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * Android 16 Compatible IStorageManagerProxy
 */
public class IStorageManagerProxy extends BinderInvocationStub {

    public static final String TAG = "IStorageManagerProxy";

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

    @ProxyMethod("getVolumeList")
    public static class GetVolumeList extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                BStorageManagerService storageManager = BlackBoxCore.getBStorageManager();
                if (args == null || args.length == 0) {
                    StorageVolume[] volumeList = storageManager.getVolumeList(
                        BActivityThread.getBUid(), null, 0, BActivityThread.getUserId());
                    return volumeList != null ? volumeList : method.invoke(who, args);
                }
                int uid = Integer.parseInt(String.valueOf(args[0]));
                String packageName = args.length > 1 ? (String) args[1] : null;
                int flags = args.length > 2 ? getFlags(args[2]) : 0;
                
                StorageVolume[] volumeList = storageManager.getVolumeList(
                    uid, packageName, flags, BActivityThread.getUserId());
                return volumeList != null ? volumeList : method.invoke(who, args);
            } catch (Throwable t) {
                Slog.e(TAG, "getVolumeList error", t);
                return method.invoke(who, args);
            }
        }
    }

    @ProxyMethod("mkdirs")
    public static class mkdirs extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    private static int getFlags(Object arg) {
        if (arg instanceof Integer) {
            return (Integer) arg;
        }
        if (arg instanceof Long) {
            return ((Long) arg).intValue();
        }
        return 0;
    }
}
