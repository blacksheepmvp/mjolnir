package xyz.blacksheep.mjolnir

import android.app.Activity
import android.os.Bundle

/**
 * An invisible activity that closes itself immediately.
 * Used by Quick Settings Tiles to collapse the notification shade.
 */
class QuickTileCollapseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
