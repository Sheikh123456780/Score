package com.Score.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceConnectionDelegate implements InvocationHandler {

    private static final Map<IBinder, ServiceConnectionDelegate> sDelegates = new ConcurrentHashMap<>();

    private final IServiceConnection mTarget;
    private final Intent mIntent;

    public ServiceConnectionDelegate(IServiceConnection target, Intent intent) {
        this.mTarget = target;
        this.mIntent = intent;
    }

    public static IServiceConnection createProxy(IServiceConnection connection, Intent intent) {
        if (connection == null) return null;
        IBinder binder = connection.asBinder();
        ServiceConnectionDelegate delegate = sDelegates.get(binder);
        if (delegate == null) {
            delegate = new ServiceConnectionDelegate(connection, intent);
            sDelegates.put(binder, delegate);
        }

        return (IServiceConnection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[]{IServiceConnection.class},
                delegate
        );
    }

    public static ServiceConnectionDelegate getDelegate(IBinder binder) {
        if (binder == null) return null;
        return sDelegates.get(binder);
    }

    public static void removeDelegate(IBinder binder) {
        if (binder != null) {
            sDelegates.remove(binder);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        
        if ("connected".equals(methodName)) {
            return handleConnected(method, args);
        } else if ("asBinder".equals(methodName)) {
            return mTarget.asBinder();
        }
        
        return method.invoke(mTarget, args);
    }

    private Object handleConnected(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(mTarget, args);
        } catch (Throwable t) {
            // Android 10-17 compatibility fallback for signature changes
            if (args != null && args.length >= 2 && args[0] instanceof ComponentName && args[1] instanceof IBinder) {
                ComponentName name = (ComponentName) args[0];
                IBinder service = (IBinder) args[1];
                boolean dead = args.length > 2 && args[2] instanceof Boolean ? (Boolean) args[2] : false;

                for (Method m : mTarget.getClass().getDeclaredMethods()) {
                    if ("connected".equals(m.getName())) {
                        m.setAccessible(true);
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 3) {
                            return m.invoke(mTarget, name, service, dead);
                        } else if (params.length == 2) {
                            return m.invoke(mTarget, name, service);
                        } else if (params.length == 4) {
                            return m.invoke(mTarget, name, service, dead, false);
                        }
                    }
                }
            }
            throw t;
        }
    }

    public IServiceConnection getTarget() {
        return mTarget;
    }

    public Intent getIntent() {
        return mIntent;
    }
}
