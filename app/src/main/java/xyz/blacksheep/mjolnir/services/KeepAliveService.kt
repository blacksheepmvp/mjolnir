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
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
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

    private fun startForegroundInternal() {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        // Gather state
        val tileEnabled = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
        val serviceRunning = isAccessibilityServiceEnabled(this)
        val topApp = prefs.getString(KEY_TOP_APP, null)
        val bottomApp = prefs.getString(KEY_BOTTOM_APP, null)
        val diagnosticsEnabled = DiagnosticsConfig.isEnabled(this)
        val homeConfigComplete = (topApp != null || bottomApp != null)

        // Determine Title
        val contentTitle = when {
            !homeConfigComplete -> "Top/bottom home not configured."
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
            // But here we call it on pref change so it might be frequent.
            // The spec says: "Notification shown" logging is required.
            // We will stick to the existing requirement.
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
    }
}