package xyz.blacksheep.mjolnir.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.PREFS_NAME
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsLogger {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        val logFile = DiagnosticsConfig.getLogFile(context)
        val logDir = logFile.parentFile
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs()
        }
    }

    fun log(tag: String, message: String, context: Context) {
        if (!DiagnosticsConfig.isEnabled(context)) return
        writeEntry(tag, message, context)
    }

    fun logEvent(tag: String, event: String, details: String? = null, context: Context) {
        if (!DiagnosticsConfig.isEnabled(context)) return
        val detailsPart = details?.let { """ details="$it""" } ?: ""
        val message = "EVENT=$event$detailsPart"
        writeEntry(tag, message, context)
    }

    fun logException(tag: String, throwable: Throwable, context: Context) {
        if (!DiagnosticsConfig.isEnabled(context)) return
        val detailsString = "where=$tag message=${throwable.message}"
        logEvent("Error", "EXCEPTION", detailsString, context)
    }

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

    fun userExport(context: Context): File {
        return DiagnosticsConfig.exportLog(context)
    }

    fun userClear(context: Context) {
        DiagnosticsConfig.clearLog(context)
    }

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
