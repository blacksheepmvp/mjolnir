package xyz.blacksheep.mjolnir

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.services.KeepAliveService
import xyz.blacksheep.mjolnir.settings.*
import xyz.blacksheep.mjolnir.ui.theme.*
import xyz.blacksheep.mjolnir.utils.*

class HomeKeyInterceptorService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences
    private var instance: HomeKeyInterceptorService? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        // Start the foreground KeepAliveService to keep this process alive.
        // A small delay is required on some devices to avoid a crash.
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, KeepAliveService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }, 250)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)

        // The AYN button on the Thor also reports as KEYCODE_HOME.
        // We need to check the scancode to differentiate them.
        // Home button scancode is 102, AYN button is 194.
        val isActualHomeButton = event.scanCode == 102

        if (isInterceptionActive && event.keyCode == KeyEvent.KEYCODE_HOME && isActualHomeButton && event.action == KeyEvent.ACTION_UP) {
            handleHomeKey()
            return true // Consume the event
        }
        return super.onKeyEvent(event)
    }

    private fun handleHomeKey() {
        val topAppPkg = prefs.getString(KEY_TOP_APP, null)
        val bottomAppPkg = prefs.getString(KEY_BOTTOM_APP, null)

        if (topAppPkg != null && bottomAppPkg != null) {
            val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
            val mainScreen = MainScreen.valueOf(prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // We can listen for window changes to be more reactive if needed, but polling on demand is simpler.
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        /*if (key == SteamFileGenActivity.KEY_SWAP_SCREENS_REQUESTED) {
            val shouldSwap = sharedPreferences?.getBoolean(key, false) ?: false
            if (shouldSwap) {
                handleSwapScreen()
                // Reset the flag immediately after handling
                prefs.edit { putBoolean(SteamFileGenActivity.KEY_SWAP_SCREENS_REQUESTED, false) }
            }
        }*/
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        instance = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Accessibility service unbound")
        instance = null
        stopKeepAliveService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopKeepAliveService()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        serviceScope.cancel()
    }

    private fun stopKeepAliveService() {
        try {
            val intent = Intent(this, KeepAliveService::class.java)
            stopService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop KeepAliveService", e)
        }
    }

    companion object {
        private const val TAG = "HomeKeyInterceptorService"
    }
}
