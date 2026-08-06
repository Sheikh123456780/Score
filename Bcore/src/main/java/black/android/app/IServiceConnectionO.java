package black.android.app;

import android.app.IBinderSession;
import android.content.ComponentName;
import android.os.IBinder;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BMethod;
import top.niunaijun.blackreflection.annotation.BMethodName;

@BClassName("android.app.IServiceConnection")
public interface IServiceConnectionO {
    
    // ============================================================
    // ANDROID 9-13 (API 28-33): 3-Parameter Method
    // ============================================================
    @BMethod
    void connected(ComponentName ComponentName0, IBinder IBinder1, boolean boolean2);
    
    // ============================================================
    // ANDROID 14+ (API 34-37+): 4-Parameter Method
    // ============================================================
    @BMethod
    @BMethodName("connected")
    void connectedV2(ComponentName ComponentName0, IBinder IBinder1, IBinderSession session, boolean boolean2);
}
