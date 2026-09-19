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
        // Intentionally a no-op now. probeWebViewFactoryProvider() used to call
        // WebViewFactory.getProvider() via reflection here, but that call has the
        // side effect of actually initializing the Chromium WebView provider
        // immediately. Because this hook fires reactively as soon as the
        // WebViewFactory class is touched, it could run before
        // BActivityThread.configureWebViewDataDirectory() gets a chance to call
        // WebView.setDataDirectorySuffix(). Once WebView is initialized, that
        // later suffix call throws IllegalStateException (swallowed, logged as a
        // warning), so the app silently falls back to the default/unsuffixed
        // WebView data directory instead of the intended per-clone one — losing
        // session/cookie isolation (e.g. Twitter login not persisting) and, in
        // multi-process cases, risking the "WebView used from more than one
        // process" crash. Leave detection to logging only; never invoke().
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
}
