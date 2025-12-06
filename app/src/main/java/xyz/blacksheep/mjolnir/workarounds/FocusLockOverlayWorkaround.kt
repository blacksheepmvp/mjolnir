package xyz.blacksheep.mjolnir.workarounds

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.FocusHackHelper

/**
 * A hacky but necessary workaround for a firmware bug on the AYN Thor device.
 *
 * **The Bug:**
 * When the device's "Focus Lock" setting is set to "Top Only" (forcing controller input to the top screen),
 * switching focus *back* to an already-running activity on the top screen can sometimes cause the input
 * system to hang or lose focus entirely.
 *
 * **The Fix:**
 * This object delegates to [FocusHackHelper] to briefly inject a focusable, invisible overlay window
 * on the Top Screen (Display 0).
 *
 * **Mechanism:**
 * 1. The overlay appears and steals window focus from the system.
 * 2. The overlay is removed shortly after.
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
    private const val TOP_DISPLAY_ID = 0

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

        // 2. Schedule the workaround
        // We wait a brief moment to let the app launch/resume animation start
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // Updated to use the new FocusHackHelper signature (removed duration parameter)
                FocusHackHelper.requestFocus(service, TOP_DISPLAY_ID) {
                    DiagnosticsLogger.logEvent(TAG, "WORKAROUND_COMPLETE", "Top screen focus hack finished.", service)
                }
            } catch (e: Exception) {
                DiagnosticsLogger.logException(TAG, e, service)
            }
        }, DELAY_BEFORE_SHOW_MS)
    }
}
