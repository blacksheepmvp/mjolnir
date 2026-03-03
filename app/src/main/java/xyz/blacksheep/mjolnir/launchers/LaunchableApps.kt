package xyz.blacksheep.mjolnir.launchers

import android.content.Context
import android.content.Intent
import xyz.blacksheep.mjolnir.utils.AppQueryHelper

/**
 * Retrieves a list of installed applications suitable for the launcher picker.
 *
 * **Logic:**
 * - Uses [AppQueryHelper] to fetch apps.
 * - If `showAll` is true: Returns all launchable apps (canonical list).
 * - If `showAll` is false: Returns only apps with `CATEGORY_HOME` (launchers).
 * - **Injects:** A special `<Nothing>` option at the top of the list to allow clearing a slot.
 *
 * @param context Context for PackageManager access.
 * @param showAll Filter toggle state.
 * @return A sorted list of [LauncherApp] objects.
 */
fun getLaunchableApps(context: Context, showAll: Boolean): List<LauncherApp> {
    val queryHelper = AppQueryHelper(context)
    val appInfoList = if (showAll) {
        queryHelper.queryAllApps()
    } else {
        queryHelper.queryLauncherApps()
    }

    val pm = context.packageManager

    val apps = appInfoList.map { appInfo ->
        val launchIntent =
            pm.getLaunchIntentForPackage(appInfo.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(appInfo.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

        LauncherApp(
            label = appInfo.label,
            packageName = appInfo.packageName,
            launchIntent = launchIntent
        )
    }.sortedBy { it.label.lowercase() }

    // --- MANUAL CHANGE START ---
    // Create the <Nothing> option
    val nothingOption = LauncherApp(
        label = "<Nothing>",
        packageName = "NOTHING", // This matches the backend check
        launchIntent = Intent()  // Empty intent
    )

    // Return the list with <Nothing> at the top
    return listOf(nothingOption) + apps
    // --- MANUAL CHANGE END ---
}
