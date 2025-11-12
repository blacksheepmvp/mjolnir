package xyz.blacksheep.mjolnir

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityManager
import androidx.core.content.edit

class MjolnirHomeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (isAccessibilityServiceEnabled()) {
            // The service is enabled, so the tile functions as a simple on/off switch.
            val prefs = getSharedPreferences(SteamFileGenActivity.PREFS_NAME, MODE_PRIVATE)
            val isActive = prefs.getBoolean(SteamFileGenActivity.KEY_HOME_INTERCEPTION_ACTIVE, false)
            prefs.edit { putBoolean(SteamFileGenActivity.KEY_HOME_INTERCEPTION_ACTIVE, !isActive) }
            updateTileState()
        } else {
            // The service is not enabled, so take the user to settings to enable it.
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedComponentName = ComponentName(this, HomeKeyInterceptorService::class.java)

        for (service in enabledServices) {
            val serviceComponentName = ComponentName(service.resolveInfo.serviceInfo.packageName, service.resolveInfo.serviceInfo.name)
            if (serviceComponentName == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun updateTileState() {
        qsTile?.let {
            val prefs = getSharedPreferences(SteamFileGenActivity.PREFS_NAME, MODE_PRIVATE)
            val isFeatureActive = prefs.getBoolean(SteamFileGenActivity.KEY_HOME_INTERCEPTION_ACTIVE, false)

            // The tile is active only if the feature is on AND the service is running.
            it.state = if (isFeatureActive && isAccessibilityServiceEnabled()) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            it.updateTile()
        }
    }
}
