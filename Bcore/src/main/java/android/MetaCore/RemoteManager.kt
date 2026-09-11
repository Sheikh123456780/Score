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

        private val exe: ExecutorService =
            Executors.newSingleThreadExecutor()

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
                instance ?: RemoteManager().also {
                    instance = it
                }
            }
        }
    }

    private fun iv(u: String?): Boolean {
        return u != null &&
                u.startsWith("https://") &&
                !u.contains(" ") &&
                !u.contains("\"")
    }

    override fun activateSdk(userkey: String?) {
        // ✅ NO LICENSE CHECK - Always activated
        val ctx = BlackBoxCore.getContext() ?: return

        val sp = ctx.getSharedPreferences(
            nk.PREFERENCE_NAME,
            Context.MODE_PRIVATE
        )

        // Save activated state with no expiry
        sp.edit().apply {
            putBoolean("activated", true)
            putString("expiry", "")
            putInt("toggle_expiry", 0)
            putInt("toggle_feature1", 1)
            putInt("toggle_feature2", 1)
            apply()
        }

        nk.setHidden("online")
        nk.Msg = "✅ SDK Activated Successfully (No License Check)"

        // These properties belong to RemoteManager.
        // They must NOT be accessed through nk.
        sEnableDaemonService = true
        sHideRoot = true
        sHideXposed = true

        showNotificationSafe(
            "✅ SDK ACTIVATED",
            "License check disabled - Full access granted"
        )
    }

    override fun getActivatedSdk(): Boolean {
        // ✅ ALWAYS RETURN TRUE - No check
        nk.setHidden("online")
        nk.Msg = "✅ SDK IS ACTIVATED (No License Check)"
        return true
    }

    override fun getServerMessage(): String {
        return try {
            val msg = nk.getServerMessage()

            if (msg.isNullOrEmpty()) {
                "No server message"
            } else {
                msg
            }
        } catch (e: Exception) {
            "Error: Failed to get server message"
        }
    }

    override fun getNetwork(): Boolean {
        // ✅ ALWAYS RETURN TRUE - No check
        nk.Msg = "✅ Network: Connected (No Check)"
        return true
    }

    private fun deviceId(): String {
        return try {
            val ctx = BlackBoxCore.getContext()

            android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getAppName(
        ctx: Context,
        pkg: String
    ): String {
        return try {
            val pm = ctx.packageManager
            val info = pm.getApplicationInfo(pkg, 0)

            pm.getApplicationLabel(info).toString()

        } catch (e: Exception) {
            pkg
        }
    }

    private fun isDaemon(d: Boolean) {
        if (d) {
            nk.Msg = "Daemon: ENABLED"
            sEnableDaemonService = true
        } else {
            nk.Msg = "Daemon: DISABLED"
            sEnableDaemonService = false
        }
    }

    private fun ishideRoot(h: Boolean) {
        if (h) {
            nk.Msg = "Root Hide: ENABLED"
            sHideRoot = true
        } else {
            nk.Msg = "Root Hide: DISABLED"
            sHideRoot = false
        }
    }

    // ---------------- Notification Helpers ----------------

    private fun showNotificationSafe(
        title: String,
        message: String
    ) {
        try {
            val ctx = BlackBoxCore.getContext()
            showNotification(
                ctx,
                title,
                message
            )
        } catch (_: Throwable) {
        }
    }

    private val CHANNEL_ID = "meta_sdk_updates"
    private val CHANNEL_NAME = "Meta SDK Updates"

    private fun showNotification(
        ctx: Context,
        title: String,
        msg: String
    ) {
        val nm =
            ctx.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val ch = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )

            ch.description =
                "SDK ACTIVATE OR UPDATE NOTIFICATIONS"

            ch.enableLights(true)
            ch.lightColor = Color.BLUE
            ch.enableVibration(true)

            nm.createNotificationChannel(ch)
        }

        val nb = NotificationCompat.Builder(
            ctx,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.stat_notify_more
            )
            .setContentTitle(title)
            .setContentText(msg)
            .setPriority(
                NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)

        nm.notify(
            (System.currentTimeMillis() and 0x7fffffff).toInt(),
            nb.build()
        )
    }

    // ================= NOTIFICATIONS =================

    private fun showServerNotification(
        title: String,
        msg: String,
        type: String
    ) {
        val ctx = BlackBoxCore.getContext()

        val nm =
            ctx.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val ch = "meta_server"

        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    ch,
                    "SERVER",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val t = type.lowercase()

        val icon = when {
            t.contains("warn") ||
                    t.contains("alert") ->
                android.R.drawable.stat_sys_warning

            t.contains("event") ->
                android.R.drawable.star_big_on

            t.contains("update") ->
                android.R.drawable.stat_sys_download_done

            else ->
                android.R.drawable.ic_dialog_info
        }

        nm.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(ctx, ch)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(msg)
                .setColor(Color.CYAN)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun showImageNotification(
        title: String,
        msg: String,
        img: String,
        base: String
    ) {
        exe.execute {
            try {

                if (img.isEmpty()) {
                    return@execute
                }

                val url =
                    if (base.isNotEmpty()) {
                        "$base/$img"
                    } else {
                        img
                    }

                val bmp =
                    BitmapFactory.decodeStream(
                        URL(url).openStream()
                    )

                val ctx =
                    BlackBoxCore.getContext()

                val nm =
                    ctx.getSystemService(
                        Context.NOTIFICATION_SERVICE
                    ) as NotificationManager

                val ch = "meta_img"

                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            ch,
                            "IMG",
                            NotificationManager.IMPORTANCE_HIGH
                        )
                    )
                }

                nm.notify(
                    System.currentTimeMillis().toInt(),
                    NotificationCompat.Builder(ctx, ch)
                        .setSmallIcon(
                            android.R.drawable.sym_def_app_icon
                        )
                        .setContentTitle(title)
                        .setContentText(msg)
                        .setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(bmp)
                        )
                        .setAutoCancel(true)
                        .build()
                )

            } catch (_: Exception) {
            }
        }
    }
}
