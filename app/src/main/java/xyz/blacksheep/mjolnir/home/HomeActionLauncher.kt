package xyz.blacksheep.mjolnir.home

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_MAIN_SCREEN
import xyz.blacksheep.mjolnir.KEY_SHOW_ALL_APPS
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.settings.MainScreen
import xyz.blacksheep.mjolnir.settings.getLaunchableApps
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher
import xyz.blacksheep.mjolnir.utils.FocusHackHelper

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

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val TOP_DISPLAY_ID = 0
    private val BOTTOM_DISPLAY_ID = 1
    private val SPECIAL_HOME_APPS = setOf("com.android.launcher3", "com.odin.odinlauncher")

    private fun getCleanApp(key: String): String? {
        val pkg = prefs.getString(key, null)
        return if (pkg == "NOTHING") null else pkg
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
        val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
        val targetPkg = topAppPkg ?: bottomAppPkg

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=TOP package=$targetPkg", context)

        if (targetPkg == null) {
            DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=TOP behavior=NONE", context)
            return
        }

        if (targetPkg in SPECIAL_HOME_APPS) {
            DiagnosticsLogger.logEvent("Launcher", "DEFAULT_HOME_REDIRECT", "slot=TOP package=$targetPkg", context)
            if (context is AccessibilityService) {
                FocusHackHelper.requestFocus(context, TOP_DISPLAY_ID) {
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
        val topAppPkg = getCleanApp(KEY_TOP_APP)
        val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
        val targetPkg = bottomAppPkg ?: topAppPkg

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTTOM package=$targetPkg", context)

        if (targetPkg == null) {
            DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=BOTTOM behavior=NONE", context)
            return
        }

        if (targetPkg in SPECIAL_HOME_APPS) {
            DiagnosticsLogger.logEvent("Launcher", "DEFAULT_HOME_REDIRECT", "slot=BOTTOM package=$targetPkg", context)
            if (context is AccessibilityService) {
                FocusHackHelper.requestFocus(context, BOTTOM_DISPLAY_ID) {
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
            DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTH packageTop=$topAppPkg packageBottom=$bottomAppPkg mainScreen=$mainScreen", context)

            if (topAppPkg == null && bottomAppPkg == null) {
                return@launch
            }

            if (topAppPkg == null || bottomAppPkg == null) {
                val targetPkg = topAppPkg ?: bottomAppPkg
                
                if (targetPkg != null && targetPkg in SPECIAL_HOME_APPS) {
                    DiagnosticsLogger.logEvent("Launcher", "DEFAULT_HOME_REDIRECT", "slot=BOTH (Single) package=$targetPkg", context)
                    if (context is AccessibilityService) {
                        val targetDisplayId = if (mainScreen == MainScreen.TOP) TOP_DISPLAY_ID else BOTTOM_DISPLAY_ID
                        FocusHackHelper.requestFocus(context, targetDisplayId) {
                             context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        }
                    }
                    return@launch
                }

                val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
                val launcherApps = getLaunchableApps(context, showAllApps)
                val appToLaunch = launcherApps.find { it.packageName == targetPkg }
                
                if (appToLaunch != null) {
                    if (mainScreen == MainScreen.TOP) DualScreenLauncher.launchOnTop(context, appToLaunch.launchIntent) else DualScreenLauncher.launchOnBottom(context, appToLaunch.launchIntent)
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
}
