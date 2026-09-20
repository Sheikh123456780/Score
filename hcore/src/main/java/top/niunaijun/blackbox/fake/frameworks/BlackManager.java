package top.niunaijun.blackbox.fake.frameworks;

import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.ParameterizedType;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.Reflector;

/**
 * Created by BlackBox on 2022/3/23.
 */
public abstract class BlackManager<Service extends IInterface> {
    public static final String TAG = "BlackManager";

    private Service mService;
    private final AtomicBoolean mServiceCreationFailed = new AtomicBoolean(false);
    private long mLastRetryTime = 0;
    private long mLastServiceCreationTime = 0;

    // Reduced timeouts so we don't block the sandbox for 2s after a single failure
    private static final long RETRY_TIMEOUT_MS = 500;
    private static final long MIN_SERVICE_CREATION_INTERVAL_MS = 0;

    // Global service failure tracking to prevent cascade failures
    private static final AtomicInteger globalServiceFailureCount = new AtomicInteger(0);
    private static final long GLOBAL_FAILURE_RESET_INTERVAL_MS = 30000; // 30 seconds
    private static long lastGlobalFailureReset = 0;

    protected abstract String getServiceName();

    public Service getService() {
        // If service creation previously failed and we're within retry timeout, return null
        if (mServiceCreationFailed.get()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - mLastRetryTime < RETRY_TIMEOUT_MS) {
                Log.d(TAG, "Skipping service creation for " + getServiceName() + " due to recent failure");
                return null;
            }
            // Reset the flag after timeout
            mServiceCreationFailed.set(false);
        }

        // Return existing healthy service
        if (mService != null) {
            try {
                if (mService.asBinder().pingBinder() && mService.asBinder().isBinderAlive()) {
                    return mService;
                }
            } catch (Exception e) {
                Log.w(TAG, "Existing service not healthy: " + getServiceName() + " - " + e.getMessage());
                mService = null;
            }
        }

        // Rate limiting (0ms by default, kept for flexibility)
        long currentTime = System.currentTimeMillis();
        if (currentTime - mLastServiceCreationTime < MIN_SERVICE_CREATION_INTERVAL_MS) {
            Log.d(TAG, "Rate limiting service creation for " + getServiceName());
            return mService; // Return existing service (might be null)
        }

        try {
            IBinder binder = BlackBoxCore.get().getService(getServiceName());
            if (binder == null) {
                Log.w(TAG, "Failed to get binder for service: " + getServiceName());
                markServiceCreationFailed();
                return null;
            }

            // Check if binder is alive before proceeding
            if (!binder.isBinderAlive()) {
                Log.w(TAG, "Binder is not alive for service: " + getServiceName());
                markServiceCreationFailed();
                return null;
            }

            String stubClassName = getTClass().getName() + "$Stub";
            Log.d(TAG, "Creating service for: " + stubClassName);

            Service created = null;

            // 1) Try asInterface reflection (normal path — may be hidden-API denied on Android 14+)
            try {
                created = Reflector.on(stubClassName)
                        .method("asInterface", IBinder.class)
                        .call(binder);
            } catch (Throwable t) {
                Log.w(TAG, "asInterface reflection failed for " + getServiceName()
                        + ": " + t.getMessage());
            }

            // 2) Fallback: if the binder is already an instance of the service interface, use it directly.
            //    HiddenApiBypass should have unsealed the API, so this is a safety net.
            if (created == null) {
                try {
                    Class<Service> iface = getTClass();
                    if (iface.isInstance(binder)) {
                        Log.d(TAG, "Binder is already an instance of " + iface.getName()
                                + " for " + getServiceName());
                        created = (Service) binder;
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Direct binder cast failed for " + getServiceName()
                            + ": " + t.getMessage());
                }
            }

            if (created == null) {
                Log.w(TAG, "Failed to create service instance for: " + getServiceName());
                markServiceCreationFailed();
                return null;
            }

            mService = created;

            // Additional health check
            try {
                if (!mService.asBinder().isBinderAlive()) {
                    Log.w(TAG, "Service binder is not alive after creation: " + getServiceName());
                    mService = null;
                    markServiceCreationFailed();
                    return null;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error checking service binder health: " + getServiceName(), e);
                mService = null;
                markServiceCreationFailed();
                return null;
            }

            final Service serviceRef = mService; // Capture reference for death recipient
            try {
                serviceRef.asBinder().linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        try {
                            if (serviceRef != null && serviceRef.asBinder() != null) {
                                serviceRef.asBinder().unlinkToDeath(this, 0);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error unlinking death recipient for " + getServiceName(), e);
                        }
                        mService = null;
                        Log.w(TAG, "Service died: " + getServiceName());
                    }
                }, 0);
            } catch (Exception e) {
                Log.w(TAG, "Error linking death recipient for " + getServiceName(), e);
                // Continue anyway, the service might still work
            }

            Log.d(TAG, "Successfully created service: " + getServiceName());
            mServiceCreationFailed.set(false); // Reset failure flag on success
            mLastServiceCreationTime = currentTime; // Update creation time

            return mService;
        } catch (Throwable e) {
            Log.e(TAG, "Error creating service for " + getServiceName(), e);
            markServiceCreationFailed();
            return null;
        }
    }

    private void markServiceCreationFailed() {
        mServiceCreationFailed.set(true);
        mLastRetryTime = System.currentTimeMillis();
        globalServiceFailureCount.incrementAndGet();
    }

    /**
     * Clear the cached service - call this when the service dies
     */
    public void clearServiceCache() {
        mService = null;
        Log.d(TAG, "Cleared service cache for " + getServiceName());
    }

    /**
     * Check if the service is healthy and available
     */
    public boolean isServiceHealthy() {
        if (mService == null) {
            return false;
        }
        try {
            return mService.asBinder().pingBinder() && mService.asBinder().isBinderAlive();
        } catch (Exception e) {
            Log.w(TAG, "Service health check failed for " + getServiceName(), e);
            return false;
        }
    }

    private Class<Service> getTClass() {
        return (Class<Service>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
}
