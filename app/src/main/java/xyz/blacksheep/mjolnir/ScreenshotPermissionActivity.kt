package xyz.blacksheep.mjolnir

import android.app.Activity
import android.os.Bundle
import xyz.blacksheep.mjolnir.utils.DualScreenshotManager

class ScreenshotPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Permission Activity is no longer needed for Root/Shell capture method.
        // If invoked by mistake, just finish and trigger the manager directly (which will use root).
        
        finish()
        DualScreenshotManager.start(this)
    }
}
