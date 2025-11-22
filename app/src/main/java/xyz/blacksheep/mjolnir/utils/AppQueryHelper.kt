package xyz.blacksheep.mjolnir.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import xyz.blacksheep.mjolnir.KEY_APP_BLACKLIST
import xyz.blacksheep.mjolnir.PREFS_NAME

/**
 * A simple data class holding the display information for an installed application.
 *
 * @property packageName The unique Android package name (e.g., "com.example.app").
 * @property label The human-readable name of the application (e.g., "My App").
 * @property icon The application's launcher icon as a Drawable.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

/**
 * A helper class for querying, filtering, and retrieving metadata about installed applications.
 *
 * It handles:
 * - Retrieving standard launcher apps and home screen replacements.
 * - Applying user-defined blacklists stored in SharedPreferences.
 * - Caching application icons in memory to improve list scrolling performance.
 *
 * @param context The application context used for accessing PackageManager and SharedPreferences.
 */
class AppQueryHelper(private val context: Context) {

    /**
     * Retrieves the current set of blacklisted package names from SharedPreferences.
     *
     * If no blacklist is found, it defaults to hiding the system launcher (Quickstep)
     * and Android Settings to avoid cluttering the Mjolnir launcher.
     */
    private fun getBlacklist(): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Default to blacklisting Quickstep (com.android.launcher3) and Android Settings if no preference exists
        val defaultBlacklist = setOf("com.android.launcher3", "com.android.settings")
        return prefs.getStringSet(KEY_APP_BLACKLIST, null) ?: defaultBlacklist
    }

    /**
     * Returns the complete, canonical list of all launchable apps on the system.
     *
     * **Scope:**
     * - Includes standard `CATEGORY_LAUNCHER` apps.
     * - Includes `CATEGORY_HOME` apps (other launchers).
     * - **NO FILTERS** are applied (blacklisted apps are included).
     *
     * **Use Case:**
     * - Use this method when populating the Blacklist Management UI, where users need to see *everything* to decide what to hide.
     *
     * @return A sorted list of [AppInfo] objects.
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
     * Returns the list of apps for the Launcher Picker when "Filter Apps" is **OFF**.
     *
     * **Logic:**
     * - Starts with [queryCanonicalApps] (everything).
     * - Removes any app present in the blacklist.
     *
     * **Use Case:**
     * - Use this when the user wants to select an app for the top/bottom screen from *all* available apps (minus hidden ones).
     *
     * @return A filtered and sorted list of [AppInfo].
     */
    fun queryAllApps(): List<AppInfo> {
        val blacklist = getBlacklist()
        val allApps = queryCanonicalApps()

        return allApps.filterNot { blacklist.contains(it.packageName) }
    }

    /**
     * Returns the list of apps for the Launcher Picker when "Filter Apps" is **ON**.
     *
     * **Logic:**
     * - Queries ONLY `CATEGORY_HOME` apps (other launchers).
     * - Removes any app present in the blacklist.
     *
     * **Use Case:**
     * - Use this when the user specifically wants to chain-load another launcher (e.g., Nova, Daijishō) and hide regular apps.
     *
     * @return A filtered and sorted list of [AppInfo].
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

        /**
         * Retrieves an icon from the in-memory cache or loads it from the PackageManager if missing.
         *
         * This method is thread-safe enough for UI usage but heavy if the cache is cold.
         * See [prewarmAllApps] for background loading.
         *
         * @param pm The PackageManager instance.
         * @param packageName The package name to look up.
         * @return The application icon Drawable.
         */
        fun getOrCacheIcon(pm: PackageManager, packageName: String): Drawable {
            return iconCache.getOrPut(packageName) {
                pm.getApplicationIcon(packageName)
            }
        }

        /**
         * Pre-loads icons for all launchable apps into the in-memory cache.
         *
         * **Performance Note:**
         * This should be called on a background thread (e.g., `Dispatchers.IO`) during app startup.
         * It ensures that subsequent calls to [queryCanonicalApps] or [getOrCacheIcon] return instantly
         * without causing frame drops during UI scrolling.
         *
         * @param context Android Context.
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
