package com.Score.fake.hook;

import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.Score.utils.MethodParameterUtils;

/** Invocation proxy base with safe reinjection and nested-proxy unwrapping. */
public abstract class ClassInvocationStub implements InvocationHandler, IInjectHook {
    public static final String TAG = ClassInvocationStub.class.getSimpleName();

    private final Map<String, MethodHook> mMethodHookMap = new HashMap<>();
    private Object mBase;
    private Object mProxyInvocation;
    private boolean onlyProxy;

    /** Keep OAuth/social-login navigation inside the virtualized WebView. */
    public static class WebViewHook extends MethodHook {
        @Override
        protected String getMethodName() {
            return "shouldOverrideUrlLoading";
        }

        @Override
        protected Object beforeHook(Object who, Method method, Object[] args) {
            if (args == null || args.length < 2 || !(args[1] instanceof String)) {
                return null;
            }
            String url = (String) args[1];
            if (url == null) {
                return null;
            }
            String lower = url.toLowerCase(java.util.Locale.US);
            if (lower.contains("facebook.com")
                    || lower.contains("fbcdn.net")
                    || lower.contains("twitter.com")
                    || lower.contains("x.com")
                    || lower.contains("api.twitter.com")
                    || lower.contains("ads-api.twitter.com")) {
                return Boolean.FALSE;
            }
            return null;
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    protected abstract Object getWho();

    protected abstract void inject(Object baseInvocation, Object proxyInvocation);

    protected void onBindMethod() {
        addMethodHook(new WebViewHook());
    }

    protected Object getProxyInvocation() {
        return mProxyInvocation;
    }

    protected Object getBase() {
        return mBase;
    }

    protected void onlyProxy(boolean value) {
        onlyProxy = value;
    }

    private Object unwrapPreviousHookProxy(Object object) {
        Object current = object;
        for (int depth = 0; depth < 16 && current != null
                && Proxy.isProxyClass(current.getClass()); depth++) {
            try {
                InvocationHandler handler = Proxy.getInvocationHandler(current);
                if (!(handler instanceof ClassInvocationStub)) {
                    break;
                }
                ClassInvocationStub stub = (ClassInvocationStub) handler;
                Object base = stub.mBase;
                if (base == null || base == current) {
                    break;
                }
                current = base;
            } catch (Throwable ignored) {
                break;
            }
        }
        return current;
    }

    @Override
    public void injectHook() {
        Object who = getWho();
        Object base = unwrapPreviousHookProxy(who);
        mBase = base;

        if (base == null) {
            Log.w(TAG, "injectHook failed: base object is null ("
                    + getClass().getSimpleName() + ")");
            return;
        }

        if (base != who) {
            Log.w(TAG, "Removed nested proxy while reinjecting "
                    + getClass().getSimpleName());
        }

        Class<?>[] interfaces = MethodParameterUtils.getAllInterface(base.getClass());
        mProxyInvocation = Proxy.newProxyInstance(
                base.getClass().getClassLoader(), interfaces, this);

        if (!onlyProxy) {
            inject(base, mProxyInvocation);
        }

        onBindMethod();
        for (Class<?> declaredClass : getClass().getDeclaredClasses()) {
            initAnnotation(declaredClass);
        }
        ScanClass scanClass = getClass().getAnnotation(ScanClass.class);
        if (scanClass != null) {
            for (Class<?> scan : scanClass.value()) {
                for (Class<?> declaredClass : scan.getDeclaredClasses()) {
                    initAnnotation(declaredClass);
                }
            }
        }
    }

    protected void initAnnotation(Class<?> clazz) {
        ProxyMethod proxyMethod = clazz.getAnnotation(ProxyMethod.class);
        if (proxyMethod != null && !TextUtils.isEmpty(proxyMethod.value())) {
            try {
                addMethodHook(proxyMethod.value(), (MethodHook) clazz.newInstance());
            } catch (Throwable t) {
                Log.w(TAG, "Unable to create hook " + clazz.getName(), t);
            }
        }

        ProxyMethods proxyMethods = clazz.getAnnotation(ProxyMethods.class);
        if (proxyMethods != null) {
            for (String name : proxyMethods.value()) {
                try {
                    addMethodHook(name, (MethodHook) clazz.newInstance());
                } catch (Throwable t) {
                    Log.w(TAG, "Unable to create hook " + clazz.getName(), t);
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
        Object base = unwrapPreviousHookProxy(mBase);
        if (base == null || base == proxy) {
            throw new IllegalStateException(
                    "Recursive proxy base for " + getClass().getSimpleName()
                            + "." + method.getName());
        }
        if (base != mBase) {
            mBase = base;
        }

        if (method != null && !method.isAccessible()) {
            try {
                method.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }

        MethodHook hook = mMethodHookMap.get(method.getName());
        if (hook == null || !hook.isEnable()) {
            try {
                return method.invoke(base, args);
            } catch (Throwable error) {
                Throwable cause = error.getCause();
                throw cause != null ? cause : error;
            }
        }

        Object before = hook.beforeHook(base, method, args);
        if (before != null) {
            return before;
        }
        return hook.afterHook(hook.hook(base, method, args));
    }
}

