package android.MetaCore

import android.MetaCore.IRemoteManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.RemoteException
import androidx.core.app.NotificationCompat
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import java.io.File
import top.niunaijun.blackbox.core.env.BEnvironment
import org.json.JSONObject
import org.json.JSONArray
import org.lsposed.lsparanoid.Obfuscate
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Obfuscate
class RemoteManager private constructor() : IRemoteManager.Stub() {

    companion object {
    
         @JvmField
         val JUNIT_JAR = File(BEnvironment.getCacheDir(), "junit.apk")
         
         @JvmField
         val EMPTY_JAR = File(BEnvironment.getCacheDir(), "empty.apk")
        
        private const val TAG = "MetaActivationManager"
        private const val CT = 45000
        private const val RT = 60000
        private const val MAX_RETRIES = 3
        private val exe: ExecutorService = Executors.newSingleThreadExecutor()

        @Volatile
        private var instance: RemoteManager? = null

        @JvmField
        @Volatile
        var sEnableDaemonService: Boolean = true

        @JvmField
        @Volatile
        var sHideRoot: Boolean = true

        @JvmField
        @Volatile
        var sHideXposed: Boolean = true

        @JvmStatic
        fun getInstance(): RemoteManager {
            return instance ?: synchronized(this) {
                instance ?: RemoteManager().also { instance = it }
            }
        }
    }

    private fun iv(u: String?): Boolean {
        return u != null && u.startsWith("https://") && !u.contains(" ") && !u.contains("\"")
    }

    override fun activateSdk(userkey: String?) {
        val bc: String
        try {
            val nc = Class.forName("android.MetaCore.nk")
            val m = nc.getMethod("getUrlHidden")
            bc = m.invoke(null) as String
        } catch (e: Exception) {
            e.printStackTrace()
            nk.Msg = "Error: Failed to get API URL - ${e.message}"
            return
        }

        if (!iv(bc)) {
            nk.Msg = "Error: Invalid API URL format"
            return
        }

        if (userkey == null || userkey.trim().isEmpty()) {
            nk.Msg = "Error: User key cannot be empty"
            return
        }

        exe.execute {
            var retryCount = 0
            var success = false
            var lastError: String? = null
            
            while (retryCount <= MAX_RETRIES && !success) {
                var conn: HttpURLConnection? = null
                try {
                    val ctx = BlackBoxCore.getContext()
                    val pkg = BlackBoxCore.getHostPkg() ?: ""

                    if (pkg.isEmpty()) {
                        nk.Msg = "Error: Package name not found"
                        success = true
                        return@execute
                    }

                    val appName = getAppName(ctx, pkg)
                    val deviceId = deviceId()
                    
                    // GET request for JSON database
                    val requestUrl = "$bc?key=${URLEncoder.encode(userkey, "UTF-8")}&package=${URLEncoder.encode(pkg, "UTF-8")}&device=${URLEncoder.encode(deviceId, "UTF-8")}"
                    
                    conn = URL(bc).openConnection() as HttpURLConnection
                    conn.connectTimeout = CT
                    conn.readTimeout = RT
                    conn.instanceFollowRedirects = true
                    conn.useCaches = false
                    conn.setRequestProperty("Connection", "close")
                    conn.setRequestProperty("User-Agent", "MetaSDK/1.0")
                    conn.requestMethod = "GET"

                    val rc = conn.responseCode
                    
                    if (rc != HttpURLConnection.HTTP_OK) {
                        lastError = "Server Error: Http $rc"
                        nk.setHidden("Offline")
                        nk.Msg = lastError
                        
                        if (rc == 404 || rc == 403) {
                            showNotificationSafe("SDK ACTIVATE FAILED", "SERVER ERROR: $rc")
                            success = true
                            return@execute
                        }
                        
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            nk.Msg = "Server Error: Retrying... ($retryCount/${MAX_RETRIES})"
                            Thread.sleep(3000)
                            continue
                        } else {
                            showNotificationSafe("SDK ACTIVATE FAILED", "SERVER ERROR: $rc")
                            success = true
                            return@execute
                        }
                    }

                    // Read response
                    val res = StringBuilder()
                    try {
                        val inputStream = conn.inputStream
                        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 8192).use { br ->
                            var line: String?
                            while (br.readLine().also { line = it } != null) {
                                res.append(line)
                            }
                        }
                    } catch (ste: SocketTimeoutException) {
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            nk.Msg = "Read Timeout: Retrying... ($retryCount/${MAX_RETRIES})"
                            Thread.sleep(3000)
                            continue
                        } else {
                            throw ste
                        }
                    }

                    if (res.isEmpty()) {
                        lastError = "Empty response from server"
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            nk.Msg = "Empty Response: Retrying... ($retryCount/${MAX_RETRIES})"
                            Thread.sleep(3000)
                            continue
                        } else {
                            nk.setHidden("Offline")
                            nk.Msg = "Error: Empty response from server"
                            showNotificationSafe("SDK ACTIVATE FAILED", "EMPTY RESPONSE")
                            success = true
                            return@execute
                        }
                    }

