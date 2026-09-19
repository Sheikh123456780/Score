package black.android.app;

import android.content.ComponentName;
import android.os.IBinder;

import java.lang.reflect.Method;

public class IServiceConnectionO {

    public static void connected(Object instance,
                                 ComponentName name,
                                 IBinder service,
                                 boolean dead) {
        if (instance == null) return;

        try {
            Method[] methods = instance.getClass().getDeclaredMethods();

            for (Method method : methods) {
                if (!"connected".equals(method.getName())) {
                    continue;
                }

                method.setAccessible(true);
                Class<?>[] paramTypes = method.getParameterTypes();

                if (paramTypes.length == 3
                        && paramTypes[0] == ComponentName.class) {
                    method.invoke(instance, name, service, dead);
                    return;
                } else if (paramTypes.length == 2
                        && paramTypes[0] == ComponentName.class) {
                    method.invoke(instance, name, service);
                    return;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
