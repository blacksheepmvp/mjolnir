package xyz.blacksheep.mjolnir

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.utils.AppQueryHelper
import xyz.blacksheep.mjolnir.utils.DiagnosticsConfig
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import java.io.File

class MjolnirApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createPersistentNotificationChannel()

        // Initialize Diagnostics
        DiagnosticsLogger.init(this)
        if (DiagnosticsConfig.isEnabled(this)) {
            DiagnosticsLogger.logHeader(this)
        }

        // Only prewarm icons in the main UI process.
        // The keepalive process should stay as lightweight as possible.
        if (!isKeepAliveProcess()) {
            CoroutineScope(Dispatchers.IO).launch {
                DiagnosticsLogger.logEvent("App", "ICON_PREWARM_START", "totalApps=N/A", this@MjolnirApp)
                val startTime = System.currentTimeMillis()
                try {
                    AppQueryHelper.prewarmAllApps(this@MjolnirApp)
                    val duration = System.currentTimeMillis() - startTime
                    DiagnosticsLogger.logEvent("App", "ICON_PREWARM_END", "durationMs=$duration totalApps=N/A", this@MjolnirApp)
                } catch (e: Exception) {
                    DiagnosticsLogger.logException("App", e, this@MjolnirApp)
                }
            }
        }
    }

    private fun isKeepAliveProcess(): Boolean {
        return try {
            val cmdline = File("/proc/self/cmdline").readText().trim { it <= ' ' }
            cmdline.endsWith(":keepalive")
        } catch (e: Exception) {
            false
        }
    }

    private fun createPersistentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PERSISTENT_CHANNEL_ID,
                "Mjolnir Persistent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Mjolnir active so home key interception remains available."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val PERSISTENT_CHANNEL_ID = "mjolnir_persistent_channel"
    }
}