package xyz.blacksheep.mjolnir

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityManager
import androidx.core.content.edit

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

        if (isAccessibilityServiceEnabled(this)) {
            // The service is enabled, so the tile functions as a simple on/off switch.
            val newValue = !prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
            prefs.edit { putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, newValue) }
        } else {
            // The service is not enabled, so take the user to settings to enable it.
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        qsTile?.let {
            val isInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
            // The tile is active only if the feature is on AND the service is running.
            it.state = if (isInterceptionActive && isAccessibilityServiceEnabled(this)) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            it.updateTile()
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
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val expectedComponentName = ComponentName(context, HomeKeyInterceptorService::class.java)

            for (service in enabledServices) {
                val serviceComponentName = ComponentName(service.resolveInfo.serviceInfo.packageName, service.resolveInfo.serviceInfo.name)
                if (serviceComponentName == expectedComponentName) {
                    return true
                }
            }
            return false
        }
    }
}
