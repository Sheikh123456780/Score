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

import black.android.app.IServiceConnectionO;
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

    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        connectedInternal(name, service, false);
    }

    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        connectedInternal(name, service, dead);
    }

    private void connectedInternal(ComponentName name, IBinder service, boolean dead) {
        try {
            if (mConn == null) return;

            int sdkInt = Build.VERSION.SDK_INT;
            
            // ============================================================
            // ANDROID 14+ (API 34-37+): Use reflection for 4-parameter method
            // ============================================================
            if (sdkInt >= 34) {
                try {
                    Class<?> binderSessionClass = Class.forName("android.app.IBinderSession");
                    Method method = mConn.getClass().getMethod("connected", 
                        ComponentName.class, IBinder.class, binderSessionClass, boolean.class);
                    method.invoke(mConn, mComponentName, service, null, dead);
                    return;
                } catch (Throwable t) {
                    // Fall through
                }
            }

            // ============================================================
            // ANDROID 9-13 (API 28-33): Use 3-parameter method
            // ============================================================
            try {
                Method method = mConn.getClass().getMethod("connected", 
                    ComponentName.class, IBinder.class, boolean.class);
                method.invoke(mConn, mComponentName, service, dead);
                return;
            } catch (NoSuchMethodException e) {
                // Fall through
            } catch (Throwable t) {
                // Fall through
            }

            // ============================================================
            // ULTIMATE FALLBACK: 2-parameter method
            // ============================================================
            try {
                Method method = mConn.getClass().getMethod("connected", 
                    ComponentName.class, IBinder.class);
                method.invoke(mConn, mComponentName, service);
            } catch (Throwable t) {
                try {
                    mConn.connected(mComponentName, service);
                } catch (Throwable ignored) {
                    // Ignore
                }
            }

        } catch (Throwable e) {
            android.util.Log.e("ServiceConnectionDelegate", "Error in connectedInternal", e);
        }
    }
}
