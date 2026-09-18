package com.Score.fake.service;

import android.content.Context;
import android.os.Process;
import android.webkit.WebSettings;
import android.webkit.WebView;
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
        try {
            ensureWebViewDataDirectorySuffix();
        } catch (Throwable t) {
            Slog.w(TAG, "inject: ensureWebViewDataDirectorySuffix failed", t);
        }
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

    private void ensureWebViewDataDirectorySuffix() {
        if (android.os.Build.VERSION.SDK_INT < 28) return;
        String suffix = buildDataDirectorySuffix();
        if (suffix == null || suffix.isEmpty()) return;
        try {
            Class<?> webViewClass = Class.forName("android.webkit.WebView");
            java.lang.reflect.Method m = webViewClass.getMethod("setDataDirectorySuffix", String.class);
            m.invoke(null, suffix);
            Slog.d(TAG, "Applied WebView suffix=" + suffix);
        } catch (Throwable t) {
            Slog.w(TAG, "Failed to set WebView suffix", t);
        }
    }

    private String buildDataDirectorySuffix() {
        try {
            int uid = Process.myUid();
            int pid = Process.myPid();
            return "blackbox_u" + uid + "_p" + pid;
        } catch (Throwable t) {
            return null;
        }
    }
}
