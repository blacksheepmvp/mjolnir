package xyz.blacksheep.mjolnir

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HomeKeyInterceptorService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(SteamFileGenActivity.PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isInterceptionActive = prefs.getBoolean(SteamFileGenActivity.KEY_HOME_INTERCEPTION_ACTIVE, false)

        if (isInterceptionActive && event.keyCode == KeyEvent.KEYCODE_HOME && event.action == KeyEvent.ACTION_UP) {
            handleHomeKey()
            return true // Consume the event
        }
        return super.onKeyEvent(event)
    }

    private fun handleHomeKey() {
        val topAppPkg = prefs.getString(SteamFileGenActivity.KEY_TOP_APP, null)
        val bottomAppPkg = prefs.getString(SteamFileGenActivity.KEY_BOTTOM_APP, null)

        if (topAppPkg != null && bottomAppPkg != null) {
            val showAllApps = prefs.getBoolean(SteamFileGenActivity.KEY_SHOW_ALL_APPS, false)
            val mainScreen = MainScreen.valueOf(prefs.getString(SteamFileGenActivity.KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)
            val launcherApps = getLaunchableApps(this, showAllApps)
            val topApp = launcherApps.find { it.packageName == topAppPkg }
            val bottomApp = launcherApps.find { it.packageName == bottomAppPkg }

            if (topApp != null && bottomApp != null) {
                serviceScope.launch {
                    DualScreenLauncher.launchOnDualScreens(this@HomeKeyInterceptorService, topApp.launchIntent, bottomApp.launchIntent, mainScreen)
                }
            }
        }
    }
/*
    private fun handleSwapScreen() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.displays

        if (displays.size < 2) return // Not a dual-screen device

        val topDisplayId = displays[0].displayId
        val bottomDisplayId = displays[1].displayId

        val topAppPkg = getTopPackageOnDisplay(topDisplayId)
        val bottomAppPkg = getTopPackageOnDisplay(bottomDisplayId)

        if (topAppPkg != null && bottomAppPkg != null) {
            val topIntent = packageManager.getLaunchIntentForPackage(topAppPkg)
            val bottomIntent = packageManager.getLaunchIntentForPackage(bottomAppPkg)

            if (topIntent != null && bottomIntent != null) {
                val mainScreen = MainScreen.valueOf(prefs.getString(SteamFileGenActivity.KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)

                serviceScope.launch {
                    DualScreenLauncher.launchOnDualScreens(
                        this@HomeKeyInterceptorService,
                        topIntent = bottomIntent, // Launch bottom app's intent on the top screen
                        bottomIntent = topIntent,   // Launch top app's intent on the bottom screen
                        mainScreen = mainScreen
                    )
                }
            }
        }
    }

    private fun getTopPackageOnDisplay(displayId: Int): String? {
        return windows
            .filter { it.displayId == displayId && it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root != null }
            .maxByOrNull { it.layer }
            ?.root?.packageName?.toString()
    }
*/

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // We can listen for window changes to be more reactive if needed, but polling on demand is simpler.
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
/*        if (key == SteamFileGenActivity.KEY_SWAP_SCREENS_REQUESTED) {
            val shouldSwap = sharedPreferences?.getBoolean(key, false) ?: false
            if (shouldSwap) {
                handleSwapScreen()
                // Reset the flag immediately after handling
                prefs.edit { putBoolean(SteamFileGenActivity.KEY_SWAP_SCREENS_REQUESTED, false) }
            }
        }*/
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        serviceScope.cancel()
    }
}
