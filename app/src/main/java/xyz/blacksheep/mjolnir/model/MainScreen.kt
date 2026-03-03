package xyz.blacksheep.mjolnir.model

/**
 * Indicates which physical display (Top or Bottom screen on the AYN Thor)
 * should be considered the "primary" display during dual-screen launching.
 *
 * This preference affects:
 * - Focus order when launching two apps simultaneously.
 * - Which app is launched first during dual-launch sequences.
 * - UI labels inside HomeLauncherSettingsMenu.
 *
 * NOTE:
 * The actual display IDs are handled by `DualScreenLauncher` and are not
 * defined here; this enum simply represents user preference.
 */
enum class MainScreen { TOP, BOTTOM }
