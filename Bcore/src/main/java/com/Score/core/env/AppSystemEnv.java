package com.Score.core.env;

import android.content.ComponentName;

import java.util.ArrayList;
import java.util.List;

import com.Score.ScoreCore;
import org.lsposed.lsparanoid.Obfuscate;
@Obfuscate
public class AppSystemEnv {
    private static final List<String> sSystemPackages = new ArrayList<>();
    private static final List<String> sSuPackages = new ArrayList<>();
    private static final List<String> sXposedPackages = new ArrayList<>();
    private static final List<String> sPreInstallPackages = new ArrayList<>();

    static {
        // System packages
        sSystemPackages.add("android");
        sSystemPackages.add("com.google.android.webview");
        sSystemPackages.add("com.google.android.webview.dev");
        sSystemPackages.add("com.google.android.webview.beta");
        sSystemPackages.add("com.google.android.webview.canary");
        sSystemPackages.add("com.android.webview");
        sSystemPackages.add("com.android.camera");
        sSystemPackages.add("com.android.talkback");
        sSystemPackages.add("com.miui.gallery");
        sSystemPackages.add("com.google.android.inputmethod.latin");
        sSystemPackages.add("com.huawei.webview");
        sSystemPackages.add("com.coloros.safecenter");

        // ========== EXTERNAL LOGIN APPS ==========
        sSystemPackages.add("com.twitter.android");
        sSystemPackages.add("com.facebook.katana");
        sSystemPackages.add("com.facebook.orca");
        sSystemPackages.add("com.facebook.lite");
        // =========================================

        // ========== AUDIO/MICROPHONE PACKAGES ==========
        sSystemPackages.add("android.media");
        sSystemPackages.add("com.android.soundrecorder");
        sSystemPackages.add("com.google.android.soundrecorder");
        // ==============================================

        // SU packages
        sSuPackages.add("com.noshufou.android.su");
        sSuPackages.add("com.noshufou.android.su.elite");
        sSuPackages.add("eu.chainfire.supersu");
        sSuPackages.add("com.koushikdutta.superuser");
        sSuPackages.add("com.thirdparty.superuser");
        sSuPackages.add("com.yellowes.su");
        sSuPackages.add("com.topjohnwu.magisk");

        // Xposed packages
        sXposedPackages.add("de.robv.android.xposed.installer");
    }

    public static boolean isOpenPackage(String packageName) {
        if (packageName == null) return false;
        
        // External login apps
        if ("com.twitter.android".equals(packageName) ||
            "com.facebook.katana".equals(packageName) ||
            "com.facebook.orca".equals(packageName) ||
            "com.facebook.lite".equals(packageName)) {
            return true;
        }
        
        // Audio packages
        if (packageName.startsWith("android.media")) {
            return true;
        }
        
        return sSystemPackages.contains(packageName);
    }

    public static boolean isOpenPackage(ComponentName componentName) {
        return componentName != null && isOpenPackage(componentName.getPackageName());
    }

    public static boolean isBlackPackage(String packageName) {
        if (packageName == null) return false;
        
        // Never hide external login apps
        if ("com.twitter.android".equals(packageName) ||
            "com.facebook.katana".equals(packageName) ||
            "com.facebook.orca".equals(packageName) ||
            "com.facebook.lite".equals(packageName)) {
            return false;
        }
        
        // Never hide audio packages
        if (packageName.startsWith("android.media")) {
            return false;
        }
        
        // Hide root apps if enabled
        if (ScoreCore.get().isHideRoot() && sSuPackages.contains(packageName)) {
            return true;
        }
        
        // Hide Xposed apps if enabled
        if (ScoreCore.get().isHideXposed() && sXposedPackages.contains(packageName)) {
            return true;
        }
        
        return false;
    }

    public static List<String> getPreInstallPackages() {
        return sPreInstallPackages;
    }
}
