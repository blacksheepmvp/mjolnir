package xyz.blacksheep.mjolnir.services

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import xyz.blacksheep.mjolnir.EXTRA_SOURCE_URI
import xyz.blacksheep.mjolnir.HomeKeyInterceptorService
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_DSS_AUTO_STITCH
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
 * - Watches MediaStore for system screenshots to trigger Auto-Stitch DSS (v0.2.5d).
 */
class KeepAliveService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private var screenshotObserver: ScreenshotObserver? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        DiagnosticsLogger.logEvent("Service", "KEEPALIVE_STARTED", context = this)
        startForegroundInternal()

        refreshDssState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val extras = intent?.extras?.keySet()?.joinToString(",") ?: "none"
        DiagnosticsLogger.logEvent("KeepAlive", "ON_START_COMMAND", "action=$action extras=[$extras]", this)

        // Handle explicit refresh request
        if (action == ACTION_REFRESH_OBSERVER) {
            DiagnosticsLogger.logEvent("Service", "ACTION_REFRESH_OBSERVER_RECEIVED", context = this)
            refreshDssState()
        }

        // Handle explicit status update request (from HomeKeyInterceptorService)
        if (action == ACTION_UPDATE_STATUS) {
            DiagnosticsLogger.logEvent("Service", "ACTION_UPDATE_STATUS_RECEIVED", context = this)
            startForegroundInternal()
        }
        
        // Handle Dual Screenshot Action (Manual Trigger)
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
        unregisterScreenshotObserver()
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
        
        // Re-register observer if DSS setting changed (to ensure permissions are picked up)
        if (key == KEY_DSS_AUTO_STITCH) {
            refreshDssState()
        }
    }

    private fun refreshDssState() {
        // Reload from disk to ensure we have the latest committed value from DssPermissionActivity
        // Although shared prefs are usually cached in memory, reload() forces a check
        // if multiple processes were involved (unlikely here, but safe).
        // However, SharedPreferences are single-process singleton mostly.
        
        val dssEnabled = prefs.getBoolean(KEY_DSS_AUTO_STITCH, false)
        val hasPermission = hasStoragePermission()
        DiagnosticsLogger.logEvent("KeepAlive", "REFRESH_DSS_STATE", "dssEnabled=$dssEnabled permission=$hasPermission", this)
        
        if (dssEnabled && hasPermission) {
             DiagnosticsLogger.logEvent("Service", "DSS_STATE_REFRESH", "status=ACTIVE", this)
             // Only re-register if not already registered to avoid churn? 
             // Actually, registerScreenshotObserver handles idempotency by unregistering first.
             registerScreenshotObserver()
        } else {
             DiagnosticsLogger.logEvent("Service", "DSS_STATE_REFRESH", "status=INACTIVE dss=$dssEnabled perm=$hasPermission", this)
             unregisterScreenshotObserver()
        }
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
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

        DiagnosticsLogger.logEvent("KeepAlive", "START_FOREGROUND_INTERNAL", "tile=$tileEnabled service=$serviceRunning", this)
        
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
        val expectedComponentName = ComponentName(context, HomeKeyInterceptorService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun registerScreenshotObserver() {
        DiagnosticsLogger.logEvent("KeepAlive", "REGISTER_OBSERVER_START", null, this)
        // Always create a fresh observer to ensure the Handler and Context are valid.
        // If one exists, unregister it first.
        unregisterScreenshotObserver()
        
        DiagnosticsLogger.logEvent("KeepAlive", "REGISTER_OBSERVER_NEW_INSTANCE", null, this)
        screenshotObserver = ScreenshotObserver(Handler(Looper.getMainLooper()))
        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
            DiagnosticsLogger.logEvent("Service", "OBSERVER_REGISTERED", context = this)
        } catch (e: Exception) {
            DiagnosticsLogger.logException("Service", e, this)
        }
    }

    private fun unregisterScreenshotObserver() {
        DiagnosticsLogger.logEvent("KeepAlive", "UNREGISTER_OBSERVER", "existing=${screenshotObserver != null}", this)
        try {
            screenshotObserver?.let { contentResolver.unregisterContentObserver(it) }
            screenshotObserver = null
            DiagnosticsLogger.logEvent("Service", "OBSERVER_UNREGISTERED", context = this)
        } catch (e: Exception) {
            // e.g. not registered
        }
    }

    inner class ScreenshotObserver(handler: Handler) : ContentObserver(handler) {
        private var lastProcessedId: Long = -1L

        override fun onChange(selfChange: Boolean) {
             super.onChange(selfChange)
             processChange(null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            processChange(uri)
        }
        
        private fun processChange(uri: Uri?) {
            // VERBOSE LOG START
            DiagnosticsLogger.logEvent("Observer", "ON_CHANGE", "uri=$uri", this@KeepAliveService)
            
            try {
                // Double check since we might have unregistered but handler msg was already posted
                if (!prefs.getBoolean(KEY_DSS_AUTO_STITCH, false)) {
                    DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=dss_disabled", this@KeepAliveService)
                    return
                }

                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT
                )

                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                val cursor = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )
                
                if (cursor == null) {
                    DiagnosticsLogger.logEvent("Observer", "QUERY_FAILED", "cursor=null (permissions?)", this@KeepAliveService)
                    return
                }

                cursor.use {
                    if (it.moveToFirst()) {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                        } else {
                            it.getColumnIndex(MediaStore.Images.Media.DATA)
                        }
                        val wColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                        val hColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                        val id = it.getLong(idColumn)
                        val dateAdded = it.getLong(dateColumn)
                        
                        val relativePath = if (pathColumn != -1) it.getString(pathColumn) ?: "" else ""
                        val width = it.getInt(wColumn)
                        val height = it.getInt(hColumn)

                        // LOG CANDIDATE
                        DiagnosticsLogger.logEvent("Observer", "CANDIDATE_FOUND", "id=$id path=$relativePath w=$width h=$height", this@KeepAliveService)

                        // Debounce
                        if (id == lastProcessedId) {
                            DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=debounce id=$id", this@KeepAliveService)
                            return
                        }

                        // Time Check (5 seconds)
                        val now = System.currentTimeMillis() / 1000
                        if (now - dateAdded > 5) {
                            DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=stale time_diff=${now - dateAdded}", this@KeepAliveService)
                            return
                        }

                        // Path Check (Avoid loops)
                        if (relativePath.contains("Mjolnir")) {
                            DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=loop_prevention", this@KeepAliveService)
                            return
                        }

                        // Dimension Check (Target Bottom Display)
                        val dm = getSystemService(DisplayManager::class.java)
                        val displays = dm.displays
                        val bottomDisplay = displays.firstOrNull { d -> d.displayId != Display.DEFAULT_DISPLAY }

                        var isMatch = false
                        if (bottomDisplay != null) {
                            val metrics = android.util.DisplayMetrics()
                            bottomDisplay.getRealMetrics(metrics)
                            // Strict match logic
                            if ((width == metrics.widthPixels && height == metrics.heightPixels) ||
                                (width == metrics.heightPixels && height == metrics.widthPixels)) {
                                isMatch = true
                            } else {
                                DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=dim_mismatch candidate=${width}x${height} bottom=${metrics.widthPixels}x${metrics.heightPixels}", this@KeepAliveService)
                            }
                        } else {
                            DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=no_bottom_display", this@KeepAliveService)
                        }

                        if (isMatch) {
                            lastProcessedId = id
                            val contentUri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )

                            DiagnosticsLogger.logEvent("KeepAlive", "DSS_TRIGGER", "id=$id", this@KeepAliveService)

                            val serviceIntent = Intent(this@KeepAliveService, DualScreenshotService::class.java).apply {
                                putExtra(EXTRA_SOURCE_URI, contentUri)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                        }
                    } else {
                        DiagnosticsLogger.logEvent("Observer", "QUERY_EMPTY", "count=0", this@KeepAliveService)
                    }
                }
            } catch (e: Exception) {
                DiagnosticsLogger.logException("KeepAlive", e, this@KeepAliveService)
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_DUAL_SCREENSHOT = "xyz.blacksheep.mjolnir.ACTION_DUAL_SCREENSHOT"
        const val ACTION_DELETE_SCREENSHOT = "xyz.blacksheep.mjolnir.ACTION_DELETE_SCREENSHOT"
        const val ACTION_REFRESH_OBSERVER = "xyz.blacksheep.mjolnir.ACTION_REFRESH_OBSERVER"
        const val ACTION_UPDATE_STATUS = "xyz.blacksheep.mjolnir.ACTION_UPDATE_STATUS"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
