package top.niunaijun.blackbox.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;

/**
 * Android 16+ compatible ServiceConnectionDelegate
 */
public class ServiceConnectionDelegate extends IServiceConnection.Stub {
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new HashMap<>();
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;

    private ServiceConnectionDelegate(IServiceConnection conn, ComponentName targetComponent) {
        this.mConn = conn;
        this.mComponentName = targetComponent;
        // DeathRecipient to clean up when connection dies
        try {
            conn.asBinder().linkToDeath(() -> {
                sServiceConnectDelegate.remove(conn.asBinder());
                conn.asBinder().unlinkToDeath(this, 0);
            }, 0);
        } catch (RemoteException ignored) {}
    }

    public static IServiceConnection createProxy(IServiceConnection base, Intent intent) {
        if (base == null) return null;
        IBinder binder = base.asBinder();
        ServiceConnectionDelegate delegate = sServiceConnectDelegate.get(binder);
        if (delegate == null) {
            delegate = new ServiceConnectionDelegate(base, intent.getComponent());
            sServiceConnectDelegate.put(binder, delegate);
        }
        return delegate;
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
    // Android 16+: 4-param connected() with session
    // CRITICAL FIX: This resolves the AbstractMethodError on Android 16
    // ============================================================
    public void connected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        dispatchConnected(name, service, session, dead);
    }

    // ============================================================
    // Core dispatch - tries all signatures from 2 to 4 params
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

        // Fallback to 2-param (Android 8-14)
        mConn.connected(name, service);
    }
}
