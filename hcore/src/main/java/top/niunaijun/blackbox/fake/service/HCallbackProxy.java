package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

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
import black.android.app.servertransaction.LaunchActivityItemContext;
import black.android.os.BRHandler;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.IInjectHook;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.proxy.record.ProxyActivityRecord;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class HCallbackProxy implements IInjectHook, Handler.Callback {
    public static final String TAG = "HCallbackStub";

    // ---- Android 10+ ActivityThread$H message constants ----
    // HARD-CODED because reflection is blocked on Android 14+
    // AOSP values (stable across versions):
    private static final int H_LAUNCH_ACTIVITY       = 100;
    private static final int H_CREATE_SERVICE        = 114;
    private static final int H_EXECUTE_TRANSACTION   = 159;

    private Handler.Callback mOtherCallback;
    private AtomicBoolean mBeing = new AtomicBoolean(false);

    private Handler.Callback getHCallback() {
        return BRHandler.get(getH()).mCallback();
    }

    private Handler getH() {
        Object currentActivityThread = BlackBoxCore.mainThread();
        return BRActivityThread.get(currentActivityThread).mH();
    }

    @Override
    public void injectHook() {
        mOtherCallback = getHCallback();
        if (mOtherCallback != null
                && (mOtherCallback == this
                    || mOtherCallback.getClass().getName().equals(this.getClass().getName()))) {
            mOtherCallback = null;
        }
        BRHandler.get(getH())._set_mCallback(this);
    }

    @Override
    public boolean isBadEnv() {
        Handler.Callback hCallback = getHCallback();
        return hCallback != null && hCallback != this;
    }

    // -----------------------------------------------------------------
    // Safe helper: never returns null, never crashes on hidden-api deny
    // -----------------------------------------------------------------
    private int safeInt(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (msg == null) {
            Slog.w(TAG, "handleMessage: msg is null");
            return false;
        }

        if (!mBeing.getAndSet(true)) {
            try {
                // ---- Resolve constants safely ----
                // Prefer reflected values when available, fall back to
                // hardcoded AOSP constants on Android 14+ where reflection
                // returns null.
                int executeTransaction = safeInt(
                        tryGet(() -> BRActivityThreadH.get().EXECUTE_TRANSACTION()),
                        H_EXECUTE_TRANSACTION);

                int launchActivity = safeInt(
                        tryGet(() -> BRActivityThreadH.get().LAUNCH_ACTIVITY()),
                        H_LAUNCH_ACTIVITY);

                int createService = safeInt(
                        tryGet(() -> BRActivityThreadH.get().CREATE_SERVICE()),
                        H_CREATE_SERVICE);

                if (BuildCompat.isPie()) {
                    if (msg.what == executeTransaction) {
                        if (handleLaunchActivity(msg.obj)) {
                            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                            return true;
                        }
                    }
                } else {
                    if (msg.what == launchActivity) {
                        if (handleLaunchActivity(msg.obj)) {
                            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                            return true;
                        }
                    }
                }

                if (msg.what == createService) {
                    return handleCreateService(msg.obj);
                }

                if (mOtherCallback != null) {
                    try {
                        return mOtherCallback.handleMessage(msg);
                    } catch (Throwable t) {
                        Slog.w(TAG, "other callback threw: " + t.getMessage());
                    }
                }
                return false;
            } catch (Throwable t) {
                Slog.e(TAG, "handleMessage failed", t);
                return false;
            } finally {
                mBeing.set(false);
            }
        }
        return false;
    }

    // -----------------------------------------------------------------
    // Helper: run a supplier that may throw or return null
    // -----------------------------------------------------------------
    private interface IntSupplier {
        Integer get() throws Throwable;
    }

    private Integer tryGet(IntSupplier s) {
        try {
            return s.get();
        } catch (Throwable t) {
            Slog.d(TAG, "constant lookup failed: " + t.getMessage());
            return null;
        }
    }

    private Object getLaunchActivityItem(Object clientTransaction) {
        List<Object> mActivityCallbacks = BRClientTransaction.get(clientTransaction).mActivityCallbacks();

        if (mActivityCallbacks == null) {
            Slog.e(TAG, "mActivityCallbacks is null for clientTransaction: " + clientTransaction);
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
        if (client == null) {
            Slog.w(TAG, "handleLaunchActivity: client is null");
            return false;
        }

        Object r;
        if (BuildCompat.isPie()) {
            r = getLaunchActivityItem(client);
        } else {
            r = client;
        }
        if (r == null)
            return false;

        Intent intent;
        IBinder token;
        if (BuildCompat.isPie()) {
            intent = BRLaunchActivityItem.get(r).mIntent();
            token = BRClientTransaction.get(client).mActivityToken();
        } else {
            ActivityThreadActivityClientRecordContext clientRecordContext =
                    BRActivityThreadActivityClientRecord.get(r);
            intent = clientRecordContext.intent();
            token = clientRecordContext.token();
        }

        if (intent == null)
            return false;

        ProxyActivityRecord stubRecord = ProxyActivityRecord.create(intent);
        ActivityInfo activityInfo = stubRecord.mActivityInfo;
        if (activityInfo != null) {
            if (BActivityThread.getAppConfig() == null) {
                BlackBoxCore.getBActivityManager().restartProcess(
                        activityInfo.packageName,
                        activityInfo.processName,
                        stubRecord.mUserId);

                Intent launchIntentForPackage =
                        BlackBoxCore.getBPackageManager()
                                .getLaunchIntentForPackage(activityInfo.packageName, stubRecord.mUserId);
                intent.setExtrasClassLoader(this.getClass().getClassLoader());
                ProxyActivityRecord.saveStub(intent, launchIntentForPackage,
                        stubRecord.mActivityInfo, stubRecord.mActivityRecord, stubRecord.mUserId);
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
            }

            if (!BActivityThread.currentActivityThread().isInit()) {
                BActivityThread.currentActivityThread().bindApplication(
                        activityInfo.packageName, activityInfo.processName);
                return true;
            }

            int taskId = BRIActivityManager.get(
                    BRActivityManagerNative.get().getDefault())
                    .getTaskForActivity(token, false);
            BlackBoxCore.getBActivityManager().onActivityCreated(
                    taskId, token, stubRecord.mActivityRecord);

            if (BuildCompat.isTiramisu()) {
                LaunchActivityItemContext launchActivityItemContext = BRLaunchActivityItem.get(r);
                launchActivityItemContext._set_mIntent(stubRecord.mTarget);
                launchActivityItemContext._set_mInfo(activityInfo);
            } else if (BuildCompat.isS()) {
                Object record = BRActivityThread.get(BlackBoxCore.mainThread())
                        .getLaunchingActivity(token);
                ActivityThreadActivityClientRecordContext clientRecordContext =
                        BRActivityThreadActivityClientRecord.get(record);
                clientRecordContext._set_intent(stubRecord.mTarget);
                clientRecordContext._set_activityInfo(activityInfo);
                clientRecordContext._set_packageInfo(
                        BActivityThread.currentActivityThread().getPackageInfo());
                checkActivityClient();
            } else if (BuildCompat.isPie()) {
                LaunchActivityItemContext launchActivityItemContext = BRLaunchActivityItem.get(r);
                launchActivityItemContext._set_mIntent(stubRecord.mTarget);
                launchActivityItemContext._set_mInfo(activityInfo);
            } else {
                ActivityThreadActivityClientRecordContext clientRecordContext =
                        BRActivityThreadActivityClientRecord.get(r);
                clientRecordContext._set_intent(stubRecord.mTarget);
                clientRecordContext._set_activityInfo(activityInfo);
            }
        }
        return false;
    }

    private boolean handleCreateService(Object data) {
        if (data == null) return false;

        if (BActivityThread.getAppConfig() != null) {
            String appPackageName = BActivityThread.getAppPackageName();
            if (appPackageName == null) return false;

            ServiceInfo serviceInfo = BRActivityThreadCreateServiceData.get(data).info();
            if (serviceInfo == null) return false;

            if (!serviceInfo.name.equals(ProxyManifest.getProxyService(BActivityThread.getAppPid()))
                    && !serviceInfo.name.equals(ProxyManifest.getProxyJobService(BActivityThread.getAppPid()))) {
                Slog.d(TAG, "handleCreateService: " + data);
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(appPackageName, serviceInfo.name));
                BlackBoxCore.getBActivityManager().startService(
                        intent, null, false, BActivityThread.getUserId());
                return true;
            }
        }
        return false;
    }

    private void checkActivityClient() {
        try {
            Object activityClientController = BRActivityClient.get().getActivityClientController();
            if (!(activityClientController instanceof Proxy)) {
                IActivityClientProxy iActivityClientProxy =
                        new IActivityClientProxy(activityClientController);
                iActivityClientProxy.onlyProxy(true);
                iActivityClientProxy.injectHook();
                Object instance = BRActivityClient.get().getInstance();
                Object o = BRActivityClient.get(instance).INTERFACE_SINGLETON();
                BRActivityClientActivityClientControllerSingleton.get(o)
                        ._set_mKnownInstance(iActivityClientProxy.getProxyInvocation());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
