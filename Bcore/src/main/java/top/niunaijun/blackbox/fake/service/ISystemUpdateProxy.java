package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;

/**
 * @author Findger
 * @function
 * @date :2022/4/2 21:59
 **/
public class ISystemUpdateProxy extends BinderInvocationStub {

    public ISystemUpdateProxy() {
        super(getServiceBinder());
    }

    private static IBinder getServiceBinder() {
        return BRServiceManager.get().getService("system_update");
    }

    @Override
    protected Object getWho() {
        IBinder binder = getServiceBinder();
        if (binder == null) {
            return null;
        }
        return binder;
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
