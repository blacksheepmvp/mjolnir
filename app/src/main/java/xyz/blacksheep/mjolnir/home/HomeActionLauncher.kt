package xyz.blacksheep.mjolnir.home

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import xyz.blacksheep.mjolnir.HomeKeyInterceptorService
import xyz.blacksheep.mjolnir.DEFAULT_TOP_BOTTOM_LAUNCH_DELAY_MS
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_BOTH_AUTO_NOTHING_TO_HOME
import xyz.blacksheep.mjolnir.KEY_MAIN_SCREEN
import xyz.blacksheep.mjolnir.KEY_SHOW_ALL_APPS
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.KEY_TOP_BOTTOM_LAUNCH_DELAY_MS
import xyz.blacksheep.mjolnir.model.MainScreen
import xyz.blacksheep.mjolnir.launchers.getLaunchableApps
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher
import xyz.blacksheep.mjolnir.utils.FocusHackHelper
import xyz.blacksheep.mjolnir.settings.settingsPrefs
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicLong

/**
 * The central coordinator for executing home-screen-like actions.
 *
 * This class bridges user preferences (which apps are assigned to which slots)
 * with the low-level launch capabilities of [DualScreenLauncher].
 *
 * **Responsibilities:**
 * 1. Reads current preferences (Top App, Bottom App, Main Screen focus).
 * 2. Resolves package names to launchable Intents.
 * 3. Handles "Single App" fallback logic (e.g., if only Top is set but user triggers Bottom).
 * 4. Delegates the actual physical launch to [DualScreenLauncher].
 * 5. Logs detailed diagnostics for every launch attempt.
 *
 * **Key Operations:**
 * - [launchTop]: Triggers the action associated with the Top Screen.
 * - [launchBottom]: Triggers the action associated with the Bottom Screen.
 * - [launchBoth]: Triggers the simultaneous launch of both apps.
 */
class HomeActionLauncher(private val context: Context) {

    private val prefs = context.settingsPrefs()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val SPECIAL_HOME_APPS = setOf("com.android.launcher3", "com.odin.odinlauncher")
    private val TAG = "HomeActionLauncher"

    companion object {
        private val TRACE_ID = AtomicLong(0)
    }

    private enum class FocusTarget { TOP, BOTTOM }

    private fun nextTraceId(): Long = TRACE_ID.incrementAndGet()

    private fun trace(event: String, traceId: Long, stage: String, extra: String? = null) {
        val suffix = if (extra.isNullOrBlank()) "" else " $extra"
        DiagnosticsLogger.logEvent(
            TAG,
            "${event}_$stage",
            "traceId=$traceId t=${SystemClock.elapsedRealtime()}$suffix",
            context
        )
    }

    private fun getCleanApp(key: String): String? {
        val pkg = prefs.getString(key, null)
        return if (pkg == "NOTHING") null else pkg
    }

