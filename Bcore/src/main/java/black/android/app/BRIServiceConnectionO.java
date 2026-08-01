package black.android.app;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BMethod;
import top.niunaijun.blackreflection.annotation.BStaticField;
import top.niunaijun.blackreflection.utils.ReflectUtils;

/**
 * Android 16 compatible IServiceConnection wrapper
 * Handles both old (3-param) and new (4-param) signatures
 */
@BClassName("android.app.IServiceConnection")
public interface BRIServiceConnectionO extends IInterface {

    @BMethod
    void connected(ComponentName name, IBinder service, boolean dead);

    /**
     * Android 16+ new signature with session parameter
     */
    @BMethod
    void connected(ComponentName name, IBinder service, Object session, boolean dead);

    /**
     * Factory to create compatible ServiceConnection proxy
     */
    class Factory {
        
        public static IServiceConnection createProxy(final IServiceConnection original) {
            if (original == null) return null;
            
            // If already a proxy, return as-is
            if (Proxy.isProxyClass(original.getClass())) {
                return original;
            }
            
            return (IServiceConnection) Proxy.newProxyInstance(
                original.getClass().getClassLoader(),
                new Class[]{IServiceConnection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String methodName = method.getName();
                        
                        if ("connected".equals(methodName)) {
                            return handleConnected(original, method, args);
                        }
                        
                        // Forward other methods to original
                        try {
                            return method.invoke(original, args);
                        } catch (Throwable t) {
                            // Try to find matching method in original
                            Method targetMethod = findMethod(original.getClass(), methodName, args);
                            if (targetMethod != null) {
                                return targetMethod.invoke(original, args);
                            }
                            throw t;
                        }
                    }
                }
            );
        }
        
        private static Object handleConnected(IServiceConnection original, Method method, Object[] args) throws Throwable {
            ComponentName name = null;
            IBinder service = null;
            Object session = null;
            boolean dead = false;
            
            // Parse arguments based on length
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof ComponentName) {
                        name = (ComponentName) arg;
                    } else if (arg instanceof IBinder) {
                        service = (IBinder) arg;
                    } else if (arg instanceof Boolean) {
                        dead = (Boolean) arg;
                    } else if (arg != null && !(arg instanceof Boolean)) {
                        // Session parameter (could be anything in Android 16)
                        session = arg;
                    }
                }
            }
            
            // Try all possible signatures
            Exception lastException = null;
            
            // 1. Try 4-param (Android 16+)
            try {
                Method m4 = findMethod(original.getClass(), "connected", 
                    new Class[]{ComponentName.class, IBinder.class, Object.class, boolean.class});
                if (m4 != null) {
                    return m4.invoke(original, name, service, session, dead);
                }
            } catch (Exception e) {
                lastException = e;
            }
            
            // 2. Try 3-param (Android 8-15)
            try {
                Method m3 = findMethod(original.getClass(), "connected",
                    new Class[]{ComponentName.class, IBinder.class, boolean.class});
                if (m3 != null) {
                    return m3.invoke(original, name, service, dead);
                }
            } catch (Exception e) {
                lastException = e;
            }
            
            // 3. Try 2-param (Older)
            try {
                Method m2 = findMethod(original.getClass(), "connected",
                    new Class[]{ComponentName.class, IBinder.class});
                if (m2 != null) {
                    return m2.invoke(original, name, service);
                }
            } catch (Exception e) {
                lastException = e;
            }
            
            // 4. Finally try original method as-is
            try {
                return method.invoke(original, args);
            } catch (Exception e) {
                if (lastException != null) {
                    throw lastException;
                }
                throw e;
            }
        }
        
        private static Method findMethod(Class<?> clazz, String name, Object[] args) {
            if (args == null) {
                return findMethod(clazz, name, new Class<?>[0]);
            }
            Class<?>[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
            return findMethod(clazz, name, paramTypes);
        }
        
        private static Method findMethod(Class<?> clazz, String name, Class<?>[] paramTypes) {
            try {
                Method method = clazz.getMethod(name, paramTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                // Try superclass
                if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
                    return findMethod(clazz.getSuperclass(), name, paramTypes);
                }
                // Try interfaces
                for (Class<?> iface : clazz.getInterfaces()) {
                    try {
                        Method method = iface.getMethod(name, paramTypes);
                        method.setAccessible(true);
                        return method;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                return null;
            }
        }
    }
}
