package com.Score.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import androidx.annotation.NonNull;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import black.android.app.ActivityThreadActivityClientRecordContext;
import black.android.app.BRActivityClient;
import black.android.app.BRActivityClientActivityClientControllerSingleton;
import black.android.app.BRActivityManagerNative;
import black.android.app.BRActivityThread;
import black.android.app.BRActivityThreadActivityClientRecord;
import black.android.app.BRActivityThreadCreateServiceData;
import black.android.app.BRActivityThreadH;
import black.android.app.BRIActivityManager;
import black.android.app.servertransaction.BRClientTransaction;
import black.android.app.servertransaction.BRLaunchActivityItem;
import black.android.app.servertransaction.LaunchActivityItem;
import black.android.app.servertransaction.LaunchActivityItemContext;
import black.android.os.BRHandler;
import com.Score.ScoreCore;
import com.Score.app.BActivityThread;
import com.Score.fake.hook.IInjectHook;
import com.Score.proxy.ProxyManifest;
import com.Score.proxy.record.ProxyActivityRecord;
import com.Score.utils.Slog;
import com.Score.utils.compat.BuildCompat;


public class HCallbackProxy implements IInjectHook, Handler.Callback {
    public static final String TAG = "HCallbackStub";
    private Handler.Callback mOtherCallback;
    private AtomicBoolean mBeing = new AtomicBoolean(false);

    private Handler.Callback getHCallback() {
        return BRHandler.get(getH()).mCallback();
    }

    private Handler getH() {
        Object currentActivityThread = ScoreCore.mainThread();
        return BRActivityThread.get(currentActivityThread).mH();
    }

    @Override
    public void injectHook() {
        mOtherCallback = getHCallback();
        if (mOtherCallback != null && (mOtherCallback == this || mOtherCallback.getClass().getName().equals(this.getClass().getName()))) {
            mOtherCallback = null;
        }
        BRHandler.get(getH())._set_mCallback(this);
    }

    @Override
    public boolean isBadEnv() {
        Handler.Callback hCallback = getHCallback();
        return hCallback != null && hCallback != this;
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (!mBeing.getAndSet(true)) {
            try {
                if (BuildCompat.isPie()) {
                    if (msg.what == BRActivityThreadH.get().EXECUTE_TRANSACTION()) {
                        if (handleLaunchActivity(msg.obj)) {
                            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                            return true;
                        }
                    }
                } else {
                    if (msg.what == BRActivityThreadH.get().LAUNCH_ACTIVITY()) {
                        if (handleLaunchActivity(msg.obj)) {
                            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                            return true;
                        }
                    }
                }
                if (msg.what == BRActivityThreadH.get().CREATE_SERVICE()) {
                    return handleCreateService(msg.obj);
                }
                if (mOtherCallback != null) {
                    return mOtherCallback.handleMessage(msg);
                }
                return false;
            } finally {
                mBeing.set(false);
            }
        }
        return false;
    }

