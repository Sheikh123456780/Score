package com.Score.utils.compat;

import android.content.Context;
import android.content.ContextWrapper;

import black.android.app.BRContextImpl;
import black.android.app.BRContextImplKitkat;
import black.android.content.AttributionSourceStateContext;
import black.android.content.BRAttributionSource;
import black.android.content.BRAttributionSourceState;
import black.android.content.BRContentResolver;
import com.Score.ScoreCore;
import com.Score.app.BActivityThread;
import top.niunaijun.blackreflection.BlackReflection;

/**
 * Created by Milk on 3/31/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ContextCompat {
    public static final String TAG = "ContextCompat";

    public static void fixAttributionSourceState(Object obj, int uid) {
        Object mAttributionSourceState;
        if (obj != null && BRAttributionSource.get(obj)._check_mAttributionSourceState() != null) {
            mAttributionSourceState = BRAttributionSource.get(obj).mAttributionSourceState();

            AttributionSourceStateContext attributionSourceStateContext = BRAttributionSourceState.get(mAttributionSourceState);
            attributionSourceStateContext._set_packageName(ScoreCore.getHostPkg());
            attributionSourceStateContext._set_uid(uid);
            fixAttributionSourceState(BRAttributionSource.get(obj).getNext(), uid);
        }
    }
    
    public static void fixAttributionSourceState(Object obj, int uid, int pid) {
        if (obj != null && BRAttributionSource.get(obj)._check_mAttributionSourceState() != null) {
            return;
        }
        AttributionSourceStateContext attributionSourceStateContext = (AttributionSourceStateContext) BlackReflection.create(AttributionSourceStateContext.class,BRAttributionSource.get(obj).mAttributionSourceState(), false);
        attributionSourceStateContext._set_packageName(ScoreCore.getHostPkg());
        attributionSourceStateContext._set_uid(Integer.valueOf(uid));
        attributionSourceStateContext._set_pid(Integer.valueOf(pid));
        fixAttributionSourceState(BRAttributionSource.get(obj).getNext(), uid,pid);
    }

    public static void fix(Context context) {
        try {
            int deep = 0;
            while (context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
                deep++;
                if (deep >= 10) {
                    return;
                }
            }
            BRContextImpl.get(context)._set_mPackageManager(null);
            try {
                context.getPackageManager();
            } catch (Throwable e) {
                e.printStackTrace();
            }

            BRContextImpl.get(context)._set_mBasePackageName(ScoreCore.getHostPkg());
            BRContextImplKitkat.get(context)._set_mOpPackageName(ScoreCore.getHostPkg());
            BRContentResolver.get(context.getContentResolver())._set_mPackageName(ScoreCore.getHostPkg());

            if (BuildCompat.isS()) {
                fixAttributionSourceState(BRContextImpl.get(context).getAttributionSource(), BActivityThread.getBUid());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
