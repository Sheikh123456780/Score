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
 * Android 16+ compatible ServiceConnectionDelegate
 * Fully handles all Android versions from API 26 (8.0) to API 36 (16.0)
 * 
 * FIX: Added 4-param connected() method for Android 16+ compatibility
 * This resolves the AbstractMethodError on Android 16
 */
public class ServiceConnectionDelegate extends IServiceConnection.Stub {
    private static final String TAG = "ServiceConnectionDelegate";
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new HashMap<>();
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;
    private final IBinder.DeathRecipient mDeathRecipient;

    private ServiceConnectionDelegate(IServiceConnection conn, ComponentName targetComponent) {
        this.mConn = conn;
        this.mComponentName = targetComponent;
        this.mDeathRecipient = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                sServiceConnectDelegate.remove(mConn.asBinder());
                mConn.asBinder().unlinkToDeath(this, 0);
            }
        };
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
    // Required by IServiceConnection.Stub for older Android versions
    // ============================================================
    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        dispatchConnected(name, service, null, false);
    }

    // ============================================================
    // Android 8-15: 3-param connected()
    // ============================================================
    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        dispatchConnected(name, service, null, dead);
    }

    // ============================================================
    // Android 15-16: 4-param connected() with session
    // CRITICAL FIX: This resolves the AbstractMethodError on Android 16
    // The Object type works with both IBinderSession and other types
    // ============================================================
    public void connected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        dispatchConnected(name, service, session, dead);
    }

    // ============================================================
    // Core dispatch logic - handles all method signatures
    // across all Android versions (8.0 - 16.0)
    // ============================================================
    private void dispatchConnected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        if (mConn == null) {
            return;
        }

        try {
            // Get all methods of the connection
            Method[] methods = mConn.getClass().getMethods();
            
            // ============================================================
            // Strategy 1: Try Android 16 signature (4 params)
            // ============================================================
            if (BuildCompat.isAndroid16()) {
                for (Method method : methods) {
                    if (!"connected".equals(method.getName())) continue;
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length == 4) {
                        try {
                            method.setAccessible(true);
                            method.invoke(mConn, name, service, session, dead);
                            return;
                        } catch (Throwable ignored) {
                            // If this fails, try the next method
                        }
                    }
                }
            }

            // ============================================================
            // Strategy 2: Try Android 15 signature (3 params)
            // ============================================================
            if (BuildCompat.isAndroid15()) {
                for (Method method : methods) {
                    if (!"connected".equals(method.getName())) continue;
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length == 3) {
                        try {
                            method.setAccessible(true);
                            method.invoke(mConn, name, service, dead);
                            return;
                        } catch (Throwable ignored) {
                            // If this fails, try the next method
                        }
                    }
                }
            }

            // ============================================================
            // Strategy 3: Try Android 8-14 signature (2 params)
            // ============================================================
            for (Method method : methods) {
                if (!"connected".equals(method.getName())) continue;
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length == 2) {
                    try {
                        method.setAccessible(true);
                        method.invoke(mConn, name, service);
                        return;
                    } catch (Throwable ignored) {
                        // If this fails, try the next method
                    }
                }
            }

            // ============================================================
            // Strategy 4: Fallback - try all possible combinations
            // ============================================================
            Object[][] fallbackArgs = {
                {name, service, session, dead},  // 4-param
                {name, service, dead},           // 3-param
                {name, service}                  // 2-param
            };

            for (Object[] args : fallbackArgs) {
                for (Method method : methods) {
                    if (!"connected".equals(method.getName())) continue;
                    if (method.getParameterTypes().length != args.length) continue;
                    
                    try {
                        method.setAccessible(true);
                        method.invoke(mConn, args);
                        return;
                    } catch (Throwable ignored) {
                        // Continue to next combination
                    }
                }
            }

            // ============================================================
            // Strategy 5: Absolute last resort - direct 2-param call
            // ============================================================
            mConn.connected(name, service);

        } catch (Throwable e) {
            // Catch ALL exceptions and throw as RemoteException
            throw new RemoteException("ServiceConnectionDelegate dispatch failed: " + e.getMessage());
        }
    }
}
