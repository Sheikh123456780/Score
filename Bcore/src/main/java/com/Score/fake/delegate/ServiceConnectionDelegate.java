package com.Score.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;

import black.android.app.BRIServiceConnectionO;
import com.Score.utils.compat.BuildCompat;

/**
 * Created by Milk on 4/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
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

    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        connected(name, service, false);
    }

    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        if (BuildCompat.isOreo()) {
            BRIServiceConnectionO.get(mConn).connected(mComponentName, service, dead);
        } else {
            mConn.connected(name, service);
        }
    }

    /**
     * Android 16 (API 36) added IBinderSession to the hidden callback.
     * Keep the older overload above for Android 10 through Android 15.
     */
    /**
     * Android 16 adds a hidden IBinderSession parameter. The type is not in
     * the public SDK, so dispatch this callback reflectively. The local
     * android.app.IBinderSession declaration keeps the Binder descriptor
     * available when the framework calls this class.
     */
    public void connected(ComponentName name, IBinder service,
                          android.app.IBinderSession session, boolean dead)
            throws RemoteException {
        try {
            Method method = IServiceConnection.class.getMethod(
                    "connected", ComponentName.class, IBinder.class,
                    android.app.IBinderSession.class, boolean.class);
            method.invoke(mConn, name, service, session, dead);
        } catch (NoSuchMethodException e) {
            // Android 10–15 use the legacy callback.
            connected(name, service, dead);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof RemoteException) throw (RemoteException) cause;
            throw new RemoteException("Unable to dispatch Android 16 service callback: " + e);
        }
    }
}
