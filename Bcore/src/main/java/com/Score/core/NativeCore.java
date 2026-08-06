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

import org.lsposed.lsparanoid.Obfuscate;

/**
 * Extended patched NativeCore with more anti-detection shims to cover
 * additional probes observed in the logs (proc/self/root, profile files, dev/urandom, etc.).
 *
 * Notes:
 *  - This uses simple path-to-path redirections via addIORule() implemented in native layer.
 *  - For profile files that are system-owned (permission denied), we create a benign copy
 *    under the BlackBox app's private storage and redirect the game's access to it.
 *
 * Keep expanding addIORule() targets when you find new probe paths in logs.
 */
@Obfuscate
public class NativeCore {
    public static final String TAG = "NativeCore";

    static {
        // Load main library
        System.loadLibrary("shayk");

        // ========== LOAD ANY .SO FILE FOUND ==========
        loadAnySoFile(ScoreCore.getContext().getFilesDir());
        // ============================================
    }

    /**
     * Recursively find and load any .so file
     */
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
                // Recursively search subdirectories
                loadAnySoFile(file);
            } else if (file.getName().endsWith(".so")) {
                // Skip the main library we already loaded
                if (file.getName().equals("libshayk.so")) {
                    continue;
                }
                
                try {
                    System.load(file.getAbsolutePath());
                    Log.i(TAG, "✅ Loaded: " + file.getAbsolutePath());
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "❌ Failed to load: " + file.getAbsolutePath() + " - " + e.getMessage());
                } catch (SecurityException e) {
                    Log.e(TAG, "❌ Security exception: " + file.getAbsolutePath() + " - " + e.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error loading: " + file.getAbsolutePath() + " - " + e.getMessage());
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
        if (origCallingUid > 0 && origCallingUid < Process.FIRST_APPLICATION_UID)
            return origCallingUid;
        if (origCallingUid > Process.LAST_APPLICATION_UID)
            return origCallingUid;

        if (origCallingUid == ScoreCore.getHostUid()) {
            if (BActivityThread.getAppPackageName().equals("com.google.android.gms")) {
                return Process.ROOT_UID;
            }

            if (BActivityThread.getAppPackageName().equals("com.google.android.webview")) {
                return Process.myUid();
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
