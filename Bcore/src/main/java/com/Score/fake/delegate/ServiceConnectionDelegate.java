package com.Score.fake.delegate;

import android.app.IBinderSession;
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

/**
 * Updated for Android 9 (API 28) to Android 17 (API 37) Support
 */
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
    // Android 9-13: 2-Parameter Method
    // ============================================================
    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        connectedInternal(name, service, null, false);
    }

    // ============================================================
    // Android 9-13: 3-Parameter Method
    // ============================================================
    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        connectedInternal(name, service, null, dead);
    }

    // ============================================================
    // Android 14+: 4-Parameter Method (NEW)
    // ============================================================
    @Override
    public void connected(ComponentName name, IBinder service, IBinderSession session, boolean dead) throws RemoteException {
        connectedInternal(name, service, session, dead);
    }

    // ============================================================
    // INTERNAL METHOD - Handles all versions
    // ============================================================
    private void connectedInternal(ComponentName name, IBinder service, IBinderSession session, boolean dead) {
        try {
            if (mConn == null) return;

            int sdkInt = Build.VERSION.SDK_INT;
            
            // ============================================================
            // ANDROID 14+ (API 34-37+): Use 4-parameter method
            // ============================================================
            if (sdkInt >= 34) {
                // Try via BlackReflection first
                try {
                    IServiceConnectionO.get(mConn).connectedV2(mComponentName, service, session, dead);
                    return;
                } catch (Throwable t) {
                    // If BlackReflection fails, use direct reflection
                }

                // Direct Reflection fallback
                try {
                    Method method = mConn.getClass().getMethod("connected", 
                        ComponentName.class, IBinder.class, IBinderSession.class, boolean.class);
                    method.invoke(mConn, mComponentName, service, session, dead);
                    return;
                } catch (NoSuchMethodException e) {
                    // Fall through to older methods
                } catch (Throwable t) {
                    // Fall through to older methods
                }
            }

            // ============================================================
            // ANDROID 9-13 (API 28-33): Use 3-parameter method
            // ============================================================
            if (sdkInt >= 28) {
                // Try via BlackReflection
                try {
                    if (BuildCompat.isOreo()) {
                        IServiceConnectionO.get(mConn).connected(mComponentName, service, dead);
                        return;
                    }
                } catch (Throwable t) {
                    // Fall through to direct reflection
                }

                // Direct Reflection fallback
                try {
                    Method method = mConn.getClass().getMethod("connected", 
                        ComponentName.class, IBinder.class, boolean.class);
                    method.invoke(mConn, mComponentName, service, dead);
                    return;
                } catch (NoSuchMethodException e) {
                    // Fall through to 2-parameter method
                } catch (Throwable t) {
                    // Fall through to 2-parameter method
                }
            }

            // ============================================================
            // ULTIMATE FALLBACK: 2-parameter method
            // ============================================================
            try {
                Method method = mConn.getClass().getMethod("connected", 
                    ComponentName.class, IBinder.class);
                method.invoke(mConn, mComponentName, service);
            } catch (Throwable t) {
                // Last resort: Call directly
                try {
                    mConn.connected(mComponentName, service);
                } catch (Throwable ignored) {
                    // Ignore
                }
            }

        } catch (Throwable e) {
            // Log but don't crash
            android.util.Log.e("ServiceConnectionDelegate", "Error in connectedInternal", e);
        }
    }
}
