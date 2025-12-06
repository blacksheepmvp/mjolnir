package xyz.blacksheep.mjolnir

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import xyz.blacksheep.mjolnir.services.KeepAliveService
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger

/**
 * A transparent Activity to request runtime permissions for Auto-Stitch DSS.
 * Launched by DualScreenshotTileService when permissions are missing.
 */
class DssPermissionActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        if (hasStoragePermission()) {
            enableFeature()
        } else {
            requestStoragePermission()
        }
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        DiagnosticsLogger.logEvent("Permission", "REQUEST_STORAGE_START", context = this)
        ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_CODE_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_CODE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                DiagnosticsLogger.logEvent("Permission", "STORAGE_GRANTED", context = this)
                enableFeature()
            } else {
                DiagnosticsLogger.logEvent("Permission", "STORAGE_DENIED", context = this)
                Toast.makeText(this, "Permission required for Auto-Stitch", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun enableFeature() {
        // Enable the feature - Use commit=true to ensure disk write before notifying service
        prefs.edit(commit = true) { putBoolean(KEY_DSS_AUTO_STITCH, true) }
        
        // Explicitly ping KeepAliveService to refresh the observer with new permissions
        try {
            val refreshIntent = Intent(this, KeepAliveService::class.java).apply {
                action = KeepAliveService.ACTION_REFRESH_OBSERVER
            }
            startService(refreshIntent)
            DiagnosticsLogger.logEvent("Permission", "OBSERVER_REFRESH_SENT", context = this)
        } catch (e: Exception) {
            DiagnosticsLogger.logException("Permission", e, this)
        }
        
        // Notify user
        Toast.makeText(this, "Auto-Stitch Enabled", Toast.LENGTH_SHORT).show()
        DiagnosticsLogger.logEvent("Permission", "FEATURE_ENABLED_AUTO", context = this)
        
        // Tile update will happen via SharedPreferences listener in the TileService
        finish()
    }

    companion object {
        private const val REQUEST_CODE_STORAGE = 2001
    }
}
