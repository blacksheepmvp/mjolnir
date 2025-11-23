package xyz.blacksheep.mjolnir.utils

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.HomeKeyInterceptorService
import xyz.blacksheep.mjolnir.services.DualScreenshotService

/**
 * Manager responsible for coordinating the Dual Screenshot process.
 *
 * **Responsibilities:**
 * 1. Handle the start request.
 * 2. Trigger notification shade collapse (best effort).
 * 3. Wait for UI to clear.
 * 4. Start the capture service.
 */
object DualScreenshotManager {

    private const val SHADE_COLLAPSE_DELAY_MS = 500L

    /**
     * Starts the Dual Screenshot process.
     *
     * @param context Context to start the service.
     */
    fun start(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            // DIRECT PATH: No MediaProjection permission check.
            // We are relying on Root Shell Capture.
            performCollapseAndCapture(context)
        }
    }

    private suspend fun performCollapseAndCapture(context: Context) {
        try {
            // 1. Attempt to collapse the notification shade.
            DiagnosticsLogger.logEvent("DualScreenshot", "COLLAPSE_REQ", "sending_broadcast=true", context)
            
            try {
                val intent = Intent(HomeKeyInterceptorService.ACTION_REQ_COLLAPSE_SHADE)
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent("DualScreenshot", "BROADCAST_FAILED", "reason=${e.message}", context)
            }

            // 2. Wait for UI to clear
            delay(SHADE_COLLAPSE_DELAY_MS)

            // 3. Start the capture service.
            val serviceIntent = Intent(context, DualScreenshotService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            DiagnosticsLogger.logEvent("DualScreenshot", "CAPTURE_INITIATED", context = context)

        } catch (e: Exception) {
            DiagnosticsLogger.logException("DualScreenshot", e, context)
        }
    }
}
