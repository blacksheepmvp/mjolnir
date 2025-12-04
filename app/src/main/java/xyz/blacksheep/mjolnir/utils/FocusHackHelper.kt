package xyz.blacksheep.mjolnir.utils

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * A utility to force input focus to a specific display.
 *
 * **Mechanism:**
 * It briefly displays a 1x1 invisible overlay window on the target display.
 * This forces the Android WindowManager to shift focus to that display.
 * Once the overlay is removed, focus typically lands on the topmost activity of that display
 * (or the launcher if no activity is present).
 *
 * **Use Cases:**
 * 1. Fixing the "Focus Lock" bug on AYN Thor where the top screen loses controller input.
 * 2. Ensuring GLOBAL_ACTION_HOME targets the correct display (Top vs Bottom).
 */
object FocusHackHelper {

    private const val TAG = "FocusHackHelper"

    /**
     * Performs the focus hack on the specified display.
     *
     * @param context Context to access WindowManager (must be an Application or Service context).
     * @param displayId The ID of the display to target (e.g., 0 for Top, 1/4 for Bottom).
     * @param durationMs How long to keep the overlay visible. Default is 100ms to ensure registration.
     * @param onComplete Optional callback to run after the hack finishes.
     */
    fun requestFocus(context: Context, displayId: Int, durationMs: Long = 100L, onComplete: (() -> Unit)? = null) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val targetDisplay = dm.getDisplay(displayId)

        if (targetDisplay == null) {
            DiagnosticsLogger.logEvent("FocusHack", "INVALID_DISPLAY", "id=$displayId", context)
            onComplete?.invoke()
            return
        }

        // Context must be associated with the specific display to add a window to it correctly on some Android versions
        val displayContext = context.createDisplayContext(targetDisplay)
        val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayView = View(displayContext)
        
        val params = WindowManager.LayoutParams(
            1, 1, // Minimal size
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Flags to aggressively steal focus:
            // - NO FLAG_NOT_FOCUSABLE (we WANT focus)
            // - NO FLAG_NOT_TOUCH_MODAL (we WANT to be the only thing touchable for a split second)
            // - FLAG_WATCH_OUTSIDE_TOUCH (to be a "real" window)
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or 
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER // Center it to be "safe"
        params.alpha = 0.01f // Almost invisible but technically visible
        params.title = "MjolnirFocusStealer"

        try {
            DiagnosticsLogger.logEvent("FocusHack", "ADDING_OVERLAY", "displayId=$displayId", context)
            wm.addView(overlayView, params)

            // Wait for the window to be added and focus to shift
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // Execute the action WHILE the overlay is there (and presumably has focus)
                    // or immediately AFTER?
                    // Usually, GLOBAL_ACTION_HOME targets the focused window's display.
                    // If our overlay has focus, it targets our display.
                    
                    // BUT if we remove the overlay, focus might snap back to the previous window 
                    // before the Home action processes.
                    // So we should probably fire the callback slightly BEFORE removing the view, 
                    // OR fire it, then remove the view.
                    
                    // Let's try firing the callback first (while overlay is active)
                    onComplete?.invoke()
                    
                    // Then remove the view after a tiny extra delay to ensure the event propagates
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            wm.removeView(overlayView)
                            DiagnosticsLogger.logEvent("FocusHack", "REMOVED_OVERLAY", "displayId=$displayId", context)
                        } catch (e: Exception) {
                            DiagnosticsLogger.logEvent("FocusHack", "REMOVE_FAILED", "msg=${e.message}", context)
                        }
                    }, 50)

                } catch (e: Exception) {
                    DiagnosticsLogger.logEvent("FocusHack", "ACTION_FAILED", "msg=${e.message}", context)
                    // Ensure cleanup
                    try { wm.removeView(overlayView) } catch (_: Exception) {}
                }
            }, durationMs)

        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("Error", "FOCUS_HACK_FAILED", "msg=${e.message}", context)
            onComplete?.invoke()
        }
    }
}
