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
        private var is_False: Boolean = false

        @JvmField
        @Volatile
        var Msg: String = "Ready"

        const val PREFERENCE_NAME: String = "license_cache"
        
        // 🔗 APNA GITHUB RAW URL YAHAN DALO
        var ActivationUrl: String = "https://raw.githubusercontent.com/Sheikh123456780/meta-activation-api/refs/heads/main/api/database.json"
        
        // Status check URL
        var StatusUrl: String = "https://raw.githubusercontent.com/Sheikh123456780/meta-activation-api/refs/heads/main/api/status.json"

        @JvmStatic
        fun getActivatedSdk(): Boolean {
            val serverOnline = GAH()
            if (!serverOnline) {
                Msg = "Server Offline"
                return false
            }
            
            val context = BlackBoxCore.getContext() ?: return false
            val sp = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            val isActivated = sp.getBoolean("activated", false)
            if (!isActivated) {
                Msg = "SDK Not Activated"
                return false
            }

            val expiryStr = sp.getString("expiry", null)
            if (expiryStr == null || expiryStr.isEmpty()) {
                Msg = "Lifetime License"
                return true
            }
            
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val expiryDate = sdf.parse(expiryStr)
                if (expiryDate == null) {
                    Msg = "Invalid Expiry Format"
                    return true
                }
                val currentTime = System.currentTimeMillis()
                val expiryTime = expiryDate.time
                if (currentTime < expiryTime) {
                    val remainingDays = (expiryTime - currentTime) / (1000 * 60 * 60 * 24)
                    Msg = "Licence Valid (${remainingDays} days remaining)"
                    true
                } else {
                    sp.edit().putBoolean("activated", false).apply()
                    Msg = "⚠️ LICENCE EXPIRED on $expiryStr"
                    false
                }
            } catch (e: Exception) {
                Msg = "Expiry Check Error"
                true
            }
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
            if (status == null) return
            try {
                val value = status.equals("online", ignoreCase = true)
                val clazz = Class.forName("android.MetaCore.nk")
                val field = clazz.getDeclaredField("is_False")
                field.isAccessible = true
                field.setBoolean(null, value)
                
                val ctx = BlackBoxCore.getContext()
                if (ctx != null) {
                    val sp = ctx.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                    sp.edit().apply {
                        putString("server_status", status)
                        apply()
                    }
                }
                
                Msg = if (value) {
                    "✅ Server Online"
                } else {
                    "❌ Server $status - Functions Blocked"
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        fun setHidden(value: Boolean) {
            setHidden(if (value) "online" else "offline")
        }

        @JvmStatic
        fun GAH(): Boolean {
            return try {
                val clazz = Class.forName("android.MetaCore.nk")
                val field = clazz.getDeclaredField("is_False")
                field.isAccessible = true
                field.get(null) as? Boolean ?: false
            } catch (_: Exception) {
                false
            }
        }

        @JvmStatic
        fun getUrlHidden(): String {
            return try {
                val clazz = Class.forName("android.MetaCore.nk")
                val field = clazz.getDeclaredField("ActivationUrl")
                field.isAccessible = true
                field.get(null) as? String ?: ActivationUrl
            } catch (_: Exception) {
                ActivationUrl
            }
        }

        @JvmStatic
        fun isSystemApp(): Boolean {
            if (!GAH()) {
                Msg = "❌ Server Offline - Functions Blocked"
                try {
                    AdvancedPopupHelper.showAuto()
                } catch (_: Exception) {}
                return false
            }
            
            val isActivated = getActivatedSdk()
            if (!isActivated) {
                try {
                    AdvancedPopupHelper.showAuto()
                } catch (_: Exception) {}
                return false
            }
            
            Msg = "✅ Server Online & Licence Valid"
            return true
        }
        
        @JvmStatic
        fun checkExpiryManually(): String {
            val context = BlackBoxCore.getContext() ?: return "No context"
            val sp = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            val expiryStr = sp.getString("expiry", null)
            if (expiryStr == null) return "No expiry date"
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val expiryDate = sdf.parse(expiryStr)
                if (expiryDate == null) return "Invalid date"
                val currentTime = System.currentTimeMillis()
                val expiryTime = expiryDate.time
                if (currentTime < expiryTime) {
                    val remaining = expiryTime - currentTime
                    val days = remaining / (1000 * 60 * 60 * 24)
                    val hours = (remaining % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                    "Valid for ${days}d ${hours}h"
                } else {
                    "EXPIRED ${(currentTime - expiryTime) / (1000 * 60 * 60 * 24)} days ago"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
        
        @JvmStatic
        fun loadSavedStatus() {
            try {
                val ctx = BlackBoxCore.getContext() ?: return
                val sp = ctx.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                val savedStatus = sp.getString("server_status", "online")
                if (savedStatus != null) {
                    setHidden(savedStatus)
                }
                getActivatedSdk()
            } catch (_: Exception) {}
        }
    }
}