package xyz.blacksheep.mjolnir

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.home.Gesture
import xyz.blacksheep.mjolnir.home.HomeActionLauncher
import xyz.blacksheep.mjolnir.home.actionLabel
import xyz.blacksheep.mjolnir.services.KeepAliveService
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger

class HomeKeyInterceptorService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "HomeKeyInterceptorService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences
    private var instance: HomeKeyInterceptorService? = null
    private lateinit var actionLauncher: HomeActionLauncher

    // Gesture detection state
    private var homePressCount = 0
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var multiPressTimeoutRunnable: Runnable? = null
    private var lastGestureTimestamp = 0L

    private var useSystemDoubleTapDelay: Boolean = true
    private var customDoubleTapDelay: Int = 0

    private val ENABLE_GESTURE_TOAST = false
    private val ENABLE_GESTURE_HAPTIC = true


    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsLogger.logEvent("Service", "ACCESSIBILITY_CONNECTED", context = this)
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        actionLauncher = HomeActionLauncher(this)
        updateGestureConfig()

        // Start the foreground KeepAliveService to keep this process alive.
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, KeepAliveService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }, 250)

        // Auto-run BOTH_HOME on boot if enabled
        try {
            val autoBootHome = prefs.getBoolean(KEY_AUTO_BOOT_BOTH_HOME, true)

            if (autoBootHome) {
                DiagnosticsLogger.logEvent("Gesture", "AUTO_BOOT_ACTION", "action=BOTH_HOME", this)
                performAction(Action.BOTH_HOME)
            }

        } catch (e: Exception) {
            DiagnosticsLogger.logException("Gesture", e, this)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
        val isActualHomeButton = event.scanCode == 102

        if (isInterceptionActive && event.keyCode == KeyEvent.KEYCODE_HOME && isActualHomeButton) {
            DiagnosticsLogger.logEvent("Gesture", "HOME_PRESS", "source=AccessibilityService action=${event.action}", this)
            handleHomeGesture(event)
            return true // Consume the event
        }
        return super.onKeyEvent(event)
    }

    private fun handleHomeGesture(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN) {

            // Cancel pending multi-press resolution if another press starts
            multiPressTimeoutRunnable?.let {
                gestureHandler.removeCallbacks(it)
                multiPressTimeoutRunnable = null
                 DiagnosticsLogger.logEvent("Gesture", "GESTURE_DISCARDED", "reason=new_press_started", this)
            }

            // On first press, schedule long-press detection
            if (homePressCount == 0) {
                longPressRunnable = Runnable {
                    DiagnosticsLogger.logEvent("Gesture", "GESTURE_RECOGNIZED", "gesture=LONG_HOME", this)
                    resolveGesture(isLongPress = true)
                    resetGestureState()
                }
                gestureHandler.postDelayed(
                    longPressRunnable!!,
                    ViewConfiguration.getLongPressTimeout().toLong()
                )
            }

            // Count this press
            homePressCount++

        } else if (event.action == KeyEvent.ACTION_UP) {

            // Finger lifted: cancel long-press detection
            longPressRunnable?.let {
                gestureHandler.removeCallbacks(it)
                longPressRunnable = null
            }

            // Start (or restart) multi-press timeout:
            // once it expires, we resolve based on homePressCount
            val timeout = if (useSystemDoubleTapDelay) {
                ViewConfiguration.getDoubleTapTimeout().toLong()
            } else {
                customDoubleTapDelay.toLong()
            }

            multiPressTimeoutRunnable = Runnable {
                val gesture = when (homePressCount) {
                    1 -> Gesture.SINGLE_HOME
                    2 -> Gesture.DOUBLE_HOME
                    3 -> Gesture.TRIPLE_HOME
                    else -> null
                }
                if(gesture != null) {
                     DiagnosticsLogger.logEvent("Gesture", "GESTURE_RECOGNIZED", "gesture=$gesture candidateCount=$homePressCount", this)
                }
                resolveGesture(isLongPress = false)
            }
            gestureHandler.postDelayed(multiPressTimeoutRunnable!!, timeout)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun resolveGesture(isLongPress: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (now - lastGestureTimestamp < 200) {
             DiagnosticsLogger.logEvent("Gesture", "GESTURE_DISCARDED", "reason=debounce", this)
            return
        }
        lastGestureTimestamp = now

        val gesture = if (isLongPress) {
            Gesture.LONG_HOME
        } else {
            when (homePressCount) {
                1 -> Gesture.SINGLE_HOME
                2 -> Gesture.DOUBLE_HOME
                3 -> Gesture.TRIPLE_HOME
                else -> null
            }
        }

        if (gesture != null) {
            val action = getConfiguredActionForGesture(gesture)
            DiagnosticsLogger.logEvent("Gesture", "GESTURE_ACTION_DISPATCH", "gesture=$gesture action=$action", this)

            // Optional feedback
            provideHapticFeedback()
            provideToastFeedback("$gesture → ${actionLabel(action)}")

            // Trigger the launcher action
            performAction(action)
        } else {
            DiagnosticsLogger.logEvent("Gesture", "GESTURE_DISCARDED", "reason=no_matching_action pressCount=$homePressCount", this)
        }

        resetGestureState()
    }

    private fun performAction(action: Action) {
        DiagnosticsLogger.logEvent("Launcher", "PERFORM_ACTION_TRIGGERED", "action=$action", this)
        when (action) {
            Action.TOP_HOME -> actionLauncher.launchTop()
            Action.BOTTOM_HOME -> actionLauncher.launchBottom()
            Action.BOTH_HOME -> actionLauncher.launchBoth()
            Action.NONE -> { /* no-op */ }
            Action.APP_SWITCH -> {
                DiagnosticsLogger.logEvent("Gesture", "ACTION_RECENTS_TRIGGERED", context = this)
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }
    }

    private fun resetGestureState() {
        longPressRunnable?.let { gestureHandler.removeCallbacks(it) }
        multiPressTimeoutRunnable?.let { gestureHandler.removeCallbacks(it) }
        longPressRunnable = null
        multiPressTimeoutRunnable = null
        homePressCount = 0
    }

    private fun updateGestureConfig() {
        useSystemDoubleTapDelay = prefs.getBoolean(KEY_USE_SYSTEM_DOUBLE_TAP_DELAY, true)
        customDoubleTapDelay = prefs.getInt(KEY_CUSTOM_DOUBLE_TAP_DELAY, ViewConfiguration.getDoubleTapTimeout())
        DiagnosticsLogger.logEvent("Prefs", "GESTURE_CONFIG_UPDATED", "useSystemDelay=$useSystemDoubleTapDelay customDelay=$customDoubleTapDelay", this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Not used for this feature
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
       when (key) {
           KEY_SINGLE_HOME_ACTION,
           KEY_DOUBLE_HOME_ACTION,
           KEY_TRIPLE_HOME_ACTION,
           KEY_LONG_HOME_ACTION,
           KEY_USE_SYSTEM_DOUBLE_TAP_DELAY,
           KEY_CUSTOM_DOUBLE_TAP_DELAY -> updateGestureConfig()
       }
    }

    private fun getConfiguredActionForGesture(gesture: Gesture): Action {
        return when (gesture) {
            Gesture.SINGLE_HOME ->
                Action.valueOf(prefs.getString(KEY_SINGLE_HOME_ACTION, Action.BOTH_HOME.name)!!)

            Gesture.DOUBLE_HOME ->
                Action.valueOf(prefs.getString(KEY_DOUBLE_HOME_ACTION, Action.NONE.name)!!)

            Gesture.TRIPLE_HOME ->
                Action.valueOf(prefs.getString(KEY_TRIPLE_HOME_ACTION, Action.NONE.name)!!)

            Gesture.LONG_HOME ->
                Action.valueOf(prefs.getString(KEY_LONG_HOME_ACTION, Action.NONE.name)!!)
        }
    }

    private fun provideToastFeedback(text: String) {
        if (!ENABLE_GESTURE_TOAST) return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun provideHapticFeedback() {
        if (!ENABLE_GESTURE_HAPTIC) return

        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    override fun onInterrupt() {
        DiagnosticsLogger.logEvent("Service", "ACCESSIBILITY_INTERRUPTED", context = this)
        instance = null
        resetGestureState()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DiagnosticsLogger.logEvent("Service", "ACCESSIBILITY_UNBOUND", context = this)
        instance = null
        stopKeepAliveService()
        resetGestureState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        DiagnosticsLogger.logEvent("Service", "ACCESSIBILITY_DESTROYED", context = this)
        stopKeepAliveService()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        serviceScope.cancel()
        resetGestureState()
    }

    private fun stopKeepAliveService() {
        try {
            val intent = Intent(this, KeepAliveService::class.java)
            stopService(intent)
        } catch (e: Exception) {
            DiagnosticsLogger.logException("Service", e, this)
        }
    }
}