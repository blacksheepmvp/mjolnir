package xyz.blacksheep.mjolnir

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log

object DualScreenLauncher {
    private const val TAG = "DualScreenLauncher"

    fun launchOnDualScreens(context: Context, topIntent: Intent, bottomIntent: Intent) {
        topIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        bottomIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = dm.displays
            val top = displays.getOrNull(0)
            val bottom = displays.getOrNull(1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                top?.let {
                    context.startActivity(
                        topIntent,
                        ActivityOptions.makeBasic().setLaunchDisplayId(it.displayId).toBundle()
                    )
                }
                bottom?.let {
                    context.startActivity(
                        bottomIntent,
                        ActivityOptions.makeBasic().setLaunchDisplayId(it.displayId).toBundle()
                    )
                }
            } else {
                context.startActivity(topIntent)
                context.startActivity(bottomIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Dual-screen launch error: ${e.message}", e)
        }
    }
}
