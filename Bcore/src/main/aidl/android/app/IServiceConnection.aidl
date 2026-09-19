package android.app;

import android.content.ComponentName;

/** @hide - Android 16 IServiceConnection compatibility copy. */
interface IServiceConnection {
    void connected(in ComponentName name, IBinder service, boolean dead);
}
