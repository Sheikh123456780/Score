package com.Score.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import black.android.app.BRActivityThread;
import black.android.app.BRContextImpl;
import com.Score.ScoreCore;
import com.Score.app.BActivityThread;
import com.Score.core.env.AppSystemEnv;
import com.Score.fake.hook.BinderInvocationStub;
import com.Score.fake.hook.MethodHook;
import com.Score.fake.hook.ProxyMethod;
import com.Score.fake.service.base.PkgMethodProxy;
import com.Score.fake.service.base.ValueMethodProxy;
import com.Score.utils.MethodParameterUtils;
import com.Score.utils.Reflector;
import com.Score.utils.Slog;
import com.Score.utils.compat.BuildCompat;
import com.Score.utils.compat.ParceledListSliceCompat;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class IPackageManagerProxy extends BinderInvocationStub {
    public static final String TAG = "PackageManagerStub";

    public IPackageManagerProxy() {
        super(BRActivityThread.get().sPackageManager().asBinder());
    }

    @Override
    protected Object getWho() {
        return BRActivityThread.get().sPackageManager();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRActivityThread.get()._set_sPackageManager(proxyInvocation);
        replaceSystemService("package");
        Object systemContext = BRActivityThread.get(ScoreCore.mainThread()).getSystemContext();
        PackageManager mPackageManager = BRContextImpl.get(systemContext).mPackageManager();
        if (mPackageManager != null) {
            try {
                Reflector.on("android.app.ApplicationPackageManager").field("mPM").set(mPackageManager, proxyInvocation);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to inject ApplicationPackageManager mPM", e);
            }
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new PkgMethodProxy("shouldShowRequestPermissionRationale"));
        addMethodHook(new CheckPermission());
        addMethodHook(new CheckUidPermission());
        addMethodHook(new GrantRuntimePermission());
        addMethodHook(new RevokeRuntimePermission());
        if (BuildCompat.isT()) {
            return;
        }
        addMethodHook(new PkgMethodProxy("clearPackagePreferredActivities"));
    }

    private static boolean isSystemWebViewPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        return "com.google.android.webview".equals(packageName)
                || "com.android.webview".equals(packageName)
                || "com.google.android.webview.beta".equals(packageName)
                || "com.google.android.webview.dev".equals(packageName)
                || "com.google.android.webview.canary".equals(packageName)
                || "com.chrome.beta".equals(packageName)
                || "com.chrome.dev".equals(packageName)
                || "com.android.chrome".equals(packageName);
    }

    private static boolean isSystemWebViewComponent(ComponentName componentName) {
        return componentName != null
                && isSystemWebViewPackage(componentName.getPackageName());
    }

    @ProxyMethod("checkPermission")
    public static class CheckPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permission = null;
            String pkg = null;
            if (args != null) {
                for (Object arg : args) {
                    if (!(arg instanceof String)) continue;
                    String value = (String) arg;
                    if (VirtualPermissionCompat.isVirtualPackage(value)) pkg = value;
                    else if (permission == null) permission = value;
                }
            }
            if (pkg != null && permission != null && VirtualPermissionCompat.isRuntimePermission(permission)) {
                return VirtualPermissionCompat.hostHasPermission(permission)
                        ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("checkUidPermission")
    public static class CheckUidPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permission = null;
            int uid = -1;
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof String && permission == null) permission = (String) arg;
                    else if (arg instanceof Integer) uid = (Integer) arg;
                }
            }
            if (uid >= 0 && VirtualPermissionCompat.isVirtualUid(uid)
                    && VirtualPermissionCompat.isRuntimePermission(permission)) {
                return VirtualPermissionCompat.hostHasPermission(permission)
                        ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("grantRuntimePermission")
    public static class GrantRuntimePermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = null;
            String permission = null;
            if (args != null) {
                for (Object arg : args) {
                    if (!(arg instanceof String)) continue;
                    String value = (String) arg;
                    if (VirtualPermissionCompat.isVirtualPackage(value)) pkg = value;
                    else if (permission == null) permission = value;
                }
            }
            if (pkg != null && permission != null && VirtualPermissionCompat.isRuntimePermission(permission)) {
                return null;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("revokeRuntimePermission")
    public static class RevokeRuntimePermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = null;
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof String && VirtualPermissionCompat.isVirtualPackage((String) arg)) {
                        pkg = (String) arg;
                        break;
                    }
                }
            }
            if (pkg != null) {
                return null;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("resolveIntent")
    public static class ResolveIntent extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = (Intent) args[0];
            String resolvedType = (String) args[1];
            int flags = MethodParameterUtils.toInt(args[2]);
            ResolveInfo resolveInfo = ScoreCore.getBPackageManager().resolveIntent(intent, resolvedType, flags, BActivityThread.getUserId());
            if (resolveInfo != null) {
                return resolveInfo;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("resolveService")
    public static class ResolveService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = (Intent) args[0];
            String resolvedType = (String) args[1];
            int flags = MethodParameterUtils.toInt(args[2]);
            ResolveInfo resolveInfo = ScoreCore.getBPackageManager().resolveService(intent, flags, resolvedType, BActivityThread.getUserId());
            if (resolveInfo != null) {
                return resolveInfo;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setComponentEnabledSetting")
    public static class SetComponentEnabledSetting extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("getPackageInfo")
    public static class GetPackageInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = (String) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);

            if (isSystemWebViewPackage(packageName)) {
                try {
                    return method.invoke(who, args);
                } catch (Throwable t) {
                    Slog.w(TAG, "Failed to resolve system WebView package info", t);
                }
            }

            PackageInfo packageInfo = ScoreCore.getBPackageManager().getPackageInfo(packageName, flags, BActivityThread.getUserId());
            if (packageInfo != null) {
                return packageInfo;
            }

            String currentPackage = BActivityThread.getAppPackageName();
            boolean isCurrentApp = packageName != null && packageName.equals(currentPackage);

            if (!isCurrentApp || AppSystemEnv.isOpenPackage(packageName)) {
                try {
                    Object realResult = method.invoke(who, args);
                    if (realResult != null) {
                        return realResult;
                    }
                } catch (Throwable ignored) {
                }
            }

            return null;
        }
    }

    @ProxyMethod("getPackageUid")
    public static class GetPackageUid extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String
                    && isSystemWebViewPackage((String) args[0])) {
                return method.invoke(who, args);
            }
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getProviderInfo")
    public static class GetProviderInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            if (isSystemWebViewComponent(componentName)) {
                return method.invoke(who, args);
            }
            ProviderInfo providerInfo = ScoreCore.getBPackageManager().getProviderInfo(componentName, flags, BActivityThread.getUserId());
            if (providerInfo != null)
                return providerInfo;
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("getReceiverInfo")
    public static class GetReceiverInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ActivityInfo receiverInfo = ScoreCore.getBPackageManager().getReceiverInfo(componentName, flags, BActivityThread.getUserId());
            if (receiverInfo != null)
                return receiverInfo;
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("getActivityInfo")
    public static class GetActivityInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ActivityInfo activityInfo = ScoreCore.getBPackageManager().getActivityInfo(componentName, flags, BActivityThread.getUserId());
            if (activityInfo != null)
                return activityInfo;
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("getServiceInfo")
    public static class GetServiceInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ServiceInfo serviceInfo = ScoreCore.getBPackageManager().getServiceInfo(componentName, flags, BActivityThread.getUserId());
            if (serviceInfo != null)
                return serviceInfo;
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("getInstalledApplications")
    public static class GetInstalledApplications extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = MethodParameterUtils.toInt(args[0]);
            List<ApplicationInfo> installedApplications = ScoreCore.getBPackageManager().getInstalledApplications(flags, BActivityThread.getUserId());
            return ParceledListSliceCompat.create(installedApplications != null ? installedApplications : new ArrayList<>());
        }
    }

    @ProxyMethod("getInstalledPackages")
    public static class GetInstalledPackages extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = MethodParameterUtils.toInt(args[0]);
            List<PackageInfo> installedPackages = ScoreCore.getBPackageManager().getInstalledPackages(flags, BActivityThread.getUserId());
            return ParceledListSliceCompat.create(installedPackages != null ? installedPackages : new ArrayList<>());
        }
    }

    @ProxyMethod("getApplicationInfo")
    public static class GetApplicationInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = (String) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);

            if (isSystemWebViewPackage(packageName)) {
                try {
                    return method.invoke(who, args);
                } catch (Throwable ignored) {
                }
            }

            ApplicationInfo applicationInfo = ScoreCore.getBPackageManager().getApplicationInfo(packageName, flags, BActivityThread.getUserId());
            if (applicationInfo != null) {
                if (applicationInfo.metaData != null) {
                    if ("com.p1.mobile.putong".equals(packageName)
                            && TextUtils.isEmpty(applicationInfo.metaData.getString("com.facebook.sdk.ClientToken"))) {
                        applicationInfo.metaData.putString("com.facebook.sdk.ClientToken", "6d8a6e54c2e859bfb2dbe047ec973ead");
                    }
                }
                return applicationInfo;
            }

            String currentPackage = BActivityThread.getAppPackageName();
            boolean isCurrentApp = packageName != null && packageName.equals(currentPackage);

            if (!isCurrentApp || AppSystemEnv.isOpenPackage(packageName)) {
                try {
                    Object realResult = method.invoke(who, args);
                    if (realResult != null) {
                        return realResult;
                    }
                } catch (Throwable ignored) {
                }
            }

            return null;
        }
    }

    @ProxyMethod("queryContentProviders")
    public static class QueryContentProviders extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = MethodParameterUtils.toInt(args[2]);
            List<ProviderInfo> providers = ScoreCore.getBPackageManager().
                    queryContentProviders(BActivityThread.getAppProcessName(), BActivityThread.getBUid(), flags, BActivityThread.getUserId());
            return ParceledListSliceCompat.create(providers != null ? providers : new ArrayList<>());
        }
    }

    @ProxyMethod("queryIntentReceivers")
    public static class QueryBroadcastReceivers extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
            String type = MethodParameterUtils.getFirstParam(args, String.class);
            Integer flags = MethodParameterUtils.getFirstParam(args, Integer.class);
            List<ResolveInfo> resolves = ScoreCore.getBPackageManager().queryBroadcastReceivers(intent, flags, type, BActivityThread.getUserId());

            if (resolves == null) {
                resolves = new ArrayList<>();
            }

            if (BuildCompat.isN()) {
                return ParceledListSliceCompat.create(resolves);
            }
            return resolves;
        }
    }

    @ProxyMethod("resolveContentProvider")
    public static class ResolveContentProvider extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String authority = (String) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ProviderInfo providerInfo = ScoreCore.getBPackageManager().resolveContentProvider(authority, flags, BActivityThread.getUserId());
            if (providerInfo == null) {
                return method.invoke(who, args);
            }
            return providerInfo;
        }
    }

    @ProxyMethod("queryIntentActivities")
    public static class QueryIntentActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
            String resolvedType = MethodParameterUtils.getFirstParam(args, String.class);
            Integer flags = MethodParameterUtils.getFirstParam(args, Integer.class);

            List<ResolveInfo> resolves = ScoreCore.getBPackageManager().queryIntentActivities(intent, flags, resolvedType, BActivityThread.getUserId());

            if ((resolves == null || resolves.isEmpty()) && !com.Score.utils.ComponentUtils.isSelf(intent)) {
                try {
                    if (args.length >= 4) {
                        Object realResult = method.invoke(who, intent, resolvedType, flags, BActivityThread.getUserId());
                        if (realResult != null) {
                            return realResult;
                        }
                    } else {
                        Object realResult = method.invoke(who, intent, resolvedType, flags);
                        if (realResult != null) {
                            return realResult;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            if (resolves == null) {
                resolves = new ArrayList<>();
            }

            if (BuildCompat.isN()) {
                return ParceledListSliceCompat.create(resolves);
            }
            return resolves;
        }
    }

    @ProxyMethod("canRequestPackageInstalls")
    public static class CanRequestPackageInstalls extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getPackagesForUid")
    public static class GetPackagesForUid extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int uid = (Integer) args[0];
            if (uid == ScoreCore.getHostUid()) {
                args[0] = BActivityThread.getBUid();
                uid = (int) args[0];
            }
            return ScoreCore.getBPackageManager().getPackagesForUid(uid);
        }
    }

    @ProxyMethod("getInstallerPackageName")
    public static class GetInstallerPackageName extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return "com.android.vending";
        }
    }

    @ProxyMethod("getSharedLibraries")
    public static class GetSharedLibraries extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParceledListSliceCompat.create(new ArrayList<>());
        }
    }

    @ProxyMethod("getComponentEnabledSetting")
    public static class getComponentEnabledSetting extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            String packageName = componentName.getPackageName();

            ApplicationInfo applicationInfo = ScoreCore.getBPackageManager().getApplicationInfo(packageName, 0, BActivityThread.getUserId());

            if (applicationInfo != null) {
                return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            }
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            throw new IllegalArgumentException();
        }
    }
}

