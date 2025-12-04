package xyz.blacksheep.mjolnir

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.edit
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger

/**
 * A Quick Settings Tile that allows the user to toggle the Home Button Interception feature on/off.
 *
 * **Behavior:**
 * - **If Accessibility Service is OFF:** Tapping the tile takes the user to Android Accessibility Settings.
 * - **If Accessibility Service is ON:** Tapping the tile toggles the internal `KEY_HOME_INTERCEPTION_ACTIVE` preference.
 *
 * **State Indication:**
 * - **Active (Lit up):** Service is running AND interception is enabled.
 * - **Inactive (Dim):** Service is running BUT interception is paused OR service is disabled.
 *
 * **Note for 0.2.5b:**
 * A separate `DualScreenshotTileService` is planned for DSS triggering if needed, but the spec
 * focuses on the notification action. This tile remains focused on Home Interception.
 */
class MjolnirHomeTileService : TileService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        DiagnosticsLogger.logEvent("Tile", "TILE_CLICKED", context = this)

        if (isAccessibilityServiceEnabled(this)) {
            // The service is enabled, so the tile functions as an on/off switch.
            val wantsToEnable = !prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)

            if (wantsToEnable) {
                // --- "NO-HOME" INVARIANT CHECK ---
                val topApp = prefs.getString(KEY_TOP_APP, null)
                val bottomApp = prefs.getString(KEY_BOTTOM_APP, null)

                if (topApp.isNullOrEmpty() && bottomApp.isNullOrEmpty()) {
                    // Refuse to enable if no apps are set.
                    Toast.makeText(
                        this,
                        "Set at least one Home app before enabling Home capture.",
                        Toast.LENGTH_LONG
                    ).show()
                    // Ensure the state remains false. The tile will update automatically.
                    if (prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)) {
                        prefs.edit { putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false) }
                    }
                    return // Exit early
                }
            }

            // Proceed with toggling the value
            prefs.edit { putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, wantsToEnable) }
            DiagnosticsLogger.logEvent("Tile", "TOGGLE_INTERCEPTION", "newValue=$wantsToEnable from=tile", this)

        } else {
            // The service is not enabled, so take the user to settings to enable it.
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
                DiagnosticsLogger.logEvent("Tile", "OPEN_ACCESSIBILITY_SETTINGS", context = this)
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent("Error", "TILE_ACTION_FAILED", "message=${e.message}", this)
            }
        }
    }

    private fun updateTileState() {
        try {
            qsTile?.let {
                val isInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
                val accessibilityEnabled = isAccessibilityServiceEnabled(this)
                // The tile is active only if the feature is on AND the service is running.
                it.state = if (isInterceptionActive && accessibilityEnabled) {
                    Tile.STATE_ACTIVE
                } else {
                    Tile.STATE_INACTIVE
                }
                DiagnosticsLogger.logEvent("Tile", "TILE_UPDATE", "state=${it.state} isInterceptionActive=$isInterceptionActive accessibilityEnabled=$accessibilityEnabled", this)
                it.updateTile()
            }
        } catch (e: Exception) {
             DiagnosticsLogger.logEvent("Error", "TILE_UPDATE_FAILED", "message=${e.message}", this)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == KEY_HOME_INTERCEPTION_ACTIVE) {
            updateTileState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    companion object {
        /**
         * Helper to check if Mjolnir's Accessibility Service is currently enabled in system settings.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, HomeKeyInterceptorService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
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
    }
}
