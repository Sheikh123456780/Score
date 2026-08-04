package top.niunaijun.blackbox.core;

import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

public class NativeCore {
    public static final String TAG = "NativeCore";
    private static boolean sInitialized = false;

    static {
        try {
            System.loadLibrary("HASAD");
            Log.d(TAG, "Loaded HASAD library successfully");
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load HASAD library", e);
        }

        try {
            File libFile = new File(
                    BlackBoxCore.getContext().getFilesDir(),
                    "libbgmi.so"
            );
            if (libFile.exists()) {
                System.load(libFile.getAbsolutePath());
                Log.d(TAG, "Loaded bgmi library successfully");
            }
        } catch (Throwable e) {
            Log.d(TAG, "bgmi library not found, skipping");
        }
    }

    // ============================================================
    // Existing Native Methods
    // ============================================================

    public static native void init(int apiLevel);
    public static native void enableIO();
    public static native void addIORule(String targetPath, String relocatePath);
    public static native void hideXposed();
    public static native boolean disableHiddenApi();
    public static native void init_seccomp();

    // ============================================================
    // Android 16 Native Methods - MUST MATCH C++ IMPLEMENTATIONS
    // ============================================================

    public static native void hookServiceConnection();
    public static native Parcel fixServiceConnectionTransaction(IBinder binder, Object[] args);
    public static native void attachServiceSession(IBinder service, Object session);
    public static native boolean convertServiceConnection(IBinder binder, Object[] oldArgs, Object[] newArgs);

    // ============================================================
    // Init Method
    // ============================================================

    public static void initCore(int apiLevel) {
        if (sInitialized) return;
        sInitialized = true;

        try {
            init(apiLevel);
            Log.d(TAG, "HASAD init completed");
            
            if (BuildCompat.isAndroid16()) {
                Log.d(TAG, "Android 16 detected, applying ServiceConnection hooks");
                try {
                    hookServiceConnection();
                    Log.d(TAG, "hookServiceConnection called successfully");
                } catch (Throwable e) {
                    Log.e(TAG, "hookServiceConnection failed: " + e.getMessage());
                }
            }

            init_seccomp();
            Log.d(TAG, "NativeCore initialized successfully");
        } catch (Throwable e) {
            Log.e(TAG, "Failed to initialize NativeCore", e);
        }
    }

    // ============================================================
    // Redirect Methods
    // ============================================================

    @Keep
    public static int getCallingUid(int origCallingUid) {
        if (origCallingUid > 0 && origCallingUid < Process.FIRST_APPLICATION_UID)
            return origCallingUid;
        if (origCallingUid > Process.LAST_APPLICATION_UID)
            return origCallingUid;

        if (origCallingUid == BlackBoxCore.getHostUid()) {
            String packageName = BActivityThread.getAppPackageName();
            if (packageName != null) {
                if (packageName.equals("com.google.android.gms")) {
                    return Process.ROOT_UID;
                }
                if (packageName.equals("com.google.android.webview")) {
                    return Process.myUid();
                }
            }
            return BActivityThread.getCallingBUid();
        }
        return origCallingUid;
    }

    @Keep
    public static String redirectPath(String path) {
        return IOCore.get().redirectPath(path);
    }

    @Keep
    public static File redirectPath(File path) {
        return IOCore.get().redirectPath(path);
    }
}
