package xyz.blacksheep.mjolnir.home

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.HomeKeyInterceptorService
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_BOTH_AUTO_NOTHING_TO_HOME
import xyz.blacksheep.mjolnir.KEY_MAIN_SCREEN
import xyz.blacksheep.mjolnir.KEY_SHOW_ALL_APPS
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.model.MainScreen
import xyz.blacksheep.mjolnir.launchers.getLaunchableApps
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher
import xyz.blacksheep.mjolnir.utils.FocusHackHelper
import xyz.blacksheep.mjolnir.settings.settingsPrefs

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

    private enum class FocusTarget { TOP, BOTTOM }

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
        val topAppPkg = getCleanApp(KEY_TOP_APP)
        val targetPkg = topAppPkg
        val (topDisplayId, _) = resolveDisplayIds()

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=TOP package=$targetPkg", context)

        if (targetPkg == null) {
            DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=TOP behavior=NONE", context)
            return
        }

        if (targetPkg in SPECIAL_HOME_APPS) {
            DiagnosticsLogger.logEvent("Launcher", "DEFAULT_HOME_REDIRECT", "slot=TOP package=$targetPkg", context)
            if (context is AccessibilityService) {
                DiagnosticsLogger.logEvent(TAG, "FOCUS_HACK_USED", "reason=TARGETED_HOME displayId=$topDisplayId", context)
                FocusHackHelper.requestFocus(context, topDisplayId) {
                    context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
            } else {
                DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "msg=Context is not AccessibilityService for FocusHack", context)
            }
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

    /**
     * Executes the logic for the "Bottom Home" action.
     *
     * @param isManualSequence If true, this is part of a [launchBoth] sequence.
     */
    fun launchBottom(isManualSequence: Boolean = false) {
        val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
        val targetPkg = bottomAppPkg
        val (_, bottomDisplayId) = resolveDisplayIds()

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTTOM package=$targetPkg", context)

        if (targetPkg == null) {
            DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=BOTTOM behavior=NONE", context)
            return
        }

        if (targetPkg in SPECIAL_HOME_APPS) {
            DiagnosticsLogger.logEvent("Launcher", "DEFAULT_HOME_REDIRECT", "slot=BOTTOM package=$targetPkg", context)
            if (context is AccessibilityService) {
                DiagnosticsLogger.logEvent(TAG, "FOCUS_HACK_USED", "reason=TARGETED_HOME displayId=$bottomDisplayId", context)
                FocusHackHelper.requestFocus(context, bottomDisplayId) {
                    context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
            } else {
                DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "msg=Context is not AccessibilityService for FocusHack", context)
            }
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

    /**
     * Executes the logic for the "Both Screens" action.
     */
    fun launchBoth() {
        scope.launch {
            val topAppPkg = getCleanApp(KEY_TOP_APP)
            val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
            val mainScreen = MainScreen.valueOf(prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)
            val (topDisplayId, bottomDisplayId) = resolveDisplayIds()
            val mapNothingToHome = prefs.getBoolean(KEY_BOTH_AUTO_NOTHING_TO_HOME, true)
            DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTH packageTop=$topAppPkg packageBottom=$bottomAppPkg mainScreen=$mainScreen", context)

            if (topAppPkg == null && bottomAppPkg == null) {
                return@launch
            }

            if (topAppPkg == null || bottomAppPkg == null) {
                if (topAppPkg == null) {
                    if (mapNothingToHome) {
                        launchDefaultHomeOnTop()
                        DiagnosticsLogger.logEvent(TAG, "EMPTY_SLOT_BEHAVIOR", "slot=TOP behavior=HOME", context)
                    } else {
                        DiagnosticsLogger.logEvent(TAG, "EMPTY_SLOT_BEHAVIOR", "slot=TOP behavior=NONE", context)
                    }
                    launchBottom()
                } else {
                    launchTop()
                    if (mapNothingToHome) {
                        launchDefaultHomeOnBottom()
                        DiagnosticsLogger.logEvent(TAG, "EMPTY_SLOT_BEHAVIOR", "slot=BOTTOM behavior=HOME", context)
                    } else {
                        DiagnosticsLogger.logEvent(TAG, "EMPTY_SLOT_BEHAVIOR", "slot=BOTTOM behavior=NONE", context)
                    }
                }
                return@launch
            }

            val topIsSpecial = topAppPkg in SPECIAL_HOME_APPS
            val bottomIsSpecial = bottomAppPkg in SPECIAL_HOME_APPS

            if (topIsSpecial || bottomIsSpecial) {
                DiagnosticsLogger.logEvent("Launcher", "MANUAL_SEQUENCE", "topIsSpecial=$topIsSpecial bottomIsSpecial=$bottomIsSpecial", context)
                
                if (topIsSpecial && !bottomIsSpecial) {
                    launchTop(isManualSequence = true)
                    delay(500)
                    launchBottom()
                } else if (!topIsSpecial && bottomIsSpecial) {
                    launchBottom(isManualSequence = true)
                    delay(500)
                    launchTop()
                } else {
                    if (mainScreen == MainScreen.TOP) {
                        launchBottom(isManualSequence = true)
                        delay(500)
                        launchTop(isManualSequence = true)
                    } else {
                        launchTop(isManualSequence = true)
                        delay(500)
                        launchBottom(isManualSequence = true)
                    }
                }
                return@launch
            }

            val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
            val launcherApps = getLaunchableApps(context, showAllApps)
            val topApp = launcherApps.find { it.packageName == topAppPkg }
            val bottomApp = launcherApps.find { it.packageName == bottomAppPkg }

            if (topApp != null && bottomApp != null) {
                DualScreenLauncher.launchOnDualScreens(context, topApp.launchIntent, bottomApp.launchIntent, mainScreen)
            }
        }
    }

    fun launchFocusAuto() {
        val focusTarget = resolveFocusTarget()
        if (focusTarget == null) {
            DiagnosticsLogger.logEvent(TAG, "FOCUS_AUTO_ABORT", "reason=NoFocusTarget", context)
            Toast.makeText(context, "Focus detection failed", Toast.LENGTH_SHORT).show()
            return
        }

        DiagnosticsLogger.logEvent(TAG, "FOCUS_AUTO_RESOLVED", "target=$focusTarget", context)
        when (focusTarget) {
            FocusTarget.TOP -> launchTop()
            FocusTarget.BOTTOM -> launchBottom()
        }
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

    fun launchDefaultHomeOnTop() {
        val (topDisplayId, _) = resolveDisplayIds()
        DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_TOP", "displayId=$topDisplayId", context)

        val homeIntent = buildDefaultHomeIntent()
        if (homeIntent != null) {
            try {
                launchOnDisplay(true, homeIntent)
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_TOP_LAUNCH", "method=ExplicitIntent", context)
                return
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_TOP_LAUNCH_FAILED", "method=ExplicitIntent msg=${e.message}", context)
            }
        }

        if (context is AccessibilityService) {
            DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_TOP_LAUNCH", "method=GlobalHome", context)
            context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_TOP_FAILED", "reason=ContextNotAccessibilityService", context)
        }
    }

    fun launchDefaultHomeOnBottom() {
        val (_, bottomDisplayId) = resolveDisplayIds()
        DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_BOTTOM", "displayId=$bottomDisplayId", context)

        val homeIntent = buildDefaultHomeIntent()
        if (homeIntent != null) {
            try {
                launchOnDisplay(false, homeIntent)
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_BOTTOM_LAUNCH", "method=ExplicitIntent", context)
                return
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_BOTTOM_LAUNCH_FAILED", "method=ExplicitIntent msg=${e.message}", context)
            }
        }

        if (context is AccessibilityService) {
            DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_BOTTOM_LAUNCH", "method=GlobalHome", context)
            context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_BOTTOM_FAILED", "reason=ContextNotAccessibilityService", context)
        }
    }

    fun launchDefaultHomeOnBoth() {
        val (topDisplayId, bottomDisplayId) = resolveDisplayIds()
        if (context is AccessibilityService) {
            DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_BOTH", "topId=$topDisplayId bottomId=$bottomDisplayId", context)
            DiagnosticsLogger.logEvent(TAG, "FOCUS_HACK_USED", "reason=TARGETED_HOME displayId=$topDisplayId", context)
            FocusHackHelper.requestFocus(context, topDisplayId) {
                context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
            scope.launch {
                delay(250)
                DiagnosticsLogger.logEvent(TAG, "FOCUS_HACK_USED", "reason=TARGETED_HOME displayId=$bottomDisplayId", context)
                FocusHackHelper.requestFocus(context, bottomDisplayId) {
                    context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
            }
        } else {
            DiagnosticsLogger.logEvent(TAG, "DEFAULT_HOME_BOTH_FAILED", "reason=ContextNotAccessibilityService", context)
        }
    }
}
