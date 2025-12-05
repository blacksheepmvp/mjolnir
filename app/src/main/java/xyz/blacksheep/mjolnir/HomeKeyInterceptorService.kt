package xyz.blacksheep.mjolnir

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.annotation.RequiresPermission
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
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher
import xyz.blacksheep.mjolnir.workarounds.FocusLockOverlayWorkaround

/**
 * The core engine of Mjolnir's gesture system.
 *
 * **Role:**
 * - Listens for hardware Home button presses (Scan Code 102) using the Accessibility API.
 * - Discriminates between Single, Double, Triple, and Long press gestures using timing logic.
 * - Dispatches the configured [Action] to the [HomeActionLauncher].
 * - Manages the lifecycle of the [KeepAliveService] to ensure the process remains active.
 *
 * **Key Behaviors:**
 * - **Interceptor:** Consumes the `KEYCODE_HOME` event to prevent the system launcher from appearing.
 * - **Gesture Recognition:** Uses [Handler.postDelayed] to wait for multi-tap sequences before committing to an action.
 * - **Auto-Boot:** Optionally triggers the "Both Home" action immediately when the service connects (on device boot).
 * - **Feedback:** Provides haptic feedback (vibration) upon successful gesture recognition.
 */
class HomeKeyInterceptorService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "HomeKeyInterceptorService"
        var instance: HomeKeyInterceptorService? = null
        const val ACTION_REQ_COLLAPSE_SHADE = "xyz.blacksheep.mjolnir.ACTION_REQ_COLLAPSE_SHADE"
        const val ACTION_REQ_SWAP_SCREENS = "xyz.blacksheep.mjolnir.ACTION_REQ_SWAP_SCREENS"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences
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

    // Receiver for cross-process requests (e.g. from KeepAliveService)
    private val collapseShadeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_REQ_COLLAPSE_SHADE -> {
                    DiagnosticsLogger.logEvent("Gesture", "SHADE_COLLAPSE_REQ_RECEIVED", context = this@HomeKeyInterceptorService)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                    }
                }
                ACTION_REQ_SWAP_SCREENS -> {
                    DiagnosticsLogger.logEvent("Gesture", "SWAP_SCREENS_REQ_RECEIVED", context = this@HomeKeyInterceptorService)
                    performScreenSwap()
                }
            }
        }
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsLogger.logEvent("Service", "ACCESSIBILITY_CONNECTED", context = this)
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        actionLauncher = HomeActionLauncher(this)
        updateGestureConfig()

        // Register receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_REQ_COLLAPSE_SHADE)
            addAction(ACTION_REQ_SWAP_SCREENS)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(collapseShadeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(collapseShadeReceiver, filter)
        }

        // Start the foreground KeepAliveService to keep this process alive.
        // Also explicitly tell it to update its status notification immediately.
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, KeepAliveService::class.java).apply {
                action = KeepAliveService.ACTION_UPDATE_STATUS
            }
            DiagnosticsLogger.logEvent("Service", "SENDING_UPDATE_STATUS_INTENT", context = this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }, 250)
        
        /*
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
        */
    }

    private fun performScreenSwap() {
        // Collapse shade first so it doesn't get in the way
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)

        // We need to find the active app. 
        // getWindows() returns windows in Z-order (front to back).
        // The first window we find that is TYPE_APPLICATION and not us is likely the "Focused" app
        // (or the one the user was using before pulling down the shade).
        
        val windows = windows
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.displays
        val topDisplayId = displays.getOrNull(0)?.displayId ?: 0

        var targetPkg: String? = null
        var currentDisplayId = -1

        for (window in windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val pkg = window.root?.packageName?.toString()
                if (pkg != null && pkg != packageName) {
                    targetPkg = pkg
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        currentDisplayId = window.displayId
                    }
                    break // Found the top-most app
                }
            }
        }

        if (targetPkg != null) {
             DiagnosticsLogger.logEvent("Swap", "SWAP_TARGET_FOUND", "pkg=$targetPkg display=$currentDisplayId", this)

            // Determine target display: Opposite of current
            val isCurrentlyOnTop = (currentDisplayId == topDisplayId)
            val targetIsTop = !isCurrentlyOnTop

            // Launch on the opposite screen. 
            // The OS/Hardware logic should handle moving the "other" app automatically.
            
            // Slight delay to allow shade to close
            Handler(Looper.getMainLooper()).postDelayed({
                launchPackageOnDisplay(targetPkg, isTop = targetIsTop)
            }, 250)
        } else {
            DiagnosticsLogger.logEvent("Swap", "SWAP_NO_TARGET", "msg=No active app found", this)
            Toast.makeText(this, "No active app found to swap", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchPackageOnDisplay(pkgName: String, isTop: Boolean) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkgName)
            if (launchIntent != null) {
                if (isTop) {
                    DualScreenLauncher.launchOnTop(this, launchIntent)
                } else {
                    DualScreenLauncher.launchOnBottom(this, launchIntent)
                }
            }
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("Swap", "SWAP_LAUNCH_FAILED", "pkg=$pkgName error=${e.message}", this)
        }
    }

    /**
     * Intercepts raw key events from the system.
     *
     * **Logic:**
     * 1. Checks if interception is globally enabled in Mjolnir settings.
     * 2. Checks if the key is explicitly the physical Home button (Scan Code 102).
     * 3. If yes, consumes the event (returns `true`) and routes it to [handleHomeGesture].
     * 4. If no, passes the event through to the system.
     */
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

    /**
     * The state machine for gesture detection.
     *
     * - **ACTION_DOWN:**
     *   - Cancels any pending multi-press timeout (user is still tapping).
     *   - Starts a timer for Long Press detection.
     *   - Increments the press counter.
     *
     * - **ACTION_UP:**
     *   - Cancels the Long Press timer (user let go before timeout).
     *   - Starts the "Resolution Timer" (waiting for next tap).
     *     - If timer expires: We commit to Single/Double/Triple press based on current count.
     */
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

        // Notify KeepAliveService that a gesture was processed.
        // This acts as a "liveness check" to refresh the notification status if it's stale (e.g. "Invalid Configuration").
        val updateIntent = Intent(this, KeepAliveService::class.java).apply {            this.action = KeepAliveService.ACTION_UPDATE_STATUS // Use "this.action" to refer to the Intent's property
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(updateIntent)
            } else {
                startService(updateIntent)
            }
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("Error", "FAILED_TO_SEND_UPDATE_STATUS", "msg=${e.message}", this)
        }

        when (action) {
            Action.TOP_HOME -> actionLauncher.launchTop()
            Action.BOTTOM_HOME -> actionLauncher.launchBottom()
            Action.BOTH_HOME -> actionLauncher.launchBoth()
            Action.NONE -> { /* no-op */ }
            Action.APP_SWITCH -> {
                DiagnosticsLogger.logEvent("Gesture", "ACTION_RECENTS_TRIGGERED", context = this)
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            Action.DEFAULT_HOME -> {
                DiagnosticsLogger.logEvent("Gesture", "ACTION_HOME_PASSTHROUGH", context = this)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }

        // Run the focus lock workaround if applicable
        maybeRunFocusLockWorkaround(action)
    }

    private fun maybeRunFocusLockWorkaround(action: Action) {
        // 1. Check if enabled in prefs
        val isEnabled = prefs.getBoolean(KEY_ENABLE_FOCUS_LOCK_WORKAROUND, false)
        if (!isEnabled) return

        // 2. Check if action involves TOP display
        val involvesTop = when (action) {
            Action.TOP_HOME, Action.BOTH_HOME -> true
            else -> false
        }

        if (involvesTop) {
            DiagnosticsLogger.logEvent("Gesture", "WORKAROUND_TRIGGERED", "action=$action", this)
            FocusLockOverlayWorkaround.run(this)
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
        try {
            unregisterReceiver(collapseShadeReceiver)
        } catch (e: Exception) { /* ignore */ }
        instance = null
        stopKeepAliveService()
        resetGestureState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        DiagnosticsLogger.logEvent("Service", "ACCESSIBILITY_DESTROYED", context = this)
        try {
            unregisterReceiver(collapseShadeReceiver)
        } catch (e: Exception) { /* ignore */ }
        stopKeepAliveService()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        serviceScope.cancel()
        resetGestureState()
    }

    private fun stopKeepAliveService() {
        try {
            // We also send a status update to let it know we are dying (optional, but good practice)
            // Actually, better to just let the service handle its own state if it stays alive.
            // But if we want to kill it when accessibility dies:
            val intent = Intent(this, KeepAliveService::class.java)
            stopService(intent)
        } catch (e: Exception) {
            DiagnosticsLogger.logException("Service", e, this)
        }
    }

    /**
     * Exposes GLOBAL_ACTION_BACK to be called from other services (e.g., DSS).
     */
    fun performBack(): Boolean {
        DiagnosticsLogger.logEvent("Gesture", "PERFORM_BACK_REQUESTED", context = this)
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Public method to allow external components to trigger the GLOBAL_ACTION_HOME
     * in the context of this accessibility service.
     */
    fun performGlobalHomeAction() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
