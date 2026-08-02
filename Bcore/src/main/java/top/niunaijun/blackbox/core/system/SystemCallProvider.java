package top.niunaijun.blackbox.core.system;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BundleCompat;

public class SystemCallProvider extends ContentProvider {
    public static final String TAG = "SystemCallProvider";

    @Override
    public boolean onCreate() {
        return initSystem();
    }

    private boolean initSystem() {
        // Initialize environment first
        BEnvironment.load();
        BlackBoxSystem.getSystem().startup();
        return true;
    }

    // ========== FIXED: Dynamic directory creation ==========
    private void ensureDirectories() {
        String hostPkg = BlackBoxCore.getHostPkg();
        String dataDir = BlackBoxCore.getContext().getDataDir().getAbsolutePath();
        String virtualRoot = BEnvironment.getVirtualRoot().getAbsolutePath();
        
        // Critical paths - all dynamically constructed
        String[][] paths = {
            // Host app directories
            {dataDir + "/app_webview", null},
            {dataDir + "/app_webview_0", null},
            {dataDir + "/app_webview_0:com.google.android.gms:com.google.android.gms", null},
            {dataDir + "/app_webview_0:com.google.android.gms:com.google.android.gms.persistent", null},
            {dataDir + "/cache/webview", null},
            {dataDir + "/cache/webview_0:com.google.android.gms:com.google.android.gms", null},
            
            // Virtual root directories
            {virtualRoot + "/cache", null},
            {virtualRoot + "/cache/temp", null},
            {virtualRoot + "/data", null},
            {virtualRoot + "/data/user", null},
            {virtualRoot + "/data/user_de", null},
            {virtualRoot + "/data/app", null},
            {virtualRoot + "/system", null},
            {virtualRoot + "/proc", null},
            
            // Google Services directories (auto-detected)
            {virtualRoot + "/data/user_de/0/com.google.android.gms", null},
            {virtualRoot + "/data/user_de/0/com.google.android.gms/app_chimera", null},
            {virtualRoot + "/data/user_de/0/com.google.android.gms/cache", null},
            {virtualRoot + "/data/user_de/0/com.google.android.gms/files", null},
            {virtualRoot + "/data/user_de/0/com.google.android.gms/shared_prefs", null},
            {virtualRoot + "/data/user/0/com.google.android.gms", null},
            {virtualRoot + "/data/user/0/com.google.android.gms/databases", null},
            {virtualRoot + "/data/user/0/com.google.android.gms/shared_prefs", null},
            {virtualRoot + "/data/user/0/com.google.android.gms/files", null},
            {virtualRoot + "/data/user/0/com.google.android.gms/cache", null},
            {virtualRoot + "/data/user_de/0/com.google.android.gsf", null},
            {virtualRoot + "/data/user/0/com.google.android.gsf", null},
            {virtualRoot + "/data/user/0/com.android.vending", null},
            {virtualRoot + "/data/user_de/0/com.google.android.webview", null},
            
            // Sdcard directories
            {"/sdcard/Android/data", null},
            {"/sdcard/Android/obb", null},
            {"/sdcard/Android/media", null},
        };
        
        for (String[] pathInfo : paths) {
            String path = pathInfo[0];
            if (path == null) continue;
            
            File dir = new File(path);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (created) {
                    Log.d(TAG, "Created directory: " + path);
                }
            }
        }
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Slog.d(TAG, "call: " + method + ", " + extras);
        
        // FIX: Ensure directories exist before any service call
        ensureDirectories();
        
        if ("VM".equals(method)) {
            Bundle bundle = new Bundle();
            if (extras != null) {
                String name = extras.getString("_B_|_server_name_");
                BundleCompat.putBinder(bundle, "_B_|_server_", ServiceManager.getService(name));
            }
            return bundle;
        }
        return super.call(method, arg, extras);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, 
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, 
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
