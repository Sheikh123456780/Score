package com.Score.fake.hook;

import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.util.Map;

import black.android.os.BRServiceManager;
import com.Score.utils.compat.BuildCompat;

/**
 * Updated for Android 9 (API 28) to Android 17 (API 37) Compatibility
 */
public abstract class BinderInvocationStub extends ClassInvocationStub implements IBinder {
    private IBinder mBaseBinder;

    public BinderInvocationStub(IBinder baseBinder) {
        mBaseBinder = baseBinder;
    }

    @Override
    protected void onBindMethod() {
    }

    @Nullable
    @Override
    public String getInterfaceDescriptor() throws RemoteException {
        if (mBaseBinder == null) return null;
        try {
            return mBaseBinder.getInterfaceDescriptor();
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    public boolean pingBinder() {
        if (mBaseBinder == null) return false;
        try {
            return mBaseBinder.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isBinderAlive() {
        if (mBaseBinder == null) return false;
        try {
            return mBaseBinder.isBinderAlive();
        } catch (Throwable t) {
            return false;
        }
    }

    @Nullable
    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        return (IInterface) getProxyInvocation();
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, @Nullable String[] args) throws RemoteException {
        if (mBaseBinder != null) {
            try {
                mBaseBinder.dump(fd, args);
            } catch (RemoteException e) {
                // Ignore
            }
        }
    }

    @Override
    public void dumpAsync(@NonNull FileDescriptor fd, @Nullable String[] args) throws RemoteException {
        if (mBaseBinder != null) {
            try {
                mBaseBinder.dumpAsync(fd, args);
            } catch (RemoteException e) {
                // Ignore
            }
        }
    }

    @Override
    public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        if (mBaseBinder == null) return false;
        try {
            return mBaseBinder.transact(code, data, reply, flags);
        } catch (RemoteException e) {
            throw e;
        }
    }

    @Override
    public void linkToDeath(@NonNull DeathRecipient recipient, int flags) throws RemoteException {
        if (mBaseBinder != null) {
            try {
                mBaseBinder.linkToDeath(recipient, flags);
            } catch (RemoteException e) {
                throw e;
            }
        }
    }

    @Override
    public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
        if (mBaseBinder == null) return false;
        try {
            return mBaseBinder.unlinkToDeath(recipient, flags);
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================
    // replaceSystemService with Android 14+ Safety
    // ============================================================
    protected void replaceSystemService(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        
        try {
            Map<String, IBinder> services = BRServiceManager.get().sCache();
            if (services != null) {
                services.put(name, this);
            }
        } catch (Throwable t) {
            // Silent fail for Android 14+
            try {
                // Try alternative method for Android 14+
                Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
                java.lang.reflect.Method addService = serviceManagerClass.getMethod("addService", String.class, IBinder.class);
                addService.invoke(null, name, this);
            } catch (Throwable ignored) {
                // Ignore
            }
        }
    }

    protected IBinder getBaseBinder() {
        return mBaseBinder;
    }
    
    protected void setBaseBinder(IBinder baseBinder) {
        this.mBaseBinder = baseBinder;
    }
}
