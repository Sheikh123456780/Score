package top.niunaijun.blackbox.fake.hook;

import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;


public abstract class ClassInvocationStub implements InvocationHandler, IInjectHook {
    public static final String TAG = ClassInvocationStub.class.getSimpleName();

    private final Map<String, MethodHook> mMethodHookMap = new HashMap<>();
    private Object mBase;
    private Object mProxyInvocation;
    private boolean onlyProxy;
    private boolean mHookInjected = false;

    protected abstract Object getWho();

    protected abstract void inject(Object baseInvocation, Object proxyInvocation);

    protected void onBindMethod() {

    }

    protected Object getProxyInvocation() {
        return mProxyInvocation;
    }

    protected Object getBase() {
        return mBase;
    }

    protected void onlyProxy(boolean o) {
        onlyProxy = o;
    }

    @Override
    public void injectHook() {
        if (mHookInjected) {
            Slog.d(TAG, getClass().getSimpleName() + ": hook already injected, skipping");
            return;
        }

        // ---- Safe getWho() ----
        try {
            mBase = getWho();
        } catch (Throwable t) {
            Slog.w(TAG, getClass().getSimpleName()
                    + ": getWho() threw, skipping hook: " + t.getMessage());
            return;
        }

        if (mBase == null) {
            Slog.w(TAG, getClass().getSimpleName()
                    + ": getWho() returned null (hidden-api deny or service missing), skipping hook");
            return;
        }

        ClassLoader cl = mBase.getClass().getClassLoader();
        Class<?>[] interfaces;
        try {
            interfaces = MethodParameterUtils.getAllInterface(mBase.getClass());
        } catch (Throwable t) {
            Slog.w(TAG, getClass().getSimpleName()
                    + ": getAllInterface failed: " + t.getMessage());
            return;
        }

        if (interfaces == null || interfaces.length == 0) {
            Slog.w(TAG, getClass().getSimpleName()
                    + ": no interfaces to proxy, skipping hook");
            return;
        }

        try {
            mProxyInvocation = Proxy.newProxyInstance(cl, interfaces, this);
        } catch (Throwable t) {
            Slog.w(TAG, getClass().getSimpleName()
                    + ": Proxy.newProxyInstance failed: " + t.getMessage());
            return;
        }

        try {
            if (!onlyProxy) {
                inject(mBase, mProxyInvocation);
            }
            onBindMethod();
            mHookInjected = true;
        } catch (Throwable t) {
            Slog.w(TAG, getClass().getSimpleName()
                    + ": inject/onBindMethod failed: " + t.getMessage());
            return;
        }

        // ---- Annotation scanning (each class wrapped individually) ----
        try {
            Class<?>[] declaredClasses = this.getClass().getDeclaredClasses();
            for (Class<?> declaredClass : declaredClasses) {
                try {
                    initAnnotation(declaredClass);
                } catch (Throwable t) {
                    Slog.w(TAG, "initAnnotation failed for "
                            + declaredClass.getName() + ": " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            Slog.w(TAG, "declaredClasses iteration failed: " + t.getMessage());
        }

        try {
            ScanClass scanClass = this.getClass().getAnnotation(ScanClass.class);
            if (scanClass != null) {
                for (Class<?> aClass : scanClass.value()) {
                    for (Class<?> declaredClass : aClass.getDeclaredClasses()) {
                        try {
                            initAnnotation(declaredClass);
                        } catch (Throwable t) {
                            Slog.w(TAG, "initAnnotation failed for "
                                    + declaredClass.getName() + ": " + t.getMessage());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Slog.w(TAG, "ScanClass processing failed: " + t.getMessage());
        }

        Slog.d(TAG, getClass().getSimpleName() + ": hook injected successfully");
    }

    protected void initAnnotation(Class<?> clazz) {
        ProxyMethod proxyMethod = clazz.getAnnotation(ProxyMethod.class);
        if (proxyMethod != null) {
            final String name = proxyMethod.value();
            if (!TextUtils.isEmpty(name)) {
                try {
                    addMethodHook(name, (MethodHook) clazz.newInstance());
                } catch (Throwable t) {
                    Slog.w(TAG, "initAnnotation ProxyMethod failed for "
                            + clazz.getName() + ": " + t.getMessage());
                }
            }
        }
        ProxyMethods proxyMethods = clazz.getAnnotation(ProxyMethods.class);
        if (proxyMethods != null) {
            String[] value = proxyMethods.value();
            for (String name : value) {
                try {
                    addMethodHook(name, (MethodHook) clazz.newInstance());
                } catch (Throwable t) {
                    Slog.w(TAG, "initAnnotation ProxyMethods failed for "
                            + clazz.getName() + ": " + t.getMessage());
                }
            }
        }
    }

    protected void addMethodHook(MethodHook methodHook) {
        mMethodHookMap.put(methodHook.getMethodName(), methodHook);
    }

    protected void addMethodHook(String name, MethodHook methodHook) {
        mMethodHookMap.put(name, methodHook);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // ---- Safe method lookup ----
        MethodHook methodHook = null;
        try {
            methodHook = mMethodHookMap.get(method.getName());
        } catch (Throwable t) {
            Slog.w(TAG, "methodHook lookup failed: " + t.getMessage());
        }

        if (methodHook == null || !methodHook.isEnable()) {
            // pass-through
            try {
                return method.invoke(mBase, args);
            } catch (Throwable e) {
                Throwable cause = e.getCause();
                throw cause != null ? cause : e;
            }
        }

        // ---- Hook path ----
        Object result;
        try {
            result = methodHook.beforeHook(mBase, method, args);
            if (result != null) {
                return result;
            }
        } catch (Throwable t) {
            Slog.w(TAG, "beforeHook failed for " + method.getName()
                    + ": " + t.getMessage());
            try {
                return method.invoke(mBase, args);
            } catch (Throwable e) {
                Throwable cause = e.getCause();
                throw cause != null ? cause : e;
            }
        }

        try {
            result = methodHook.hook(mBase, method, args);
        } catch (Throwable t) {
            Slog.w(TAG, "hook failed for " + method.getName()
                    + ": " + t.getMessage());
            try {
                return method.invoke(mBase, args);
            } catch (Throwable e) {
                Throwable cause = e.getCause();
                throw cause != null ? cause : e;
            }
        }

        try {
            result = methodHook.afterHook(result);
        } catch (Throwable t) {
            Slog.w(TAG, "afterHook failed for " + method.getName()
                    + ": " + t.getMessage());
        }

        return result;
    }
}
