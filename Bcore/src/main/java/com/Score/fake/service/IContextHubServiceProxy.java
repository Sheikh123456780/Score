package com.Score.fake.service;

import android.os.IBinder;
import android.os.IInterface;

import black.android.os.BRServiceManager;
import com.Score.fake.hook.BinderInvocationStub;
import com.Score.fake.service.base.ValueMethodProxy;
import com.Score.utils.compat.BuildCompat;

/**
 * Updated for Android 9 (API 28) to Android 17 (API 37) Compatibility
 */
public class IContextHubServiceProxy extends BinderInvocationStub {

    public IContextHubServiceProxy() {
        super(getServiceBinder());
    }

    private static IBinder getServiceBinder() {
        try {
            String serviceName = BuildCompat.isOreo() ? "contexthub" : "contexthub_service";
            return BRServiceManager.get().getService(serviceName);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    protected Object getWho() {
        IBinder binder = getServiceBinder();
        if (binder == null) {
            return null;
        }

        try {
            // Try multiple class names for different Android versions
            String[] classNames = {
                "android.hardware.location.IContextHubService$Stub",
                "android.hardware.location.IContextHubService$Stub",
                "android.hardware.IContextHubService$Stub"
            };

            for (String className : classNames) {
                try {
                    Class<?> stubClass = Class.forName(className);
                    java.lang.reflect.Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
                    Object result = asInterface.invoke(null, binder);
                    if (result != null) {
                        return result;
                    }
                } catch (ClassNotFoundException e) {
                    // Try next class name
                } catch (Throwable t) {
                    // Try next class name
                }
            }

            // Return binder as fallback
            return binder;

        } catch (Throwable t) {
            return binder;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        if (getWho() != null) {
            try {
                replaceSystemService(getServiceName());
            } catch (Throwable t) {
                // Silent fail
            }
        }
    }

    private String getServiceName() {
        return BuildCompat.isOreo() ? "contexthub" : "contexthub_service";
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
