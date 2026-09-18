package android.app;

/**
 * Compile-time declaration for the hidden Android 16 IServiceConnection
 * callback parameter. On Android 16+ the framework boot class takes
 * precedence; on older releases this descriptor is only used by the legacy
 * delegate class and is never instantiated.
 */
public interface IBinderSession {
}