    private fun resolveDisplayIds(): Pair<Int, Int> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.displays
        val topId = displays.getOrNull(0)?.displayId ?: 0
        val bottomId = displays.getOrNull(1)?.displayId ?: topId
        DiagnosticsLogger.logEvent(TAG, "DISPLAY_IDS_RESOLVED", "topId=$topId bottomId=$bottomId count=${displays.size}", context)
        return topId to bottomId
    }

    private fun resolveFocusedDisplayId(): Int? {
        if (context !is AccessibilityService) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_RESOLUTION_FAILED", "reason=ContextNotAccessibilityService", context)
            return null
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_RESOLUTION_FAILED", "reason=ApiTooLow", context)
            return null
        }
        val windows = context.windows
        val root = context.rootInActiveWindow
        val rootWindowId = root?.windowId
        DiagnosticsLogger.logEvent(TAG, "FOCUS_WINDOW_SCAN", "windowCount=${windows.size} rootWindowId=$rootWindowId", context)

        val byRoot = windows.firstOrNull { it.id == rootWindowId }
        if (byRoot != null) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_WINDOW_FOUND", "source=rootInActiveWindow pkg=${byRoot.root?.packageName} displayId=${byRoot.displayId} focused=${byRoot.isFocused}", context)
            return byRoot.displayId
        }

        val focused = windows.firstOrNull { it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        if (focused != null) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_WINDOW_FOUND", "source=focused pkg=${focused.root?.packageName} displayId=${focused.displayId}", context)
            return focused.displayId
        }

        val active = windows.firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        if (active != null) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_WINDOW_FOUND", "source=active pkg=${active.root?.packageName} displayId=${active.displayId}", context)
            return active.displayId
        }

        val anyApp = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        if (anyApp != null) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_WINDOW_FOUND", "source=firstApp pkg=${anyApp.root?.packageName} displayId=${anyApp.displayId}", context)
            return anyApp.displayId
        }

        DiagnosticsLogger.logEvent(TAG, "FOCUS_RESOLUTION_FAILED", "reason=NoApplicationWindow", context)
        return null
    }

    private fun resolveFocusTarget(): FocusTarget? {
        val cachedDisplayId = HomeKeyInterceptorService.lastFocusedDisplayId
        if (cachedDisplayId != null) {
            val (topId, bottomId) = resolveDisplayIds()
            val target = when (cachedDisplayId) {
                topId -> FocusTarget.TOP
                bottomId -> FocusTarget.BOTTOM
                else -> null
            }
            DiagnosticsLogger.logEvent(TAG, "FOCUS_CACHE_HIT", "cachedDisplayId=$cachedDisplayId target=$target", context)
            if (target != null) return target
            DiagnosticsLogger.logEvent(TAG, "FOCUS_CACHE_MISS", "cachedDisplayId=$cachedDisplayId topId=$topId bottomId=$bottomId", context)
        } else {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_CACHE_EMPTY", null, context)
        }

        val focusedDisplayId = resolveFocusedDisplayId() ?: return null
        val (topId, bottomId) = resolveDisplayIds()
        val target = when (focusedDisplayId) {
            topId -> FocusTarget.TOP
            bottomId -> FocusTarget.BOTTOM
            else -> null
        }
        DiagnosticsLogger.logEvent(TAG, "FOCUS_TARGET_RESOLVED", "focusedDisplayId=$focusedDisplayId target=$target", context)
        return target
    }

    private fun resolveAppLabel(pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        return try {
            val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent(TAG, "APP_LABEL_FAILED", "pkg=$pkg error=${e.message}", context)
            null
        }
    }

    fun getTopAppLabel(): String? = resolveAppLabel(getCleanApp(KEY_TOP_APP))

    fun getBottomAppLabel(): String? = resolveAppLabel(getCleanApp(KEY_BOTTOM_APP))

    private fun getInterLaunchDelayMs(): Long {
        val raw = prefs.getInt(KEY_TOP_BOTTOM_LAUNCH_DELAY_MS, DEFAULT_TOP_BOTTOM_LAUNCH_DELAY_MS)
        return raw.coerceIn(0, 500).toLong()
    }

    private fun handleEmptySlot(isTop: Boolean): Boolean {
        val slot = if (isTop) "TOP" else "BOTTOM"
        val mapNothingToHome = prefs.getBoolean(KEY_BOTH_AUTO_NOTHING_TO_HOME, true)
        if (mapNothingToHome) {
            launchDefaultHomeForAuto(isTop)
            DiagnosticsLogger.logEvent(TAG, "EMPTY_SLOT_BEHAVIOR", "slot=$slot behavior=HOME", context)
        } else {
            DiagnosticsLogger.logEvent(TAG, "EMPTY_SLOT_BEHAVIOR", "slot=$slot behavior=NONE", context)
        }
        return true
    }

    private fun resolveEffectiveMainScreen(topAppPkg: String?, bottomAppPkg: String?): MainScreen {
        return when {
            topAppPkg == null && bottomAppPkg != null -> MainScreen.BOTTOM
            bottomAppPkg == null && topAppPkg != null -> MainScreen.TOP
            else -> MainScreen.valueOf(
                prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name
            )
        }
    }

    private suspend fun handleEmptySlotAwait(isTop: Boolean): Boolean {
        val slot = if (isTop) "TOP" else "BOTTOM"
        val mapNothingToHome = prefs.getBoolean(KEY_BOTH_AUTO_NOTHING_TO_HOME, true)
        if (mapNothingToHome) {
            launchDefaultHomeForAutoAwait(isTop)
            DiagnosticsLogger.logEvent(TAG, "EMPTY_SLOT_BEHAVIOR", "slot=$slot behavior=HOME", context)
        } else {
            DiagnosticsLogger.logEvent(TAG, "EMPTY_SLOT_BEHAVIOR", "slot=$slot behavior=NONE", context)
        }
        return true
    }

    private fun launchOnDisplay(isTop: Boolean, intent: Intent) {
        if (isTop) {
            DualScreenLauncher.launchOnTop(context, intent)
        } else {
            DualScreenLauncher.launchOnBottom(context, intent)
        }
    }

    private fun buildDefaultHomeIntent(): Intent? {
        val pm = context.packageManager
        val baseIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = pm.resolveActivity(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = resolved?.activityInfo?.packageName
        if (pkg.isNullOrBlank()) {
            DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_INTENT_FAILED", "reason=NoResolvedPackage", context)
            return null
        }

        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Executes the logic for the "Top Home" action.
     *
     * @param isManualSequence If true, indicates this call is part of a [launchBoth] sequence involving
     * a default home app. In this case, we do NOT return early after the default home redirect,
     * allowing the sequence to proceed to the next step.
     */
    fun launchTop(isManualSequence: Boolean = false) {
        val traceId = nextTraceId()
        trace("TOP_AUTO", traceId, "START", "manual=$isManualSequence")
        val topAppPkg = getCleanApp(KEY_TOP_APP)
        val targetPkg = topAppPkg

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=TOP package=$targetPkg", context)
        try {
            if (targetPkg == null) {
                handleEmptySlot(isTop = true)
                return
            }

            if (targetPkg in SPECIAL_HOME_APPS) {
                DiagnosticsLogger.logEvent("Launcher", "DEFAULT_HOME_REDIRECT", "slot=TOP package=$targetPkg", context)
                launchDefaultHomeOnTop()
                if (!isManualSequence) return
            } else {
                val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
                val launcherApps = getLaunchableApps(context, showAllApps)
                val appToLaunch = launcherApps.find { it.packageName == targetPkg }

                if (appToLaunch != null) {
                    try {
                        DualScreenLauncher.launchOnTop(context, appToLaunch.launchIntent)
                        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_SUCCESS", "slot=TOP package=$targetPkg", context)
                    } catch (e: Exception) {
                        DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=TOP package=$targetPkg message=${e.message}", context)
                    }
                } else {
                    DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=TOP package=$targetPkg message=App not found", context)
                }
            }
        } finally {
            trace("TOP_AUTO", traceId, "END")
        }
    }

    /**
     * Executes the logic for the "Bottom Home" action.
     *
     * @param isManualSequence If true, this is part of a [launchBoth] sequence.
     */
    fun launchBottom(isManualSequence: Boolean = false) {
        val traceId = nextTraceId()
        trace("BOTTOM_AUTO", traceId, "START", "manual=$isManualSequence")
        val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
        val targetPkg = bottomAppPkg

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTTOM package=$targetPkg", context)
        try {
            if (targetPkg == null) {
                handleEmptySlot(isTop = false)
                return
            }

            if (targetPkg in SPECIAL_HOME_APPS) {
                DiagnosticsLogger.logEvent("Launcher", "DEFAULT_HOME_REDIRECT", "slot=BOTTOM package=$targetPkg", context)
                launchDefaultHomeOnBottom()
                if (!isManualSequence) return
            } else {
                val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
                val launcherApps = getLaunchableApps(context, showAllApps)
                val appToLaunch = launcherApps.find { it.packageName == targetPkg }

                if (appToLaunch != null) {
                    try {
                        DualScreenLauncher.launchOnBottom(context, appToLaunch.launchIntent)
                        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_SUCCESS", "slot=BOTTOM package=$targetPkg", context)
                    } catch (e: Exception) {
                        DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTTOM package=$targetPkg message=${e.message}", context)
                    }
                } else {
                    DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTTOM package=$targetPkg message=App not found", context)
                }
            }
        } finally {
            trace("BOTTOM_AUTO", traceId, "END")
        }
    }

    /**
     * Executes the logic for the "Both Screens" action.
     */
    fun launchBoth() {
        val traceId = nextTraceId()
        trace("BOTH_AUTO", traceId, "START")
        scope.launch {
            try {
                val topAppPkg = getCleanApp(KEY_TOP_APP)
                val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
                val mainScreen = resolveEffectiveMainScreen(topAppPkg, bottomAppPkg)
                DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTH packageTop=$topAppPkg packageBottom=$bottomAppPkg mainScreen=$mainScreen", context)
                val interLaunchDelayMs = getInterLaunchDelayMs()

                if (topAppPkg == null && bottomAppPkg == null) {
                    return@launch
                }

                if (mainScreen == MainScreen.TOP) {
                    launchTopAwait()
                    if (interLaunchDelayMs > 0) delay(interLaunchDelayMs)
                    launchBottomAwait()
                } else {
                    launchBottomAwait()
                    if (interLaunchDelayMs > 0) delay(interLaunchDelayMs)
                    launchTopAwait()
                }
            } finally {
                trace("BOTH_AUTO", traceId, "END")
            }
        }
    }

    fun launchFocusAuto() {
        val traceId = nextTraceId()
        trace("FOCUS_AUTO", traceId, "START")
        val focusTarget = resolveFocusTarget()
        if (focusTarget == null) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_AUTO_ABORT", "reason=NoFocusTarget", context)
            Toast.makeText(context, "Focus detection failed", Toast.LENGTH_SHORT).show()
            trace("FOCUS_AUTO", traceId, "END", "result=NoFocusTarget")
            return
        }

        DiagnosticsLogger.logEvent(TAG, "FOCUS_AUTO_RESOLVED", "target=$focusTarget", context)
        when (focusTarget) {
            FocusTarget.TOP -> launchTop()
            FocusTarget.BOTTOM -> launchBottom()
        }
        trace("FOCUS_AUTO", traceId, "END", "target=$focusTarget")
    }

    fun launchFocusTopApp() {
        val focusTarget = resolveFocusTarget()
        if (focusTarget == null) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_TOP_APP_ABORT", "reason=NoFocusTarget", context)
            Toast.makeText(context, "Focus detection failed", Toast.LENGTH_SHORT).show()
            return
        }

        val topAppPkg = getCleanApp(KEY_TOP_APP)
        val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
        val targetPkg = topAppPkg ?: bottomAppPkg
        DiagnosticsLogger.logEvent(TAG, "FOCUS_TOP_APP_TARGET", "target=$focusTarget package=$targetPkg", context)

        if (targetPkg == null) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_TOP_APP_EMPTY", "reason=NoTopOrBottomPackage", context)
            return
        }

        val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
        val launcherApps = getLaunchableApps(context, showAllApps)
        val appToLaunch = launcherApps.find { it.packageName == targetPkg }

        if (appToLaunch != null) {
            try {
                launchOnDisplay(focusTarget == FocusTarget.TOP, appToLaunch.launchIntent)
                DiagnosticsLogger.logEvent(TAG, "FOCUS_TOP_APP_LAUNCH_SUCCESS", "target=$focusTarget package=$targetPkg", context)
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent(TAG, "FOCUS_TOP_APP_LAUNCH_FAILED", "target=$focusTarget package=$targetPkg error=${e.message}", context)
            }
        } else {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_TOP_APP_LAUNCH_FAILED", "target=$focusTarget package=$targetPkg error=AppNotFound", context)
        }
    }

    private fun launchDefaultHomeOnDisplay(isTop: Boolean) {
        val (topDisplayId, bottomDisplayId) = resolveDisplayIds()
        val targetDisplayId = if (isTop) topDisplayId else bottomDisplayId
        val slot = if (isTop) "TOP" else "BOTTOM"
        DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_$slot", "displayId=$targetDisplayId", context)

        if (context is AccessibilityService) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_HACK_USED", "reason=TARGETED_HOME displayId=$targetDisplayId", context)
            FocusHackHelper.requestFocus(context, targetDisplayId) {
                context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
            return
        }

        val homeIntent = buildDefaultHomeIntent()
        if (homeIntent != null) {
            try {
                launchOnDisplay(isTop, homeIntent)
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_LAUNCH", "method=ExplicitIntent", context)
                return
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_LAUNCH_FAILED", "method=ExplicitIntent msg=${e.message}", context)
            }
        }

        DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_FAILED", "reason=ContextNotAccessibilityService", context)
    }

    private fun launchDefaultHomeForAuto(isTop: Boolean) {
        val slot = if (isTop) "TOP" else "BOTTOM"
        if (!isTop) {
            DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_AUTO_ROUTE", "method=TargetedHomePath", context)
            launchDefaultHomeOnDisplay(isTop = false)
            return
        }
        val homeIntent = buildDefaultHomeIntent()
        if (homeIntent != null) {
            try {
                launchOnDisplay(isTop, homeIntent)
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_AUTO_LAUNCH", "method=ExplicitIntent", context)
                return
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_AUTO_LAUNCH_FAILED", "method=ExplicitIntent msg=${e.message}", context)
            }
        }
        launchDefaultHomeOnDisplay(isTop)
    }

    private suspend fun launchDefaultHomeForAutoAwait(isTop: Boolean) {
        val slot = if (isTop) "TOP" else "BOTTOM"
        if (!isTop) {
            DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_AUTO_ROUTE", "method=TargetedHomePathAwait", context)
            launchDefaultHomeOnDisplayAwait(isTop = false)
            return
        }
        val homeIntent = buildDefaultHomeIntent()
        if (homeIntent != null) {
            try {
                launchOnDisplay(isTop, homeIntent)
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_AUTO_LAUNCH", "method=ExplicitIntent", context)
                return
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_AUTO_LAUNCH_FAILED", "method=ExplicitIntent msg=${e.message}", context)
            }
        }
        launchDefaultHomeOnDisplayAwait(isTop)
    }

    private suspend fun launchDefaultHomeOnDisplayAwait(isTop: Boolean) {
        val (topDisplayId, bottomDisplayId) = resolveDisplayIds()
        val targetDisplayId = if (isTop) topDisplayId else bottomDisplayId
        val slot = if (isTop) "TOP" else "BOTTOM"
        DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_AWAIT", "displayId=$targetDisplayId", context)

        if (context is AccessibilityService) {
            val completed = withTimeoutOrNull(1000L) {
                suspendCancellableCoroutine { continuation ->
                    FocusHackHelper.requestFocus(
                        context = context,
                        displayId = targetDisplayId,
                        onAction = {
                            context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        },
                        onComplete = {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    )
                }
            }
            if (completed == null) {
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_AWAIT_TIMEOUT", "displayId=$targetDisplayId", context)
            }
            return
        }

        val homeIntent = buildDefaultHomeIntent()
        if (homeIntent != null) {
            try {
                launchOnDisplay(isTop, homeIntent)
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_LAUNCH", "method=ExplicitIntent", context)
                return
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_LAUNCH_FAILED", "method=ExplicitIntent msg=${e.message}", context)
            }
        }

        DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_${slot}_FAILED", "reason=ContextNotAccessibilityService", context)
    }

    private suspend fun launchTopAwait(isManualSequence: Boolean = false) {
        val topAppPkg = getCleanApp(KEY_TOP_APP)
        val targetPkg = topAppPkg

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=TOP package=$targetPkg", context)

        if (targetPkg == null) {
            handleEmptySlotAwait(isTop = true)
            return
        }

        if (targetPkg in SPECIAL_HOME_APPS) {
            DiagnosticsLogger.logEvent("Launcher", "DEFAULT_HOME_REDIRECT", "slot=TOP package=$targetPkg", context)
            launchDefaultHomeOnDisplayAwait(isTop = true)
            if (!isManualSequence) return
        } else {
            val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
            val launcherApps = getLaunchableApps(context, showAllApps)
            val appToLaunch = launcherApps.find { it.packageName == targetPkg }

            if (appToLaunch != null) {
                try {
                    DualScreenLauncher.launchOnTop(context, appToLaunch.launchIntent)
                    DiagnosticsLogger.logEvent("Launcher", "LAUNCH_SUCCESS", "slot=TOP package=$targetPkg", context)
                } catch (e: Exception) {
                    DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=TOP package=$targetPkg message=${e.message}", context)
                }
            } else {
                DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=TOP package=$targetPkg message=App not found", context)
            }
        }
    }

    private suspend fun launchBottomAwait(isManualSequence: Boolean = false) {
        val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
        val targetPkg = bottomAppPkg

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTTOM package=$targetPkg", context)

        if (targetPkg == null) {
            handleEmptySlotAwait(isTop = false)
            return
        }

        if (targetPkg in SPECIAL_HOME_APPS) {
            DiagnosticsLogger.logEvent("Launcher", "DEFAULT_HOME_REDIRECT", "slot=BOTTOM package=$targetPkg", context)
            launchDefaultHomeOnDisplayAwait(isTop = false)
            if (!isManualSequence) return
        } else {
            val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
            val launcherApps = getLaunchableApps(context, showAllApps)
            val appToLaunch = launcherApps.find { it.packageName == targetPkg }

            if (appToLaunch != null) {
                try {
                    DualScreenLauncher.launchOnBottom(context, appToLaunch.launchIntent)
                    DiagnosticsLogger.logEvent("Launcher", "LAUNCH_SUCCESS", "slot=BOTTOM package=$targetPkg", context)
                } catch (e: Exception) {
                    DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTTOM package=$targetPkg message=${e.message}", context)
                }
            } else {
                DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTTOM package=$targetPkg message=App not found", context)
            }
        }
    }

    fun launchDefaultHomeOnTop() {
        val traceId = nextTraceId()
        trace("TOP_HOME", traceId, "START")
        launchDefaultHomeOnDisplay(isTop = true)
        trace("TOP_HOME", traceId, "END")
    }

    fun launchDefaultHomeOnBottom() {
        val traceId = nextTraceId()
        trace("BOTTOM_HOME", traceId, "START")
        launchDefaultHomeOnDisplay(isTop = false)
        trace("BOTTOM_HOME", traceId, "END")
    }

    fun launchDefaultHomeOnBoth() {
        val traceId = nextTraceId()
        trace("BOTH_HOME", traceId, "START")
        val topAppPkg = getCleanApp(KEY_TOP_APP)
        val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
        val mainScreen = resolveEffectiveMainScreen(topAppPkg, bottomAppPkg)
        val (topDisplayId, bottomDisplayId) = resolveDisplayIds()
        DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_BOTH", "topId=$topDisplayId bottomId=$bottomDisplayId mainScreen=$mainScreen", context)
        val interLaunchDelayMs = getInterLaunchDelayMs()
        scope.launch {
            try {
                if (mainScreen == MainScreen.TOP) {
                    launchDefaultHomeOnDisplayAwait(isTop = true)
                    if (interLaunchDelayMs > 0) delay(interLaunchDelayMs)
                    launchDefaultHomeOnDisplayAwait(isTop = false)
                } else {
                    launchDefaultHomeOnDisplayAwait(isTop = false)
                    if (interLaunchDelayMs > 0) delay(interLaunchDelayMs)
                    launchDefaultHomeOnDisplayAwait(isTop = true)
                }
            } finally {
                trace("BOTH_HOME", traceId, "END")
            }
        }
    }
}
