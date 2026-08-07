package black.android.app;

import android.content.ComponentName;
import android.os.IBinder;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BMethod;

@BClassName("android.app.IServiceConnection")
public interface IServiceConnectionO {
    
    @BMethod
    void connected(ComponentName name, IBinder service, boolean dead);
}
