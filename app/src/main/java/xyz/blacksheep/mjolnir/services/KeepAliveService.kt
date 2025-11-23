package xyz.blacksheep.mjolnir.services

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import xyz.blacksheep.mjolnir.HomeKeyInterceptorService
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.MainActivity
import xyz.blacksheep.mjolnir.MjolnirApp
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.R
import xyz.blacksheep.mjolnir.utils.DiagnosticsConfig
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.DualScreenshotManager

/**
 * A foreground service responsible for keeping the Mjolnir application process alive.
 *
 * **Why is this needed?**
 * Android aggressively kills background processes to save memory. If the Mjolnir process dies:
 * 1. The in-memory icon cache (`AppQueryHelper.iconCache`) is lost, causing lag on the next launch.
 * 2. The Accessibility Service (`HomeKeyInterceptorService`) might be restarted or delayed.
 * 3. The responsiveness of the Home button interception drops.
 *
 * **Key Behaviors:**
 * - Starts as a Foreground Service with a persistent notification.
 * - Updates the notification text dynamically based on app state (e.g., "Home button capture ENABLED").
 * - Uses [AlarmManager] to auto-restart itself if the task is swiped away (see [onTaskRemoved]).
 * - Listens to SharedPreferences to update the notification UI immediately when settings change.
 * - Provides an action button to trigger Dual Screenshot (v0.2.5b).
 * - Handles deletion of screenshots (v0.2.5b).
 */
class KeepAliveService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        DiagnosticsLogger.logEvent("Service", "KEEPALIVE_STARTED", context = this)
        startForegroundInternal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        // Handle Dual Screenshot Action
        if (action == ACTION_DUAL_SCREENSHOT) {
            DiagnosticsLogger.logEvent("Service", "ACTION_DUAL_SCREENSHOT_RECEIVED", context = this)
            DualScreenshotManager.start(this)
            // Re-post the notification to ensure it remains consistent
            startForegroundInternal()
            return START_STICKY
        }

        // Handle Delete Screenshot Action
        if (action == ACTION_DELETE_SCREENSHOT) {
            val uri = intent?.data
            val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1
            
            if (uri != null) {
                try {
                    contentResolver.delete(uri, null, null)
                    if (notificationId != -1) {
                        getSystemService(NotificationManager::class.java).cancel(notificationId)
                    }
                    Toast.makeText(this, "Screenshot deleted", Toast.LENGTH_SHORT).show()
                    DiagnosticsLogger.logEvent("Service", "SCREENSHOT_DELETED", "uri=$uri", this)
                } catch (e: Exception) {
                    DiagnosticsLogger.logException("Service", e, this)
                    Toast.makeText(this, "Failed to delete screenshot", Toast.LENGTH_SHORT).show()
                }
            }
            // We don't need to update the foreground notification for this action
            return START_STICKY
        }

        // Service does no work; it only exists to keep the process alive.
        // START_STICKY ensures it restarts if the system kills it.
        // We also update notification here in case service was restarted or state changed
        startForegroundInternal()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Not a bound service.
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        DiagnosticsLogger.logEvent("Service", "KEEPALIVE_STOPPED", context = this)
    }

    /**
     * Called if the user swipes the app away from the "Recents" menu.
     * We schedule an immediate restart via AlarmManager to ensure persistence.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        DiagnosticsLogger.logEvent("Service", "KEEPALIVE_TASK_REMOVED", "reason=clear_all", this)
        
        try {
            val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
            restartIntent.setPackage(packageName)
            
            // Use getForegroundService on O+ to ensure we can restart from background
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE
                )
            }
            
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Use set() instead of setExact() to avoid SecurityException on Android 12+ 
            // if SCHEDULE_EXACT_ALARM permission is missing.
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 2000, // 2s delay
                pendingIntent
            )
        } catch (e: Exception) {
            DiagnosticsLogger.logException("Service", e, this)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Update notification if relevant keys change
        if (key == KEY_HOME_INTERCEPTION_ACTIVE || 
            key == KEY_TOP_APP || 
            key == KEY_BOTTOM_APP ||
            key == xyz.blacksheep.mjolnir.utils.KEY_DIAGNOSTICS_ENABLED) {
            
            startForegroundInternal()
        }
    }

    /**
     * Builds and posts the persistent notification that anchors this service.
     * The notification text reflects the current status of the Home Button Interceptor.
     */
    private fun startForegroundInternal() {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        // Dual Screenshot Action Intent
        val dssIntent = Intent(this, KeepAliveService::class.java).apply {
            action = ACTION_DUAL_SCREENSHOT
        }
        val dssPendingIntent = PendingIntent.getService(
            this, 2, dssIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Gather state
        val tileEnabled = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
        val serviceRunning = isAccessibilityServiceEnabled(this)
        val topApp = prefs.getString(KEY_TOP_APP, null)
        val bottomApp = prefs.getString(KEY_BOTTOM_APP, null)
        val diagnosticsEnabled = DiagnosticsConfig.isEnabled(this)
        
        // Determine if home config is complete (at least one app set)
        val homeConfigComplete = (topApp != null || bottomApp != null)

        // Determine Title
        val contentTitle = when {
            !homeConfigComplete -> "Home app not configured."
            tileEnabled && serviceRunning -> "Home button capture ENABLED."
            !serviceRunning -> "Home button capture service DISABLED. Tap to open Mjolnir."
            !tileEnabled && serviceRunning -> "Home button capture DISABLED."
            else -> "Mjolnir Service Active" // Fallback
        }

        // Determine Text
        val contentText = if (diagnosticsEnabled) {
            "Diagnostic data is being collected locally. Tap to open Mjolnir."
        } else {
            "Tap to open Mjolnir."
        }

        val notification: Notification = NotificationCompat.Builder(this, MjolnirApp.PERSISTENT_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_home)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW) // legacy devices
            .addAction(R.drawable.ic_home, "Dual Screenshot", dssPendingIntent)
            .setSilent(true)
            .build()

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                0 // No special foreground type; we don't use location/camera/etc.
            )
            // Only log if this is a fresh start or significant update to avoid spamming logs on every pref change if we called this more often
            DiagnosticsLogger.logEvent("Service", "KEEPALIVE_NOTIFICATION_SHOWN", "title=\"$contentTitle\"", this)
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("Error", "KEEPALIVE_NOTIFICATION_FAILED", "message=${e.message}", this)
        }
    }

    private fun stopForegroundInternal() {
        // Remove notification and stop service.
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            DiagnosticsLogger.logEvent("Service", "KEEPALIVE_NOTIFICATION_REMOVED", "reason=self_initiated", this)
            stopSelf()
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("Error", "KEEPALIVE_STOP_FAILED", "message=${e.message}", this)
        }
    }

    /**
     * Checks if the Mjolnir HomeKeyInterceptorService is actually enabled in System Settings.
     */
    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedComponentName = ComponentName(context, HomeKeyInterceptorService::class.java)

        for (service in enabledServices) {
            val serviceComponentName = ComponentName(service.resolveInfo.serviceInfo.packageName, service.resolveInfo.serviceInfo.name)
            if (serviceComponentName == expectedComponentName) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_DUAL_SCREENSHOT = "xyz.blacksheep.mjolnir.ACTION_DUAL_SCREENSHOT"
        const val ACTION_DELETE_SCREENSHOT = "xyz.blacksheep.mjolnir.ACTION_DELETE_SCREENSHOT"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
