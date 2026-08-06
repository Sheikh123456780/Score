package com.Score.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.Score.utils.compat.BuildCompat;

public class ServiceConnectionDelegate extends IServiceConnection.Stub {
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new HashMap<>();
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;

    private ServiceConnectionDelegate(IServiceConnection mConn, ComponentName targetComponent) {
        this.mConn = mConn;
        this.mComponentName = targetComponent;
    }

    public static ServiceConnectionDelegate getDelegate(IBinder iBinder) {
        return sServiceConnectDelegate.get(iBinder);
    }

    public static IServiceConnection createProxy(IServiceConnection base, Intent intent) {
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
    // ANDROID 14+ (API 34+): 4-parameter method - OVERRIDE THIS!
    // ============================================================
    @Override
    public void connected(ComponentName name, IBinder service, android.app.IBinderSession session, boolean dead) throws RemoteException {
        if (mConn == null) return;
        try {
            // Try to call the 4-parameter method on the original connection
            try {
                Method method = mConn.getClass().getDeclaredMethod("connected",
                    ComponentName.class, IBinder.class, android.app.IBinderSession.class, boolean.class);
                method.setAccessible(true);
                method.invoke(mConn, mComponentName, service, session, dead);
                return;
            } catch (NoSuchMethodException e) {
                // Fall through to 3-parameter
            }

            // Fallback: Call 3-parameter method if 4-parameter not available
            try {
                Method method = mConn.getClass().getDeclaredMethod("connected",
                    ComponentName.class, IBinder.class, boolean.class);
                method.setAccessible(true);
                method.invoke(mConn, mComponentName, service, dead);
            } catch (NoSuchMethodException e2) {
                // Fallback: Call 2-parameter method
                try {
                    Method method = mConn.getClass().getDeclaredMethod("connected",
                        ComponentName.class, IBinder.class);
                    method.setAccessible(true);
                    method.invoke(mConn, mComponentName, service);
                } catch (NoSuchMethodException e3) {
                    mConn.connected(mComponentName, service);
                }
            }
        } catch (Throwable e) {
            android.util.Log.e("ServiceConnectionDelegate", "Error in connected (4-param)", e);
            throw new RemoteException(e.getMessage());
        }
    }

    // ============================================================
    // ANDROID 9-13 (API 28-33): 3-parameter method
    // ============================================================
    @Override
    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        if (mConn == null) return;
        try {
            // Try 4-parameter first (Android 14+ original connection)
            try {
                Method method = mConn.getClass().getDeclaredMethod("connected",
                    ComponentName.class, IBinder.class, android.app.IBinderSession.class, boolean.class);
                method.setAccessible(true);
                method.invoke(mConn, mComponentName, service, null, dead);
                return;
            } catch (NoSuchMethodException e) {
                // Fall through to 3-parameter
            }

            // Try 3-parameter method
            try {
                Method method = mConn.getClass().getDeclaredMethod("connected",
                    ComponentName.class, IBinder.class, boolean.class);
                method.setAccessible(true);
                method.invoke(mConn, mComponentName, service, dead);
            } catch (NoSuchMethodException e2) {
                // Fallback to 2-parameter
                try {
                    Method method = mConn.getClass().getDeclaredMethod("connected",
                        ComponentName.class, IBinder.class);
                    method.setAccessible(true);
                    method.invoke(mConn, mComponentName, service);
                } catch (NoSuchMethodException e3) {
                    mConn.connected(mComponentName, service);
                }
            }
        } catch (Throwable e) {
            android.util.Log.e("ServiceConnectionDelegate", "Error in connected (3-param)", e);
            throw new RemoteException(e.getMessage());
        }
    }

    // ============================================================
    // LEGACY: 2-parameter method
    // ============================================================
    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        if (mConn == null) return;
        try {
            // Try the most complete method first
            try {
                Method method = mConn.getClass().getDeclaredMethod("connected",
                    ComponentName.class, IBinder.class, android.app.IBinderSession.class, boolean.class);
                method.setAccessible(true);
                method.invoke(mConn, mComponentName, service, null, false);
                return;
            } catch (NoSuchMethodException e) {
                // Fall through
            }

            // Try 3-parameter
            try {
                Method method = mConn.getClass().getDeclaredMethod("connected",
                    ComponentName.class, IBinder.class, boolean.class);
                method.setAccessible(true);
                method.invoke(mConn, mComponentName, service, false);
            } catch (NoSuchMethodException e2) {
                // Fallback to 2-parameter
                try {
                    Method method = mConn.getClass().getDeclaredMethod("connected",
                        ComponentName.class, IBinder.class);
                    method.setAccessible(true);
                    method.invoke(mConn, mComponentName, service);
                } catch (NoSuchMethodException e3) {
                    mConn.connected(mComponentName, service);
                }
            }
        } catch (Throwable e) {
            android.util.Log.e("ServiceConnectionDelegate", "Error in connected (2-param)", e);
            throw new RemoteException(e.getMessage());
        }
    }
}
