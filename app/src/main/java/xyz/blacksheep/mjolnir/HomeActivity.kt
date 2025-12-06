package xyz.blacksheep.mjolnir

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import xyz.blacksheep.mjolnir.onboarding.OnboardingActivity
import xyz.blacksheep.mjolnir.settings.MainScreen
import xyz.blacksheep.mjolnir.settings.getLaunchableApps
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        if (!isConfigurationValid()) {
            wipeConfigAndLaunchOnboarding()
            return
        }

        val failureCount = prefs.getInt(KEY_LAUNCH_FAILURE_COUNT, 0)

        if (failureCount >= 3) {
            prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, 0) }
            Toast.makeText(this, "Repeated launch failures. Resetting configuration.", Toast.LENGTH_LONG).show()
            wipeConfigAndLaunchOnboarding()
            return
        }

        val topAppPkg = prefs.getString(KEY_TOP_APP, null)
        val bottomAppPkg = prefs.getString(KEY_BOTTOM_APP, null)
        val showAllApps = prefs.getBoolean(KEY_SHOW_ALL_APPS, false)
        val mainScreen = MainScreen.valueOf(prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name) ?: MainScreen.TOP.name)

        if (topAppPkg != null || bottomAppPkg != null) {
            val launcherApps = getLaunchableApps(this, showAllApps)

            if (topAppPkg == null || bottomAppPkg == null) {
                 val targetPkg = topAppPkg ?: bottomAppPkg
                 val appToLaunch = launcherApps.find { it.packageName == targetPkg }
                 if (appToLaunch != null) {
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
            launchSettings()
        }
        finish()
    }

    private fun isConfigurationValid(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
        val topApp = prefs.getString(KEY_TOP_APP, null)
        val bottomApp = prefs.getString(KEY_BOTTOM_APP, null)
        val SPECIAL_HOME_APPS = setOf("com.android.launcher3", "com.odin.odinlauncher")

        if (topApp == null && bottomApp == null) return false

        if (isInterceptionActive) {
            if (!isAccessibilityServiceEnabled()) return false

            if (topApp in SPECIAL_HOME_APPS) {
                val defaultHome = getCurrentDefaultHomePackage(this)
                if (defaultHome != topApp) return false
            }
        }

        return true
    }

    private fun getCurrentDefaultHomePackage(context: Context): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo: ResolveInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, HomeKeyInterceptorService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun wipeConfigAndLaunchOnboarding() {
        Toast.makeText(this, "Mjolnir config invalid, resetting.", Toast.LENGTH_LONG).show()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            remove(KEY_TOP_APP)
            remove(KEY_BOTTOM_APP)
            remove(KEY_HOME_INTERCEPTION_ACTIVE)
        }
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }

    private fun launchSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_settings", true)
        }
        startActivity(intent)
    }
}
