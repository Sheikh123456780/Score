package com.Score.fake.service;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

import java.lang.reflect.Method;

import black.android.media.BRAudioManager;
import black.android.media.BRIAudioServiceStub;
import black.android.os.BRServiceManager;
import com.Score.fake.hook.BinderInvocationStub;
import com.Score.fake.hook.MethodHook;
import com.Score.fake.hook.ProxyMethod;
import com.Score.utils.MethodParameterUtils;
import com.Score.utils.Slog;

public class IAudioManagerProxy extends BinderInvocationStub {
    public static final String TAG = "AudioManagerProxy";

    public IAudioManagerProxy() {
        super(BRServiceManager.get().getService(Context.AUDIO_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIAudioServiceStub.get().asInterface(BRServiceManager.get().getService(Context.AUDIO_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            BRAudioManager.get()._set_sService(proxyInvocation);
        }
        replaceSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("setMicrophoneMute")
    public static class SetMicrophoneMute extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            MethodParameterUtils.replaceLastUserId(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("isMicrophoneMute")
    public static class IsMicrophoneMute extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return false;
        }
    }

    @ProxyMethod("adjustStreamVolume")
    public static class AdjustStreamVolume extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setStreamVolume")
    public static class SetStreamVolume extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setMode")
    public static class SetMode extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getMode")
    public static class GetMode extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return AudioManager.MODE_NORMAL;
        }
    }

    @ProxyMethod("requestAudioFocus")
    public static class RequestAudioFocus extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("abandonAudioFocus")
    public static class AbandonAudioFocus extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setSpeakerphoneOn")
    public static class SetSpeakerphoneOn extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("isSpeakerphoneOn")
    public static class IsSpeakerphoneOn extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return false;
        }
    }

    @ProxyMethod("setBluetoothScoOn")
    public static class SetBluetoothScoOn extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("startBluetoothSco")
    public static class StartBluetoothSco extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("stopBluetoothSco")
    public static class StopBluetoothSco extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("isBluetoothScoOn")
    public static class IsBluetoothScoOn extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return false;
        }
    }

    @ProxyMethod("isStreamMute")
    public static class IsStreamMute extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int streamType = (int) args[0];
            if (streamType == 0 || streamType == 3) {
                return false;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("isWiredHeadsetOn")
    public static class IsWiredHeadsetOn extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return false;
        }
    }
}
