package xyz.blacksheep.mjolnir

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.edit

class SwapScreensTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(SteamFileGenActivity.PREFS_NAME, MODE_PRIVATE)
        prefs.edit { putBoolean(SteamFileGenActivity.KEY_SWAP_SCREENS_REQUESTED, true) }

        // Collapse the notification shade
        val collapseIntent = Intent(this, QuickTileCollapseActivity::class.java)
        collapseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(collapseIntent)
    }
}
