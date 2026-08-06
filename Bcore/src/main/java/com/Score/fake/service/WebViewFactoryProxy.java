package com.Score.fake.service;

import android.content.Context;
import com.Score.ScoreCore;
import com.Score.fake.hook.ClassInvocationStub;
import com.Score.utils.Slog;

public class WebViewFactoryProxy extends ClassInvocationStub {
    public static final String TAG = "WebViewFactoryProxy";

    public WebViewFactoryProxy() {}

    @Override
    protected Object getWho() {
        try {
            return Class.forName("android.webkit.WebViewFactory");
        } catch (Throwable t) {
            Slog.w(TAG, "getWho: WebViewFactory not found", t);
            return "android.webkit.WebViewFactory";
        }
    }

    @Override
    protected void inject(Object who, Object origin) {
        // Nothing to call on super (abstract in base class).
        try {
            probeWebViewFactoryProvider();
        } catch (Throwable t) {
            Slog.w(TAG, "inject: probe failed", t);
        }
    }

    @Override
    public boolean isBadEnv() {
        try {
            Context ctx = ScoreCore.get() != null ? ScoreCore.get().getContext() : null;
            if (ctx == null) return true;
            if (android.os.Build.VERSION.SDK_INT < 14) return true;
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    private void probeWebViewFactoryProvider() {
        try {
            Class<?> factoryCls = Class.forName("android.webkit.WebViewFactory");
            String[] methods = {"getProvider", "getFactory", "getProviderClass"};
            for (String name : methods) {
                try {
                    java.lang.reflect.Method m = factoryCls.getDeclaredMethod(name);
                    m.setAccessible(true);
                    Object provider = m.invoke(null);
                    Slog.d(TAG, "Found " + name + ", provider=" + (provider == null ? "null" : provider.getClass().getName()));
                    if (provider != null) break;
                } catch (NoSuchMethodException ignore) {}
            }
        } catch (Throwable t) {
            Slog.w(TAG, "probeWebViewFactoryProvider: reflection failed", t);
        }
    }
}
