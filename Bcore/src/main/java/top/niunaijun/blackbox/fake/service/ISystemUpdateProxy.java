package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;

import black.android.os.BRServiceManager;
import black.android.os.BRISystemUpdateManagerStub; // Fixed: AutoFill manager ki jagah correct stub import kiya
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;

/**
 * @author Findger
 * @function
 * @date :2022/4/2 21:59
 **/
public class ISystemUpdateProxy extends BinderInvocationStub {

    public ISystemUpdateProxy() {
        super(getSystemUpdateService());
    }

    private static IBinder getSystemUpdateService() {
        return BRServiceManager.get().getService("system_update");
    }

    @Override
    protected Object getWho() {
        IBinder binder = getSystemUpdateService();
        if (binder == null) {
            return null;
        }
        return BRISystemUpdateManagerStub.get().asInterface(binder);
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
