package xyz.blacksheep.mjolnir

import android.app.PendingIntent
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log

class DebugSafetyNetTileService : TileService() {
    companion object {
        private const val TAG = "DebugSafetyNetTile"
    }

    override fun onClick() {
        super.onClick()

        val displayManager = getSystemService(DisplayManager::class.java)
        val displays = displayManager.displays

        SafetyNetManager.forceSafetyNetToFront(this)
        collapseShadeSafely(displays.size)
    }

    private fun collapseShadeSafely(displayCount: Int) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            Log.d(TAG, "Debug tile collapsed shade for $displayCount displays")
        } catch (e: Exception) {
            Log.w(TAG, "Debug tile could not collapse shade: ${e.message}")
        }
    }
}
