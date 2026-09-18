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
        if (iBinder == null) return null;
        synchronized (sServiceConnectDelegate) {
            return sServiceConnectDelegate.get(iBinder);
        }
    }

    public static IServiceConnection createProxy(IServiceConnection base, Intent intent) {
        if (base == null) return null;
        final IBinder iBinder = base.asBinder();
        if (iBinder == null) return base;

        synchronized (sServiceConnectDelegate) {
            ServiceConnectionDelegate existing = sServiceConnectDelegate.get(iBinder);
            if (existing != null) return existing;

            final ServiceConnectionDelegate delegate =
                    new ServiceConnectionDelegate(base,
                            intent != null ? intent.getComponent() : null);
            sServiceConnectDelegate.put(iBinder, delegate);

            try {
                iBinder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        synchronized (sServiceConnectDelegate) {
                            sServiceConnectDelegate.remove(iBinder);
                        }
                        try {
                            iBinder.unlinkToDeath(this, 0);
                        } catch (Throwable ignored) {
                        }
                    }
                }, 0);
            } catch (RemoteException e) {
                // The connection died while being registered. Do not leave a
                // stale delegate in the cache.
                sServiceConnectDelegate.remove(iBinder);
            }
            return delegate;
        }
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


}
