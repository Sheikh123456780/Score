package android.app;

import android.os.IBinder;
import android.os.IInterface;

/**
 * Stub class for compilation compatibility with Android 14+ APIs.
 * This interface was introduced in Android 14 (API 34).
 */
public interface IBinderSession extends IInterface {
    // Core methods that might be needed
    public IBinder asBinder();
}
