package com.Score.utils.compat;

import android.os.Build;

public class BuildCompat {

    public static int getPreviewSDKInt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return Build.VERSION.PREVIEW_SDK_INT;
            } catch (Throwable e) {
                // ignore
            }
        }
        return 0;
    }

    // ============================================================
    // ANDROID 17+ (API 37+)
    // ============================================================
    public static boolean isAndroid17() {
        return Build.VERSION.SDK_INT >= 37;
    }
    
    // ============================================================
    // ANDROID 16 (API 36+) - Baklava
    // ============================================================
    public static boolean isAndroid16() {
        return Build.VERSION.SDK_INT >= 36;
    }
    
    // ============================================================
    // ANDROID 15 (API 35+) - Vanilla Ice Cream
    // ============================================================
    public static boolean isAndroid15() {
        return Build.VERSION.SDK_INT >= 35 || (Build.VERSION.SDK_INT >= 34 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }
    
    // ============================================================
    // ANDROID 14 (API 34+) - Upside Down Cake
    // ============================================================
    public static boolean isU() {
        return Build.VERSION.SDK_INT >= 34 || (Build.VERSION.SDK_INT >= 33 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }
    
    // ============================================================
    // ANDROID 13 (API 33+) - Tiramisu
    // ============================================================
    public static boolean isT() {
        return Build.VERSION.SDK_INT >= 33 || (Build.VERSION.SDK_INT >= 32 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    // ============================================================
    // ANDROID 12 (API 31-32) - Snow Cone
    // ============================================================
    public static boolean isS() {
        return Build.VERSION.SDK_INT >= 31 || (Build.VERSION.SDK_INT >= 30 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    // ============================================================
    // ANDROID 11 (API 30) - Red Velvet Cake
    // ============================================================
    public static boolean isR() {
        return Build.VERSION.SDK_INT >= 30 || (Build.VERSION.SDK_INT >= 29 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    // ============================================================
    // ANDROID 10 (API 29) - Queen Cake
    // ============================================================
    public static boolean isQ() {
        return Build.VERSION.SDK_INT >= 29 || (Build.VERSION.SDK_INT >= 28 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    // ============================================================
    // ANDROID 9 (API 28) - Pie
    // ============================================================
    public static boolean isPie() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P || (Build.VERSION.SDK_INT >= 27 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    // ============================================================
    // ANDROID 8 (API 26-27) - Oreo
    // ============================================================
    public static boolean isOreo() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || (Build.VERSION.SDK_INT >= 25 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================
    
    public static boolean isAndroid14OrAbove() {
        return isU() || isAndroid15() || isAndroid16() || isAndroid17();
    }
    
    public static boolean isAndroid13OrAbove() {
        return isT() || isAndroid14OrAbove();
    }
    
    public static boolean isAndroid12OrAbove() {
        return isS() || isAndroid13OrAbove();
    }
    
    public static boolean isAndroid11OrAbove() {
        return isR() || isAndroid12OrAbove();
    }
    
    public static boolean isAndroid10OrAbove() {
        return isQ() || isAndroid11OrAbove();
    }

    public static boolean isN_MR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 || (Build.VERSION.SDK_INT >= 24 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    public static boolean isN() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || (Build.VERSION.SDK_INT >= 23 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    public static boolean isM() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static boolean isL() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    // ============================================================
    // OEM DETECTION
    // ============================================================
    
    public static boolean isSamsung() {
        return "samsung".equalsIgnoreCase(Build.BRAND) || "samsung".equalsIgnoreCase(Build.MANUFACTURER);
    }

    public static boolean isEMUI() {
        if (Build.DISPLAY.toUpperCase().startsWith("EMUI")) {
            return true;
        }
        String property = SystemPropertiesCompat.get("ro.build.version.emui");
        return property != null && property.contains("EmotionUI");
    }

    public static boolean isMIUI() {
        return SystemPropertiesCompat.getInt("ro.miui.ui.version.code", 0) > 0;
    }

    public static boolean isFlyme() {
        return Build.DISPLAY.toLowerCase().contains("flyme");
    }

    public static boolean isColorOS() {
        return SystemPropertiesCompat.isExist("ro.build.version.opporom")
                || SystemPropertiesCompat.isExist("ro.rom.different.version");
    }

    public static boolean is360UI() {
        String property = SystemPropertiesCompat.get("ro.build.uiversion");
        return property != null && property.toUpperCase().contains("360UI");
    }

    public static boolean isLetv() {
        return Build.MANUFACTURER.equalsIgnoreCase("Letv");
    }

    public static boolean isVivo() {
        return SystemPropertiesCompat.isExist("ro.vivo.os.build.display.id");
    }

    private static ROMType sRomType;

    public static ROMType getROMType() {
        if (sRomType == null) {
            if (isEMUI()) {
                sRomType = ROMType.EMUI;
            } else if (isMIUI()) {
                sRomType = ROMType.MIUI;
            } else if (isFlyme()) {
                sRomType = ROMType.FLYME;
            } else if (isColorOS()) {
                sRomType = ROMType.COLOR_OS;
            } else if (is360UI()) {
                sRomType = ROMType._360;
            } else if (isLetv()) {
                sRomType = ROMType.LETV;
            } else if (isVivo()) {
                sRomType = ROMType.VIVO;
            } else if (isSamsung()) {
                sRomType = ROMType.SAMSUNG;
            } else {
                sRomType = ROMType.OTHER;
            }
        }
        return sRomType;
    }

    public enum ROMType {
        EMUI,
        MIUI,
        FLYME,
        COLOR_OS,
        LETV,
        VIVO,
        _360,
        SAMSUNG,
        OTHER
    }
}
