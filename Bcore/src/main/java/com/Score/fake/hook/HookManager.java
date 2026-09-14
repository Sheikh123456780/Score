package com.Score.fake.hook;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import com.Score.ScoreCore;
import com.Score.fake.delegate.AppInstrumentation;
import com.Score.fake.service.AudioRecordProxy;
import com.Score.fake.service.HCallbackProxy;
import com.Score.fake.service.IAccessibilityManagerProxy;
import com.Score.fake.service.IAccountManagerProxy;
import com.Score.fake.service.IActivityClientProxy;
import com.Score.fake.service.IActivityManagerProxy;
import com.Score.fake.service.IActivityTaskManagerProxy;
import com.Score.fake.service.IAlarmManagerProxy;
import com.Score.fake.service.IAppOpsManagerProxy;
import com.Score.fake.service.IAppWidgetManagerProxy;
import com.Score.fake.service.IAudioManagerProxy;
import com.Score.fake.service.IAutofillManagerProxy;
import com.Score.fake.service.IConnectivityManagerProxy;
import com.Score.fake.service.IContextHubServiceProxy;
import com.Score.fake.service.IDeviceIdentifiersPolicyProxy;
import com.Score.fake.service.IDevicePolicyManagerProxy;
import com.Score.fake.service.IDisplayManagerProxy;
import com.Score.fake.service.IFingerprintManagerProxy;
import com.Score.fake.service.IGraphicsStatsProxy;
import com.Score.fake.service.IJobServiceProxy;
import com.Score.fake.service.ILauncherAppsProxy;
import com.Score.fake.service.ILocaleManagerProxy;
import com.Score.fake.service.ILocationManagerProxy;
import com.Score.fake.service.IMediaRouterServiceProxy;
import com.Score.fake.service.IMediaSessionManagerProxy;
import com.Score.fake.service.INetworkManagementServiceProxy;
import com.Score.fake.service.INotificationManagerProxy;
import com.Score.fake.service.IPackageManagerProxy;
import com.Score.fake.service.IPermissionManagerProxy;
import com.Score.fake.service.IPersistentDataBlockServiceProxy;
import com.Score.fake.service.IPhoneSubInfoProxy;
import com.Score.fake.service.IPowerManagerProxy;
import com.Score.fake.service.IShortcutManagerProxy;
import com.Score.fake.service.IStorageManagerProxy;
import com.Score.fake.service.IStorageStatsManagerProxy;
import com.Score.fake.service.ISystemUpdateProxy;
import com.Score.fake.service.ITelephonyManagerProxy;
import com.Score.fake.service.ITelephonyRegistryProxy;
import com.Score.fake.service.IUserManagerProxy;
import com.Score.fake.service.IVibratorServiceProxy;
import com.Score.fake.service.IVpnManagerProxy;
import com.Score.fake.service.IWebViewUpdateServiceProxy;
import com.Score.fake.service.IWifiManagerProxy;
import com.Score.fake.service.IWifiScannerProxy;
import com.Score.fake.service.IWindowManagerProxy;
import com.Score.fake.service.MediaRecorderProxy;
import com.Score.fake.service.WebViewFactoryProxy;
import com.Score.fake.service.WebViewProxy;
import com.Score.fake.service.context.ContentServiceStub;
import com.Score.fake.service.context.RestrictionsManagerStub;
import com.Score.fake.service.libcore.OsStub;
import com.Score.fake.service.vivo.IVivoPermissionServiceProxy;
import com.Score.utils.Slog;
import com.Score.utils.compat.BuildCompat;
import org.lsposed.lsparanoid.Obfuscate;
/**
 * Created by Milk on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
@Obfuscate
public class HookManager {
    public static final String TAG = "HookManager";

    private static final HookManager sHookManager = new HookManager();

    private final Map<Class<?>, IInjectHook> mInjectors = new HashMap<>();

    public static HookManager get() {
        return sHookManager;
    }

    public void init() {
        if (ScoreCore.get().isBlackProcess() || ScoreCore.get().isServerProcess()) {
            addInjector(new IDisplayManagerProxy());
            addInjector(new OsStub());
            addInjector(new IActivityManagerProxy());
            addInjector(new IPackageManagerProxy());
            addInjector(new ITelephonyManagerProxy());
            addInjector(new HCallbackProxy());
            addInjector(new IAppOpsManagerProxy());
            addInjector(new INotificationManagerProxy());
            addInjector(new IAlarmManagerProxy());
            addInjector(new IAppWidgetManagerProxy());
            addInjector(new ContentServiceStub());
            addInjector(new IWindowManagerProxy());
            addInjector(new IUserManagerProxy());
            addInjector(new RestrictionsManagerStub());
            
            // ========== AUDIO/MICROPHONE FIXES ==========
            addInjector(new IAudioManagerProxy());
            addInjector(new MediaRecorderProxy());
            addInjector(new AudioRecordProxy());
            // ============================================
            
            addInjector(new IMediaSessionManagerProxy());
            addInjector(new ILocationManagerProxy());
            addInjector(new IStorageManagerProxy());
            addInjector(new ILauncherAppsProxy());
            addInjector(new IJobServiceProxy());
            addInjector(new IAccessibilityManagerProxy());
            addInjector(new ITelephonyRegistryProxy());
            addInjector(new IDevicePolicyManagerProxy());
            addInjector(new IAccountManagerProxy());
            addInjector(new IConnectivityManagerProxy());
            addInjector(new IPhoneSubInfoProxy());
            addInjector(new IMediaRouterServiceProxy());
            addInjector(new IPowerManagerProxy());
            addInjector(new IContextHubServiceProxy());
            addInjector(new IVibratorServiceProxy());
            addInjector(new IPersistentDataBlockServiceProxy());
            
            addInjector(AppInstrumentation.get());
            addInjector(new IWifiManagerProxy());
            addInjector(new IWifiScannerProxy());
            
            // 15.0
            if (BuildCompat.isVivo()){
                addInjector(new IVivoPermissionServiceProxy());
            }
            
            // 13.0
            if (BuildCompat.isT()){
                addInjector(new ILocaleManagerProxy());
            }
            
            // 12.0
            if (BuildCompat.isS()) {
                addInjector(new IActivityClientProxy(null));
                addInjector(new IVpnManagerProxy());
            }
            // 11.0
            if (BuildCompat.isR()) {
                addInjector(new IPermissionManagerProxy());
            }
            // 10.0
            if (BuildCompat.isQ()) {
                addInjector(new IActivityTaskManagerProxy());
            }
            // 9.0
            if (BuildCompat.isPie()) {
                addInjector(new ISystemUpdateProxy());
            }
            // 8.0
            if (BuildCompat.isOreo()) {
                addInjector(new IAutofillManagerProxy());
                addInjector(new IDeviceIdentifiersPolicyProxy());
                addInjector(new IStorageStatsManagerProxy());
            }
            // 7.1
            if (BuildCompat.isN_MR1()) {
                addInjector(new IShortcutManagerProxy());
            }
            // 7.0
            if (BuildCompat.isN()) {
                addInjector(new INetworkManagementServiceProxy());
            }
            // 6.0
            if (BuildCompat.isM()) {
                addInjector(new IFingerprintManagerProxy());
                addInjector(new IGraphicsStatsProxy());
            }
            // 5.0
            if (BuildCompat.isL()) {
                addInjector(new IJobServiceProxy());
            }
        }
        injectAll();
    }

    public void checkEnv(Class<?> clazz) {
        IInjectHook iInjectHook = mInjectors.get(clazz);
        if (iInjectHook != null && iInjectHook.isBadEnv()) {
            Log.d(TAG, "checkEnv: " + clazz.getSimpleName() + " is bad env");
            iInjectHook.injectHook();
        }
    }

    public void checkAll() {
        for (Class<?> aClass : mInjectors.keySet()) {
            IInjectHook iInjectHook = mInjectors.get(aClass);
            if (iInjectHook != null && iInjectHook.isBadEnv()) {
                Log.d(TAG, "checkEnv: " + aClass.getSimpleName() + " is bad env");
                iInjectHook.injectHook();
            }
        }
    }

    void addInjector(IInjectHook injectHook) {
        mInjectors.put(injectHook.getClass(), injectHook);
    }

    void injectAll() {
        for (IInjectHook value : mInjectors.values()) {
            try {
                Slog.d(TAG, "hook: " + value);
                value.injectHook();
            } catch (Exception e) {
                Slog.d(TAG, "hook error: " + value);
            }
        }
    }
}