                    // ===== JSON DATABASE PARSING =====
                    val data = JSONObject(res.toString())
                    val keysArray = data.optJSONArray("keys")
                    
                    if (keysArray == null || keysArray.length() == 0) {
                        nk.setHidden("Offline")
                        nk.Msg = "Error: No keys in database"
                        showNotificationSafe("SDK ACTIVATE FAILED", "DATABASE EMPTY")
                        success = true
                        return@execute
                    }

                    var keyFound = false
                    
                    for (i in 0 until keysArray.length()) {
                        val keyObj = keysArray.getJSONObject(i)
                        
                        if (keyObj.getString("key").trim().equals(userkey.trim(), ignoreCase = true)) {
                            keyFound = true
                            
                            // CHECK BANNED
                            if (keyObj.optBoolean("banned", false)) {
                                nk.setHidden("Offline")
                                nk.Msg = "❌ Key Banned"
                                showNotificationSafe("SDK ACTIVATE FAILED", "KEY BANNED")
                                success = true
                                return@execute
                            }
                            
                            // CHECK EXPIRY
                            val expiry = keyObj.optString("expiry", "")
                            val daemon = keyObj.optInt("daemon", 0)
                            val hideRoot = keyObj.optInt("hide_root", 0)
                            val userName = keyObj.optString("name", "User")
                            
                            if (expiry.isNotEmpty()) {
                                try {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                    val expiryDate = sdf.parse(expiry)
                                    
                                    if (expiryDate != null) {
                                        val currentTime = System.currentTimeMillis()
                                        val expiryTime = expiryDate.time
                                        
                                        if (currentTime > expiryTime) {
                                            // EXPIRED
                                            nk.setHidden("Offline")
                                            nk.Msg = "❌ Key Expired on $expiry"
                                            showNotificationSafe("SDK ACTIVATE FAILED", "KEY EXPIRED")
                                            success = true
                                            return@execute
                                        } else {
                                            // VALID
                                            val remainingDays = (expiryTime - currentTime) / (1000 * 60 * 60 * 24)
                                            nk.Msg = "✅ Welcome $userName (${remainingDays}d remaining)"
                                        }
                                    }
                                } catch (e: Exception) {
                                    nk.Msg = "✅ Welcome $userName (Date parse error)"
                                }
                            } else {
                                // LIFETIME
                                nk.Msg = "✅ Welcome $userName (Lifetime)"
                            }
                            
                            // ===== SUCCESS - SAVE =====
                            nk.setHidden("online")
                            
                            val sp = ctx.getSharedPreferences(nk.PREFERENCE_NAME, Context.MODE_PRIVATE)
                            sp.edit().apply {
                                putBoolean("activated", true)
                                putString("expiry", expiry)
                                putString("user_name", userName)
                                putInt("toggle_feature1", daemon)
                                putInt("toggle_feature2", hideRoot)
                                apply()
                            }
                            
                            // Features set karo
                            isDaemon(daemon == 1)
                            ishideRoot(hideRoot == 1)
                            
                            // Success notification
                            showNotificationSafe("✅ SDK ACTIVATED", "$userName - ${if (expiry.isEmpty()) "Lifetime" else "Expiry: $expiry"}")
                            
                            break
                        }
                    }
                    
                    if (!keyFound) {
                        nk.setHidden("Offline")
                        nk.Msg = "❌ Invalid Key: $userkey"
                        showNotificationSafe("SDK ACTIVATE FAILED", "INVALID KEY")
                    }
                    
