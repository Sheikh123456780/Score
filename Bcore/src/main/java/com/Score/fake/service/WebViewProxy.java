package com.Score.fake.service;

import android.content.Context;
import com.Score.ScoreCore;
import com.Score.fake.hook.ClassInvocationStub;
import com.Score.utils.Slog;

public class WebViewProxy extends ClassInvocationStub {
    public static final String TAG = "WebViewProxy";

    public WebViewProxy() {}

    @Override
    protected Object getWho() {
        try {
            return Class.forName("android.webkit.WebView");
        } catch (Throwable t) {
            Slog.w(TAG, "getWho: WebView class not found", t);
            return "android.webkit.WebView";
        }
    }

    @Override
    protected void inject(Object who, Object origin) {
        // No super.inject() here because ClassInvocationStub defines it abstract.
        // Deliberately not calling ensureWebViewDataDirectorySuffix() anymore.
        // BActivityThread.configureWebViewDataDirectory() is the single source of
        // truth for the WebView data directory suffix: it runs at a well-defined
        // point (handleBindApplication) and builds a stable suffix from
        // userId+package+process. This hook fires reactively whenever the
        // WebView class loads, with no guaranteed ordering relative to that —
        // so it could win the race and apply its own suffix first. Two problems
        // if it does: (1) it's built from Process.myPid(), which changes on
        // every process restart, so the WebView profile (and any session/cookie
        // data, e.g. a Twitter login) would never persist across relaunches;
        // (2) whichever suffix call runs second throws IllegalStateException,
        // which is silently swallowed, so the "real" configured suffix never
        // actually takes effect.
    }

    @Override
    public boolean isBadEnv() {
        try {
            Context ctx = ScoreCore.get() != null ? ScoreCore.get().getContext() : null;
            if (ctx == null) {
                Slog.w(TAG, "isBadEnv: BlackBox context is null");
                return true;
            }
            if (android.os.Build.VERSION.SDK_INT < 14) {
                Slog.w(TAG, "isBadEnv: SDK too old: " + android.os.Build.VERSION.SDK_INT);
                return true;
            }
            return false;
        } catch (Throwable t) {
            Slog.w(TAG, "isBadEnv error", t);
            return true;
        }
    }
}
