package black.android.app;

import android.content.ComponentName;
import android.os.IBinder;

import java.lang.reflect.Method;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BMethod;

@BClassName("android.app.IServiceConnection")
public interface IServiceConnectionO {

    @BMethod
    void connected(ComponentName name, IBinder service, boolean dead);

    /**
     * Compat helper to handle all Android versions including Android 16
     */
    class Compat {
        public static void connected(Object target, ComponentName name, IBinder service, Object session, boolean dead) {
            if (target == null) return;
            
            try {
                // Try all possible method signatures
                Method[] methods = target.getClass().getMethods();
                
                // Try 4-param first (Android 15-16)
                for (Method m : methods) {
                    if (!m.getName().equals("connected")) continue;
                    if (m.getParameterTypes().length == 4) {
                        try {
                            m.setAccessible(true);
                            m.invoke(target, name, service, session, dead);
                            return;
                        } catch (Throwable ignored) {}
                    }
                }
                
                // Try 3-param (Android 8-15)
                for (Method m : methods) {
                    if (!m.getName().equals("connected")) continue;
                    if (m.getParameterTypes().length == 3) {
                        try {
                            m.setAccessible(true);
                            m.invoke(target, name, service, dead);
                            return;
                        } catch (Throwable ignored) {}
                    }
                }
                
                // Try 2-param (Android 8-14)
                for (Method m : methods) {
                    if (!m.getName().equals("connected")) continue;
                    if (m.getParameterTypes().length == 2) {
                        try {
                            m.setAccessible(true);
                            m.invoke(target, name, service);
                            return;
                        } catch (Throwable ignored) {}
                    }
                }
                
            } catch (Throwable ignored) {}
        }
    }
}
