package xyz.blacksheep.mjolnir

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
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
import xyz.blacksheep.mjolnir.settings.settingsPrefs
import xyz.blacksheep.mjolnir.settings.GestureConfigStore

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
        @Volatile var lastFocusedDisplayId: Int? = null
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
    private var longPressDelayMs: Int = ViewConfiguration.getLongPressTimeout()
    private var activeGestureConfig: GestureConfigStore.GestureConfig? = null
    private var pendingBootBottomLaunch = false
    private var pendingBootDefaultHome: String? = null
    private var pendingBootLogCount = 0
    private var pendingBootBottomDisplayId: Int? = null
    private var bootBottomLaunchDone = false
    private var bootBottomRetryDone = false

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

    private val focusChangeObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val focusValue = try {
                Settings.System.getInt(contentResolver, "focus_change", -1)
            } catch (e: Exception) {
                -1
            }
            DiagnosticsLogger.logEvent("Focus", "SYSTEM_FOCUS_CHANGE", "value=$focusValue", this@HomeKeyInterceptorService)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                DiagnosticsLogger.logEvent("Focus", "SYSTEM_FOCUS_SCAN_SKIPPED", "reason=ApiTooLow", this@HomeKeyInterceptorService)
                return
            }

            val windows = windows
            val focused = windows.firstOrNull { it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val active = windows.firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val anyApp = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val chosen = focused ?: active ?: anyApp
            val displayId = chosen?.displayId
            if (displayId != null) {
                lastFocusedDisplayId = displayId
                DiagnosticsLogger.logEvent(
                    "Focus",
                    "SYSTEM_FOCUS_RESOLVED",
                    "displayId=$displayId pkg=${chosen.root?.packageName} focused=${chosen.isFocused} active=${chosen.isActive} windowId=${chosen.id}",
                    this@HomeKeyInterceptorService
                )
            } else {
                val windowDump = windows.joinToString(
                    prefix = "[",
                    postfix = "]",
                    limit = 12,
                    truncated = "..."
                ) { win ->
                    val winPkg = win.root?.packageName?.toString()
                    "id=${win.id},disp=${win.displayId},type=${win.type},focused=${win.isFocused},active=${win.isActive},pkg=$winPkg"
                }
                DiagnosticsLogger.logEvent(
                    "Focus",
                    "SYSTEM_FOCUS_UNRESOLVED",
                    "windowCount=${windows.size} windows=$windowDump",
                    this@HomeKeyInterceptorService
                )
            }
        }
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsLogger.logEvent("Service", "ACCESSIBILITY_CONNECTED", context = this)
        instance = this
        prefs = settingsPrefs()
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

        try {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor("focus_change"),
                false,
                focusChangeObserver
            )
            val initial = Settings.System.getInt(contentResolver, "focus_change", -1)
            if (initial != -1) {
                lastFocusedDisplayId = initial
            }
            DiagnosticsLogger.logEvent("Focus", "SYSTEM_FOCUS_OBSERVER_REGISTERED", "initial=$initial", this)
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("Focus", "SYSTEM_FOCUS_OBSERVER_FAILED", "message=${e.message}", this)
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

        maybeRunBootAction()
    }

    private fun maybeRunBootAction() {
        try {
            val prefs = settingsPrefs()
            val isAdvanced = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
            if (!isAdvanced) {
                DiagnosticsLogger.logEvent("Gesture", "AUTO_BOOT_SKIPPED", "reason=not_advanced", this)
                return
            }

            val isFresh = isFreshBoot(prefs)
            if (!isFresh) {
                DiagnosticsLogger.logEvent("Gesture", "AUTO_BOOT_SKIPPED", "reason=not_fresh_boot", this)
                return
            }

            val startOnBootAuto = prefs.getBoolean(KEY_AUTO_BOOT_BOTH_HOME, true)
            if (startOnBootAuto) {
                val defaultHome = getCurrentDefaultHomePackage()
                if (!defaultHome.isNullOrBlank() && defaultHome != packageName) {
                    pendingBootDefaultHome = defaultHome
                    pendingBootBottomLaunch = true
                    pendingBootLogCount = 0
                    bootBottomLaunchDone = false
                    bootBottomRetryDone = false
                    pendingBootBottomDisplayId = runCatching {
                        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                        dm.displays.getOrNull(1)?.displayId
                    }.getOrNull()
                    DiagnosticsLogger.logEvent("Gesture", "AUTO_BOOT_ACTION", "action=BOTH_HOME_DEFER_BOTTOM defaultHome=$defaultHome", this)
                    actionLauncher.launchTop()
                    return
                }
                DiagnosticsLogger.logEvent("Gesture", "AUTO_BOOT_ACTION", "action=BOTH_HOME", this)
                performAction(Action.BOTH_HOME)
            } else {
                DiagnosticsLogger.logEvent("Gesture", "AUTO_BOOT_ACTION", "action=BOTH_HOME_DEFAULT", this)
                performAction(Action.BOTH_HOME_DEFAULT)
            }
        } catch (e: Exception) {
            DiagnosticsLogger.logException("Gesture", e, this)
        }
    }

    private fun isFreshBoot(prefs: SharedPreferences): Boolean {
        val bootCount = runCatching {
            Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrNull()

        if (bootCount != null && bootCount >= 0) {
            val lastBootCount = prefs.getInt(KEY_LAST_BOOT_COUNT, -1)
            if (bootCount != lastBootCount) {
                prefs.edit().putInt(KEY_LAST_BOOT_COUNT, bootCount).apply()
                return true
            }
            return false
        }

        val elapsed = SystemClock.elapsedRealtime()
        val lastElapsed = prefs.getLong(KEY_LAST_BOOT_ELAPSED, -1L)
        val fresh = lastElapsed < 0L || elapsed < lastElapsed
        prefs.edit().putLong(KEY_LAST_BOOT_ELAPSED, elapsed).apply()
        return fresh
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
                    longPressDelayMs.toLong()
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
            val topLabel = actionLauncher.getTopAppLabel()
            val bottomLabel = actionLauncher.getBottomAppLabel()
            provideToastFeedback("$gesture → ${actionLabel(action, topLabel, bottomLabel)}")

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
            Action.FOCUS_AUTO -> actionLauncher.launchFocusAuto()
            Action.FOCUS_TOP_APP -> actionLauncher.launchFocusTopApp()
            Action.NONE -> { /* no-op */ }
            Action.APP_SWITCH -> {
                DiagnosticsLogger.logEvent("Gesture", "ACTION_RECENTS_TRIGGERED", context = this)
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            Action.DEFAULT_HOME -> {
                DiagnosticsLogger.logEvent("Gesture", "ACTION_HOME_PASSTHROUGH", context = this)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            Action.TOP_HOME_DEFAULT -> {
                DiagnosticsLogger.logEvent("Gesture", "ACTION_HOME_TOP_TRIGGERED", context = this)
                actionLauncher.launchDefaultHomeOnTop()
            }
            Action.BOTTOM_HOME_DEFAULT -> {
                DiagnosticsLogger.logEvent("Gesture", "ACTION_HOME_BOTTOM_TRIGGERED", context = this)
                actionLauncher.launchDefaultHomeOnBottom()
            }
            Action.BOTH_HOME_DEFAULT -> {
                DiagnosticsLogger.logEvent("Gesture", "ACTION_HOME_BOTH_TRIGGERED", context = this)
                actionLauncher.launchDefaultHomeOnBoth()
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
            Action.TOP_HOME,
            Action.BOTH_HOME,
            Action.TOP_HOME_DEFAULT,
            Action.BOTH_HOME_DEFAULT -> true
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
        val config = GestureConfigStore.getActiveConfig(this)
        activeGestureConfig = config
        longPressDelayMs = if (config.longPressDelayMs > 0) {
            config.longPressDelayMs
        } else {
            ViewConfiguration.getLongPressTimeout()
        }
        useSystemDoubleTapDelay = prefs.getBoolean(KEY_USE_SYSTEM_DOUBLE_TAP_DELAY, true)
        customDoubleTapDelay = prefs.getInt(KEY_CUSTOM_DOUBLE_TAP_DELAY, ViewConfiguration.getDoubleTapTimeout())
        DiagnosticsLogger.logEvent(
            "Prefs",
            "GESTURE_CONFIG_UPDATED",
            "preset=${config.fileName} name=${config.name} longPressDelay=$longPressDelayMs useSystemDelay=$useSystemDoubleTapDelay customDelay=$customDoubleTapDelay",
            this
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (pendingBootBottomLaunch) {
            val eventPkg = event.packageName?.toString()
            val defaultHome = pendingBootDefaultHome
            val matchesEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventPkg == defaultHome
            val windowsMatch = defaultHome != null && windows.any { it.root?.packageName?.toString() == defaultHome }

            if (matchesEvent || windowsMatch) {
                pendingBootBottomLaunch = false
                val reason = if (matchesEvent) "event" else "windows"
                DiagnosticsLogger.logEvent("Gesture", "AUTO_BOOT_BOTTOM_LAUNCH", "defaultHome=$defaultHome reason=$reason delayMs=150", this)
                Handler(Looper.getMainLooper()).postDelayed({
                    bootBottomLaunchDone = true
                    actionLauncher.launchBottom()
                }, 150)
            } else if (pendingBootLogCount < 6) {
                pendingBootLogCount++
                val windowDump = windows.joinToString(
                    prefix = "[",
                    postfix = "]",
                    limit = 6,
                    truncated = "..."
                ) { win ->
                    val winPkg = win.root?.packageName?.toString()
                    "disp=${win.displayId},type=${win.type},focused=${win.isFocused},pkg=$winPkg"
                }
                DiagnosticsLogger.logEvent(
                    "Gesture",
                    "AUTO_BOOT_WAITING",
                    "type=${event.eventType} pkg=$eventPkg defaultHome=$defaultHome windows=$windowDump",
                    this
                )
            }
        }

        val eventType = event.eventType
        val isFocusEvent = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START ||
            eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END

        if (!isFocusEvent) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            DiagnosticsLogger.logEvent("Focus", "EVENT_IGNORED", "reason=ApiTooLow type=$eventType", this)
            return
        }

        val eventDisplayId = event.displayId
        if (eventDisplayId != -1) {
            lastFocusedDisplayId = eventDisplayId
            DiagnosticsLogger.logEvent(
                "Focus",
                "EVENT_DISPLAY_CAPTURED",
                "type=$eventType displayId=$eventDisplayId windowId=${event.windowId} pkg=${event.packageName}",
                this
            )
            maybeRetryBootBottom(eventDisplayId, event.packageName?.toString())
            return
        }

        val windowId = event.windowId
        val windows = windows
        val window = windows.firstOrNull { it.id == windowId }
        val displayId = window?.displayId
        val pkg = event.packageName?.toString()

        if (displayId != null) {
            lastFocusedDisplayId = displayId
            DiagnosticsLogger.logEvent(
                "Focus",
                "EVENT_CAPTURED",
                "type=$eventType windowId=$windowId displayId=$displayId pkg=$pkg windowCount=${windows.size}",
                this
            )
            maybeRetryBootBottom(displayId, pkg)
        } else {
            val windowDump = windows.joinToString(
                prefix = "[",
                postfix = "]",
                limit = 12,
                truncated = "..."
            ) { win ->
                val winPkg = win.root?.packageName?.toString()
                "id=${win.id},disp=${win.displayId},type=${win.type},focused=${win.isFocused},active=${win.isActive},pkg=$winPkg"
            }
            DiagnosticsLogger.logEvent(
                "Focus",
                "EVENT_UNMAPPED",
                "type=$eventType windowId=$windowId pkg=$pkg windowCount=${windows.size} windows=$windowDump",
                this
            )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
       when (key) {
           KEY_SINGLE_HOME_ACTION,
           KEY_DOUBLE_HOME_ACTION,
           KEY_TRIPLE_HOME_ACTION,
           KEY_LONG_HOME_ACTION,
           KEY_ACTIVE_GESTURE_CONFIG,
           KEY_USE_SYSTEM_DOUBLE_TAP_DELAY,
           KEY_CUSTOM_DOUBLE_TAP_DELAY -> updateGestureConfig()
       }
    }

    private fun getConfiguredActionForGesture(gesture: Gesture): Action {
        val config = activeGestureConfig ?: GestureConfigStore.getActiveConfig(this)
        return when (gesture) {
            Gesture.SINGLE_HOME -> config.single
            Gesture.DOUBLE_HOME -> config.double
            Gesture.TRIPLE_HOME -> config.triple
            Gesture.LONG_HOME -> config.long
        }
    }

    private fun getCurrentDefaultHomePackage(): String? {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo: android.content.pm.ResolveInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName
    }

    private fun maybeRetryBootBottom(displayId: Int, pkg: String?) {
        if (!bootBottomLaunchDone || bootBottomRetryDone) return
        val defaultHome = pendingBootDefaultHome ?: return
        val bottomDisplayId = pendingBootBottomDisplayId ?: return
        if (displayId == bottomDisplayId && pkg == defaultHome) {
            bootBottomRetryDone = true
            DiagnosticsLogger.logEvent("Gesture", "AUTO_BOOT_BOTTOM_RETRY", "defaultHome=$defaultHome delayMs=150", this)
            Handler(Looper.getMainLooper()).postDelayed({
                actionLauncher.launchBottom()
            }, 150)
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
        lastFocusedDisplayId = null
        resetGestureState()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DiagnosticsLogger.logEvent("Service", "ACCESSIBILITY_UNBOUND", context = this)
        try {
            unregisterReceiver(collapseShadeReceiver)
        } catch (e: Exception) { /* ignore */ }
        try {
            contentResolver.unregisterContentObserver(focusChangeObserver)
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
        try {
            contentResolver.unregisterContentObserver(focusChangeObserver)
        } catch (e: Exception) { /* ignore */ }
        stopKeepAliveService()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        serviceScope.cancel()
        lastFocusedDisplayId = null
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
