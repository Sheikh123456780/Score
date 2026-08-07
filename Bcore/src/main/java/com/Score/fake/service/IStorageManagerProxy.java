package com.Score.fake.service;

import android.os.IInterface;
import android.os.storage.StorageVolume;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import black.android.os.mount.BRIMountServiceStub;
import black.android.os.storage.BRIStorageManagerStub;
import com.Score.ScoreCore;
import com.Score.app.BActivityThread;
import com.Score.fake.hook.BinderInvocationStub;
import com.Score.fake.hook.MethodHook;
import com.Score.fake.hook.ProxyMethod;
import com.Score.utils.Slog;
import com.Score.utils.compat.BuildCompat;

/**
 * Created by Milk on 4/10/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IStorageManagerProxy extends BinderInvocationStub {

  public IStorageManagerProxy() {
    super(BRServiceManager.get().getService("mount"));
  }

  @Override
  protected Object getWho() {
    IInterface mount;
    if (BuildCompat.isOreo()) {
      mount = BRIStorageManagerStub.get().asInterface(BRServiceManager.get().getService("mount"));
    } else {
      mount = BRIMountServiceStub.get().asInterface(BRServiceManager.get().getService("mount"));
    }
    return mount;
  }

  @Override
  protected void inject(Object baseInvocation, Object proxyInvocation) {
    replaceSystemService("mount");
  }

  @Override
  public boolean isBadEnv() {
    return false;
  }

  @ProxyMethod("fixupAppDir")
  public static class FixupAppDir extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      Slog.e(TAG, "fixupAppDir");
      if (args != null) {
        for (Object o : args) {
          Slog.e(TAG, "args=" + o);
        }
      }
      return method.invoke(who, args);
    }
  }

  @ProxyMethod("getVolumeList")
  public static class GetVolumeList extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      if (args == null) {
        StorageVolume[] volumeList = ScoreCore.getBStorageManager().getVolumeList(BActivityThread.getBUid(), null, 0, BActivityThread.getUserId());
        if (volumeList == null) {
          return method.invoke(who, args);
        }
        return volumeList;
      }
      try {
        int uid = (int) args[0];
        String packageName = (String) args[1];
        int flags = (int) args[2];
        int userId = BActivityThread.getUserId();
        
        // Handle Android 11+ (API 30+) which may have additional parameters
        if (args.length > 3) {
          // Some Android versions pass userId as 4th parameter
          userId = (int) args[3];
        }
        
        StorageVolume[] volumeList = ScoreCore.getBStorageManager().getVolumeList(uid, packageName, flags, userId);
        if (volumeList == null) {
          return method.invoke(who, args);
        }
        return volumeList;
      } catch (Throwable t) {
        return method.invoke(who, args);
      }
    }
  }

  @ProxyMethod("mkdirs")
  public static class mkdirs extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      return 0;
    }
  }

  // Android 13+ (API 33+) - Added method
  @ProxyMethod("getStorageUserIds")
  public static class GetStorageUserIds extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      try {
        // Return current user ID for Android 13+
        return new int[]{BActivityThread.getUserId()};
      } catch (Throwable t) {
        return method.invoke(who, args);
      }
    }
  }

  // Android 11+ (API 30+) - Added method for scoped storage
  @ProxyMethod("allocateDiskBytes")
  public static class AllocateDiskBytes extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      // Allow disk allocation for virtual environment
      return method.invoke(who, args);
    }
  }
}