                    success = true

                } catch (ste: SocketTimeoutException) {
                    lastError = "Connection timeout"
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        nk.Msg = "Timeout: Retrying... ($retryCount/${MAX_RETRIES})"
                        Thread.sleep(3000)
                        continue
                    } else {
                        nk.setHidden("Offline")
                        nk.Msg = "Error: Connection timeout"
                        showNotificationSafe("SDK ACTIVATE FAILED", "CONNECTION TIMEOUT")
                        success = true
                        return@execute
                    }
                } catch (e: java.net.ConnectException) {
                    lastError = "Cannot connect to server"
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        nk.Msg = "Connect Error: Retrying... ($retryCount/${MAX_RETRIES})"
                        Thread.sleep(3000)
                        continue
                    } else {
                        nk.setHidden("Offline")
                        nk.Msg = "Error: Cannot connect to server"
                        showNotificationSafe("SDK ACTIVATE FAILED", "NO CONNECTION")
                        success = true
                        return@execute
                    }
                } catch (e: java.net.UnknownHostException) {
                    lastError = "Invalid server URL"
                    nk.setHidden("Offline")
                    nk.Msg = "Error: Invalid server URL"
                    showNotificationSafe("SDK ACTIVATE FAILED", "INVALID URL")
                    success = true
                    return@execute
                } catch (e: Exception) {
                    lastError = e.message ?: "Unknown error"
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        nk.Msg = "Error: Retrying... ($retryCount/${MAX_RETRIES})"
                        Thread.sleep(3000)
                        continue
                    } else {
                        nk.setHidden("Offline")
                        nk.Msg = "Unexpected Error: ${e.message}"
                        showNotificationSafe("SDK ACTIVATE FAILED", "ERROR")
                        success = true
                        return@execute
                    }
                } finally {
                    try {
                        conn?.disconnect()
                    } catch (e: Exception) {}
                }
            }
            
            if (!success) {
                nk.setHidden("Offline")
                val finalError = lastError ?: "Unknown error"
                nk.Msg = "Failed after $MAX_RETRIES attempts: $finalError"
                showNotificationSafe("SDK ACTIVATE FAILED", "MAX RETRIES EXCEEDED")
            }
        }
    }

    override fun getActivatedSdk(): Boolean {
        return try {
            val result = nk.getActivatedSdk()
            nk.Msg = if (result) "✅ SDK IS ACTIVATED" else "❌ SDK IS NOT ACTIVATED"
            result
        } catch (e: Exception) {
            nk.Msg = "ERROR: FAILED TO GET ACTIVATE STATUS"
            false
        }
    }

    override fun getServerMessage(): String {
        return try {
            val msg = nk.getServerMessage()
            if (msg.isNullOrEmpty()) "No server message" else msg
        } catch (e: Exception) {
            "Error: Failed to get server message"
        }
    }

    override fun getNetwork(): Boolean {
        return try {
            val net = nk.isSystemApp()
            nk.Msg = if (net) "✅ Network: Connected" else "❌ Network: Disconnected"
            net
        } catch (e: Exception) {
            nk.Msg = "Error: Failed to check network status"
            false
        }
    }

    private fun deviceId(): String {
        return try {
            val ctx = BlackBoxCore.getContext()
            android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getAppName(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun isDaemon(d: Boolean) {
        sEnableDaemonService = d
        nk.Msg = if (d) "Daemon: ENABLED" else "Daemon: DISABLED"
    }

    private fun ishideRoot(h: Boolean) {
        sHideRoot = h
        nk.Msg = if (h) "Root Hide: ENABLED" else "Root Hide: DISABLED"
    }

    private fun showNotificationSafe(title: String, message: String) {
        try {
            val ctx = BlackBoxCore.getContext()
            showNotification(ctx, title, message)
        } catch (_: Throwable) {}
    }

    private val CHANNEL_ID = "meta_sdk_updates"
    private val CHANNEL_NAME = "Meta SDK Updates"

    private fun showNotification(ctx: Context, title: String, msg: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            ch.description = "SDK ACTIVATE OR UPDATE NOTIFICATIONS"
            ch.enableLights(true)
            ch.lightColor = Color.BLUE
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val nb = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        nm.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), nb.build())
    }
    
    fun showServerNotification(title: String, msg: String, type: String) {
        try {
            val ctx = BlackBoxCore.getContext()
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = "meta_server"
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(ch, "SERVER", NotificationManager.IMPORTANCE_HIGH))
            }
            val t = type.lowercase()
            val icon = when {
                t.contains("warn") || t.contains("alert") -> android.R.drawable.stat_sys_warning
                t.contains("event") -> android.R.drawable.star_big_on
                t.contains("update") -> android.R.drawable.stat_sys_download_done
                else -> android.R.drawable.ic_dialog_info
            }
            nm.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(ctx, ch)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(msg)
                .setColor(Color.CYAN)
                .setAutoCancel(true)
                .build())
        } catch (_: Exception) {}
    }
    
    fun showImageNotification(title: String, msg: String, img: String, base: String) {
        exe.execute {
            try {
                if (img.isEmpty()) return@execute
                val url = if (base.isNotEmpty()) "$base/$img" else img
                val bmp = BitmapFactory.decodeStream(URL(url).openStream())
                val ctx = BlackBoxCore.getContext()
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val ch = "meta_img"
                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(NotificationChannel(ch, "IMG", NotificationManager.IMPORTANCE_HIGH))
                }
                nm.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(ctx, ch)
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setContentTitle(title)
                    .setContentText(msg)
                    .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp))
                    .setAutoCancel(true)
                    .build())
            } catch (_: Exception) {}
        }
    }
}