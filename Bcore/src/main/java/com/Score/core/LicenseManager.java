package com.Score.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class LicenseManager {
    private static final String T = "LM";
    
    // Obfuscated constants
    private static final String U1 = "Sheikh123456780";
    private static final String U2 = "Bboxauth";
    private static final String U3 = "License";
    private static final String U4 = "https://raw.githubusercontent.com/" + U1 + "/" + U2 + "/main/" + U3 + "/";
    
    private static volatile boolean Z1 = false;
    private static volatile boolean Z2 = false;
    private static String Z3 = "NV";
    private static long Z4 = 0;
    private static Context Z5;
    
    // Native methods with meaningless names
    private static native void A1();
    private static native void A2(boolean p, String s);
    
    static {
        try {
            System.loadLibrary("shayk");
        } catch (Throwable t) {}
    }
    
    public interface C1 {
        void onResult(boolean valid, String error, long days);
    }
    
    public static void X1(Context c, String k, C1 cb) {
        try { A1(); } catch (Throwable t) {}
        
        Z2 = false;
        Z3 = "V...";
        Z1 = false;
        
        if (c == null) {
            Z2 = true;
            Z3 = "CE";
            A2(false, Z3);
            if (cb != null) cb.onResult(false, Z3, 0);
            return;
        }
        
        Z5 = c.getApplicationContext();
        
        new Thread(() -> {
            try {
                String u = U4 + k + ".json";
                HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                
                int rc = con.getResponseCode();
                
                if (rc == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                    r.close();
                    
                    JSONObject j = new JSONObject(sb.toString());
                    String cp = c.getPackageName();
                    
                    // Signature check (obfuscated)
                    String cs = P1(c);
                    
                    JSONArray ba = j.optJSONArray("blocked_signatures");
                    if (ba != null) {
                        for (int i = 0; i < ba.length(); i++) {
                            if (cs.equalsIgnoreCase(ba.getString(i))) {
                                F1("Signature Blocked", "❌ SDK EXPIRED - Sdk Blocked", cb);
                                return;
                            }
                        }
                    }
                    
                    JSONArray aa = j.optJSONArray("authorized_signatures");
                    boolean sv = (aa == null || aa.length() == 0);
                    
                    if (!sv) {
                        sv = false;
                        for (int i = 0; i < aa.length(); i++) {
                            if (cs.equalsIgnoreCase(aa.getString(i))) {
                                sv = true;
                                break;
                            }
                        }
                    }
                    
                    if (!sv) {
                        F1("Invalid Signature", "❌ SDK EXPIRED - Invalid key", cb);
                        return;
                    }
                    
                    // License check (obfuscated)
                    String de = j.optString("expiry", "");
                    boolean ds = j.optBoolean("status", true);
                    
                    if (!ds) {
                        F1("License Disabled", "❌ License DISABLED", cb);
                        return;
                    }
                    
                    String pe = de;
                    boolean ps = ds;
                    boolean pf = false;
                    
                    if (j.has("packages")) {
                        JSONObject pk = j.getJSONObject("packages");
                        if (pk.has(cp)) {
                            JSONObject pi = pk.getJSONObject(cp);
                            ps = pi.optBoolean("status", ds);
                            pe = pi.optString("expiry", de);
                            pf = true;
                        }
                    }
                    
                    if (!pf) {
                        F1("Package Not Allowed", "❌ This app is NOT ALLOWED", cb);
                        return;
                    }
                    
                    if (!ps) {
                        F1("Package Blocked", "❌ This app is BLOCKED", cb);
                        return;
                    }
                    
                    if (pe.isEmpty()) {
                        F1("No Expiry", "❌ Invalid license config", cb);
                        return;
                    }
                    
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    Date exp = sdf.parse(pe);
                    Date now = new Date();
                    
                    if (now.after(exp)) {
                        F1("License Expired", "❌ License EXPIRED on " + pe, cb);
                        return;
                    }
                    
                    Z1 = true;
                    Z4 = TimeUnit.DAYS.convert(exp.getTime() - now.getTime(), TimeUnit.MILLISECONDS);
                    Z3 = "";
                    Z2 = true;
                    
                    M1("✅ License Active - " + Z4 + " days left");
                    A2(true, "");
                    if (cb != null) cb.onResult(true, "", Z4);
                    
                } else if (rc == 404) {
                    F1("Invalid License Key", "❌ Invalid License Key", cb);
                } else {
                    F1("Server Error: " + rc, "❌ Server Error", cb);
                }
                con.disconnect();
                
            } catch (Exception e) {
                F1("Network Error", "❌ Network Error", cb);
            }
        }).start();
    }
    
    private static String P1(Context c) {
        try {
            PackageInfo i = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES);
            if (i == null || i.signatures == null || i.signatures.length == 0) return "";
            Signature s = i.signatures[0];
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    private static void F1(String err, String toast, C1 cb) {
        Z1 = false;
        Z3 = err;
        Z2 = true;
        M1(toast);
        A2(false, Z3);
        if (cb != null) cb.onResult(false, Z3, 0);
    }
    
    private static void M1(String m) {
        if (Z5 != null) {
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(Z5, m, Toast.LENGTH_LONG).show());
        }
    }
    
    public static boolean Z6() { return Z1; }
    public static boolean Z7() { return Z2; }
    public static String Z8() { return Z3; }
    public static long Z9() { return Z4; }
}
