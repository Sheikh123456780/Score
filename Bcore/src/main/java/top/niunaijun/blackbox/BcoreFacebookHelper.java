package top.niunaijun.blackbox;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import java.util.Arrays;

public class BcoreFacebookHelper {
    
    private static final String TAG = "BcoreFBHelper";
    private static BcoreFacebookHelper instance;
    private CallbackManager callbackManager;
    private FacebookLoginListener listener;
    
    public interface FacebookLoginListener {
        void onSuccess(String accessToken, String userId);
        void onCancel();
        void onError(String error);
    }
    
    private BcoreFacebookHelper() {}
    
    public static BcoreFacebookHelper getInstance() {
        if (instance == null) {
            instance = new BcoreFacebookHelper();
        }
        return instance;
    }
    
    public void init(Activity activity) {
        FacebookSdk.sdkInitialize(activity.getApplicationContext());
        AppEventsLogger.activateApp(activity.getApplication());
        callbackManager = CallbackManager.Factory.create();
        Log.d(TAG, "✅ Facebook SDK Initialized in Bcore");
    }
    
    public void login(Activity activity, FacebookLoginListener listener) {
        this.listener = listener;
        
        AccessToken token = AccessToken.getCurrentAccessToken();
        if (token != null && !token.isExpired()) {
            if (listener != null) {
                listener.onSuccess(token.getToken(), token.getUserId());
            }
            return;
        }
        
        LoginManager.getInstance().logInWithReadPermissions(
            activity,
            Arrays.asList("email", "public_profile")
        );
        
        LoginManager.getInstance().registerCallback(callbackManager,
            new FacebookCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult result) {
                    Log.d(TAG, "✅ Facebook Login Success!");
                    AccessToken t = result.getAccessToken();
                    if (listener != null) {
                        listener.onSuccess(t.getToken(), t.getUserId());
                    }
                }
                
                @Override
                public void onCancel() {
                    Log.d(TAG, "❌ Facebook Login Cancelled");
                    if (listener != null) {
                        listener.onCancel();
                    }
                }
                
                @Override
                public void onError(FacebookException error) {
                    Log.e(TAG, "❌ Facebook Login Error", error);
                    if (listener != null) {
                        listener.onError(error.getMessage());
                    }
                }
            }
        );
    }
    
    // ✅ CRITICAL - White Screen Fix
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (callbackManager != null) {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }
    
    public void logout() {
        LoginManager.getInstance().logOut();
    }
}