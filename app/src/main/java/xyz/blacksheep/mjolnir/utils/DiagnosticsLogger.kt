package xyz.blacksheep.mjolnir.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.PREFS_NAME
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A centralized logging utility for recording runtime events, errors, and system state to a file.
 *
 * This logger is controlled by [DiagnosticsConfig] and honors user preferences for
 * enabling/disabling logging and enforcing file size limits.
 *
 * **Key Operations:**
 * - [log]: Records a simple message.
 * - [logEvent]: Records a structured event with optional details.
 * - [logHeader]: Writes system info (OS, app version, preferences) to the log (usually on startup).
 * - [userExport] / [userClear]: Exposes file management to the UI.
 */
object DiagnosticsLogger {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Initializes the logging system by ensuring the log directory exists.
     *
     * @param context Android Context.
     */
    fun init(context: Context) {
        val logFile = DiagnosticsConfig.getLogFile(context)
        val logDir = logFile.parentFile
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs()
        }
    }

    /**
     * Logs a generic message if diagnostics are enabled.
     *
     * @param tag A short identifier for the source of the log (e.g., "HomeService").
     * @param message The content to log.
     * @param context Android Context (needed to check preferences).
     */
    fun log(tag: String, message: String, context: Context) {
        if (!DiagnosticsConfig.isEnabled(context)) return
        writeEntry(tag, message, context)
    }

    /**
     * Logs a structured event if diagnostics are enabled.
     *
     * Output format: `[Timestamp][Tag] EVENT=eventName details="details"`
     *
     * @param tag A short identifier for the source of the log.
     * @param event The name of the event (e.g., "LAUNCH_ACTIVITY").
     * @param details Optional extra information (e.g., "packageName=com.foo.bar").
     * @param context Android Context.
     */
    fun logEvent(tag: String, event: String, details: String? = null, context: Context) {
        if (!DiagnosticsConfig.isEnabled(context)) return
        val detailsPart = details?.let { """ details="$it""" } ?: ""
        val message = "EVENT=$event$detailsPart"
        writeEntry(tag, message, context)
    }

    /**
     * Logs an exception with its message if diagnostics are enabled.
     *
     * @param tag A short identifier for the source of the error.
     * @param throwable The exception to log.
     * @param context Android Context.
     */
    fun logException(tag: String, throwable: Throwable, context: Context) {
        if (!DiagnosticsConfig.isEnabled(context)) return
        val detailsString = "where=$tag message=${throwable.message}"
        logEvent("Error", "EXCEPTION", detailsString, context)
    }

    /**
     * Writes a header block to the log containing system and app metadata.
     *
     * Includes:
     * - App Version Name / Code
     * - Android OS Version
     * - Device Model
     * - Current Max Log Size Preference
     * - A snapshot of all SharedPreference keys and values.
     *
     * Call this on app startup to provide context for the session.
     *
     * @param context Android Context.
     */
    fun logHeader(context: Context) {
        val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = pInfo.versionName
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode.toLong()
        val osVersion = Build.VERSION.RELEASE
        val device = Build.DEVICE
        val maxBytes = DiagnosticsConfig.getMaxBytes(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefsSnapshot = prefs.all.map { "${it.key}=${it.value}" }.joinToString(" ")

        val headerDetails = "versionName=$versionName versionCode=$versionCode os=$osVersion device=$device maxBytes=$maxBytes"
        logEvent("System", "DIAGNOSTICS_HEADER", headerDetails, context)
        logEvent("Prefs", "PREFS_SNAPSHOT", prefsSnapshot, context)
    }

    /**
     * Retrieves the current log file for export purposes.
     *
     * @param context Android Context.
     * @return The File object representing the active log.
     */
    fun userExport(context: Context): File {
        return DiagnosticsConfig.exportLog(context)
    }

    /**
     * Deletes the current log content.
     *
     * @param context Android Context.
     */
    fun userClear(context: Context) {
        DiagnosticsConfig.clearLog(context)
    }

    /**
     * Runs all display diagnostic tasks (Log, Discover, Shell Capture).
     * This is a manual trigger for debugging.
     */
    fun runFullDisplayDiagnostics(context: Context) {
        if (!DiagnosticsConfig.isEnabled(context)) return
        
        coroutineScope.launch {
            logEvent("DiagnosticsLogger", "FULL_DIAG_START", null, context)
            debugLogAllDisplays(context)
            val ids = discoverHardwareDisplayIds(context)
            attemptShellCapture(context, ids)
            logEvent("DiagnosticsLogger", "FULL_DIAG_END", null, context)
        }
    }

    /**
     * Task 1: Logs all available display information using DisplayManager.
     * Must be called from a context where Diagnostics is enabled.
     */
    fun debugLogAllDisplays(context: Context) {
        if (!DiagnosticsConfig.isEnabled(context)) return

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        logEvent("DiagnosticsLogger", "DISPLAY_DEBUG_START", "count=${displays.size}", context)

        displays.forEach { display ->
            val sb = StringBuilder()
            sb.append("id=${display.displayId} ")
            sb.append("name='${display.name}' ")
            sb.append("flags=${display.flags} ")
            sb.append("state=${display.state} ")
            sb.append("isValid=${display.isValid} ")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                 // Mode info
                 val mode = display.mode
                 sb.append("modeId=${mode.modeId} ")
                 sb.append("physWidth=${mode.physicalWidth} ")
                 sb.append("physHeight=${mode.physicalHeight} ")
                 sb.append("refreshRate=${mode.refreshRate} ")
            }
            
            // Try to get uniqueId (API 31+ or hidden)
            try {
                val method = Display::class.java.getMethod("getUniqueId")
                val uniqueId = method.invoke(display) as? String
                sb.append("uniqueId='$uniqueId' ")
            } catch (e: Exception) {
                // Method not found or other error
            }
            
            logEvent("DiagnosticsLogger", "DISPLAY_INFO", sb.toString(), context)
        }
        
        logEvent("DiagnosticsLogger", "DISPLAY_DEBUG_END", null, context)
    }

    /**
     * Task 2: Discover hardware display IDs via dumpsys.
     *
     * Executes "dumpsys SurfaceFlinger --display-id" and parses results.
     * Returns a list of found IDs.
     */
    fun discoverHardwareDisplayIds(context: Context): List<Long> {
         if (!DiagnosticsConfig.isEnabled(context)) return emptyList()
         
         // Try su first, fallback to normal dumpsys
         var output = runShellCommand("su -c dumpsys SurfaceFlinger --display-id")
         if (output.startsWith("EXEC_FAILED") || output.contains("Permission Denial")) {
              output = runShellCommand("dumpsys SurfaceFlinger --display-id")
         }
         
         logEvent("DiagnosticsLogger", "DUMPSYS_OUTPUT", "len=${output.length}", context)
         
         if (output.isNotEmpty()) {
             // Log a snippet to verify we got valid output
             val snippet = output.take(500).replace("\n", " | ")
             log("DiagnosticsLogger", "Dumpsys(head): $snippet", context)
         }

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
         // uniqueId='local:4630946482288158084'
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

         logEvent("DiagnosticsLogger", "DISCOVERED_IDS", "count=${ids.size} ids=$ids", context)
         return ids.toList()
    }

    /**
     * Task 3: Attempt shell screenshot for each ID.
     */
    fun attemptShellCapture(context: Context, displayIds: List<Long>) {
        if (!DiagnosticsConfig.isEnabled(context)) return
        
        // Save to Pictures/Screenshots to be easily visible
        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots")
        if (!outDir.exists()) outDir.mkdirs()
        
        displayIds.forEach { id ->
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "diag_cap_${id}_$timestamp.png"
            val file = File(outDir, fileName)
            
            // Try capturing with SU
            // Note: screencap -d accepts display ID (long)
            val cmd = "su -c screencap -d $id -p ${file.absolutePath}"
            val result = runShellCommand(cmd)
            
            val exists = file.exists()
            val size = if (exists) file.length() else 0
            
            logEvent("DiagnosticsLogger", "SHELL_CAPTURE_ATTEMPT", "id=$id file=$fileName size=$size result='$result'", context)
        }
    }

    /**
     * Helper to execute a shell command.
     */
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

    /**
     * Internal helper to write a formatted line to the file.
     * Runs on Dispatchers.IO to avoid blocking.
     * Also handles file rotation/trimming if size limits are exceeded.
     */
    private fun writeEntry(tag: String, message: String, context: Context) {
        coroutineScope.launch {
            try {
                val logFile = DiagnosticsConfig.getLogFile(context)
                val maxLogSize = DiagnosticsConfig.getMaxBytes(context)
                if (logFile.exists() && logFile.length() > maxLogSize) {
                    trimLogFile(logFile)
                }
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                val logLine = "[$timestamp][$tag] $message\n"
                logFile.appendText(logLine)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Trims the log file when it exceeds the size limit.
     *
     * Strategy: Keeps the *last* 50% of lines (tail), discards the old half.
     */
    private fun trimLogFile(logFile: File) {
        try {
            val lines = logFile.readLines()
            val linesToKeep = lines.takeLast(lines.size / 2)
            logFile.writeText(linesToKeep.joinToString("\n") + "\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
