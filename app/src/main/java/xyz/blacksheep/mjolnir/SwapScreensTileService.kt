package xyz.blacksheep.mjolnir

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger

/**
 * A Quick Settings tile that triggers a screen swap via the Accessibility Service.
 *
 * **Behavior:**
 * 1. User taps tile.
 * 2. Tile sends a broadcast action `ACTION_REQ_SWAP_SCREENS`.
 * 3. [HomeKeyInterceptorService] receives the broadcast.
 * 4. It identifies the foreground app on Display 0 (Top) and Display 1 (Bottom).
 * 5. It relaunches them on the opposite displays.
 */
class SwapScreensTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        DiagnosticsLogger.logEvent("Tile", "SWAP_CLICKED", context = this)

        val intent = Intent(HomeKeyInterceptorService.ACTION_REQ_SWAP_SCREENS)
        intent.setPackage(packageName) // Restrict to our own app
        sendBroadcast(intent)

        // Visual feedback
        val tile = qsTile
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
        
        // Reset state shortly after (since it's a momentary action)
        // In reality, the tile should probably just stay active or inactive based on availability
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        // We could check if accessibility is enabled here to gray it out if not
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.flip_screens_tile_label)
        tile.updateTile()
    }
}
