package xyz.blacksheep.mjolnir

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import xyz.blacksheep.mjolnir.settings.MainScreen
import xyz.blacksheep.mjolnir.settings.getLaunchableApps
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val failureCount = prefs.getInt(KEY_LAUNCH_FAILURE_COUNT, 0)

        if (failureCount >= 3) {
            // Too many failures, clear settings and go to stock launcher settings
            prefs.edit {
                remove(KEY_TOP_APP)
                remove(KEY_BOTTOM_APP)
                remove(KEY_LAUNCH_FAILURE_COUNT)
            }
            Toast.makeText(this, "Resetting home launcher due to repeated errors.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
            finish()
            return
        }

        val topAppPkg = prefs.getString(KEY_TOP_APP, null)
        val bottomAppPkg = prefs.getString(KEY_BOTTOM_APP, null)
        val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
        val mainScreen = MainScreen.valueOf(prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)

        // Updated Logic: Only need ONE app to be set
        if (topAppPkg != null || bottomAppPkg != null) {
            val launcherApps = getLaunchableApps(this, showAllApps)

            // SPECIAL CASE: Only 1 app is set
            if (topAppPkg == null || bottomAppPkg == null) {
                 val targetPkg = topAppPkg ?: bottomAppPkg
                 val appToLaunch = launcherApps.find { it.packageName == targetPkg }
                 if (appToLaunch != null) {
                     // Launch single app on the main screen
                     if (mainScreen == MainScreen.TOP) {
                         DualScreenLauncher.launchOnTop(this, appToLaunch.launchIntent)
                     } else {
                         DualScreenLauncher.launchOnBottom(this, appToLaunch.launchIntent)
                     }
                     prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, 0) }
                 } else {
                      prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, failureCount + 1) }
                      launchSettings()
                 }
            } else {
                // BOTH apps are set
                val topApp = launcherApps.find { it.packageName == topAppPkg }
                val bottomApp = launcherApps.find { it.packageName == bottomAppPkg }

                if (topApp != null && bottomApp != null) {
                    val success = DualScreenLauncher.launchOnDualScreens(this, topApp.launchIntent, bottomApp.launchIntent, mainScreen)
                    if (success) {
                        prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, 0) }
                    } else {
                        prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, failureCount + 1) }
                    }
                } else {
                    prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, failureCount + 1) }
                    launchSettings()
                }
            }
        } else {
            // NO apps are set
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
}
