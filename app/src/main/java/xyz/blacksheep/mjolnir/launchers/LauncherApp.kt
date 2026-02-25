package xyz.blacksheep.mjolnir.launchers

import android.content.Intent

/**
 * Lightweight model representing an app selectable in the dual-screen launcher.
 *
 * Fields:
 * - `label`        → Resolved app label (already localized).
 * - `packageName`  → Package name used as stable identifier.
 * - `launchIntent` → Intent used to start the application.
 *
 * Notes:
 * - This is separate from AppInfo to decouple UI-level launcher metadata from
 *   the raw query helper format.
 */
data class LauncherApp(
    val label: String,
    val packageName: String,
    val launchIntent: Intent
)
