package top.niunaijun.blackbox.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import black.android.app.IServiceConnectionO;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

public class ServiceConnectionDelegate extends IServiceConnection.Stub {
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

    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        dispatchConnected(name, service, null, false);
    }

    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        dispatchConnected(name, service, null, dead);
    }

    public void connected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        dispatchConnected(name, service, session, dead);
    }

    private void dispatchConnected(ComponentName name, IBinder service, Object session, boolean dead) throws RemoteException {
        try {
            if (BuildCompat.isAndroid16()) {
                // Android 16: Try 4-param first
                callConnectedMethod(mConn, name, service, session, dead);
            } else if (BuildCompat.isAndroid15()) {
                // Android 15: Try 4-param, fallback to 3-param
                try {
                    callConnectedMethod(mConn, name, service, session, dead);
                } catch (Throwable e) {
                    callConnectedMethod(mConn, name, service, dead);
                }
            } else if (BuildCompat.isOreo()) {
                // Android 8-14: Use 3-param or 2-param
                try {
                    callConnectedMethod(mConn, name, service, dead);
                } catch (Throwable e) {
                    callConnectedMethod(mConn, name, service);
                }
            } else {
                // Fallback: Try all signatures
                callConnectedMethod(mConn, name, service, session, dead);
            }
        } catch (Throwable e) {
            try {
                mConn.connected(name, service);
            } catch (Throwable ignored) {
                e.printStackTrace();
            }
        }
    }

    private void callConnectedMethod(IServiceConnection conn, Object... args) throws Throwable {
        if (conn == null) return;
        
        Method[] methods = conn.getClass().getMethods();
        for (Method method : methods) {
            if (!"connected".equals(method.getName())) continue;
            
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != args.length) continue;
            
            boolean match = true;
            for (int i = 0; i < paramTypes.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    if (paramTypes[i].isPrimitive()) {
                        match = false;
                        break;
                    }
                    continue;
                }
                if (!paramTypes[i].isAssignableFrom(arg.getClass())) {
                    match = false;
                    break;
                }
            }
            
            if (match) {
                method.setAccessible(true);
                method.invoke(conn, args);
                return;
            }
        }
        
        for (Method method : methods) {
            if (!"connected".equals(method.getName())) continue;
            if (method.getParameterTypes().length == args.length) {
                method.setAccessible(true);
                method.invoke(conn, args);
                return;
            }
        }
        
        throw new NoSuchMethodException("No connected() method with " + args.length + " parameters found");
    }
}
