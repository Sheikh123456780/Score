package com.Score.fake.service;

import android.os.IBinder;

import black.android.hardware.location.BRIContextHubServiceStub;
import black.android.os.BRServiceManager;
import com.Score.fake.hook.BinderInvocationStub;
import com.Score.fake.service.base.ValueMethodProxy;
import com.Score.utils.compat.BuildCompat;

/**
 * Updated for Android 9 (API 28) to Android 17 (API 37) Compatibility
 */
public class IContextHubServiceProxy extends BinderInvocationStub {

    public IContextHubServiceProxy() {
        super(BRServiceManager.get().getService(getServiceName()));
    }

    private static String getServiceName() {
        return BuildCompat.isOreo() ? "contexthub" : "contexthub_service";
    }

    @Override
    protected Object getWho() {
        // Safe check to prevent NullPointerException on Android 14-17
        IBinder binder = BRServiceManager.get().getService(getServiceName());
        if (binder == null) {
            return null;
        }

        try {
            return BRIContextHubServiceStub.get().asInterface(binder);
        } catch (Throwable t) {
            // Catch reflection/stub changes on newer Android versions
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        // Only attempt replacement if valid service instance exists
        if (getWho() != null) {
            replaceSystemService(getServiceName());
        }
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("registerCallback", 0));
        addMethodHook(new ValueMethodProxy("getContextHubInfo", null));
        addMethodHook(new ValueMethodProxy("getContextHubHandles", new int[]{}));
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
