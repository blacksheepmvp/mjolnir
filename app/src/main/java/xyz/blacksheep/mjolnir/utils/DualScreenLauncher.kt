package xyz.blacksheep.mjolnir.utils

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import xyz.blacksheep.mjolnir.settings.MainScreen

object DualScreenLauncher {
    private const val TAG = "DualScreenLauncher"

    fun launchOnTop(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val topDisplay = dm.displays.getOrNull(0)
            if (topDisplay == null) {
                Log.e(TAG, "Could not get top display.")
                return false
            }

            @SuppressLint("NewApi")
            val options = ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setLaunchDisplayId(topDisplay.displayId)
                }
            }
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching on top screen: ${e.message}", e)
            false
        }
    }

    fun launchOnBottom(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val bottomDisplay = dm.displays.getOrNull(1)
            if (bottomDisplay == null) {
                Log.w(TAG, "Could not get bottom display. Falling back to top.")
                return launchOnTop(context, intent)
            }

            @SuppressLint("NewApi")
            val options = ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setLaunchDisplayId(bottomDisplay.displayId)
                }
            }
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching on bottom screen: ${e.message}", e)
            false
        }
    }

    fun launchOnDualScreens(
        context: Context,
        topIntent: Intent,
        bottomIntent: Intent,
        mainScreen: MainScreen
    ): Boolean {
        Log.d(TAG, "Dual-screen launch requested. Main screen to focus: $mainScreen")
        val topSuccess: Boolean
        val bottomSuccess: Boolean

        if (mainScreen == MainScreen.TOP) {
            // Launch bottom first, then top to give top focus.
            bottomSuccess = launchOnBottom(context, bottomIntent)
            topSuccess = launchOnTop(context, topIntent)
        } else {
            // Launch top first, then bottom to give bottom focus.
            topSuccess = launchOnTop(context, topIntent)
            bottomSuccess = launchOnBottom(context, bottomIntent)
        }

        return topSuccess && bottomSuccess
    }
}
