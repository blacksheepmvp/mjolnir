package xyz.blacksheep.mjolnir.model

/**
 * Represents the available visual themes supported by Mjolnir.
 *
 * This enum controls the global Material3 theme mode for the application.
 *
 * - `LIGHT`  → Forces a light theme regardless of system settings.
 * - `DARK`   → Forces a dark theme regardless of system settings.
 * - `SYSTEM` → Follows the device's system-wide light/dark setting.
 *
 * These values are stored in SharedPreferences and are used by the main
 * Activity to configure theming via CompositionLocal providers.
 */
enum class AppTheme { LIGHT, DARK, SYSTEM }