    private Object getLaunchActivityItem(Object clientTransaction) {
    List<Object> mActivityCallbacks = BRClientTransaction.get(clientTransaction).mActivityCallbacks();
    // Add null check to prevent NPE
    if (mActivityCallbacks == null) {
        return null;
    }

    for (Object obj : mActivityCallbacks) {
        if (BRLaunchActivityItem.getRealClass().getName().equals(obj.getClass().getCanonicalName())) {
            return obj;
        }
    }
    return null;
}
    private boolean handleLaunchActivity(Object client) {
    Object r;
    try {
        if (BuildCompat.isPie()) {
            r = getLaunchActivityItem(client);
        } else {
            r = client;
        }
        
        if (r == null) {
            Slog.w(TAG, "handleLaunchActivity: Null activity record");
            return false;
        }

        Intent intent;
        IBinder token;
        if (BuildCompat.isPie()) {
            intent = BRLaunchActivityItem.get(r).mIntent();
            token = BRClientTransaction.get(client).mActivityToken();
        } else {
            ActivityThreadActivityClientRecordContext clientRecordContext = BRActivityThreadActivityClientRecord.get(r);
            intent = clientRecordContext.intent();
            token = clientRecordContext.token();
        }

        if (intent == null) {
            Slog.w(TAG, "handleLaunchActivity: Null intent");
            return false;
        }

        // Set class loader before processing
        intent.setExtrasClassLoader(getClass().getClassLoader());
        
        ProxyActivityRecord stubRecord;
        try {
            stubRecord = ProxyActivityRecord.create(intent);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to create ProxyActivityRecord", e);
            return false;
        }

        if (stubRecord.mActivityInfo == null) {
            Slog.w(TAG, "handleLaunchActivity: Null activity info");
            return false;
        }

        ActivityInfo activityInfo = stubRecord.mActivityInfo;
        
        if (BActivityThread.getAppConfig() == null) {
            try {
                // The whole process/heap that owned this task just died (that's
                // exactly what getAppConfig()==null means here). Whatever
                // savedInstanceState Bundle is still attached to this launch
                // record was captured from an activity instance that no longer
                // exists. If it's allowed to ride along into whatever activity
                // ends up launching (here we're about to redirect to the
                // package's launcher intent), it can carry stale view-hierarchy
                // state — e.g. a WebView's saved navigation history for a POST
                // request (like an OAuth/Twitter login) whose disk cache entry
                // is long gone, surfacing as net::ERR_CACHE_MISS ("Page not
                // available") the moment that view tries to restore itself.
                // A fresh process restart should never replay old state, so we
                // strip it here before doing anything else.
                clearStaleSavedState(r);

                ScoreCore.getBActivityManager().restartProcess(
                    activityInfo.packageName, 
                    activityInfo.processName, 
                    stubRecord.mUserId
                );

                Intent launchIntentForPackage = ScoreCore.getBPackageManager()
                    .getLaunchIntentForPackage(activityInfo.packageName, stubRecord.mUserId);
                
                if (launchIntentForPackage == null) {
                    Slog.e(TAG, "No launch intent for package: " + activityInfo.packageName);
                    return false;
                }

                intent.setExtrasClassLoader(getClass().getClassLoader());
                ProxyActivityRecord.saveStub(
                    intent, 
                    launchIntentForPackage, 
                    stubRecord.mActivityInfo, 
                    stubRecord.mActivityRecord, 
                    stubRecord.mUserId
                );

                if (BuildCompat.isPie()) {
                    LaunchActivityItemContext launchActivityItemContext = BRLaunchActivityItem.get(r);
                    launchActivityItemContext._set_mIntent(intent);
                    launchActivityItemContext._set_mInfo(activityInfo);
                } else {
                    ActivityThreadActivityClientRecordContext clientRecordContext = 
                        BRActivityThreadActivityClientRecord.get(r);
                    clientRecordContext._set_intent(intent);
                    clientRecordContext._set_activityInfo(activityInfo);
                }
                return true;
            } catch (Exception e) {
                Slog.e(TAG, "Failed to restart process", e);
                return false;
            }
        }

        // bind application if not initialized
        if (!BActivityThread.currentActivityThread().isInit()) {
            try {
                BActivityThread.currentActivityThread().bindApplication(
                    activityInfo.packageName,
                    activityInfo.processName
                );
                return true;
            } catch (Exception e) {
                Slog.e(TAG, "Failed to bind application", e);
                return false;
            }
        }

        try {
            int taskId = BRIActivityManager.get(BRActivityManagerNative.get().getDefault())
                .getTaskForActivity(token, false);
            ScoreCore.getBActivityManager()
                .onActivityCreated(taskId, token, stubRecord.mActivityRecord);

            LaunchActivityItemContext launchActivityItemContext = BRLaunchActivityItem.get(r);
            
            if (BuildCompat.isPie()) {
                launchActivityItemContext._set_mIntent(stubRecord.mTarget);
                launchActivityItemContext._set_mInfo(activityInfo);
                return true;
            }

            if (Build.VERSION.SDK_INT == 31 || 
                (Build.VERSION.SDK_INT == 30 && Build.VERSION.PREVIEW_SDK_INT == 1)) {
                Object record = BRActivityThread.get(ScoreCore.mainThread())
                    .getLaunchingActivity(token);
                ActivityThreadActivityClientRecordContext clientRecordContext = 
                    BRActivityThreadActivityClientRecord.get(record);
                clientRecordContext._set_intent(stubRecord.mTarget);
                clientRecordContext._set_activityInfo(activityInfo);
                clientRecordContext._set_packageInfo(
                    BActivityThread.currentActivityThread().getPackageInfo()
                );
                checkActivityClient();
            } else {
                ActivityThreadActivityClientRecordContext clientRecordContext = 
                    BRActivityThreadActivityClientRecord.get(r);
                clientRecordContext._set_intent(stubRecord.mTarget);
                clientRecordContext._set_activityInfo(activityInfo);
            }
            
            return true;
        } catch (Exception e) {
            Slog.e(TAG, "Failed to handle activity launch", e);
            return false;
        }
    } catch (Exception e) {
        Slog.e(TAG, "Unexpected error in handleLaunchActivity", e);
        return false;
    }
}

