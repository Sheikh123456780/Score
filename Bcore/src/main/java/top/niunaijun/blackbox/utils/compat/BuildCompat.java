package top.niunaijun.blackbox.utils.compat;

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
    // Android Version Checks - Clean & Consistent
    // ============================================================

    /**
     * Android 16 (API 36) - 'Baklava'
     */
    public static boolean isAndroid16() {
        return Build.VERSION.SDK_INT >= 36;
    }

    /**
     * Android 15 (API 35) - 'Vanilla Ice Cream'
     */
    public static boolean isAndroid15() {
        return Build.VERSION.SDK_INT >= 35;
    }

    /**
     * Android 14 (API 34) - 'Upside Down Cake'
     */
    public static boolean isAndroid14() {
        return Build.VERSION.SDK_INT >= 34;
    }

    /**
     * Android 13 (API 33) - 'Tiramisu'
     */
    public static boolean isT() {
        return Build.VERSION.SDK_INT >= 33 || (Build.VERSION.SDK_INT >= 32 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    /**
     * Android 12 / 12L (API 31-32) - 'Snow Cone'
     */
    public static boolean isS() {
        return Build.VERSION.SDK_INT >= 31 || (Build.VERSION.SDK_INT >= 30 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    /**
     * Android 11 (API 30) - 'Red Velvet Cake'
     */
    public static boolean isR() {
        return Build.VERSION.SDK_INT >= 30 || (Build.VERSION.SDK_INT >= 29 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    /**
     * Android 10 (API 29) - 'Queen Cake'
     */
    public static boolean isQ() {
        return Build.VERSION.SDK_INT >= 29 || (Build.VERSION.SDK_INT >= 28 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    /**
     * Android 9 (API 28) - 'Pie'
     */
    public static boolean isPie() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P 
                || (Build.VERSION.SDK_INT >= 27 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    /**
     * Android 8 (API 26-27) - 'Oreo'
     */
    public static boolean isOreo() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                || (Build.VERSION.SDK_INT >= 25 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    /**
     * Android 7.1 (API 25) - 'Nougat MR1'
     */
    public static boolean isN_MR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 
                || (Build.VERSION.SDK_INT >= 24 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    /**
     * Android 7.0 (API 24) - 'Nougat'
     */
    public static boolean isN() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N 
                || (Build.VERSION.SDK_INT >= 23 && Build.VERSION.PREVIEW_SDK_INT >= 1);
    }

    /**
     * Android 6.0 (API 23) - 'Marshmallow'
     */
    public static boolean isM() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /**
     * Android 5.0-5.1 (API 21-22) - 'Lollipop'
     */
    public static boolean isL() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    // ============================================================
    // Legacy/Deprecated Methods (Keep for backward compatibility)
    // ============================================================

    /**
     * @deprecated Use {@link #isAndroid14()} instead
     */
    @Deprecated
    public static boolean isU() {
        return isAndroid14();
    }

    /**
     * @deprecated Use {@link #isAndroid15()} instead
     */
    @Deprecated
    public static boolean isV() {
        return isAndroid15();
    }

    // ============================================================
    // OEM/ROM Detection (Keep as-is)
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

    // ============================================================
    // ROM Type Enum
    // ============================================================

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
