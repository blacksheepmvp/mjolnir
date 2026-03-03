package xyz.blacksheep.mjolnir

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.view.Display
import android.util.Log
import xyz.blacksheep.mjolnir.settings.settingsPrefs

object SafetyNetManager {
    private const val TAG = "SafetyNetManager"

    data class SafetyNetStatus(
        val activeDisplayIds: List<Int>,
        val runningDisplayIds: Set<Int>
    )

    fun markPending(context: Context) {
        val prefs = context.settingsPrefs()
        prefs.edit().putBoolean(KEY_SAFETY_NET_PENDING, true).apply()
    }

    fun clearPending(context: Context) {
        val prefs = context.settingsPrefs()
        prefs.edit().remove(KEY_SAFETY_NET_PENDING).apply()
    }

    fun ensureSafetyNetActivities(context: Context, allowStart: Boolean = true) {
        if (!allowStart) {
            markPending(context)
            return
        }

        val displayManager = context.getSystemService(DisplayManager::class.java)
        val displays = displayManager.displays
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val targetDisplayIds = mutableSetOf(Display.DEFAULT_DISPLAY)
        displays.forEach { targetDisplayIds.add(it.displayId) }
        val displaySummary = displays.joinToString { "${it.displayId}:${it.name}" }
        Log.d(TAG, "DisplayManager reports: [$displaySummary]; targetIds=$targetDisplayIds")

        for (displayId in targetDisplayIds) {
            if (hasSafetyNetTask(activityManager, displayId)) continue

            val intent = Intent(context, SafetyNetActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                data = Uri.parse("mjolnir://safetynet/$displayId")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
            Log.d(TAG, "Requested SafetyNetActivity for displayId=$displayId data=${intent.data}")
        }

        clearPending(context)
    }

    fun bringSafetyNetToFront(context: Context) {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val displays = displayManager.displays
        val targetDisplayIds = mutableSetOf(Display.DEFAULT_DISPLAY)
        displays.forEach { targetDisplayIds.add(it.displayId) }

        for (displayId in targetDisplayIds) {
            val intent = Intent(context, SafetyNetActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                data = Uri.parse("mjolnir://safetynet/$displayId")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
            Log.d(TAG, "Bring SafetyNetActivity to front for displayId=$displayId data=${intent.data}")
        }
    }

    fun forceSafetyNetToFront(context: Context) {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val displays = displayManager.displays
        val targetDisplayIds = mutableSetOf(Display.DEFAULT_DISPLAY)
        displays.forEach { targetDisplayIds.add(it.displayId) }

        for (displayId in targetDisplayIds) {
            val intent = Intent(context, SafetyNetActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                data = Uri.parse("mjolnir://safetynet/$displayId")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
            Log.d(TAG, "Force SafetyNetActivity to front for displayId=$displayId data=${intent.data}")
        }
    }

    fun getSafetyNetStatus(context: Context): SafetyNetStatus {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val displays = displayManager.displays
        val activeDisplayIds = mutableSetOf(Display.DEFAULT_DISPLAY)
        displays.forEach { activeDisplayIds.add(it.displayId) }

        val activityManager = context.getSystemService(ActivityManager::class.java)
        val runningDisplayIds = mutableSetOf<Int>()
        for (task in activityManager.appTasks) {
            val info = task.taskInfo
            val component = info.baseIntent?.component ?: continue
            if (component.className != SafetyNetActivity::class.java.name) continue
            val dataDisplayId = info.baseIntent?.data?.lastPathSegment?.toIntOrNull()
            if (dataDisplayId != null) {
                runningDisplayIds.add(dataDisplayId)
                continue
            }

            val infoDisplayId = readTaskDisplayId(info)
            if (infoDisplayId != null) {
                runningDisplayIds.add(infoDisplayId)
            }
        }

        return SafetyNetStatus(
            activeDisplayIds = activeDisplayIds.toList(),
            runningDisplayIds = runningDisplayIds
        )
    }

    fun isDefaultHome(context: Context): Boolean {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    private fun hasSafetyNetTask(activityManager: ActivityManager, displayId: Int): Boolean {
        val tasks = activityManager.appTasks
        for (task in tasks) {
            val info = task.taskInfo
            val component = info.baseIntent?.component ?: continue
            if (component.className != SafetyNetActivity::class.java.name) continue
            val dataDisplayId = info.baseIntent?.data?.lastPathSegment?.toIntOrNull()
            if (dataDisplayId != null && dataDisplayId == displayId) return true
            val infoDisplayId = readTaskDisplayId(info)
            if (infoDisplayId != null && infoDisplayId == displayId) return true
        }
        return false
    }

    private fun readTaskDisplayId(info: ActivityManager.RecentTaskInfo): Int? {
        return try {
            val field = info.javaClass.getDeclaredField("displayId")
            field.isAccessible = true
            field.get(info) as? Int
        } catch (e: Exception) {
            null
        }
    }
}