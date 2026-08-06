package com.Score.fake.service;

import android.media.MediaRecorder;

import java.lang.reflect.Method;

import com.Score.fake.hook.ClassInvocationStub;
import com.Score.fake.hook.MethodHook;
import com.Score.fake.hook.ProxyMethod;
import com.Score.fake.hook.ScanClass;
import com.Score.utils.Slog;

@ScanClass(MediaRecorderProxy.class)
public class MediaRecorderProxy extends ClassInvocationStub {
    public static final String TAG = "MediaRecorderProxy";

    @Override
    protected Object getWho() {
        return MediaRecorder.class;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("start")
    public static class Start extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.d(TAG, "MediaRecorder.start() - Allowing");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setAudioSource")
    public static class SetAudioSource extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.d(TAG, "setAudioSource: " + args[0]);
            return method.invoke(who, args);
        }
    }
}
