package top.niunaijun.blackbox.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;

import black.android.app.IServiceConnectionO;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

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
public void connected(ComponentName name,
                      IBinder service) throws RemoteException {
    dispatchConnected(name, service, null, false);
}

public void connected(ComponentName name,
                      IBinder service,
                      boolean dead) throws RemoteException {
    dispatchConnected(name, service, null, dead);
}

/*
 * Android 15 / Android 16
 * New IServiceConnection callback.
 *
 * The third argument type changed between Android releases,
 * so use Object to remain compatible with all API levels.
 */
public void connected(ComponentName name,
                      IBinder service,
                      Object session,
                      boolean dead) throws RemoteException {
    dispatchConnected(name, service, session, dead);
}

private void dispatchConnected(ComponentName name,
                               IBinder service,
                               Object session,
                               boolean dead) throws RemoteException {

    try {

        if (BuildCompat.isOreo()) {

            IServiceConnectionO.Compat.connected(
                    mConn,
                    mComponentName,
                    service,
                    session,
                    dead
            );

        } else {

            try {
                mConn.connected(name, service);
            } catch (Throwable ignored) {
            }

        }

    } catch (Throwable e) {

        try {
            mConn.connected(name, service);
        } catch (Throwable ignored) {
        }

    }
}
