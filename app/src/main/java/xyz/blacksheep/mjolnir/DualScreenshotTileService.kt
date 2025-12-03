package xyz.blacksheep.mjolnir

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import xyz.blacksheep.mjolnir.services.KeepAliveService
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger

/**
 * A Quick Settings Tile that allows the user to toggle the Rootless DSS Auto-Stitch feature.
 *
 * **Behavior:**
 * - **Tap:** Toggles `KEY_DSS_AUTO_STITCH`.
 * - **Active (Lit):** Auto-Stitch is ON.
 * - **Inactive (Dim):** Auto-Stitch is OFF.
 *
 * Implements 0.2.5d Rootless DSS Spec.
 */
class DualScreenshotTileService : TileService(), SharedPreferences.OnSharedPreferenceChangeListener {

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
        
        // Check Permissions before toggling
        if (!hasStoragePermission()) {
            DiagnosticsLogger.logEvent("Tile", "DSS_PERMISSION_MISSING_LAUNCH_ACTIVITY", context = this)
            
            // Launch transparent activity to request permission inline
            val intent = Intent(this, DssPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent("Error", "FAILED_TO_LAUNCH_PERM_ACTIVITY", "msg=${e.message}", this)
                // Fallback to toast if activity fails
                Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        // Simple toggle behavior per 0.2.5d spec
        val currentState = prefs.getBoolean(KEY_DSS_AUTO_STITCH, false)
        val newState = !currentState
        
        prefs.edit { putBoolean(KEY_DSS_AUTO_STITCH, newState) }
        DiagnosticsLogger.logEvent("Tile", "TOGGLE_DSS", "newState=$newState", this)
        
        // Update immediately for responsiveness
        updateTileState()

        // Explicitly notify KeepAliveService to refresh its observer state
        try {
            val refreshIntent = Intent(this, KeepAliveService::class.java).apply {
                action = KeepAliveService.ACTION_REFRESH_OBSERVER
            }
            DiagnosticsLogger.logEvent("Tile", "SENDING_REFRESH_OBSERVER_INTENT", "newState=$newState", this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(refreshIntent)
            } else {
                startService(refreshIntent)
            }
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("Error", "DSS_REFRESH_INTENT_FAILED", "message=${e.message}", this)
        }
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateTileState() {
        try {
            qsTile?.let { tile ->
                val isActive = prefs.getBoolean(KEY_DSS_AUTO_STITCH, false)
                
                tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = if (isActive) "DualShot: Auto" else "DualShot: Off"
                
                tile.updateTile()
            }
        } catch (e: Exception) {
             DiagnosticsLogger.logEvent("Error", "DSS_TILE_UPDATE_FAILED", "message=${e.message}", this)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == KEY_DSS_AUTO_STITCH) {
            updateTileState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }
}
