package xyz.blacksheep.mjolnir.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import xyz.blacksheep.mjolnir.HomeKeyInterceptorService
import java.io.File
import java.util.concurrent.Executor

/**
 * Utility for capturing screenshots using available APIs.
 */
object ScreenshotUtil {

    /**
     * Attempts to capture a screenshot of the specified display ID via Accessibility Service.
     * 
     * @param context Context
     * @param displayId The logical ID of the display to capture (0, 1, etc.).
     * @return Bitmap if successful, null otherwise.
     */
    suspend fun captureDisplay(context: Context, displayId: Int): Bitmap? {
        // Attempt to use AccessibilityService.takeScreenshot() (API 30+)
        if (Build.VERSION.SDK_INT >= 30) {
            val service = HomeKeyInterceptorService.instance
            if (service != null) {
                DiagnosticsLogger.logEvent("ScreenshotUtil", "CAPTURE_ATTEMPT", "method=accessibility displayId=$displayId", context)
                return captureViaAccessibility(service, displayId)
            } else {
                DiagnosticsLogger.logEvent("ScreenshotUtil", "CAPTURE_FAILED", "reason=accessibility_service_null", context)
            }
        } else {
            DiagnosticsLogger.logEvent("ScreenshotUtil", "CAPTURE_FAILED", "reason=api_too_low api=${Build.VERSION.SDK_INT}", context)
        }
        
        return null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureViaAccessibility(service: AccessibilityService, displayId: Int): Bitmap? {
        val deferred = CompletableDeferred<Bitmap?>()
        
        try {
            val executor = Executor { command -> command.run() }
            
            service.takeScreenshot(displayId, executor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        
                        hardwareBuffer.close()
                        
                        if (softwareBitmap != null) {
                            DiagnosticsLogger.logEvent("ScreenshotUtil", "CAPTURE_SUCCESS", "displayId=$displayId", service)
                            deferred.complete(softwareBitmap)
                        } else {
                            DiagnosticsLogger.logEvent("ScreenshotUtil", "CAPTURE_FAILED", "reason=bitmap_copy_failed", service)
                            deferred.complete(null)
                        }
                    } catch (e: Exception) {
                        DiagnosticsLogger.logException("ScreenshotUtil", e, service)
                        deferred.complete(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    DiagnosticsLogger.logEvent("ScreenshotUtil", "ACCESSIBILITY_CAPTURE_FAILED", "errorCode=$errorCode displayId=$displayId", service)
                    deferred.complete(null)
                }
            })
        } catch (e: Exception) {
             DiagnosticsLogger.logException("ScreenshotUtil", e, service)
             deferred.complete(null)
        }

        return deferred.await()
    }

    /**
     * Discovers hardware display IDs via dumpsys SurfaceFlinger (Root required).
     */
    fun discoverHardwareDisplayIds(context: Context): List<Long> {
         // Try su first, fallback to normal dumpsys
         var output = runShellCommand("su -c dumpsys SurfaceFlinger --display-id")
         if (output.startsWith("EXEC_FAILED") || output.contains("Permission Denial")) {
              output = runShellCommand("dumpsys SurfaceFlinger --display-id")
         }
         
         DiagnosticsLogger.logEvent("ScreenshotUtil", "DUMPSYS_OUTPUT", "len=${output.length}", context)

         val ids = mutableSetOf<Long>()
         
         // Pattern: Display <number> (HWC display X)
         val regexDisplay = Regex("Display (\\d+) \\(HWC display")
         regexDisplay.findAll(output).forEach { m -> 
             m.groups[1]?.value?.toLongOrNull()?.let { ids.add(it) } 
         }
         
         // Pattern: local:<number> (From dumpsys display sometimes)
         val regexLocal = Regex("local:(\\d+)")
         regexLocal.findAll(output).forEach { m ->
             m.groups[1]?.value?.toLongOrNull()?.let { ids.add(it) } 
         }
         
         // Add IDs parsed from DisplayManager via uniqueId string if available
         val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
         displayManager.displays.forEach { display ->
             try {
                 val method = Display::class.java.getMethod("getUniqueId")
                 val uniqueId = method.invoke(display) as? String
                 if (uniqueId != null && uniqueId.startsWith("local:")) {
                     val idStr = uniqueId.removePrefix("local:")
                     idStr.toLongOrNull()?.let { ids.add(it) }
                 }
             } catch (e: Exception) {
                 // Ignore
             }
         }

         DiagnosticsLogger.logEvent("ScreenshotUtil", "DISCOVERED_IDS", "count=${ids.size} ids=$ids", context)
         return ids.toList().sorted()
    }

    /**
     * Captures a screenshot using root shell commands.
     * 
     * @param context Context
     * @param displayId The physical display ID (Long).
     * @return Bitmap if successful, null otherwise.
     */
    fun captureViaShell(context: Context, displayId: Long): Bitmap? {
        val tempFile = File(context.cacheDir, "temp_cap_$displayId.png")
        // Clean up previous run if needed
        if (tempFile.exists()) tempFile.delete()
        
        try {
            // Use su -c to run screencap
            val cmd = "su -c screencap -d $displayId -p ${tempFile.absolutePath}"
            val output = runShellCommand(cmd)
            
            if (tempFile.exists() && tempFile.length() > 0) {
                val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                DiagnosticsLogger.logEvent("ScreenshotUtil", "SHELL_CAPTURE_SUCCESS", "id=$displayId size=${tempFile.length()}", context)
                return bitmap
            } else {
                DiagnosticsLogger.logEvent("ScreenshotUtil", "SHELL_CAPTURE_FAILED", "id=$displayId output=$output", context)
            }
        } catch (e: Exception) {
            DiagnosticsLogger.logException("ScreenshotUtil", e, context)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
        return null
    }

    private fun runShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            process.waitFor()
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            
            if (error.isNotBlank()) {
                "$output [STDERR: $error]"
            } else {
                output
            }
        } catch (e: Exception) {
            "EXEC_FAILED: ${e.message}"
        }
    }
}
