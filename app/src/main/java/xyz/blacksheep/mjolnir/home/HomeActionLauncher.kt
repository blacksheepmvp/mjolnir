package xyz.blacksheep.mjolnir.home

import android.content.Context
import android.util.Log
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
        Log.d("Mjolnir", "launchTop action requested")
        val topAppPkg = prefs.getString(KEY_TOP_APP, null)
        if (topAppPkg == null) {
            Log.w("Mjolnir", "Top app not set, cannot launch.")
            return
        }

        val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
        val launcherApps = getLaunchableApps(context, showAllApps)
        val topApp = launcherApps.find { it.packageName == topAppPkg }

        if (topApp != null) {
            DualScreenLauncher.launchOnTop(context, topApp.launchIntent)
        } else {
            Log.e("Mjolnir", "Could not find top launcher app: $topAppPkg")
        }
    }

    fun launchBottom() {
        Log.d("Mjolnir", "launchBottom action requested")
        val bottomAppPkg = prefs.getString(KEY_BOTTOM_APP, null)
        if (bottomAppPkg == null) {
            Log.w("Mjolnir", "Bottom app not set, cannot launch.")
            return
        }

        val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
        val launcherApps = getLaunchableApps(context, showAllApps)
        val bottomApp = launcherApps.find { it.packageName == bottomAppPkg }

        if (bottomApp != null) {
            DualScreenLauncher.launchOnBottom(context, bottomApp.launchIntent)
        } else {
            Log.e("Mjolnir", "Could not find bottom launcher app: $bottomAppPkg")
        }
    }

    fun launchBoth() {
        Log.d("Mjolnir", "launchBoth action requested")
        scope.launch {
            val topAppPkg = prefs.getString(KEY_TOP_APP, null)
            val bottomAppPkg = prefs.getString(KEY_BOTTOM_APP, null)
            val mainScreen = MainScreen.valueOf(prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)

            if (topAppPkg == null || bottomAppPkg == null) {
                Log.w("Mjolnir", "Top or bottom app not set for dual launch.")
                return@launch
            }

            val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
            val launcherApps = getLaunchableApps(context, showAllApps)
            val topApp = launcherApps.find { it.packageName == topAppPkg }
            val bottomApp = launcherApps.find { it.packageName == bottomAppPkg }

            if (topApp != null && bottomApp != null) {
                DualScreenLauncher.launchOnDualScreens(context, topApp.launchIntent, bottomApp.launchIntent, mainScreen)
            } else {
                Log.e("Mjolnir", "Could not find one or both apps for dual launch.")
            }
        }
    }
}
