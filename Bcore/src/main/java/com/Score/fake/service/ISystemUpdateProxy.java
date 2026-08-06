package com.Score.fake.service;

import android.os.Build;
import android.os.IBinder;

import black.android.os.BRServiceManager;
import com.Score.fake.hook.BinderInvocationStub;

import java.lang.reflect.Method;

/**
 * Updated for Android 9 (API 28) to Android 17 (API 37) Compatibility
 */
public class ISystemUpdateProxy extends BinderInvocationStub {

    public ISystemUpdateProxy() {
        super(getServiceBinder());
    }

    private static IBinder getServiceBinder() {
        try {
            return BRServiceManager.get().getService("system_update");
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
            // Try multiple possible class names for different Android versions
            String[] classNames = {
                "android.os.ISystemUpdateManager$Stub",
                "android.os.IUpdateEngine$Stub",
                "android.os.ISystemUpdateService$Stub"
            };

            for (String className : classNames) {
                try {
                    Class<?> stubClass = Class.forName(className);
                    Method asInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);
                    Object result = asInterfaceMethod.invoke(null, binder);
                    if (result != null) {
                        return result;
                    }
                } catch (ClassNotFoundException e) {
                    // Try next class name
                } catch (Throwable t) {
                    // Try next class name
                }
            }

            // Fallback: Return binder itself as a proxy
            return binder;

        } catch (Throwable t) {
            return binder;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        if (getWho() != null) {
            try {
                replaceSystemService("system_update");
            } catch (Throwable t) {
                // Silent fail
            }
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
