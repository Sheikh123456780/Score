package com.Score.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import java.io.File;
import java.lang.reflect.Method;

import com.Score.ScoreCore;
import com.Score.app.BActivityThread;
import com.Score.fake.hook.MethodHook;
import com.Score.fake.hook.ProxyMethod;
import com.Score.fake.provider.FileProviderHandler;
import com.Score.utils.ComponentUtils;
import com.Score.utils.MethodParameterUtils;
import com.Score.utils.Slog;
import com.Score.utils.compat.BuildCompat;
import com.Score.utils.compat.StartActivityCompat;

import static android.content.pm.PackageManager.GET_META_DATA;

/**
 * Created by Milk on 4/21/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
import org.lsposed.lsparanoid.Obfuscate;
@Obfuscate
public class ActivityManagerCommonProxy {
    public static final String TAG = "CommonStub";

    @ProxyMethod("startActivity")
    public static class StartActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            Intent intent = getIntent(args);
            Slog.d(TAG, "Hook in : " + intent);
            assert intent != null;
            
            // ========== CHECK IF INTENT IS FOR EXTERNAL APP ==========
            // If this intent is NOT for an app inside BlackBox
            // Forward it to the REAL Android system
            if (!ComponentUtils.isSelf(intent)) {
                Slog.d(TAG, "External intent detected, forwarding to real system: " + intent);
                return method.invoke(who, args);
            }
            // =========================================================
            
            if (intent.getParcelableExtra("_B_|_target_") != null) {
                return method.invoke(who, args);
            }
            if (ComponentUtils.isRequestInstall(intent)) {
                File file = FileProviderHandler.convertFile(BActivityThread.getApplication(), intent.getData());
                if (ScoreCore.get().requestInstallPackage(file, BActivityThread.getUserId())) {
                    return 0;
                }
                intent.setData(FileProviderHandler.convertFileUri(BActivityThread.getApplication(), intent.getData()));
                return method.invoke(who, args);
            }
            String dataString = intent.getDataString();
            if (dataString != null && dataString.equals("package:" + BActivityThread.getAppPackageName())) {
                intent.setData(Uri.parse("package:" + ScoreCore.getHostPkg()));
            }

            ResolveInfo resolveInfo = ScoreCore.getBPackageManager().resolveActivity(intent, GET_META_DATA, StartActivityCompat.getResolvedType(args), BActivityThread.getUserId());
            if (resolveInfo == null) {
                String origPackage = intent.getPackage();
                if (intent.getPackage() == null && intent.getComponent() == null) {
                    intent.setPackage(BActivityThread.getAppPackageName());
                } else {
                    origPackage = intent.getPackage();
                }
                resolveInfo = ScoreCore.getBPackageManager().resolveActivity(intent, GET_META_DATA, StartActivityCompat.getResolvedType(args), BActivityThread.getUserId());
                if (resolveInfo == null) {
                    intent.setPackage(origPackage);
                    return method.invoke(who, args);
                }
            }

            // Only internal intents reach here
            Slog.d(TAG, "Internal intent, routing inside BlackBox: " + intent);
            intent.setExtrasClassLoader(who.getClass().getClassLoader());
            intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
            ScoreCore.getBActivityManager().startActivityAms(BActivityThread.getUserId(),
                    StartActivityCompat.getIntent(args),
                    StartActivityCompat.getResolvedType(args),
                    StartActivityCompat.getResultTo(args),
                    StartActivityCompat.getResultWho(args),
                    StartActivityCompat.getRequestCode(args),
                    StartActivityCompat.getFlags(args),
                    StartActivityCompat.getOptions(args));
            return 0;
        }

        private Intent getIntent(Object[] args) {
            int index;
            if (BuildCompat.isR()) {
                index = 3;
            } else {
                index = 2;
            }
            if (args[index] instanceof Intent) {
                return (Intent) args[index];
            }
            for (Object arg : args) {
                if (arg instanceof Intent) {
                    return (Intent) arg;
                }
            }
            return null;
        }
    }

    @ProxyMethod("startActivities")
    public static class StartActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int index = getIntents();
            Intent[] intents = (Intent[]) args[index++];
            String[] resolvedTypes = (String[]) args[index++];
            IBinder resultTo = (IBinder) args[index++];
            Bundle options = (Bundle) args[index];
            
            // Check if ALL intents are internal
            boolean allInternal = true;
            for (Intent intent : intents) {
                if (!ComponentUtils.isSelf(intent)) {
                    allInternal = false;
                    break;
                }
            }
            
            // If any intent is external, let real system handle all
            if (!allInternal) {
                Slog.d(TAG, "External intents in batch, forwarding to real system");
                return method.invoke(who, args);
            }

            // All intents are internal, use BlackBox routing
            for (Intent intent : intents) {
                intent.setExtrasClassLoader(who.getClass().getClassLoader());
            }
            return ScoreCore.getBActivityManager().startActivities(BActivityThread.getUserId(),
                    intents, resolvedTypes, resultTo, options);
        }

        public int getIntents() {
            if (BuildCompat.isR()) {
                return 3;
            }
            return 2;
        }
    }

    @ProxyMethod("startIntentSenderForResult")
    public static class StartIntentSenderForResult extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityResumed")
    public static class ActivityResumed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ScoreCore.getBActivityManager().onActivityResumed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityDestroyed")
    public static class ActivityDestroyed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ScoreCore.getBActivityManager().onActivityDestroyed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("finishActivity")
    public static class FinishActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ScoreCore.getBActivityManager().onFinishActivity((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAppTasks")
    public static class GetAppTasks extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getCallingPackage")
    public static class getCallingPackage extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ScoreCore.getBActivityManager().getCallingPackage((IBinder) args[0], BActivityThread.getUserId());
        }
    }

    @ProxyMethod("getCallingActivity")
    public static class getCallingActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ScoreCore.getBActivityManager().getCallingActivity((IBinder) args[0], BActivityThread.getUserId());
        }
    }
}
