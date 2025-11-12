package xyz.blacksheep.mjolnir

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log

object DualScreenLauncher {
    private const val TAG = "DualScreenLauncher"

    fun launchOnDualScreens(
        context: Context,
        topIntent: Intent,
        bottomIntent: Intent,
        mainScreen: MainScreen
    ): Boolean {
        topIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        bottomIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = dm.displays

            if (displays.isEmpty()) {
                Log.e(TAG, "No displays found to launch on.")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val topDisplayId = displays.getOrNull(0)?.displayId

                if (topDisplayId == null) {
                    Log.e(TAG, "Could not get top display ID.")
                    return false
                }

                val bottomDisplayId = displays.getOrNull(1)?.displayId ?: topDisplayId

                val topOptions = ActivityOptions.makeBasic().setLaunchDisplayId(topDisplayId)
                val bottomOptions = ActivityOptions.makeBasic().setLaunchDisplayId(bottomDisplayId)

                // Launch both activities first
                context.startActivity(topIntent, topOptions.toBundle())
                context.startActivity(bottomIntent, bottomOptions.toBundle())

                // Re-launch the one intended for the main screen to ensure it gets focus.
                if (mainScreen == MainScreen.TOP) {
                    context.startActivity(topIntent, topOptions.toBundle())
                } else {
                    context.startActivity(bottomIntent, bottomOptions.toBundle())
                }
            } else {
                // Legacy launch order might not guarantee focus
                if (mainScreen == MainScreen.TOP) {
                    context.startActivity(bottomIntent)
                    context.startActivity(topIntent)
                } else {
                    context.startActivity(topIntent)
                    context.startActivity(bottomIntent)
                }
            }
            true // Success
        } catch (e: Exception) {
            Log.e(TAG, "Dual-screen launch error: ${e.message}", e)
            false // Failure
        }
    }
}
