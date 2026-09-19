package com.Score.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.Score.ScoreCore;
import com.Score.R;
import com.Score.utils.Slog;

/**
 * Created by BlackBox on 2022/2/24.
 */
public class LauncherActivity extends Activity {
    public static final String TAG = "SplashScreen";

    public static final String KEY_INTENT = "launch_intent";
    public static final String KEY_PKG = "launch_pkg";
    public static final String KEY_USER_ID = "launch_user_id";
    private boolean isRunning = false;
    private boolean launchRequested = false;

    public static void launch(Intent intent, int userId) {
        Intent splash = new Intent();
        splash.setClass(ScoreCore.getContext(), LauncherActivity.class);
        splash.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        splash.putExtra(LauncherActivity.KEY_INTENT, intent);
        String packageName = intent.getPackage();
        if (packageName == null && intent.getComponent() != null) {
            packageName = intent.getComponent().getPackageName();
        }
        splash.putExtra(LauncherActivity.KEY_PKG, packageName);
        splash.putExtra(LauncherActivity.KEY_USER_ID, userId);
        ScoreCore.getContext().startActivity(splash);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        Intent launchIntent = intent.getParcelableExtra(KEY_INTENT);
        String packageName = intent.getStringExtra(KEY_PKG);
        int userId = intent.getIntExtra(KEY_USER_ID, 0);

        PackageInfo packageInfo = ScoreCore.getBPackageManager().getPackageInfo(packageName, 0, userId);
        if (packageInfo == null) {
            Slog.e(TAG, packageName + " not installed!");
            finish();
            return;
        }
        if (launchIntent == null) {
            launchIntent = ScoreCore.getBPackageManager().getLaunchIntentForPackage(packageName, userId);
        }
        if (launchIntent == null) {
            Slog.e(TAG, packageName + " has no launchable activity");
            finish();
            return;
        }

        if (("com.facebook.katana".equals(packageName)
                || "com.facebook.orca".equals(packageName)
                || "com.facebook.lite".equals(packageName))
                && launchIntent.getComponent() == null) {
            Slog.e(TAG, "Facebook launch intent has no concrete virtual component: " + packageName);
            finish();
            return;
        }

        if (launchIntent.getComponent() == null && packageName != null) {
            launchIntent.setPackage(packageName);
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Drawable drawable = packageInfo.applicationInfo.loadIcon(ScoreCore.getPackageManager());
        setContentView(R.layout.activity_launcher);
        findViewById(R.id.iv_icon).setBackgroundDrawable(drawable);

        final Intent finalLaunchIntent = launchIntent;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (isFinishing() || isDestroyed()) return;
            launchRequested = true;
            try {
                ScoreCore.getBActivityManager().startActivity(finalLaunchIntent, userId);
            } catch (Throwable t) {
                Slog.e(TAG, "Launch failed for " + packageName + ": " + t);
                launchRequested = false;
                finish();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (launchRequested) {
            isRunning = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRunning && launchRequested) {
            finish();
        }
    }
}
