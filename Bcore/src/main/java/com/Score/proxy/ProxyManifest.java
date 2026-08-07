package com.Score.proxy;

import java.util.Locale;

import com.Score.ScoreCore;

/**
 * Created by Milk on 4/1/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ProxyManifest {
    public static final int FREE_COUNT = 50;

    public static boolean isProxy(String msg) {
        return getBindProvider().equals(msg) || msg.contains("proxy_content_provider_");
    }

    public static String getBindProvider() {
        return ScoreCore.getHostPkg() + ".SdCard.SystemCallProvider";
    }

    public static String getProxyAuthorities(int index) {
        return String.format(Locale.CHINA, "%s.proxy_content_provider_%d", ScoreCore.getHostPkg(), index);
    }

    public static String getProxyPendingActivity(int index) {
        return String.format(Locale.CHINA, "com.Score.proxy.ProxyPendingActivity$P%d", index);
    }

    public static String getProxyActivity(int index) {
        return String.format(Locale.CHINA, "com.Score.proxy.ProxyActivity$P%d", index);
    }

    public static String TransparentProxyActivity(int index) {
        return String.format(Locale.CHINA, "com.Score.proxy.TransparentProxyActivity$P%d", index);
    }

    public static String getProxyService(int index) {
        return String.format(Locale.CHINA, "com.Score.proxy.ProxyService$P%d", index);
    }

    public static String getProxyJobService(int index) {
        return String.format(Locale.CHINA, "com.Score.proxy.ProxyJobService$P%d", index);
    }

    public static String getProxyFileProvider() {
        return ScoreCore.getHostPkg() + ".SdCard.FileProvider";
    }

    public static String getProxyReceiver() {
        return ScoreCore.getHostPkg() + ".stub_receiver";
    }

    public static String getProcessName(int bPid) {
        return ScoreCore.getHostPkg() + ":p" + bPid;
    }
}
