package xyz.blacksheep.mjolnir

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.TextUtils
import androidx.core.content.edit
import xyz.blacksheep.mjolnir.onboarding.AdvancedRequiredActivity
import xyz.blacksheep.mjolnir.services.KeepAliveService

class DualScreenshotTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!isAccessibilityServiceEnabled()) {
            // Show dialog explaining advanced mode is required
            val intent = Intent(this, AdvancedRequiredActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_DSS_AUTO_STITCH, false)
        prefs.edit { putBoolean(KEY_DSS_AUTO_STITCH, !isEnabled) }
        updateTile()

        // Explicitly trigger a refresh in KeepAliveService
        val serviceIntent = Intent(this, KeepAliveService::class.java).apply {
            action = KeepAliveService.ACTION_REFRESH_OBSERVER
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun updateTile() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_DSS_AUTO_STITCH, false)
        val tile = qsTile

        if (tile != null) {
            tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (isEnabled) "DualShot Active" else "DualShot"
            tile.updateTile()
        }
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
}