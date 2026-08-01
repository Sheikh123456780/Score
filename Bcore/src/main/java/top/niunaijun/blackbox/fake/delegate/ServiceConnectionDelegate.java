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
 * Android 16 compatible ServiceConnectionDelegate
 * Handles all Android versions from 8 to 16
 * 
 * Key fix: Android 16 uses IBinderSession (4th parameter) which is a Binder object
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
    // Called by system for older Android versions
    // ============================================================
    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        dispatchConnected(name, service, null, false);
    }

    // ============================================================
    // Android 8-15: 3-param connected()
    // Called by system for Android 8-15
    // ============================================================
    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        dispatchConnected(name, service, null, dead);
    }

    // ============================================================
    // Android 15-16: 4-param connected() with session
    // Called by system for Android 15-16
    // ============================================================
    public void connected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        dispatchConnected(name, service, session, dead);
    }

    // ============================================================
    // Core Dispatch Logic - FIXED FOR ANDROID 16
    // ============================================================
    private void dispatchConnected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        try {
            // Get all methods of the connection
            Method[] methods = mConn.getClass().getMethods();
            
            // Android 16: Try 4-param with IBinderSession first
            if (BuildCompat.isAndroid16()) {
                // Try to find and invoke 4-param method
                for (Method method : methods) {
                    if (!"connected".equals(method.getName())) continue;
                    if (method.getParameterTypes().length == 4) {
                        try {
                            method.setAccessible(true);
                            method.invoke(mConn, name, service, session, dead);
                            return;
                        } catch (Throwable ignored) {
                            // Continue to next method
                        }
                    }
                }
            }
            
            // Android 15: Try 4-param with Object session
            if (BuildCompat.isAndroid15()) {
                for (Method method : methods) {
                    if (!"connected".equals(method.getName())) continue;
                    if (method.getParameterTypes().length == 4) {
                        try {
                            method.setAccessible(true);
                            method.invoke(mConn, name, service, session, dead);
                            return;
                        } catch (Throwable ignored) {
                            // Continue to next method
                        }
                    }
                }
            }
            
            // Android 8-15: Try 3-param
            for (Method method : methods) {
                if (!"connected".equals(method.getName())) continue;
                if (method.getParameterTypes().length == 3) {
                    try {
                        method.setAccessible(true);
                        method.invoke(mConn, name, service, dead);
                        return;
                    } catch (Throwable ignored) {
                        // Continue to next method
                    }
                }
            }
            
            // Android 8-14: Try 2-param
            for (Method method : methods) {
                if (!"connected".equals(method.getName())) continue;
                if (method.getParameterTypes().length == 2) {
                    try {
                        method.setAccessible(true);
                        method.invoke(mConn, name, service);
                        return;
                    } catch (Throwable ignored) {
                        // Continue to next method
                    }
                }
            }
            
            // ============================================================
            // ULTIMATE FALLBACK: Try all possible combinations
            // ============================================================
            Object[][] testArgs = {
                {name, service, session, dead},  // 4-param with session
                {name, service, dead},           // 3-param
                {name, service}                  // 2-param
            };
            
            for (Object[] args : testArgs) {
                for (Method method : methods) {
                    if (!"connected".equals(method.getName())) continue;
                    if (method.getParameterTypes().length != args.length) continue;
                    
                    try {
                        method.setAccessible(true);
                        method.invoke(mConn, args);
                        return;
                    } catch (Throwable ignored) {
                        // Continue to next
                    }
                }
            }
            
            // ============================================================
            // LAST RESORT: Try to call via IServiceConnectionO.Compat
            // ============================================================
            try {
                IServiceConnectionO.Compat.connected(mConn, name, service, session, dead);
                return;
            } catch (Throwable ignored) {
                // Continue to final fallback
            }
            
            // ============================================================
            // FINAL FALLBACK: Direct 2-param call
            // ============================================================
            mConn.connected(name, service);
            
        } catch (Throwable e) {
            // Ultimate fallback: try 2-param
            try {
                mConn.connected(name, service);
            } catch (Throwable ignored) {
                e.printStackTrace();
                throw new RemoteException("Failed to call connected()", e);
            }
        }
    }
}