    /**
     * Nulls out any Bundle/PersistableBundle field on the given launch record
     * whose name suggests it holds saved instance state (e.g. "mState",
     * "mPersistentState"). Used only when we know the owning process just
     * died, so any such state is guaranteed stale and unsafe to replay.
     *
     * Uses reflection over declared fields (rather than the typed BR*
     * accessor interfaces) so it keeps working across API levels even if the
     * exact field name for saved state differs between LaunchActivityItem
     * (Pie+) and ActivityClientRecord (pre-Pie), or shifts between AOSP
     * versions.
     */
    private void clearStaleSavedState(Object launchRecord) {
        if (launchRecord == null) return;
        try {
            Class<?> cls = launchRecord.getClass();
            while (cls != null && cls != Object.class) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    Class<?> type = f.getType();
                    boolean isBundleLike = android.os.Bundle.class.isAssignableFrom(type)
                            || android.os.PersistableBundle.class.isAssignableFrom(type);
                    if (isBundleLike && f.getName().toLowerCase().contains("state")) {
                        try {
                            f.setAccessible(true);
                            Object existing = f.get(launchRecord);
                            if (existing != null) {
                                f.set(launchRecord, null);
                                Slog.d(TAG, "clearStaleSavedState: cleared " + cls.getSimpleName() + "." + f.getName());
                            }
                        } catch (Throwable inner) {
                            Slog.w(TAG, "clearStaleSavedState: failed to clear " + f.getName(), inner);
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            Slog.w(TAG, "clearStaleSavedState failed", t);
        }
    }

    private boolean handleCreateService(Object data) {
        if (BActivityThread.getAppConfig() != null) {
            String appPackageName = BActivityThread.getAppPackageName();
            assert appPackageName != null;

            ServiceInfo serviceInfo = BRActivityThreadCreateServiceData.get(data).info();
            if (!serviceInfo.name.equals(ProxyManifest.getProxyService(BActivityThread.getAppPid()))
                    && !serviceInfo.name.equals(ProxyManifest.getProxyJobService(BActivityThread.getAppPid()))) {
                Slog.d(TAG, "handleCreateService: " + data);
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(appPackageName, serviceInfo.name));
                ScoreCore.getBActivityManager().startService(intent, null, false, BActivityThread.getUserId());
                return true;
            }
        }
        return false;
    }

    private void checkActivityClient() {
        try {
            Object activityClientController = BRActivityClient.get().getActivityClientController();
            if (!(activityClientController instanceof Proxy)) {
                IActivityClientProxy iActivityClientProxy = new IActivityClientProxy(activityClientController);
                iActivityClientProxy.onlyProxy(true);
                iActivityClientProxy.injectHook();
                Object instance = BRActivityClient.get().getInstance();
                Object o = BRActivityClient.get(instance).INTERFACE_SINGLETON();
                BRActivityClientActivityClientControllerSingleton.get(o)._set_mKnownInstance(iActivityClientProxy.getProxyInvocation());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

