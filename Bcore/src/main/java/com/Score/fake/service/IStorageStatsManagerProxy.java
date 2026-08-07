package com.Score.fake.service;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import java.lang.reflect.Method;

import black.android.app.usage.BRIStorageStatsManagerStub;
import black.android.os.BRServiceManager;
import com.Score.app.BActivityThread;
import com.Score.fake.hook.BinderInvocationStub;
import com.Score.utils.MethodParameterUtils;

/**
 * Created by BlackBox on 2022/3/3.
 */
@TargetApi(Build.VERSION_CODES.O)
public class IStorageStatsManagerProxy extends BinderInvocationStub {

    public IStorageStatsManagerProxy() {
        super(BRServiceManager.get().getService(Context.STORAGE_STATS_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIStorageStatsManagerStub.get().asInterface(BRServiceManager.get().getService(Context.STORAGE_STATS_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.STORAGE_STATS_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceFirstAppPkg(args);
        
        // Android 11+ (API 30+) - Handle userId parameter
        if (args != null && args.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Ensure userId is correct for Android 11+
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Integer) {
                    int value = (Integer) args[i];
                    // Check if this looks like a userId parameter
                    if (value >= 0 && value <= 10000) {
                        args[i] = BActivityThread.getUserId();
                        break;
                    }
                }
            }
        }
        
        return super.invoke(proxy, method, args);
    }
}
