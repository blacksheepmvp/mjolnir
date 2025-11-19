package xyz.blacksheep.mjolnir.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityManager
import xyz.blacksheep.mjolnir.HomeKeyInterceptorService
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.PREFS_NAME
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Provides read-only access to diagnostics metadata for the Summary Screen.
 */
object DiagnosticsSummary {

    fun isEnabled(context: Context): Boolean {
        return DiagnosticsConfig.isEnabled(context)
    }

    fun getLogFile(context: Context): File {
        return DiagnosticsConfig.getLogFile(context)
    }

    fun getLogFileExists(context: Context): Boolean {
        return getLogFile(context).exists()
    }

    fun getLogFileSize(context: Context): Long {
        val file = getLogFile(context)
        return if (file.exists()) file.length() else 0L
    }

    fun getMaxBytes(context: Context): Long {
        return DiagnosticsConfig.getMaxBytes(context)
    }

    fun getLastModified(context: Context): Long {
        val file = getLogFile(context)
        return if (file.exists()) file.lastModified() else 0L
    }

    fun getApproxEntryCount(context: Context): Int {
        val file = getLogFile(context)
        if (!file.exists()) return -1
        
        // Simple line counting. 
        // Since max log size is small (max 5MB), this is acceptable on IO thread or coroutine.
        return try {
            var lines = 0
            file.useLines { sequence ->
                lines = sequence.count()
            }
            lines
        } catch (e: Exception) {
            -1
        }
    }
}

/**
 * Handles actions triggered from the Summary Screen.
 */
object DiagnosticsActions {

    /**
     * Returns the raw active log file for viewing via FileProvider.
     */
    fun getLogFileForViewing(context: Context): File {
        return DiagnosticsConfig.getLogFile(context)
    }

    /**
     * Creates a temporary export file with a summary header prepended.
     */
    fun createExportWithSummary(context: Context): File {
        val currentLog = DiagnosticsConfig.getLogFile(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val exportFileName = "mjolnir_diag_$timestamp.log"
        
        // Store export in the same directory as logs (which is configured for FileProvider)
        val exportFile = File(currentLog.parentFile, exportFileName)

        val summaryBuilder = StringBuilder()
        val lastModTime = if (currentLog.exists()) currentLog.lastModified() else System.currentTimeMillis()
        val lastModStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastModTime))

        summaryBuilder.append("===== Mjolnir Diagnostics Summary =====\n")
        summaryBuilder.append("Diagnostics Enabled: ${DiagnosticsConfig.isEnabled(context)}\n")
        summaryBuilder.append("Log File Size: ${if (currentLog.exists()) currentLog.length() else 0}\n")
        summaryBuilder.append("Max File Size: ${DiagnosticsConfig.getMaxBytes(context)}\n")
        summaryBuilder.append("Last Modified: $lastModStr\n")
        summaryBuilder.append("Log File Path: ${currentLog.absolutePath}\n")
        summaryBuilder.append("=======================================\n")
        summaryBuilder.append("\n")

        // Write summary first
        exportFile.writeText(summaryBuilder.toString())

        // Append active log content if it exists
        if (currentLog.exists()) {
             exportFile.appendBytes(currentLog.readBytes())
        }

        // Append Settings Snapshot
        exportFile.appendText("\n")
        exportFile.appendText(createSettingsSnapshot(context))

        return exportFile
    }

    /**
     * Deletes the active log file.
     */
    fun deleteLog(context: Context) {
        val logFile = DiagnosticsConfig.getLogFile(context)
        if (logFile.exists()) {
            logFile.delete()
        }
    }

    private fun createSettingsSnapshot(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sb = StringBuilder()
        sb.append("===== Mjolnir Settings Snapshot =====\n")
        
        // Dump all prefs
        prefs.all.toSortedMap().forEach { (key, value) ->
            sb.append("$key: $value\n")
        }

        // Runtime checks
        val accessibilityRunning = isAccessibilityServiceEnabled(context)
        val homeTileActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
        val topApp = prefs.getString(KEY_TOP_APP, null)
        val bottomApp = prefs.getString(KEY_BOTTOM_APP, null)
        val homeConfigComplete = (topApp != null || bottomApp != null)
        
        // Notification Visible check isn't trivially available from here without 
        // essentially reimplementing the logic, so we'll mark it N/A or use a best guess 
        // based on service running state which effectively implies notification is visible for a FG service.
        // For accuracy per spec "true if reliably detectable", we will use N/A as we are not the service itself.
        val notificationVisible = "N/A" 

        sb.append("AccessibilityServiceRunning: $accessibilityRunning\n")
        sb.append("HomeTileActive: $homeTileActive\n")
        sb.append("HomeConfigComplete: $homeConfigComplete\n")
        sb.append("NotificationVisible: $notificationVisible\n")
        sb.append("=====================================\n")
        
        return sb.toString()
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedComponentName = ComponentName(context, HomeKeyInterceptorService::class.java)

        for (service in enabledServices) {
            val serviceComponentName = ComponentName(service.resolveInfo.serviceInfo.packageName, service.resolveInfo.serviceInfo.name)
            if (serviceComponentName == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
