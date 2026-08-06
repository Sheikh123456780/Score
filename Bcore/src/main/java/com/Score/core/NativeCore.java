package com.Score.core;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.Score.ScoreCore;
import com.Score.app.BActivityThread;
import com.Score.utils.compat.BuildCompat;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class NativeCore {
    public static final String TAG = "NativeCore";
    private static boolean sInitialized = false;

    static {
        try {
            System.loadLibrary("shayk");
            loadAnySoFile(ScoreCore.getContext().getFilesDir());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load native library", t);
        }
    }

    private static void loadAnySoFile(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                loadAnySoFile(file);
            } else if (file.getName().endsWith(".so")) {
                if (file.getName().equals("libshayk.so")) {
                    continue;
                }
                try {
                    System.load(file.getAbsolutePath());
                    Log.i(TAG, "✅ Loaded: " + file.getAbsolutePath());
                } catch (Throwable e) {
                    Log.e(TAG, "❌ Failed to load: " + file.getAbsolutePath() + " - " + e.getMessage());
                }
            }
        }
    }

    public static native void init(int apiLevel);

    public static native void enableIO();

    public static native void addIORule(String targetPath, String relocatePath);

    public static native void hideXposed();

    public static native boolean disableHiddenApi();

    public static native void init_seccomp();

    @Keep
    public static int getCallingUid(int origCallingUid) {
        try {
            if (origCallingUid > 0 && origCallingUid < Process.FIRST_APPLICATION_UID)
                return origCallingUid;
            if (origCallingUid > Process.LAST_APPLICATION_UID)
                return origCallingUid;

            if (origCallingUid == ScoreCore.getHostUid()) {
                if (BActivityThread.getAppPackageName() != null) {
                    if (BActivityThread.getAppPackageName().equals("com.google.android.gms")) {
                        return Process.ROOT_UID;
                    }
                    if (BActivityThread.getAppPackageName().equals("com.google.android.webview")) {
                        return Process.myUid();
                    }
                }
                return BActivityThread.getCallingBUid();
            }
            return origCallingUid;
        } catch (Throwable t) {
            Log.e(TAG, "Error in getCallingUid", t);
            return Process.myUid();
        }
    }

    @Keep
    public static String redirectPath(String path) {
        try {
            return IOCore.get().redirectPath(path);
        } catch (Throwable t) {
            return path;
        }
    }

    @Keep
    public static File redirectPath(File path) {
        try {
            return IOCore.get().redirectPath(path);
        } catch (Throwable t) {
            return path;
        }
    }

    // ========== ANDROID 14+ COMPATIBILITY ==========
    public static void initialize() {
        if (sInitialized) return;
        try {
            int apiLevel = Build.VERSION.SDK_INT;
            Log.i(TAG, "Initializing NativeCore for API level: " + apiLevel);
            
            // Initialize native
            init(apiLevel);
            
            // Try to disable hidden API
            boolean hiddenApiDisabled = disableHiddenApi();
            if (hiddenApiDisabled) {
                Log.i(TAG, "Hidden API disabled successfully");
            } else {
                Log.w(TAG, "Failed to disable hidden API, some features may not work");
            }
            
            sInitialized = true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize NativeCore", t);
        }
    }
}
