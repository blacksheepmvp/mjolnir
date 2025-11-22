package xyz.blacksheep.mjolnir.workarounds

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger

/**
 * A hacky but necessary workaround for a firmware bug on the AYN Thor device.
 *
 * **The Bug:**
 * When the device's "Focus Lock" setting is set to "Top Only" (forcing controller input to the top screen),
 * switching focus *back* to an already-running activity on the top screen can sometimes cause the input
 * system to hang or lose focus entirely.
 *
 * **The Fix:**
 * This object briefly (100ms) injects a 1x1 pixel, focusable, invisible overlay window into the WindowManager.
 *
 * **Mechanism:**
 * 1. The overlay appears and steals window focus from the system.
 * 2. The overlay is removed 100ms later.
 * 3. The Android WindowManager is forced to re-calculate the "topmost" focused window.
 * 4. Focus correctly falls back to the intended Top Screen activity, restoring controller input.
 *
 * **Requirement:**
 * Requires `ACTION_MANAGE_OVERLAY_PERMISSION` (Display over other apps).
 */
object FocusLockOverlayWorkaround {

    private const val TAG = "FocusLockOverlayWorkaround"
    private const val DELAY_BEFORE_SHOW_MS = 200L
    private const val DURATION_SHOW_MS = 100L

    // Keep track of the current overlay view to ensure we remove it
    private var currentOverlayView: View? = null

    /**
     * Trigger the workaround sequence.
     *
     * @param service The AccessibilityService context (needed for WindowManager token and permission check).
     */
    fun run(service: AccessibilityService) {
        // 1. Check Permission
        if (!Settings.canDrawOverlays(service)) {
            DiagnosticsLogger.logEvent(
                TAG,
                "SKIPPED_NO_PERMISSION",
                "Overlay permission missing, cannot run workaround.",
                service
            )
            return
        }

        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            DiagnosticsLogger.logEvent(TAG, "SKIPPED_NO_WM", "WindowManager not found.", service)
            return
        }

        // 2. Schedule the workaround
        // We wait a brief moment to let the app launch/resume animation start
        Handler(Looper.getMainLooper()).postDelayed({
            showOverlay(service, windowManager)
        }, DELAY_BEFORE_SHOW_MS)
    }

    private fun showOverlay(context: Context, wm: WindowManager) {
        try {
            // Remove any existing overlay first (safety)
            removeOverlay(wm)

            val params = WindowManager.LayoutParams(
                1, 1, // Minimal size
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Flags:
                // NOT_TOUCH_MODAL: Allow outside touches to go to windows behind us
                // NOT_TOUCHABLE: Do not accept touch events (pass through)
                // We DO NOT use FLAG_NOT_FOCUSABLE because we WANT to steal focus briefly.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 0
            params.title = "MjolnirFocusFix"

            val view = View(context)
            view.setBackgroundColor(Color.TRANSPARENT) // Invisible

            wm.addView(view, params)
            currentOverlayView = view

            DiagnosticsLogger.logEvent(TAG, "OVERLAY_SHOWN", "Invisible overlay added to force focus reset.", context)

            // 3. Remove it after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                removeOverlay(wm)
            }, DURATION_SHOW_MS)

        } catch (e: Exception) {
            DiagnosticsLogger.logException(TAG, e, context)
            // Try to cleanup if add failed
            removeOverlay(wm)
        }
    }

    private fun removeOverlay(wm: WindowManager) {
        val view = currentOverlayView
        if (view != null) {
            try {
                wm.removeView(view)
                currentOverlayView = null
                DiagnosticsLogger.logEvent(TAG, "OVERLAY_REMOVED", "Focus workaround complete.", view.context)
            } catch (e: Exception) {
                // If view is already gone or not attached, just ignore
                DiagnosticsLogger.logEvent(TAG, "REMOVE_FAILED", "Error removing overlay: ${e.message}", view.context)
                currentOverlayView = null
            }
        }
    }
}
