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
import android.content.pm.ResolveInfo
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
import androidx.core.content.edit
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
import xyz.blacksheep.mjolnir.onboarding.OnboardingActivity
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

        if (action == ACTION_TOGGLE_INTERCEPTION) {
            val currentStatus = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
            prefs.edit { putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, !currentStatus) }
            return START_STICKY
        }

        if (action == ACTION_REFRESH_OBSERVER) {
            DiagnosticsLogger.logEvent("Service", "ACTION_REFRESH_OBSERVER_RECEIVED", context = this)
            refreshDssState()
        }

        if (action == ACTION_UPDATE_STATUS) {
            DiagnosticsLogger.logEvent("Service", "ACTION_UPDATE_STATUS_RECEIVED", context = this)
            startForegroundInternal()
        }
        
        if (action == ACTION_DUAL_SCREENSHOT) {
            DiagnosticsLogger.logEvent("Service", "ACTION_DUAL_SCREENSHOT_RECEIVED", context = this)
            DualScreenshotManager.start(this)
            startForegroundInternal()
            return START_STICKY
        }

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
            return START_STICKY
        }

        startForegroundInternal()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
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
            
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE)
            } else {
                PendingIntent.getService(this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE)
            }
            
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 2000, pendingIntent)
        } catch (e: Exception) {
            DiagnosticsLogger.logException("Service", e, this)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == KEY_HOME_INTERCEPTION_ACTIVE || key == KEY_TOP_APP || key == KEY_BOTTOM_APP || key == xyz.blacksheep.mjolnir.utils.KEY_DIAGNOSTICS_ENABLED) {
            startForegroundInternal()
        }
        
        if (key == KEY_DSS_AUTO_STITCH) {
            refreshDssState()
        }
    }

    private fun refreshDssState() {
        val dssEnabled = prefs.getBoolean(KEY_DSS_AUTO_STITCH, false)
        val hasPermission = hasStoragePermission()
        DiagnosticsLogger.logEvent("KeepAlive", "REFRESH_DSS_STATE", "dssEnabled=$dssEnabled permission=$hasPermission", this)
        
        if (dssEnabled && hasPermission) {
             registerScreenshotObserver()
        } else {
             unregisterScreenshotObserver()
        }
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * A check to determine if the current Mjolnir configuration is valid.
     * @return `true` if the configuration is valid, `false` otherwise.
     */
    private fun isConfigurationValid(): Boolean {
        val isInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
        val topApp = prefs.getString(KEY_TOP_APP, null)
        val bottomApp = prefs.getString(KEY_BOTTOM_APP, null)
        val SPECIAL_HOME_APPS = setOf("com.android.launcher3", "com.odin.odinlauncher")

        if (topApp == null && bottomApp == null) return false

        if (isInterceptionActive) {
            if (!isAccessibilityServiceEnabled(this)) return false

            if (topApp in SPECIAL_HOME_APPS) {
                val defaultHome = getCurrentDefaultHomePackage(this)
                if (defaultHome != topApp) return false
            }
        }

        return true
    }

    private fun getCurrentDefaultHomePackage(context: Context): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo: ResolveInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName
    }

    /**
     * Builds and posts the persistent notification that anchors this service.
     * The notification text reflects the current status of the Home Button Interceptor.
     */
    private fun startForegroundInternal() {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val isAdvancedModeReady = isAccessibilityServiceEnabled(this) && hasNotificationPermission()
        val isToggleEnabled = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
        val isValid = isConfigurationValid()
        val currentDefaultHome = getCurrentDefaultHomePackage(this)
        val isMjolnirDefault = currentDefaultHome?.contains(packageName) == true

        val contentTitle: String
        val notificationAction: NotificationCompat.Action?
        val notificationPriority: Int
        val clickIntent: PendingIntent

        when {
            !isValid -> {
                contentTitle = "Invalid Mjolnir Configuration"
                notificationAction = null
                notificationPriority = NotificationCompat.PRIORITY_HIGH
                clickIntent = Intent(this, OnboardingActivity::class.java).let {
                    PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
                }
            }
            !isToggleEnabled && isValid && isMjolnirDefault -> {
                contentTitle = "Mjolnir Home Basic is active."
                notificationAction = createToggleAction("Enable Advanced")
                notificationPriority = NotificationCompat.PRIORITY_LOW
                clickIntent = pendingIntent
            }
            isAdvancedModeReady && isToggleEnabled -> {
                contentTitle = "Mjolnir Home Advanced is active."
                notificationAction = createToggleAction("Disable")
                notificationPriority = NotificationCompat.PRIORITY_LOW
                clickIntent = pendingIntent
            }
            else -> { 
                contentTitle = "Mjolnir Home is inactive."
                notificationAction = createToggleAction("Enable")
                notificationPriority = NotificationCompat.PRIORITY_DEFAULT
                clickIntent = pendingIntent
            }
        }

        val contentText = if (isValid) {
            if (DiagnosticsConfig.isEnabled(this)) "Diagnostic data is being collected locally. Tap to open Mjolnir." else "Tap to open Mjolnir."
        } else {
            "Tap to fix configuration."
        }

        val builder = NotificationCompat.Builder(this, MjolnirApp.PERSISTENT_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_home)
            .setContentIntent(clickIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(notificationPriority)
            .setSilent(true)

        notificationAction?.let {
            builder.addAction(it)
        }

        val notification: Notification = builder.build()

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            DiagnosticsLogger.logEvent("Service", "KEEPALIVE_NOTIFICATION_SHOWN", "title=\"$contentTitle\"", this)
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("Error", "KEEPALIVE_NOTIFICATION_FAILED", "message=${e.message}", this)
        }
    }

    private fun createToggleAction(title: String): NotificationCompat.Action {
        val toggleIntent = Intent(this, KeepAliveService::class.java).apply { action = ACTION_TOGGLE_INTERCEPTION }
        val pendingToggleIntent = PendingIntent.getService(this, 3, toggleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(R.drawable.ic_home, title, pendingToggleIntent).build()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun stopForegroundInternal() {
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
        unregisterScreenshotObserver()
        
        DiagnosticsLogger.logEvent("KeepAlive", "REGISTER_OBSERVER_NEW_INSTANCE", null, this)
        screenshotObserver = ScreenshotObserver(Handler(Looper.getMainLooper()))
        try {
            contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, screenshotObserver!!)
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
        } catch (e: Exception) { }
    }

    inner class ScreenshotObserver(handler: Handler) : ContentObserver(handler) {
        private var lastProcessedId: Long = -1L

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            processChange(uri)
        }
        
        private fun processChange(uri: Uri?) {
            DiagnosticsLogger.logEvent("Observer", "ON_CHANGE", "uri=$uri", this@KeepAliveService)
            
            try {
                if (!prefs.getBoolean(KEY_DSS_AUTO_STITCH, false)) {
                    DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=dss_disabled", this@KeepAliveService)
                    return
                }

                val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT)
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                val cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)
                
                if (cursor == null) {
                    DiagnosticsLogger.logEvent("Observer", "QUERY_FAILED", "cursor=null (permissions?)", this@KeepAliveService)
                    return
                }

                cursor.use {
                    if (it.moveToFirst()) {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH) else it.getColumnIndex(MediaStore.Images.Media.DATA)
                        val wColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                        val hColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                        val id = it.getLong(idColumn)
                        val dateAdded = it.getLong(dateColumn)
                        val relativePath = if (pathColumn != -1) it.getString(pathColumn) ?: "" else ""
                        val width = it.getInt(wColumn)
                        val height = it.getInt(hColumn)

                        DiagnosticsLogger.logEvent("Observer", "CANDIDATE_FOUND", "id=$id path=$relativePath w=$width h=$height", this@KeepAliveService)

                        if (id == lastProcessedId) {
                            DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=debounce id=$id", this@KeepAliveService)
                            return
                        }

                        val now = System.currentTimeMillis() / 1000
                        if (now - dateAdded > 5) {
                            DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=stale time_diff=${now - dateAdded}", this@KeepAliveService)
                            return
                        }

                        if (relativePath.contains("Mjolnir")) {
                            DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=loop_prevention", this@KeepAliveService)
                            return
                        }

                        val dm = getSystemService(DisplayManager::class.java)
                        val displays = dm.displays
                        val bottomDisplay = displays.firstOrNull { d -> d.displayId != Display.DEFAULT_DISPLAY }

                        var isMatch = false
                        if (bottomDisplay != null) {
                            val metrics = android.util.DisplayMetrics()
                            bottomDisplay.getRealMetrics(metrics)
                            if ((width == metrics.widthPixels && height == metrics.heightPixels) || (width == metrics.heightPixels && height == metrics.widthPixels)) {
                                isMatch = true
                            } else {
                                DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=dim_mismatch candidate=${width}x${height} bottom=${metrics.widthPixels}x${metrics.heightPixels}", this@KeepAliveService)
                            }
                        } else {
                            DiagnosticsLogger.logEvent("Observer", "SKIPPED", "reason=no_bottom_display", this@KeepAliveService)
                        }

                        if (isMatch) {
                            lastProcessedId = id
                            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

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
        const val ACTION_TOGGLE_INTERCEPTION = "xyz.blacksheep.mjolnir.ACTION_TOGGLE_INTERCEPTION"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
