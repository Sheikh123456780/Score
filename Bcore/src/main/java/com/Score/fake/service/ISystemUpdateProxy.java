package com.Score.fake.service;

import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import black.android.os.BRServiceManager;
import com.Score.fake.hook.BinderInvocationStub;

import java.lang.reflect.Method;

public class ISystemUpdateProxy extends BinderInvocationStub {

    public ISystemUpdateProxy() {
        super(BRServiceManager.get().getService("system_update"));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService("system_update");
        if (binder == null) {
            return null;
        }

        try {
            // Dynamic resolution based on API version targeting system update stubs
            Class<?> stubClass = Class.forName("android.os.ISystemUpdateManager$Stub");
            Method asInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);
            return asInterfaceMethod.invoke(null, binder);
        } catch (Throwable t) {
            // Fallback generic proxy for non-standard OEM implementations
            return binder;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        if (getWho() != null) {
            replaceSystemService("system_update");
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
