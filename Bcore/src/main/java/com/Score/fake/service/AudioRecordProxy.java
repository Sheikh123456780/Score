package com.Score.fake.service;

import android.media.AudioRecord;

import java.lang.reflect.Method;

import com.Score.fake.hook.ClassInvocationStub;
import com.Score.fake.hook.MethodHook;
import com.Score.fake.hook.ProxyMethod;
import com.Score.fake.hook.ScanClass;
import com.Score.utils.Slog;

@ScanClass(AudioRecordProxy.class)
public class AudioRecordProxy extends ClassInvocationStub {
    public static final String TAG = "AudioRecordProxy";

    @Override
    protected Object getWho() {
        return AudioRecord.class;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("startRecording")
    public static class StartRecording extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.d(TAG, "AudioRecord.startRecording() - Allowing");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("stop")
    public static class Stop extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }
}
