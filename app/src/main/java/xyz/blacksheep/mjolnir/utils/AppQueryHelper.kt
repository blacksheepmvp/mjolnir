package xyz.blacksheep.mjolnir.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

class AppQueryHelper(private val context: Context) {

    fun queryAllApps(): List<AppInfo> {
        val pm = context.packageManager

        // Equivalent to what the app drawer shows
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolved = pm.queryIntentActivities(intent, 0)

        return resolved
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                AppInfo(
                    packageName = pkg,
                    label = ri.loadLabel(pm).toString(),
                    icon = getOrCacheIcon(pm, pkg)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun queryLauncherApps(): List<AppInfo> {
        val pm = context.packageManager

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val resolved = pm.queryIntentActivities(intent, 0)

        return resolved
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                AppInfo(
                    packageName = pkg,
                    label = ri.loadLabel(pm).toString(),
                    icon = getOrCacheIcon(pm, pkg)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    companion object {
        // volatile in-memory cache, shared across the app process
        private val iconCache = mutableMapOf<String, Drawable>()

        fun getOrCacheIcon(pm: PackageManager, packageName: String): Drawable {
            return iconCache.getOrPut(packageName) {
                // getApplicationIcon is what you want for full-color app icons
                pm.getApplicationIcon(packageName)
            }
        }

        /**
         * Prewarm icon cache for all launchable apps.
         * Safe to call from a background thread (IO dispatcher).
         */
        fun prewarmAllApps(context: Context) {
            val pm = context.packageManager

            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolved = pm.queryIntentActivities(intent, 0)

            resolved.forEach { ri ->
                val pkg = ri.activityInfo.packageName
                if (!iconCache.containsKey(pkg)) {
                    try {
                        iconCache[pkg] = pm.getApplicationIcon(pkg)
                    } catch (_: Exception) {
                        // ignore broken / system entries
                    }
                }
            }
        }
    }

}
