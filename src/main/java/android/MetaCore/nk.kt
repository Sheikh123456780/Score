package android.MetaCore

import android.content.Context
import android.os.Handler
import android.os.Looper
import top.niunaijun.blackbox.BlackBoxCore
import org.lsposed.lsparanoid.Obfuscate
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast

@Obfuscate
class nk {

    companion object {
        @Volatile
        private var is_False: Boolean = true  // ✅ Always true (online)

        @JvmField
        @Volatile
        var Msg: String = "Ready"

        const val PREFERENCE_NAME: String = "license_cache"
        var ActivationUrl: String = "https://api-box-pannel.vercel.app/api/connect"

        @JvmStatic
        fun getActivatedSdk(): Boolean {
            // ✅ ALWAYS RETURN TRUE - No checks at all
            Msg = "✅ SDK Activated (No License Check)"
            return true
        }

        @JvmStatic
        fun getServerMessage(): String {
            return Msg
        }

        @JvmStatic
        fun ismsg(msg: String?) {
            if (msg == null) return
            val ctx = BlackBoxCore.getContext() ?: return
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                } catch (_: Exception) {}
            }
        }

        @JvmStatic
        fun setHidden(status: String?) {
            // ✅ Always online
            is_False = true
            Msg = "✅ Server Online"
        }

        @JvmStatic
        fun setHidden(value: Boolean) {
            is_False = true
            Msg = "✅ Server Online"
        }

        @JvmStatic
        fun GAH(): Boolean {
            // ✅ ALWAYS RETURN TRUE - Server always online
            return true
        }

        @JvmStatic
        fun getUrlHidden(): String {
            return "https://api-box-pannel.vercel.app/api/connect"
        }

        @JvmStatic
        fun 获取接口地址(): String {
            return "https://api-box-pannel.vercel.app/api/connect"
        }
        
        @JvmStatic
        fun isSystemApp(): Boolean {
            // ✅ ALWAYS RETURN TRUE - No checks
            Msg = "✅ Full Access Granted (No License Check)"
            return true
        }
        
        @JvmStatic
        fun checkExpiryManually(): String {
            return "No expiry - Unlimited access"
        }
        
        @JvmStatic
        fun loadSavedStatus() {
            // ✅ No need to load anything - always active
            is_False = true
            Msg = "✅ SDK Active (No License Check)"
        }
    }
}