package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;

import black.android.hardware.location.BRIContextHubServiceStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.service.base.ValueMethodProxy;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * Created by BlackBox on 2022/3/2.
 */
public class IContextHubServiceProxy extends BinderInvocationStub {

    public IContextHubServiceProxy() {
        super(getContextHubService());
    }

    private static String getServiceName() {
        return BuildCompat.isOreo() ? "contexthub" : "contexthub_service";
    }

    private static IBinder getContextHubService() {
        return BRServiceManager.get().getService(getServiceName());
    }

    @Override
    protected Object getWho() {
        IBinder binder = getContextHubService();
        if (binder == null) {
            return null;
        }
        try {
            return BRIContextHubServiceStub.get().asInterface(binder);
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
protected void inject(Object baseInvocation, Object proxyInvocation) {
    // Only attempt to replace the system service if the base interface is non-null
    if (getWho() != null) {
        replaceSystemService(getServiceName());
    } else {
        // Log gracefully or skip injection instead of throwing
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
