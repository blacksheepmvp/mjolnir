package xyz.blacksheep.mjolnir.utils

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import xyz.blacksheep.mjolnir.settings.MainScreen

/**
 * A utility object for handling application launches across multiple displays on dual-screen devices (like the AYN Thor).
 *
 * This utility manages:
 * - Identifying the top (Display 0) and bottom (Display 1) screens.
 * - Constructing [ActivityOptions] with correct `launchDisplayId`.
 * - Orchestrating simultaneous dual-app launches while respecting focus order.
 *
 * **Key Operations:**
 * - [launchOnTop]: Starts an activity explicitly on the top screen.
 * - [launchOnBottom]: Starts an activity explicitly on the bottom screen (falls back to top if missing).
 * - [launchOnDualScreens]: Launches both simultaneously, ordering them to ensure the correct one gets focus.
 */
object DualScreenLauncher {
    private const val TAG = "DualScreenLauncher"

    /**
     * Launches an activity on the primary (top) display (Display ID 0).
     *
     * @param context Android Context (Activity or Service).
     * @param intent The Intent to launch. Flags like `FLAG_ACTIVITY_NEW_TASK` are added automatically.
     * @return `true` if launch was successful, `false` otherwise.
     */
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

    /**
     * Launches an activity on the secondary (bottom) display (Display ID 1).
     *
     * **Fallback Behavior:**
     * If the device does not have a second screen (e.g., running on a standard phone),
     * this method will log a warning and fall back to launching on the top screen.
     *
     * @param context Android Context.
     * @param intent The Intent to launch.
     * @return `true` if launch was successful (on either screen).
     */
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

    /**
     * Launches two distinct activities simultaneously, one on each screen.
     *
     * **Focus Management:**
     * The order of launching is determined by the `mainScreen` parameter. The app launched *last*
     * is the one that will receive input focus immediately after launch.
     *
     * - If [MainScreen.TOP] is primary: Bottom launches first -> Top launches last (Top gets focus).
     * - If [MainScreen.BOTTOM] is primary: Top launches first -> Bottom launches last (Bottom gets focus).
     *
     * @param context Android Context.
     * @param topIntent The intent for the top screen app.
     * @param bottomIntent The intent for the bottom screen app.
     * @param mainScreen The user's preferred primary screen (determines focus).
     * @return `true` if both launches succeeded.
     */
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
