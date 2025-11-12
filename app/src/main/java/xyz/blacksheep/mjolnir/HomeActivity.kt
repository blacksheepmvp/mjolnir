package xyz.blacksheep.mjolnir

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.edit

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(SteamFileGenActivity.PREFS_NAME, MODE_PRIVATE)
        val failureCount = prefs.getInt(KEY_LAUNCH_FAILURE_COUNT, 0)

        if (failureCount >= 3) {
            // Too many failures, clear settings and go to stock launcher settings
            prefs.edit {
                remove(SteamFileGenActivity.KEY_TOP_APP)
                remove(SteamFileGenActivity.KEY_BOTTOM_APP)
                remove(KEY_LAUNCH_FAILURE_COUNT)
            }
            Toast.makeText(this, "Resetting home launcher due to repeated errors.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
            finish()
            return
        }

        val topAppPkg = prefs.getString(SteamFileGenActivity.KEY_TOP_APP, null)
        val bottomAppPkg = prefs.getString(SteamFileGenActivity.KEY_BOTTOM_APP, null)
        val showAllApps = prefs.getBoolean(SteamFileGenActivity.KEY_SHOW_ALL_APPS, false)
        val mainScreen = MainScreen.valueOf(prefs.getString(SteamFileGenActivity.KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)

        if (topAppPkg != null && bottomAppPkg != null) {
            val launcherApps = getLaunchableApps(this, showAllApps)
            val topApp = launcherApps.find { it.packageName == topAppPkg }
            val bottomApp = launcherApps.find { it.packageName == bottomAppPkg }

            if (topApp != null && bottomApp != null) {
                val success = DualScreenLauncher.launchOnDualScreens(this, topApp.launchIntent, bottomApp.launchIntent, mainScreen)
                if (success) {
                    // Reset failure count on success
                    prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, 0) }
                } else {
                    // Increment failure count
                    prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, failureCount + 1) }
                }
            } else {
                // If for some reason the apps are not found, go to settings
                prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, failureCount + 1) }
                launchSettings()
            }
        } else {
            launchSettings()
        }
        finish()
    }

    private fun launchSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_settings", true)
        }
        startActivity(intent)
    }

    companion object {
        const val KEY_LAUNCH_FAILURE_COUNT = "launch_failure_count"
    }
}
