package com.Score.fake.service;

import android.Manifest;
import android.content.pm.PackageManager;

import com.Score.ScoreCore;
import com.Score.app.BActivityThread;

/**
 * Permission bridge for applications running inside the virtual process.
 *
 * The real Android permission controller grants RECORD_AUDIO (and other runtime
 * permissions) to the host package because the host owns the real process/UID.
 * The virtual package must therefore see the host's permission state when it
 * calls checkSelfPermission/checkPermission.
 *
 * This does NOT bypass the device permission. If the host package has not been
 * granted the permission by Android, the virtual app is also reported denied.
 */
final class VirtualPermissionCompat {
    private VirtualPermissionCompat() {}

    static boolean isVirtualPackage(String pkg) {
        if (pkg == null) return false;
        try {
            return ScoreCore.get().isInstalled(pkg, BActivityThread.getUserId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isVirtualUid(int uid) {
        try {
            return uid == BActivityThread.getBUid();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean hostHasPermission(String permission) {
        if (permission == null) return false;
        try {
            return ScoreCore.getContext().getPackageManager().checkPermission(
                    permission, ScoreCore.getHostPkg()) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            try {
                return ScoreCore.getContext().checkPermission(
                        permission,
                        android.os.Process.myPid(),
                        ScoreCore.getHostUid()) == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable ignoredAgain) {
                return false;
            }
        }
    }

    static boolean isRuntimePermission(String permission) {
        if (permission == null) return false;
        // Keep this broad: the host manifest remains the authority. A permission
        // can only appear granted here when Android has actually granted it to
        // the host package.
        return permission.startsWith("android.permission.")
                || permission.startsWith("com.android.");
    }
}
