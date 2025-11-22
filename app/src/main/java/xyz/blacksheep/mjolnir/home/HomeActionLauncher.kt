package xyz.blacksheep.mjolnir.home

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    /**
     * Helper to retrieve a sanitized package name from preferences.
     * Treats the sentinel value "NOTHING" as null.
     */
    private fun getCleanApp(key: String): String? {
        val pkg = prefs.getString(key, null)
        return if (pkg == "NOTHING") null else pkg
    }

    /**
     * Executes the logic for the "Top Home" action.
     *
     * **Fallback Logic:**
     * - If a Top App is assigned, it launches on the Top Screen.
     * - If NO Top App is assigned, but a Bottom App IS, it launches the Bottom App on the Top Screen.
     * - If neither is assigned, it logs an "EMPTY_SLOT" event and does nothing.
     */
    fun launchTop() {
        val topAppPkg = getCleanApp(KEY_TOP_APP)
        val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)

        // If top is assigned, use it. If not, but bottom IS assigned, use bottom.
        val targetPkg = topAppPkg ?: bottomAppPkg

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=TOP package=$targetPkg", context)
        
        if (targetPkg == null) {
            DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=TOP behavior=NONE", context)
            return
        }

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

    /**
     * Executes the logic for the "Bottom Home" action.
     *
     * **Fallback Logic:**
     * - If a Bottom App is assigned, it launches on the Bottom Screen.
     * - If NO Bottom App is assigned, but a Top App IS, it launches the Top App on the Bottom Screen.
     * - If neither is assigned, it logs an "EMPTY_SLOT" event and does nothing.
     */
    fun launchBottom() {
        val topAppPkg = getCleanApp(KEY_TOP_APP)
        val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)

        // If bottom is assigned, use it. If not, but top IS assigned, use top.
        val targetPkg = bottomAppPkg ?: topAppPkg

        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTTOM package=$targetPkg", context)
        
        if (targetPkg == null) {
            DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=BOTTOM behavior=NONE", context)
            return
        }

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

    /**
     * Executes the logic for the "Both Screens" action.
     *
     * **Behavior:**
     * - If both slots have apps assigned: Launches both simultaneously using [DualScreenLauncher.launchOnDualScreens].
     * - If only one slot has an app assigned: Launches that single app on the user's preferred [MainScreen].
     * - If neither is assigned: Does nothing.
     *
     * **Focus:**
     * The focus order is determined by [KEY_MAIN_SCREEN]. The "Main" screen app is launched *last* to ensure it receives input focus.
     */
    fun launchBoth() {
        scope.launch {
            val topAppPkg = getCleanApp(KEY_TOP_APP)
            val bottomAppPkg = getCleanApp(KEY_BOTTOM_APP)
            val mainScreen = MainScreen.valueOf(prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)
            DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTH packageTop=$topAppPkg packageBottom=$bottomAppPkg mainScreen=$mainScreen", context)

            if (topAppPkg == null && bottomAppPkg == null) {
                DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=BOTH behavior=NONE", context)
                return@launch
            }

            val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
            val launcherApps = getLaunchableApps(context, showAllApps)

            // Special case: Only one app assigned
            if (topAppPkg == null || bottomAppPkg == null) {
                val targetPkg = topAppPkg ?: bottomAppPkg
                val appToLaunch = launcherApps.find { it.packageName == targetPkg }
                
                if (appToLaunch != null) {
                    try {
                        // Launch the single app on the MAIN screen
                        if (mainScreen == MainScreen.TOP) {
                            DualScreenLauncher.launchOnTop(context, appToLaunch.launchIntent)
                        } else {
                            DualScreenLauncher.launchOnBottom(context, appToLaunch.launchIntent)
                        }
                        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_SUCCESS", "slot=BOTH (Single) package=$targetPkg screen=$mainScreen", context)
                    } catch (e: Exception) {
                        DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTH (Single) package=$targetPkg message=${e.message}", context)
                    }
                } else {
                     DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTH (Single) package=$targetPkg message=App not found", context)
                }
                return@launch
            }

            // Standard behavior: Both apps assigned
            val topApp = launcherApps.find { it.packageName == topAppPkg }
            val bottomApp = launcherApps.find { it.packageName == bottomAppPkg }

            if (topApp != null && bottomApp != null) {
                try {
                    DualScreenLauncher.launchOnDualScreens(context, topApp.launchIntent, bottomApp.launchIntent, mainScreen)
                    DiagnosticsLogger.logEvent("Launcher", "LAUNCH_SUCCESS", "slot=BOTH packageTop=$topAppPkg packageBottom=$bottomAppPkg", context)
                } catch (e: Exception) {
                    DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTH packageTop=$topAppPkg packageBottom=$bottomAppPkg message=${e.message}", context)
                }
            } else {
                val notFound = mutableListOf<String>()
                if(topApp == null) notFound.add("TOP")
                if(bottomApp == null) notFound.add("BOTTOM")
                DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTH message=App not found in slots: ${notFound.joinToString()}", context)
            }
        }
    }
}
