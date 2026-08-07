package com.Score.fake.frameworks;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;

import java.util.Collections;
import java.util.List;

import com.Score.ScoreCore;
import com.Score.app.BActivityThread;
import com.Score.core.system.ServiceManager;
import com.Score.core.system.pm.IBPackageManagerService;
import com.Score.entity.pm.InstallOption;
import com.Score.entity.pm.InstallResult;
import com.Score.entity.pm.InstalledPackage;
import com.Score.utils.ComponentUtils;
import com.Score.utils.Slog;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * Created by Milk on 4/14/21.
 */
@Obfuscate
public class BPackageManager extends BlackManager<IBPackageManagerService> {
    private static final BPackageManager sPackageManager = new BPackageManager();
    private static final String TAG = "BPackageManager";

    public static BPackageManager get() {
        return sPackageManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.PACKAGE_MANAGER;
    }

    public Intent getLaunchIntentForPackage(String packageName, int userId) {
        Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
        intentToResolve.addCategory(Intent.CATEGORY_INFO);
        intentToResolve.setPackage(packageName);
        List<ResolveInfo> queryIntentActivities = queryIntentActivities(
                intentToResolve, 
                0, 
                intentToResolve.resolveTypeIfNeeded(ScoreCore.getContext().getContentResolver()), 
                userId
        );
        
        if (queryIntentActivities == null || queryIntentActivities.isEmpty()) {
            intentToResolve.removeCategory(Intent.CATEGORY_INFO);
            intentToResolve.addCategory(Intent.CATEGORY_LAUNCHER);
            intentToResolve.setPackage(packageName);
            queryIntentActivities = queryIntentActivities(
                    intentToResolve, 
                    0, 
                    intentToResolve.resolveTypeIfNeeded(ScoreCore.getContext().getContentResolver()), 
                    userId
            );
        }
        
        if (queryIntentActivities == null || queryIntentActivities.isEmpty()) {
            return null;
        }
        
        Intent intent = new Intent(intentToResolve);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                queryIntentActivities.get(0).activityInfo.packageName, 
                queryIntentActivities.get(0).activityInfo.name
        );
        return intent;
    }

    public ResolveInfo resolveService(Intent intent, int flags, String resolvedType, int userId) {
        try {
            return getService().resolveService(intent, flags, resolvedType, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ResolveInfo resolveActivity(Intent intent, int flags, String resolvedType, int userId) {
        try {
            return getService().resolveActivity(intent, flags, resolvedType, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ProviderInfo resolveContentProvider(String authority, int flags, int userId) {
        try {
            return getService().resolveContentProvider(authority, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags, int userId) {
        try {
            return getService().resolveIntent(intent, resolvedType, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) {
        try {
            return getService().getApplicationInfo(packageName, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public PackageInfo getPackageInfo(String packageName, int flags, int userId) {
        try {
            return getService().getPackageInfo(packageName, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ServiceInfo getServiceInfo(ComponentName component, int flags, int userId) {
        try {
            return getService().getServiceInfo(component, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ActivityInfo getReceiverInfo(ComponentName componentName, int flags, int userId) {
        try {
            return getService().getReceiverInfo(componentName, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ActivityInfo getActivityInfo(ComponentName component, int flags, int userId) {
        try {
            return getService().getActivityInfo(component, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public ProviderInfo getProviderInfo(ComponentName component, int flags, int userId) {
        try {
            return getService().getProviderInfo(component, flags, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags, String resolvedType, int userId) {
        try {
            List<ResolveInfo> internalResult = getService().queryIntentActivities(intent, flags, resolvedType, userId);
            
            if ((internalResult == null || internalResult.isEmpty()) && !ComponentUtils.isSelf(intent)) {
                Slog.d(TAG, "queryIntentActivities: External intent, querying real system: " + intent);
                if (ScoreCore.getContext() != null) {
                    PackageManager pm = ScoreCore.getContext().getPackageManager();
                    if (pm != null) {
                        try {
                            List<ResolveInfo> realResult = pm.queryIntentActivities(intent, flags);
                            if (realResult != null && !realResult.isEmpty()) {
                                Slog.d(TAG, "queryIntentActivities: Found in real system: " + realResult.size());
                                return realResult;
                            }
                        } catch (Throwable t) {
                            Slog.e(TAG, "Failed querying host system PackageManager", t);
                        }
                    }
                }
            }
            return internalResult != null ? internalResult : Collections.emptyList();
        } catch (RemoteException e) {
            crash(e);
        }
        return Collections.emptyList();
    }

            public List<ResolveInfo> queryIntentServices(Intent intent, int flags, String resolvedType, int userId) {
        try {
            List<ResolveInfo> result = getService().queryIntentServices(intent, flags, resolvedType, userId);

            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            crash(e);
        }
        return Collections.emptyList();
    }


    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags, String resolvedType, int userId) {
        try {
            List<ResolveInfo> result = getService().queryBroadcastReceivers(intent, flags, resolvedType, userId);
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            crash(e);
        }
        return Collections.emptyList();
    }

    public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags, int userId) {
        try {
            List<ProviderInfo> result = getService().queryContentProviders(processName, uid, flags, userId);
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            crash(e);
        }
        return Collections.emptyList();
    }

    public InstallResult installPackageAsUser(String file, InstallOption option, int userId) {
        try {
            return getService().installPackageAsUser(file, option, userId);
        } catch (RemoteException e) {
            crash(e);
        }
        return null;
    }

    public List<ApplicationInfo> getInstalledApplications(int flags, int userId) {
        try {
            List<ApplicationInfo> apps = getService().getInstalledApplications(flags, userId);
            return apps != null ? apps : Collections.emptyList();
        } catch (RemoteException e) {
            Slog.e(TAG, "getInstalledApplications failed", e);
        }
        return Collections.emptyList();
    }

    public List<PackageInfo> getInstalledPackages(int flags, int userId) {
        try {
            List<PackageInfo> pkgs = getService().getInstalledPackages(flags, userId);
            return pkgs != null ? pkgs : Collections.emptyList();
        } catch (RemoteException e) {
            Slog.e(TAG, "getInstalledPackages failed", e);
        }
        return Collections.emptyList();
    }

    public void clearPackage(String packageName, int userId) {
        try {
            getService().clearPackage(packageName, userId);
        } catch (RemoteException e) {
            Slog.e(TAG, "clearPackage failed for " + packageName, e);
        }
    }

    public void stopPackage(String packageName, int userId) {
        try {
            getService().stopPackage(packageName, userId);
        } catch (RemoteException e) {
            Slog.e(TAG, "stopPackage failed for " + packageName, e);
        }
    }

    public void uninstallPackageAsUser(String packageName, int userId) {
        try {
            getService().uninstallPackageAsUser(packageName, userId);
        } catch (RemoteException e) {
            Slog.e(TAG, "uninstallPackageAsUser failed for " + packageName, e);
        }
    }

    public void uninstallPackage(String packageName) {
        try {
            getService().uninstallPackage(packageName);
        } catch (RemoteException e) {
            Slog.e(TAG, "uninstallPackage failed for " + packageName, e);
        }
    }

    public boolean isInstalled(String packageName, int userId) {
        try {
            return getService().isInstalled(packageName, userId);
        } catch (RemoteException e) {
            Slog.e(TAG, "isInstalled check failed for " + packageName, e);
        }
        return false;
    }

    public List<InstalledPackage> getInstalledPackagesAsUser(int userId) {
        try {
            List<InstalledPackage> pkgs = getService().getInstalledPackagesAsUser(userId);
            return pkgs != null ? pkgs : Collections.emptyList();
        } catch (RemoteException e) {
            Slog.e(TAG, "getInstalledPackagesAsUser failed", e);
        }
        return Collections.emptyList();
    }

    public String[] getPackagesForUid(int uid) {
        try {
            String[] pkgs = getService().getPackagesForUid(uid, BActivityThread.getUserId());
            return pkgs != null ? pkgs : new String[0];
        } catch (RemoteException e) {
            Slog.e(TAG, "getPackagesForUid failed for UID " + uid, e);
        }
        return new String[0];
    }

    private void crash(Throwable e) {
        Slog.e(TAG, "RemoteException in BPackageManager", e);
    }
}
