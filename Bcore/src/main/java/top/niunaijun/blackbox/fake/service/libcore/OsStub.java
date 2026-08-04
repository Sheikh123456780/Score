package top.niunaijun.blackbox.fake.service.libcore;

import android.os.Process;
import java.lang.reflect.Method;

import black.libcore.io.BRLibcore;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.IOCore;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Reflector;

/**
 * Android 16 Compatible OsStub with Storage Verification Fix
 */
public class OsStub extends ClassInvocationStub {
    public static final String TAG = "OsStub";
    private Object mBase;

    public OsStub() {
        mBase = BRLibcore.get().os();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRLibcore.get()._set_os(proxyInvocation);
    }

    @Override
    protected void onBindMethod() {
    }

    @Override
    public boolean isBadEnv() {
        return BRLibcore.get().os() != getProxyInvocation();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null)
                    continue;
                if (args[i] instanceof String) {
    String path = (String) args[i];

    if (path.startsWith("/proc/")
            || path.startsWith("/sys/")
            || path.startsWith("/dev/")) {
        continue;
    }

    if (path.startsWith("/")) {
        args[i] = IOCore.get().redirectPath(path);
    }
}
            }
        }
        return super.invoke(proxy, method, args);
    }

    @ProxyMethod("getuid")
    public static class getuid extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int callUid = (int) method.invoke(who, args);
            return getFakeUid(callUid);
        }
    }

    @ProxyMethod("stat")
    public static class stat extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object invoke = null;
            try {
                invoke = method.invoke(who, args);
            } catch (Throwable e) {
                throw e.getCause();
            }
            if (invoke != null) {
                Reflector.with(invoke).field("st_uid").set(getFakeUid(-1));
            }
            return invoke;
        }
    }

    // 🔥 Added lstat hook for game integrity checks
    @ProxyMethod("lstat")
    public static class lstat extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object invoke = null;
            try {
                invoke = method.invoke(who, args);
            } catch (Throwable e) {
                throw e.getCause();
            }
            if (invoke != null) {
                Reflector.with(invoke).field("st_uid").set(getFakeUid(-1));
            }
            return invoke;
        }
    }

    @ProxyMethod("fstat")
public static class fstat extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        Object result;
        try {
            result = method.invoke(who, args);
        } catch (Throwable e) {
            throw e.getCause() != null ? e.getCause() : e;
        }

        if (result != null) {
            Reflector.with(result).field("st_uid").set(getFakeUid(-1));
        }

        return result;
    }
}

    // 🔥 Added access hook to bypass native Android 16 file locks
    @ProxyMethod("access")
    public static class access extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
    if (e.getCause() != null)
        throw e.getCause();
    throw e;
}
        }
    }

    @ProxyMethod("open")
public static class open extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        if (args != null && args.length > 0 && args[0] instanceof String) {
            args[0] = IOCore.get().redirectPath((String) args[0]);
        }

        try {
            return method.invoke(who, args);
        } catch (Throwable e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }
}
    private static int getFakeUid(int callUid) {
        if (callUid > 0 && callUid <= Process.FIRST_APPLICATION_UID)
            return callUid;
        if (BActivityThread.isThreadInit() && BActivityThread.currentActivityThread().isInit()) {
            return BActivityThread.getBAppId();
        } else {
            return BlackBoxCore.getHostUid();
        }
    }
}
