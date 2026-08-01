package top.niunaijun.blackbox.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.utils.compat.BuildCompat;

public class ServiceConnectionDelegate extends IServiceConnection.Stub implements IBinder.DeathRecipient {
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new HashMap<>();
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;

    private ServiceConnectionDelegate(IServiceConnection conn, ComponentName targetComponent) {
        this.mConn = conn;
        this.mComponentName = targetComponent;
    }

    // 🔥 FIX 1: Add this method back for compatibility with other BlackBox classes
    public static ServiceConnectionDelegate getDelegate(IBinder iBinder) {
        return sServiceConnectDelegate.get(iBinder);
    }

    public static IServiceConnection createProxy(IServiceConnection base, Intent intent) {
        if (base == null) return null;
        
        final IBinder iBinder = base.asBinder();
        ServiceConnectionDelegate delegate = sServiceConnectDelegate.get(iBinder);
        
        if (delegate == null) {
            try {
                iBinder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        sServiceConnectDelegate.remove(iBinder);
                        iBinder.unlinkToDeath(this, 0);
                    }
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            delegate = new ServiceConnectionDelegate(base, intent.getComponent());
            sServiceConnectDelegate.put(iBinder, delegate);
        }
        return delegate;
    }

    // 🔥 FIX 2: Implement DeathRecipient interface
    @Override
    public void binderDied() {
        IBinder binder = mConn != null ? mConn.asBinder() : null;
        if (binder != null) {
            sServiceConnectDelegate.remove(binder);
            binder.unlinkToDeath(this, 0);
        }
    }

    // ============================================================
    // Android 8-14: 2-param connected()
    // ============================================================
    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        dispatchConnected(name, service, null, false);
    }

    // ============================================================
    // Android 15: 3-param connected()
    // ============================================================
    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        dispatchConnected(name, service, null, dead);
    }

    // ============================================================
    // 🔥 Android 16+: 4-param connected() with session
    // This resolves the AbstractMethodError
    // ============================================================
    public void connected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        dispatchConnected(name, service, session, dead);
    }

    // ============================================================
    // Core dispatch - tries all signatures
    // ============================================================
    private void dispatchConnected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        if (mConn == null) return;

        // Try 4-param (Android 16+)
        try {
            java.lang.reflect.Method m = mConn.getClass().getMethod(
                "connected",
                ComponentName.class,
                IBinder.class,
                Object.class,
                boolean.class
            );
            m.invoke(mConn, name, service, session, dead);
            return;
        } catch (NoSuchMethodException ignored) {}

        // Try 3-param (Android 15)
        try {
            java.lang.reflect.Method m = mConn.getClass().getMethod(
                "connected",
                ComponentName.class,
                IBinder.class,
                boolean.class
            );
            m.invoke(mConn, name, service, dead);
            return;
        } catch (NoSuchMethodException ignored) {}

        // Try 2-param (Android 8-14)
        try {
            java.lang.reflect.Method m = mConn.getClass().getMethod(
                "connected",
                ComponentName.class,
                IBinder.class
            );
            m.invoke(mConn, name, service);
            return;
        } catch (NoSuchMethodException ignored) {}

        // Absolute fallback
        mConn.connected(name, service);
    }
}
