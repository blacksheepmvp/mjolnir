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
 * The "Brains" of the home action system.
 * This class decides WHAT to do based on user preferences.
 * It delegates the HOW of launching to the DualScreenLauncher utility.
 */
class HomeActionLauncher(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)

    fun launchTop() {
        val topAppPkg = prefs.getString(KEY_TOP_APP, null)
        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=TOP package=$topAppPkg", context)
        if (topAppPkg == null) {
            DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=TOP behavior=NONE", context)
            return
        }

        val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
        val launcherApps = getLaunchableApps(context, showAllApps)
        val topApp = launcherApps.find { it.packageName == topAppPkg }

        if (topApp != null) {
            try {
                DualScreenLauncher.launchOnTop(context, topApp.launchIntent)
                DiagnosticsLogger.logEvent("Launcher", "LAUNCH_SUCCESS", "slot=TOP package=$topAppPkg", context)
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=TOP package=$topAppPkg message=${e.message}", context)
            }
        } else {
            DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=TOP package=$topAppPkg message=App not found", context)
        }
    }

    fun launchBottom() {
        val bottomAppPkg = prefs.getString(KEY_BOTTOM_APP, null)
        DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTTOM package=$bottomAppPkg", context)
        if (bottomAppPkg == null) {
            DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=BOTTOM behavior=NONE", context)
            return
        }

        val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
        val launcherApps = getLaunchableApps(context, showAllApps)
        val bottomApp = launcherApps.find { it.packageName == bottomAppPkg }

        if (bottomApp != null) {
            try {
                DualScreenLauncher.launchOnBottom(context, bottomApp.launchIntent)
                DiagnosticsLogger.logEvent("Launcher", "LAUNCH_SUCCESS", "slot=BOTTOM package=$bottomAppPkg", context)
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTTOM package=$bottomAppPkg message=${e.message}", context)
            }
        } else {
            DiagnosticsLogger.logEvent("Error", "LAUNCH_FAILED", "slot=BOTTOM package=$bottomAppPkg message=App not found", context)
        }
    }

    fun launchBoth() {
        scope.launch {
            val topAppPkg = prefs.getString(KEY_TOP_APP, null)
            val bottomAppPkg = prefs.getString(KEY_BOTTOM_APP, null)
            val mainScreen = MainScreen.valueOf(prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)
            DiagnosticsLogger.logEvent("Launcher", "LAUNCH_ATTEMPT", "slot=BOTH packageTop=$topAppPkg packageBottom=$bottomAppPkg mainScreen=$mainScreen", context)

            if (topAppPkg == null || bottomAppPkg == null) {
                DiagnosticsLogger.logEvent("Launcher", "EMPTY_SLOT_ACTIVATED", "slot=BOTH behavior=NONE", context)
                return@launch
            }

            val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
            val launcherApps = getLaunchableApps(context, showAllApps)
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
