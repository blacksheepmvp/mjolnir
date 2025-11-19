package xyz.blacksheep.mjolnir.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import xyz.blacksheep.mjolnir.KEY_APP_BLACKLIST
import xyz.blacksheep.mjolnir.PREFS_NAME

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

class AppQueryHelper(private val context: Context) {

    private fun getBlacklist(): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Default to blacklisting Quickstep (com.android.launcher3) if no preference exists
        return prefs.getStringSet(KEY_APP_BLACKLIST, null) ?: setOf("com.android.launcher3")
    }

    /**
     * Returns the complete, canonical list of all launchable apps on the system.
     * Includes standard Launchers AND Home apps (Quickstep).
     * NO FILTERS applied.
     * Use this for the Blacklist Management UI.
     */
    fun queryCanonicalApps(): List<AppInfo> {
        val pm = context.packageManager

        // 1. Query standard launcher apps
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launchers = pm.queryIntentActivities(launcherIntent, 0)

        // 2. Query home apps
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val homeApps = pm.queryIntentActivities(homeIntent, 0)

        // 3. Merge and deduplicate
        val combined = (launchers + homeApps)
            .distinctBy { it.activityInfo.packageName }

        return combined.map { ri ->
            val pkg = ri.activityInfo.packageName
            AppInfo(
                packageName = pkg,
                label = ri.loadLabel(pm).toString(),
                icon = getOrCacheIcon(pm, pkg)
            )
        }.sortedBy { it.label.lowercase() }
    }

    /**
     * Returns the list for the Picker when "Filter Apps" is OFF.
     * Logic: Canonical List MINUS Blacklist.
     */
    fun queryAllApps(): List<AppInfo> {
        val blacklist = getBlacklist()
        val allApps = queryCanonicalApps()

        return allApps.filterNot { blacklist.contains(it.packageName) }
    }

    /**
     * Returns the list for the Picker when "Filter Apps" is ON.
     * Logic: Only HOME apps (Quickstep, Nova, etc) MINUS Blacklist.
     */
    fun queryLauncherApps(): List<AppInfo> {
        val pm = context.packageManager
        val blacklist = getBlacklist()

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val resolved = pm.queryIntentActivities(intent, 0)

        return resolved
            .distinctBy { it.activityInfo.packageName }
            .filterNot { blacklist.contains(it.activityInfo.packageName) }
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
                pm.getApplicationIcon(packageName)
            }
        }

        /**
         * Prewarm icon cache for all launchable apps.
         */
        fun prewarmAllApps(context: Context) {
            val pm = context.packageManager

            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }

            val resolved = pm.queryIntentActivities(launcherIntent, 0) +
                           pm.queryIntentActivities(homeIntent, 0)

            resolved.distinctBy { it.activityInfo.packageName }.forEach { ri ->
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
