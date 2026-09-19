package com.Score.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.Score.utils.Slog;

/**
 * WebView login activity.
 *
 * This keeps the WebView profile inside the cloned application's process,
 * preserves cookies/session data, supports OAuth-style redirects and handles
 * incoming VIEW/redirect intents without leaving the virtualized app.
 */
public class WebViewActivity extends Activity {
    public static final String TAG = "WebViewActivity";

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_REDIRECT_URL = "redirect_url";
    public static final String EXTRA_REDIRECT_PATH = "redirectPath";
    public static final String EXTRA_ENABLE_REDIRECT = "enableRedirect";
    public static final String EXTRA_FOLLOW_REDIRECTS = "followRedirects";

    private WebView webView;
    private boolean enableRedirect = true;
    private boolean followRedirects = true;
    private boolean cacheMissRetried = false;

    public static class LoginWebChromeClient extends WebChromeClient {
        private LoginWebChromeClient() {
        }

        @Override
        public void onProgressChanged(WebView view, int progress) {
            Slog.d(TAG, "onProgressChanged: " + progress + "%");
        }
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            enableRedirect = intent.getBooleanExtra(EXTRA_ENABLE_REDIRECT, true);
            followRedirects = intent.getBooleanExtra(EXTRA_FOLLOW_REDIRECTS, true);
        }

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(webView);
        setContentView(root);

        configureWebView(webView);

        String url = getInitialUrl(intent);
        if (!TextUtils.isEmpty(url)) {
            loadUrl(url);
        } else {
            Slog.w(TAG, "No URL in intent extras");
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        // Login forms/OAuth redirects can arrive as POST navigations. On some
        // Samsung WebView builds a stale WebView cache produces ERR_CACHE_MISS.
        // Start with a network-first cache policy so the login page is not
        // dependent on a cached POST response.
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        // Keep authentication/session cookies in the WebView profile.
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            cookies.setAcceptThirdPartyCookies(view, true);
        }

        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setWebViewClient(new LoginWebViewClient());
        view.setWebChromeClient(new LoginWebChromeClient());

        WebView.setWebContentsDebuggingEnabled(false);
    }

    private String getInitialUrl(Intent intent) {
        if (intent == null) return null;

        String url = intent.getStringExtra(EXTRA_URL);
        if (TextUtils.isEmpty(url)) {
            url = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        if (TextUtils.isEmpty(url) && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) url = data.toString();
        }

        return url;
    }

    private void loadUrl(String url) {
        if (webView == null || TextUtils.isEmpty(url)) return;

        Slog.d(TAG, "Loading URL: " + url);

        if (url.startsWith("http://") || url.startsWith("https://")) {
            webView.loadUrl(url);
        } else {
            handleRedirect(url);
        }
    }

    /**
     * Handles OAuth/login callback schemes without attempting to load them as
     * normal HTTP pages.
     */
    private void handleRedirect(String url) {
        if (!enableRedirect) {
            Slog.d(TAG, "Redirect disabled: " + url);
            return;
        }

        Slog.d(TAG, "handleRedirect: " + url);

        Intent result = new Intent();
        result.setData(Uri.parse(url));
        result.putExtra(EXTRA_REDIRECT_URL, url);
        setResult(Activity.RESULT_OK, result);

        // If a caller explicitly asked us to follow redirects, keep the
        // callback in this activity; otherwise return the callback to caller.
        if (!followRedirects) {
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String url = getInitialUrl(intent);
        if (!TextUtils.isEmpty(url)) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (webView != null) webView.loadUrl(url);
            } else {
                handleRedirect(url);
            }
        }
    }

    /**
     * Called by the virtual ActivityThread redirect path when a login callback
     * is delivered as a new intent.
     */
    public void handleNewIntent(Intent intent) {
        onNewIntent(intent);
    }

    public boolean isEnableRedirect() {
        return enableRedirect;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.setWebViewClient(null);
                webView.setWebChromeClient(null);
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable t) {
                Slog.w(TAG, "WebView destroy failed: " + t);
            }
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public class LoginWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Slog.d(TAG, "onPageStarted: " + url);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Slog.d(TAG, "onPageFinished: " + url);

            CookieManager cookies = CookieManager.getInstance();
            cookies.flush();
            cacheMissRetried = false;

            super.onPageFinished(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) return false;

            String url = request.getUrl().toString();
            Slog.d(TAG, "shouldOverrideUrlLoading: " + url);

            if (isCallbackUrl(url)) {
                handleRedirect(url);
                return true;
            }

            return !followRedirects;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Slog.d(TAG, "shouldOverrideUrlLoading: " + url);

            if (isCallbackUrl(url)) {
                handleRedirect(url);
                return true;
            }

            return !followRedirects;
        }

        private boolean isCallbackUrl(String url) {
            if (TextUtils.isEmpty(url)) return false;

            String lower = url.toLowerCase();
            String configured = getIntent() == null
                    ? null
                    : getIntent().getStringExtra(EXTRA_REDIRECT_PATH);

            if (!TextUtils.isEmpty(configured) && lower.contains(configured.toLowerCase())) {
                return true;
            }

            // OAuth/login callbacks commonly use custom schemes or explicit
            // callback paths. Normal https pages remain inside WebView.
            return !(lower.startsWith("http://") || lower.startsWith("https://"));
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            if (request != null && error != null) {
                Slog.e(TAG, "onReceivedError: " + request.getUrl()
                        + " code=" + error.getErrorCode()
                        + " desc=" + error.getDescription());

                // ERROR_CACHE_MISS is especially common when an OAuth/login
                // form is resumed after a POST navigation. Retry the main
                // frame once with LOAD_NO_CACHE instead of leaving the user
                // on Chromium's generic "Page not available" screen.
                if (request.isForMainFrame()
                        && error.getErrorCode() == WebViewClient.ERROR_CACHE_MISS
                        && !cacheMissRetried) {
                    cacheMissRetried = true;
                    String retryUrl = request.getUrl().toString();
                    view.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                    view.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (webView != null && !TextUtils.isEmpty(retryUrl)) {
                                Slog.w(TAG, "Retrying after ERR_CACHE_MISS: " + retryUrl);
                                webView.loadUrl(retryUrl);
                            }
                        }
                    }, 150L);
                }
            }
            // Do not call super for a cache-miss retry; Chromium otherwise
            // immediately commits its generic error page. Other errors keep
            // the normal WebView behavior.
            if (error == null
                    || error.getErrorCode() != WebViewClient.ERROR_CACHE_MISS
                    || request == null
                    || !request.isForMainFrame()
                    || !cacheMissRetried) {
                super.onReceivedError(view, request, error);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode,
                                    String description, String failingUrl) {
            Slog.e(TAG, "onReceivedError (legacy): " + failingUrl
                    + " code=" + errorCode + " desc=" + description);

            if (errorCode == WebViewClient.ERROR_CACHE_MISS && !cacheMissRetried
                    && !TextUtils.isEmpty(failingUrl)) {
                cacheMissRetried = true;
                view.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                view.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null) {
                            Slog.w(TAG, "Retrying after legacy ERR_CACHE_MISS: " + failingUrl);
                            webView.loadUrl(failingUrl);
                        }
                    }
                }, 150L);
                return;
            }

            super.onReceivedError(view, errorCode, description, failingUrl);
        }
    }
}
