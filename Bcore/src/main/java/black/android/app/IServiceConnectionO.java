package black.android.app;

import android.content.ComponentName;
import android.os.IBinder;

import java.lang.reflect.Method;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BMethod;

@BClassName("android.app.IServiceConnection")
public interface IServiceConnectionO {

    // Android 8–14
    @BMethod
    void connected(ComponentName name,
                   IBinder service,
                   boolean dead);

    /*
     * Android 15 / Android 16 compatibility.
     *
     * Newer Android versions changed the Binder callback.
     * We intentionally resolve it by reflection so the same
     * source works on older Android versions too.
     */
    class Compat {

        public static void connected(Object target,
                                     ComponentName name,
                                     IBinder service,
                                     Object session,
                                     boolean dead) {

            if (target == null)
                return;

            try {
                for (Method m : target.getClass().getMethods()) {

                    if (!m.getName().equals("connected"))
                        continue;

                    Class<?>[] p = m.getParameterTypes();

                    if (p.length == 4) {
                        m.invoke(target,
                                name,
                                service,
                                session,
                                dead);
                        return;
                    }

                    if (p.length == 3) {
                        m.invoke(target,
                                name,
                                service,
                                dead);
                        return;
                    }

                    if (p.length == 2) {
                        m.invoke(target,
                                name,
                                service);
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
