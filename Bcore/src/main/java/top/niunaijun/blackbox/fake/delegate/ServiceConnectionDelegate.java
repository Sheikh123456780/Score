package top.niunaijun.blackbox.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * Android 8-16+ compatible ServiceConnectionDelegate
 * 
 * Handles all connected() method signatures:
 * - 2-param: connected(name, service)          → Android 8-14 (API 26-34)
 * - 3-param: connected(name, service, dead)    → Android 15 (API 35)
 * - 4-param: connected(name, service, session, dead) → Android 16+ (API 36)
 */
public class ServiceConnectionDelegate extends IServiceConnection.Stub {
    
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new HashMap<>();
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;

    private ServiceConnectionDelegate(IServiceConnection conn, ComponentName targetComponent) {
        this.mConn = conn;
        this.mComponentName = targetComponent;
    }

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
    // This resolves the AbstractMethodError on Android 16
    // ============================================================
    public void connected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        dispatchConnected(name, service, session, dead);
    }

    // ============================================================
    // Core dispatch - tries all signatures from 2 to 4 params
    // ============================================================
    private void dispatchConnected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        if (mConn == null) return;

        int sdkInt = android.os.Build.VERSION.SDK_INT;

        // ============================================================
        // Strategy 1: Try Android 16 signature (4 params) - API 36+
        // ============================================================
        if (sdkInt >= 36) {
            try {
                Method m = mConn.getClass().getMethod(
                    "connected",
                    ComponentName.class,
                    IBinder.class,
                    Object.class,
                    boolean.class
                );
                m.setAccessible(true);
                m.invoke(mConn, name, service, session, dead);
                return;
            } catch (ReflectiveOperationException ignored) {}
        }

        // ============================================================
        // Strategy 2: Try Android 15 signature (3 params) - API 35
        // ============================================================
        if (sdkInt >= 35) {
            try {
                Method m = mConn.getClass().getMethod(
                    "connected",
                    ComponentName.class,
                    IBinder.class,
                    boolean.class
                );
                m.setAccessible(true);
                m.invoke(mConn, name, service, dead);
                return;
            } catch (ReflectiveOperationException ignored) {}
        }

        // ============================================================
        // Strategy 3: Try Android 8-14 signature (2 params) - API 26-34
        // ============================================================
        try {
            Method m = mConn.getClass().getMethod(
                "connected",
                ComponentName.class,
                IBinder.class
            );
            m.setAccessible(true);
            m.invoke(mConn, name, service);
            return;
        } catch (ReflectiveOperationException ignored) {}

        // ============================================================
        // Strategy 4: Absolute fallback - try all possible combinations
        // ============================================================
        Object[][] fallbackArgs = {
            {name, service, session, dead},  // 4-param
            {name, service, dead},           // 3-param
            {name, service}                  // 2-param
        };

        for (Object[] args : fallbackArgs) {
            try {
                // Find any method named "connected" with matching parameter count
                for (Method m : mConn.getClass().getMethods()) {
                    if (!"connected".equals(m.getName())) continue;
                    if (m.getParameterCount() != args.length) continue;
                    m.setAccessible(true);
                    m.invoke(mConn, args);
                    return;
                }
            } catch (ReflectiveOperationException ignored) {}
        }

        // ============================================================
        // Last resort: direct 2-param call
        // ============================================================
        try {
            mConn.connected(name, service);
        } catch (Throwable t) {
            throw new RemoteException("ServiceConnectionDelegate dispatch failed: " + t.getMessage());
        }
    }
}
