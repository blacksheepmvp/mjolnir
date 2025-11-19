package xyz.blacksheep.mjolnir.utils

import android.content.Context
import xyz.blacksheep.mjolnir.PREFS_NAME
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val KEY_DIAGNOSTICS_ENABLED = "diagnostics_enabled"
const val KEY_DIAGNOSTICS_MAX_BYTES = "diagnostics_max_bytes"

object DiagnosticsConfig {

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DIAGNOSTICS_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DIAGNOSTICS_ENABLED, enabled).apply()
    }

    fun getMaxBytes(context: Context): Long {
        // Default to 1MB
        return getPrefs(context).getLong(KEY_DIAGNOSTICS_MAX_BYTES, 1 * 1024 * 1024L)
    }

    fun setMaxBytes(context: Context, bytes: Long) {
        getPrefs(context).edit().putLong(KEY_DIAGNOSTICS_MAX_BYTES, bytes).apply()
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, "logs/diagnostics_current.log")
    }

    fun clearLog(context: Context) {
        val logFile = getLogFile(context)
        if (logFile.exists()) {
            logFile.writeText("")
        }
    }

    fun exportLog(context: Context): File {
        val currentLog = getLogFile(context)
        if (!currentLog.exists()) {
            return currentLog // Return non-existent file to signal error
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val exportFileName = "mjolnir_diag_$timestamp.log"
        val exportFile = File(currentLog.parentFile, exportFileName)

        currentLog.copyTo(exportFile, overwrite = true)
        return exportFile
    }
}
