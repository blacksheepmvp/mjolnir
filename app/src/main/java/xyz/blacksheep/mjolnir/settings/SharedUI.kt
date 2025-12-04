package xyz.blacksheep.mjolnir.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import xyz.blacksheep.mjolnir.KEY_APP_BLACKLIST
import xyz.blacksheep.mjolnir.KEY_CUSTOM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.KEY_DOUBLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_ENABLE_FOCUS_LOCK_WORKAROUND
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_LONG_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_SINGLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_TRIPLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_USE_SYSTEM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.home.actionLabel
import xyz.blacksheep.mjolnir.utils.AppInfo
import xyz.blacksheep.mjolnir.utils.AppQueryHelper
import xyz.blacksheep.mjolnir.utils.DiagnosticsActions
import xyz.blacksheep.mjolnir.utils.DiagnosticsConfig
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.DiagnosticsSummary
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher
import xyz.blacksheep.mjolnir.utils.GameInfo
import xyz.blacksheep.mjolnir.utils.GameInfoSaver
import xyz.blacksheep.mjolnir.utils.OverwriteInfo
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

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

/**
 * A custom `Saver` implementation for persisting and restoring [UiState]
 * instances across configuration changes, process recreation, or navigation.
 *
 * This is used primarily by the Steam File Generator search UI, which must
 * persist both intermediate loading states and successful search results
 * (represented by [GameInfo]).
 *
 * Serialization Strategy:
 * - `Idle`     → Stored as the string literal `"Idle"`.
 * - `Loading`  → Stored as `["Loading", appId]`.
 * - `Success`  → Serialized using [GameInfoSaver], prefixed by `"Success"`.
 * - `Failure`  → Stored as `["Failure", errorMessage]`.
 *
 * Restoration Strategy:
 * - Reconstructs the appropriate sealed UiState subtype based on the first
 *   item of the serialized payload.
 * - Delegates deserialization of GameInfo to [GameInfoSaver].
 *
 * This saver ensures UiState survives process death within Compose.
 */
val UiStateSaver = Saver<UiState, Any>(
    save = {
        when (it) {
            is UiState.Idle -> "Idle"
            is UiState.Loading -> listOf("Loading", it.appId)
            is UiState.Success -> with(GameInfoSaver) { save(it.gameInfo)?.let { list -> listOf("Success") + list } ?: error("Could not save GameInfo") }
            is UiState.Failure -> listOf("Failure", it.error)
        }
    },
    restore = {
        when (val value = it as? List<*>) {
            null -> UiState.Idle
            else -> when (value[0]) {
                "Loading" -> UiState.Loading(value[1] as String)
                "Success" -> {
                    @Suppress("UNCHECKED_CAST")
                    GameInfoSaver.restore(value.drop(1) as? List<String> ?: emptyList())?.let { gameInfo ->
                        UiState.Success(gameInfo)
                    } ?: UiState.Idle
                }
                "Failure" -> UiState.Failure(value[1] as String)
                else -> UiState.Idle
            }
        }
    }
)

/**
 * Represents the different UI states of the Steam File Generator's search
 * workflow. This sealed interface allows the Search UI to display:
 *
 * - Idle instructions
 * - A loading indicator
 * - A successful game metadata result
 * - Errors from querying the API
 *
 * Subtypes:
 * - [Idle]    → No search executed yet.
 * - [Loading] → Actively querying Steam metadata for a given AppID.
 * - [Success] → Query finished; metadata stored in [gameInfo].
 * - [Failure] → Query failed; error message stored for display.
 *
 * These states are saved/restored using [UiStateSaver].
 */
sealed interface UiState {
    object Idle : UiState
    data class Loading(val appId: String) : UiState
    data class Success(val gameInfo: GameInfo) : UiState
    data class Failure(val error: String) : UiState
}

/**
 * Displays a configurable launcher slot for either the Top or Bottom screen.
 *
 * This card is used inside HomeLauncherSettingsMenu to visually represent the
 * currently assigned app for each screen. The card is fully clickable and
 * behaves like a button that triggers the dropdown menu for app selection.
 *
 * Behavior:
 * - **Empty state:** Shows a "+" icon and the provided label.
 * - **Populated state:** Shows the app's icon (retrieved via `getPackageIcon`)
 *   and the resolved app label.
 *
 * Layout:
 * - Fixed height (120.dp)
 * - Rounded corners and elevated surface
 * - Centered column content
 *
 * @param app The launcher app currently assigned to this slot, or null if none.
 * @param label User-facing label describing the slot ("Select Top Screen App").
 * @param onClick Triggered when the user taps the card to expand the dropdown.
 * @param modifier Optional layout modifier.
 */
@Composable
fun AppSlotCard(
    modifier: Modifier = Modifier,
    app: LauncherApp?,              // null = no selection yet
    label: String,                  // "Top Screen App", "Bottom Screen App"
    onClick: () -> Unit             // triggers drop-down
) {
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = bgColor,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (app == null) {
                // Empty slot placeholder
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // Show app icon and name
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = app.label,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Converts an Android [Drawable] into a Compose [Painter] instance suitable
 * for displaying inside Image composables.
 *
 * Behavior:
 * - If the drawable is non-null, it is converted to a Bitmap and wrapped in
 *   a [BitmapPainter].
 * - If null, returns a transparent [ColorPainter].
 *
 * This helper ensures consistent image rendering within Compose without
 * leaking Android-specific types.
 *
 * @param drawable The drawable to convert into a painter.
 * @return A compose Painter that can be passed directly to Image().
 */
@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter {
    return remember(drawable) { drawable?.toBitmap()?.let { BitmapPainter(it.asImageBitmap()) } ?: ColorPainter(Color.Transparent) }
}

/**
 * Extension function that retrieves the application icon for the package
 * referenced by this intent.
 *
 * Behavior:
 * - Extracts the package name from the intent.
 * - Calls PackageManager.getApplicationIcon(packageName).
 * - Returns null if the package cannot be resolved or the icon fails to load.
 *
 * NOTE:
 * This method is used primarily in app selection UIs to render launcher icons.
 *
 * @receiver Intent whose package should be inspected.
 * @param context Optional context (defaults to LocalContext).
 * @return Drawable for the app's icon, or null on failure.
 */
@Composable
fun Intent.getPackageIcon(context: Context = LocalContext.current): Drawable? {
    val pm = context.packageManager
    val pkg = this.getPackage() ?: return null
    return try {
        pm.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }
}

/**
 * A simple overflow menu (three-dot menu) providing quick access to:
 * - Settings
 * - About dialog
 * - Quit action
 *
 * This component is typically used in top-level surfaces where a compact
 * navigation affordance is needed. The menu expands from an IconButton and
 * collapses automatically after an item is selected.
 *
 * All behavior is delegated through callbacks to keep UI stateless.
 *
 * @param onSettingsClick Invoked when "Settings" is selected.
 * @param onAboutClick Invoked when "About" is selected.
 * @param onQuitClick Invoked when "Quit" is selected.
 */
@Composable
fun HamburgerMenu(
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onQuitClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(4.dp)) {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    onSettingsClick()
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("About") },
                onClick = {
                    onAboutClick()
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Quit") },
                onClick = {
                    onQuitClick()
                    menuExpanded = false
                }
            )
        }
    }
}

/**
 * High-level entrypoint into Mjolnir’s Settings UI. This overload directly
 * accepts all configuration parameters used across the various settings
 * sub-screens and forwards them to the internal navigation-driven version of
 * SettingsScreen.
 *
 * This function does **not** create or hold a NavController itself. Instead,
 * it immediately delegates to the second SettingsScreen overload, injecting
 * `startDestination = "main"` and passing all other parameters unchanged.
 *
 * ## Responsibilities
 * - Acts as the stable public-facing Settings entry from MainActivity.
 * - Ensures consistent routing into the internal settings NavHost.
 * - Bundles all state values and callbacks required by nested menus, including:
 *   - Steam File Generator settings
 *   - Theme selection
 *   - Directory selection
 *   - Developer mode toggle
 *   - Home Launcher configuration (top/bottom apps, app filtering, dual-screen)
 *
 * @param currentPath The ROM directory currently configured for Steam FG.
 * @param currentTheme The active application theme.
 * @param onThemeChange Callback invoked when the user selects a new theme.
 * @param onChangeDirectory Triggers a directory picker for ROM location.
 * @param onClose Called when the user exits settings.
 * @param confirmDelete Whether to show delete-confirmation dialogs.
 * @param onConfirmDeleteChange Updates the confirm-delete preference.
 * @param autoCreateFile Whether Steam FG auto-creates files on search success.
 * @param onAutoCreateFileChange Callback invoked when this toggle changes.
 * @param devMode Whether developer mode is enabled.
 * @param onDevModeChange Callback to update developer mode.
 * @param topApp The package name assigned to the Top screen launcher slot.
 * @param onTopAppChange Callback to update the Top screen launcher slot.
 * @param bottomApp The package name assigned to the Bottom screen launcher slot.
 * @param onBottomAppChange Callback to update the Bottom screen launcher slot.
 * @param showAllApps Toggles between showing all launchable apps or only launchers.
 * @param onShowAllAppsChange Callback invoked when the app filter changes.
 * @param onSetDefaultHome Triggers the system dialog for choosing the default Home app.
 * @param onLaunchDualScreen Callback invoked for testing dual-screen launch.
 * @param mainScreen The screen considered “primary” after a dual-screen launch.
 * @param onMainScreenChange Callback invoked when mainScreen is updated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentPath: String,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onChangeDirectory: () -> Unit,
    onClose: () -> Unit,
    confirmDelete: Boolean,
    onConfirmDeleteChange: (Boolean) -> Unit,
    autoCreateFile: Boolean,
    onAutoCreateFileChange: (Boolean) -> Unit,
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit,
    topApp: String?,
    onTopAppChange: (String?) -> Unit,
    bottomApp: String?,
    onBottomAppChange: (String?) -> Unit,
    showAllApps: Boolean,
    onShowAllAppsChange: (Boolean) -> Unit,
    onSetDefaultHome: () -> Unit,
    onLaunchDualScreen: () -> Unit,
    mainScreen: MainScreen,
    onMainScreenChange: (MainScreen) -> Unit
) {
    SettingsScreen(
        startDestination = "main",
        currentPath = currentPath,
        currentTheme = currentTheme,
        onThemeChange = onThemeChange,
        onChangeDirectory = onChangeDirectory,
        onClose = onClose,
        confirmDelete = confirmDelete,
        onConfirmDeleteChange = onConfirmDeleteChange,
        autoCreateFile = autoCreateFile,
        onAutoCreateFileChange = onAutoCreateFileChange,
        devMode = devMode,
        onDevModeChange = onDevModeChange,
        topApp = topApp,
        onTopAppChange = onTopAppChange,
        bottomApp = bottomApp,
        onBottomAppChange = onBottomAppChange,
        showAllApps = showAllApps,
        onShowAllAppsChange = onShowAllAppsChange,
        onSetDefaultHome = onSetDefaultHome,
        onLaunchDualScreen = onLaunchDualScreen,
        mainScreen = mainScreen,
        onMainScreenChange = onMainScreenChange
    )
}

/**
 * Core Settings implementation backed by an internal [NavController] and
 * [NavHost]. This version creates its own navigation controller and contains
 * the complete routing logic for all settings sub-screens.
 *
 * ## Responsibilities
 * - Hosts all settings UI within a single navigation graph.
 * - Handles back-press behavior: pops navigation if possible, otherwise calls
 *   `onClose()`.
 * - Passes all configuration state and callbacks to each destination screen.
 *
 * ## Routes Provided
 * - "main" → MainSettingsScreen
 * - "tool_settings" → Steam File Generator configuration
 * - "appearance" → Theme selection
 * - "home_launcher" → Dual-screen launcher configuration
 * - "app_blacklist" → App blacklist editor
 * - "developer_mode" → Developer settings
 * - "about" → About dialog
 * - "diagnostics_summary" → Diagnostics summary/export screen
 *
 * @param startDestination Navigation start route (usually "main").
 * @param currentPath Current ROM directory for Steam FG.
 * @param currentTheme The active app theme.
 * @param onThemeChange Callback invoked when theme is changed.
 * @param onChangeDirectory Triggers ROM directory picker.
 * @param onClose Called when exiting the entire settings UI.
 * @param confirmDelete Whether to show delete confirmation dialogs.
 * @param onConfirmDeleteChange Updates confirm-delete preference.
 * @param autoCreateFile Whether Steam FG auto-creates files on search success.
 * @param onAutoCreateFileChange Updates auto-create preference.
 * @param devMode Whether developer mode is enabled.
 * @param onDevModeChange Updates developer mode flag.
 * @param topApp Package name for the Top launcher slot.
 * @param onTopAppChange Updates the Top launcher slot.
 * @param bottomApp Package name for the Bottom launcher slot.
 * @param onBottomAppChange Updates the Bottom launcher slot.
 * @param showAllApps Toggle to switch between all apps and launcher-only apps.
 * @param onShowAllAppsChange Updates show-all-apps preference.
 * @param onSetDefaultHome Launches system “Set default Home” screen.
 * @param onLaunchDualScreen Triggers optional dual-screen test launch.
 * @param mainScreen Which display is primary after launch.
 * @param onMainScreenChange Callback to update mainScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    startDestination: String,
    currentPath: String,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onChangeDirectory: () -> Unit,
    onClose: () -> Unit,
    confirmDelete: Boolean,
    onConfirmDeleteChange: (Boolean) -> Unit,
    autoCreateFile: Boolean,
    onAutoCreateFileChange: (Boolean) -> Unit,
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit,
    topApp: String?,
    onTopAppChange: (String?) -> Unit,
    bottomApp: String?,
    onBottomAppChange: (String?) -> Unit,
    showAllApps: Boolean,
    onShowAllAppsChange: (Boolean) -> Unit,
    onSetDefaultHome: () -> Unit,
    onLaunchDualScreen: () -> Unit,
    mainScreen: MainScreen,
    onMainScreenChange: (MainScreen) -> Unit
) {
    val navController = rememberNavController()

    BackHandler {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            onClose()
        }
    }

    /**
     * Internal navigation graph defining all available settings routes.
     *
     * Each route corresponds to one of Mjolnir’s configuration screens. All of
     * these destinations were historically contained in SharedUI.kt, but are
     * structured here as independent composables within a single NavHost.
     *
     * Routes:
     * - "main"               → MainSettingsScreen
     * - "tool_settings"      → ToolSettingsScreen
     * - "appearance"         → AppearanceSettingsScreen
     * - "home_launcher"      → HomeLauncherSettingsMenu
     * - "app_blacklist"      → BlacklistSettingsScreen
     * - "developer_mode"     → DeveloperSettingsScreen
     * - "about"              → AboutDialog
     * - "diagnostics_summary"→ DiagnosticsSummaryScreen
     * - "home_setup"         → HomeSetup
     *
     * Behavior:
     * - No animations are applied by default.
     * - Back navigation is handled by parent NavController.
     * - Each screen is responsible for managing its own state and side effects.
     *
     * Lifecycle Notes:
     * - Because the settings system is large and stateful, each screen should
     *   avoid heavy recomposition and rely on remembered state where needed.
     */
    NavHost(navController = navController, startDestination = startDestination) {
        composable("main") {
            MainSettingsScreen(navController = navController, onClose = onClose)
        }
        composable("tool_settings") {
            ToolSettingsScreen(
                navController = navController,
                confirmDelete = confirmDelete,
                onConfirmDeleteChange = onConfirmDeleteChange,
                autoCreateFile = autoCreateFile,
                onAutoCreateFileChange = onAutoCreateFileChange,
                romsDirectory = currentPath,
                onChangeDirectory = onChangeDirectory
            )
        }
        composable("appearance") {
            AppearanceSettingsScreen(navController = navController, currentTheme = currentTheme, onThemeChange = onThemeChange)
        }
        composable("home_launcher") {
            HomeLauncherSettingsMenu(
                navController = navController,
                topApp = topApp,
                onTopAppChange = onTopAppChange,
                bottomApp = bottomApp,
                onBottomAppChange = onBottomAppChange,
                showAllApps = showAllApps,
                onShowAllAppsChange = onShowAllAppsChange,
                onSetDefaultHome = onSetDefaultHome,
                onLaunchDualScreen = onLaunchDualScreen,
                mainScreen = mainScreen,
                onMainScreenChange = onMainScreenChange
            )
        }
        composable("app_blacklist") {
            BlacklistSettingsScreen(navController = navController)
        }
        composable("developer_mode") {
            DeveloperSettingsScreen(navController = navController, devMode = devMode, onDevModeChange = onDevModeChange)
        }
        composable("about") {
            AboutDialog() { navController.popBackStack() }
        }
        composable("diagnostics_summary") {
            DiagnosticsSummaryScreen(navController = navController)
        }
    }
}

/**
 * The top-level settings landing page. Displays the primary categories of
 * configuration using a vertical list.
 *
 * Behavior:
 * - Provides large, touch-friendly entry points into deeper settings menus.
 * - Navigates using the supplied [NavController].
 * - Allows exiting the settings UI entirely via `onClose`.
 *
 * @param navController Controller used to navigate to sub-screens.
 * @param onClose Invoked when the user presses the toolbar back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettingsScreen(navController: NavController, onClose: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) {
        innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Text("Tool Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Home,
                    title = "Mjolnir Home Settings",
                    subtitle = "Customize your DS-home environment"
                ) { navController.navigate("home_launcher") }
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Build,
                    title = "Steam File Settings",
                    subtitle = "Manage file creation and directory settings"
                ) { navController.navigate("tool_settings") }
            }
            item {
                HorizontalDivider()
            }
            item {
                Text("System", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Add,
                    title = "Appearance",
                    subtitle = "Adjust themes and colors"
                ) { navController.navigate("appearance") }
            }

            /*item {
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "Developer Mode",
                    subtitle = "Access advanced developer options"
                ) { navController.navigate("developer_mode") }
            }*/
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Version and project information"
                ) { navController.navigate("about") }
            }
        }
    }
}

/**
 * Reusable list item representing a clickable entry within the settings menu.
 *
 * Behavior:
 * - Entire row is tappable and triggers the provided callback.
 * - Displays a title and optional subtitle/description.
 * - Automatically handles padding, spacing, and ripple behavior.
 *
 * Intended Use:
 * - Top-level navigation entries inside MainSettingsScreen.
 * - Mid-level entries inside HomeLauncherSettingsMenu or other screens.
 *
 * Design Notes:
 * - Should remain visually simple so it can appear in multiple settings
 *   contexts without conflicting with other UI elements.
 *
 * @param title Main label for the row.
 * @param description Optional smaller text describing the item.
 * @param onClick Invoked when the row is tapped.
 */
@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Settings screen for configuring the Steam File Generator (Steam FG).
 *
 * ## Responsibilities
 * - Toggle: confirmDelete — whether to require a dialog before deleting files.
 * - Toggle: autoCreateFile — automatically generate the `.steam` file when a
 *   single successful search result is returned.
 * - Displays the currently selected ROM directory.
 * - Allows the user to choose a new ROM directory.
 *
 * ## Behavior
 * - Reads and writes preferences directly via supplied callbacks.
 * - Navigates back using the provided NavController.
 *
 * @param navController For back navigation.
 * @param confirmDelete Whether delete confirmation dialogs are enabled.
 * @param onConfirmDeleteChange Updates the confirm-delete preference.
 * @param autoCreateFile Whether auto-creation of `.steam` files is enabled.
 * @param onAutoCreateFileChange Updates the auto-create preference.
 * @param romsDirectory The currently configured ROM directory path.
 * @param onChangeDirectory Callback to open a directory picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolSettingsScreen(
    navController: NavController,
    confirmDelete: Boolean,
    onConfirmDeleteChange: (Boolean) -> Unit,
    autoCreateFile: Boolean,
    onAutoCreateFileChange: (Boolean) -> Unit,
    romsDirectory: String,
    onChangeDirectory: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Steam File Generator") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) {
        innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Confirm file deletions", style = MaterialTheme.typography.bodyLarge)
                        Text("Show a confirmation dialog before deleting files", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = confirmDelete, onCheckedChange = onConfirmDeleteChange)
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-create file on search success", style = MaterialTheme.typography.bodyLarge)
                        Text("Automatically generate the file on a single search result", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoCreateFile, onCheckedChange = onAutoCreateFileChange)
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text("Current ROMs Directory", style = MaterialTheme.typography.bodyLarge)
                    Text(romsDirectory, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onChangeDirectory) { Text("Change Directory") }
                }
            }
        }
    }
}

@Composable
private fun SystemSettingsScreen(navController: NavController) {
    // This can be a placeholder or contain system-wide settings in the future
}

/**
 * Appearance settings menu, allowing the user to switch between Light, Dark,
 * and System themes.
 *
 * @param navController Used for back navigation.
 * @param currentTheme The currently active theme selection.
 * @param onThemeChange Called when the user selects a new theme.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsScreen(
    navController: NavController,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val themeOptions = AppTheme.entries.map { it.name }
    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Appearance") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) {
        innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Adjust themes and colors",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                                onClick = { onThemeChange(AppTheme.valueOf(label)) },
                                selected = currentTheme.name == label
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Settings screen for managing the App Blacklist used by the Home Launcher.
 *
 * ## Responsibilities
 * - Displays all currently blacklisted apps.
 * - Allows removing apps from the blacklist.
 * - Opens a dialog to add new apps to the blacklist.
 *
 * ## Behavior
 * - Loads initial blacklist from SharedPreferences.
 * - Updates SharedPreferences immediately when modifications occur.
 *
 * @param navController For back navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlacklistSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // State for the current blacklist. Read once, then updated locally.
    var blacklistedPackages by rememberSaveable {
        mutableStateOf(prefs.getStringSet(KEY_APP_BLACKLIST, emptySet()) ?: emptySet())
    }

    // Function to update both the state and SharedPreferences
    fun updateBlacklist(newBlacklist: Set<String>) {
        blacklistedPackages = newBlacklist
        prefs.edit().putStringSet(KEY_APP_BLACKLIST, newBlacklist).apply()
    }

    // State for the "Add App" dialog
    var showAddDialog by remember { mutableStateOf(false) }

    // Get all apps to display in the lists
    val allApps = remember {
        // We use queryAllApps and handle filtering manually here
        AppQueryHelper(context).queryCanonicalApps().sortedBy { it.label.lowercase() }
    }

    val blacklistedAppInfo = remember(blacklistedPackages, allApps) {
        allApps.filter { it.packageName in blacklistedPackages }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("App Blacklist") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add to blacklist")
            }
        }
    ) { innerPadding ->
        if (showAddDialog) {
            val nonBlacklistedApps = remember(allApps, blacklistedPackages) {
                allApps.filter { it.packageName !in blacklistedPackages }
            }
            AddAppToBlacklistDialog(
                allApps = nonBlacklistedApps,
                onDismiss = { showAddDialog = false },
                onAppSelected = { appPackage ->
                    updateBlacklist(blacklistedPackages + appPackage)
                }
            )
        }

        if (blacklistedAppInfo.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Blacklist is empty")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(blacklistedAppInfo, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberDrawablePainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = app.label,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            updateBlacklist(blacklistedPackages - app.packageName)
                        }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog listing all apps not currently blacklisted and allowing the user to
 * add any one of them to the blacklist.
 *
 * @param allApps List of all non-blacklisted apps (as AppInfo instances).
 * @param onDismiss Called when the dialog is closed.
 * @param onAppSelected Callback invoked with the selected package name.
 */
@Composable
private fun AddAppToBlacklistDialog(
    allApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add App to Blacklist") },
        text = {
            LazyColumn {
                items(allApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(app.packageName) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberDrawablePainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(app.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private const val TAG = "MjolnirHomeLauncher"

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

/**
 * Configuration screen for customizing Mjolnir’s dual-screen launcher:
 * top/bottom app assignment, gestures, double-tap delay, diagnostics, and
 * focus-lock workaround.
 *
 * @param navController For back navigation.
 * @param topApp Package name assigned to the Top screen slot.
 * @param onTopAppChange Callback invoked when Top slot app changes.
 * @param bottomApp Package name assigned to the Bottom screen slot.
 * @param onBottomAppChange Callback invoked when Bottom slot app changes.
 * @param showAllApps Whether to show all apps or only launcher apps.
 * @param onShowAllAppsChange Callback invoked when the filter changes.
 * @param onSetDefaultHome Opens the system default-home app chooser.
 * @param onLaunchDualScreen Required callback used for test dual-screen launch.
 * @param mainScreen Which screen should be primary after launch.
 * @param onMainScreenChange Callback invoked when mainScreen changes.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeLauncherSettingsMenu(
    navController: NavController,
    topApp: String?,
    onTopAppChange: (String?) -> Unit,
    bottomApp: String?,
    onBottomAppChange: (String?) -> Unit,
    showAllApps: Boolean,
    onShowAllAppsChange: (Boolean) -> Unit,
    onSetDefaultHome: () -> Unit,
    onLaunchDualScreen: () -> Unit,
    mainScreen: MainScreen,
    onMainScreenChange: (MainScreen) -> Unit
) {
    val context = LocalContext.current
    val launcherApps = remember(showAllApps) { getLaunchableApps(context, showAllApps) }
    var topExpanded by remember { mutableStateOf(false) }
    var bottomExpanded by remember { mutableStateOf(false) }

    val selectedTopApp = remember(topApp) { launcherApps.find { it.packageName == topApp } }
    val selectedBottomApp = remember(bottomApp) { launcherApps.find { it.packageName == bottomApp } }

    val diagnosticsEnabled = remember { mutableStateOf(DiagnosticsConfig.isEnabled(context)) }
    val maxLogSize = remember { mutableStateOf(DiagnosticsConfig.getMaxBytes(context)) }

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val focusLockWorkaroundState = remember { mutableStateOf(prefs.getBoolean(KEY_ENABLE_FOCUS_LOCK_WORKAROUND, false)) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Optional: check Settings.canDrawOverlays(context) here if you want to auto-enable the toggle
    }

    val mainScreenOptions = MainScreen.entries.map { it.name }

    // --- SPECIAL CASE HANDLING FOR QUICKSTEP / ODIN ---
    val SPECIAL_HOME_APPS = remember {
        setOf("com.android.launcher3", "com.odin.odinlauncher")
    }

    // Pending state for when a special app is selected but not yet set as default
    var pendingSlot by remember { mutableStateOf<Boolean?>(null) } // true = top, false = bottom, null = none
    var pendingPackage by remember { mutableStateOf<String?>(null) }

    // Helper to check if a package is the current system default home
    fun isSystemDefaultHome(pkg: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == pkg
    }

    // Re-check pending state when the screen resumes (e.g. back from system settings)
    // Note: In Compose, we use lifecycle effects or simple recomposition triggers.
    // Since we don't have a simple onResume here without extra dependencies,
    // we can rely on the user interaction flow or a side-effect if the window focus changes.
    // For simplicity in this snippet, we check when the composition enters or updates.
    LaunchedEffect(Unit) {
        // This runs on first composition. Ideally we want to check on resume.
        // A more robust way in pure Compose without LifecycleEventObserver is tricky,
        // but typically the user will click 'Set Default Home', go to system UI, and come back.
        // When they come back, if we can trigger a check, we're good.
        // For now, we handle the check in the dialog's "positive button" flow or rely on re-composition if the parent triggers it.
    }

    // We use a LifecycleEventObserver to detect onResume if needed, but let's keep it simple first.
    // We will check immediately if there is a pending state.

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Check if we have a pending package waiting for default status
                val pkg = pendingPackage
                val isTop = pendingSlot

                if (pkg != null && isTop != null) {
                    if (isSystemDefaultHome(pkg)) {
                        // User successfully set it as default! Apply the change.
                        handleAppSelection(
                            selectedAppPackage = pkg,
                            isForTopSlot = isTop,
                            currentTopApp = topApp,
                            currentBottomApp = bottomApp,
                            onTopAppChange = onTopAppChange,
                            onBottomAppChange = onBottomAppChange,
                            context = context
                        )
                        // Clear pending
                        pendingPackage = null
                        pendingSlot = null
                    } else {
                        // Still not default. Do nothing, leave slot as <Nothing>.
                        // Optionally clear pending if you want to force them to try again,
                        // but keeping it allows them to try multiple times.
                        // Per spec: "If it does not match: Leave the slot as <Nothing>. In all cases, clear pending state."
                        pendingPackage = null
                        pendingSlot = null
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (pendingPackage != null) {
        AlertDialog(
            onDismissRequest = {
                // User cancelled or tapped outside
                pendingPackage = null
                pendingSlot = null
            },
            title = { Text("Requires default home") },
            text = {
                // Try to get the app label for the message
                val label = launcherApps.find { it.packageName == pendingPackage }?.label ?: pendingPackage ?: "App"
                Text("In order to use $label with Mjolnir, you must also set $label as your default home.")
            },
            confirmButton = {
                TextButton(onClick = {
                    // Launch system home picker
                    onSetDefaultHome()
                    // Do NOT clear pending state here; we wait for onResume
                }) {
                    Text("Set Default Home")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingPackage = null
                    pendingSlot = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Wrapper for selection logic to intercept special apps
    fun onAppSelectRequest(pkg: String, isTop: Boolean) {
        if (pkg in SPECIAL_HOME_APPS && !isSystemDefaultHome(pkg)) {
            // Trigger the warning dialog
            pendingPackage = pkg
            pendingSlot = isTop
            // Do NOT apply the change yet
        } else {
            // Normal path
            handleAppSelection(
                selectedAppPackage = pkg,
                isForTopSlot = isTop,
                currentTopApp = topApp,
                currentBottomApp = bottomApp,
                onTopAppChange = onTopAppChange,
                onBottomAppChange = onBottomAppChange,
                context = context
            )
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Home Launcher") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onShowAllAppsChange(!showAllApps) }) {
                            Text(
                                text = if (showAllApps) "Filter Apps" else "Remove App Filter",
                                color = if (showAllApps)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                    }
                )
            }
        }
    ) {
        innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {

                    // TITLE
                    Text(
                        text = "Select Home Apps",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(
                            start = 0.dp,
                            top = 4.dp,
                            end = 0.dp,
                            bottom = 12.dp
                        )
                    )

                    val cardHeight = 140.dp
                    val topCardWidth = cardHeight * (16f / 9f)
                    val bottomCardWidth = cardHeight * (4f / 3f)

                    val mainTopLabel = if (mainScreen == MainScreen.TOP) "Main Screen" else ""
                    val mainBottomLabel = if (mainScreen == MainScreen.BOTTOM) "Main Screen" else ""

                    ConstraintLayout(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        // GUIDELINES FOR 3 COLUMNS
                        val c1 = createGuidelineFromStart(0.0f)
                        val c2 = createGuidelineFromStart(0.33f)
                        val c3 = createGuidelineFromEnd(0.33f)
                        val c4 = createGuidelineFromEnd(0.0f)

                        val (
                            radioTop, cardTop, labelTop,
                            radioBottom, cardBottom, labelBottom
                        ) = createRefs()

                        // -------------------------
                        // TOP ROW
                        // -------------------------

                        RadioButton(
                            selected = mainScreen == MainScreen.TOP,
                            onClick = { onMainScreenChange(MainScreen.TOP) },
                            modifier = Modifier.constrainAs(radioTop) {
                                linkTo(start = c1, end = c2)
                                centerVerticallyTo(cardTop)
                            }
                        )

                        // CARD + DROPDOWN (TOP)
                        Box(
                            modifier = Modifier
                                .width(topCardWidth)
                                .height(cardHeight)
                                .constrainAs(cardTop) {
                                    start.linkTo(c2)
                                    end.linkTo(c3)
                                    top.linkTo(parent.top)
                                }
                        ) {

                            AppSlotCard(
                                app = selectedTopApp,
                                label = "Select Top Screen App",
                                onClick = { topExpanded = true }
                            )

                            ExposedDropdownMenuBox(
                                expanded = topExpanded,
                                onExpandedChange = { topExpanded = it }
                            ) {
                                TextField(
                                    value = selectedTopApp?.label ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .menuAnchor()
                                        .alpha(0f),
                                        //.size(1.dp),
                                    enabled = false,
                                    colors = ExposedDropdownMenuDefaults.textFieldColors(
                                        disabledIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent
                                    )
                                )

                                ExposedDropdownMenu(
                                    expanded = topExpanded,
                                    onDismissRequest = { topExpanded = false }
                                ) {
                                    launcherApps.forEach { app ->
                                        DropdownMenuItem(
                                            text = { Text(app.label) },
                                            leadingIcon = {
                                                Image(
                                                    painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            },
                                            onClick = {
                                                onAppSelectRequest(
                                                    pkg = app.packageName,
                                                    isTop = true
                                                )
                                                topExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = mainTopLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.constrainAs(labelTop) {
                                linkTo(start = c3, end = c4)
                                centerVerticallyTo(cardTop)
                            }
                        )

                        // -------------------------
                        // BOTTOM ROW
                        // -------------------------

                        RadioButton(
                            selected = mainScreen == MainScreen.BOTTOM,
                            onClick = { onMainScreenChange(MainScreen.BOTTOM) },
                            modifier = Modifier.constrainAs(radioBottom) {
                                linkTo(start = c1, end = c2)
                                centerVerticallyTo(cardBottom)
                            }
                        )

                        Box(
                            modifier = Modifier
                                .width(bottomCardWidth)
                                .height(cardHeight)
                                .constrainAs(cardBottom) {
                                    start.linkTo(c2)
                                    end.linkTo(c3)
                                    top.linkTo(cardTop.bottom, margin = 24.dp)
                                }
                        ) {

                            AppSlotCard(
                                app = selectedBottomApp,
                                label = "Select Bottom Screen App",
                                onClick = { bottomExpanded = true }
                            )

                            ExposedDropdownMenuBox(
                                expanded = bottomExpanded,
                                onExpandedChange = { bottomExpanded = it }
                            ) {
                                TextField(
                                    value = selectedBottomApp?.label ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .menuAnchor()
                                        .alpha(0f),
                                        //.size(1.dp),
                                    enabled = false,
                                    colors = ExposedDropdownMenuDefaults.textFieldColors(
                                        disabledIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent
                                    )
                                )

                                ExposedDropdownMenu(
                                    expanded = bottomExpanded,
                                    onDismissRequest = { bottomExpanded = false }
                                ) {
                                    launcherApps.forEach { app ->
                                        DropdownMenuItem(
                                            text = { Text(app.label) },
                                            leadingIcon = {
                                                Image(
                                                    painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            },
                                            onClick = {
                                                onAppSelectRequest(
                                                    pkg = app.packageName,
                                                    isTop = false
                                                )
                                                bottomExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = mainBottomLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.constrainAs(labelBottom) {
                                linkTo(start = c3, end = c4)
                                centerVerticallyTo(cardBottom)
                            }
                        )
                    }
                }
            }

            // In MainSettingsScreen, after the "Mjolnir Home Settings" SettingsItem
            item {
                SettingsItem(
                    icon = Icons.Default.Block, // You may need to import `import androidx.compose.material.icons.filled.Block`
                    title = "App Blacklist",
                    subtitle = "Hide apps from the launcher selection"
                ) { navController.navigate("app_blacklist") }
            }


            item {
                Text(
                    text = "Home Button Behavior",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        top = 24.dp,
                        end = 16.dp,
                        bottom = 8.dp
                    )
                )
            }

            item {
                Divider(
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                // --- Single Press Home Row ---
                val context = LocalContext.current

                // Read current value (replace this with your prefs system if different)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var singleHomeAction by remember {
                    mutableStateOf(
                        Action.valueOf(
                            prefs.getString(KEY_SINGLE_HOME_ACTION, Action.BOTH_HOME.name)!!
                        )
                    )
                }

                // Update preference helper
                fun updateSingleHomeAction(newAction: Action) {
                    singleHomeAction = newAction
                    prefs.edit().putString(KEY_SINGLE_HOME_ACTION, newAction.name).apply()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* no-op, dropdown handles interaction */ }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Single press Home",
                        modifier = Modifier.weight(1f)
                    )

                    var expanded by remember { mutableStateOf(false) }

                    Box {
                        Text(
                            text = actionLabel(singleHomeAction),
                            modifier = Modifier
                                .clickable { expanded = true }
                                .padding(8.dp)
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            Action.values().forEach { action ->
                                DropdownMenuItem(
                                    onClick = {
                                        updateSingleHomeAction(action)
                                        expanded = false
                                    },
                                    text = { Text(actionLabel(action)) }
                                )
                            }
                        }
                    }
                }
            }

            item {

                // --- Double-Tap Home Row ---
                val context = LocalContext.current

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var doubleHomeAction by remember {
                    mutableStateOf(
                        Action.valueOf(
                            prefs.getString(KEY_DOUBLE_HOME_ACTION, Action.NONE.name)!!
                        )
                    )
                }

                fun updateDoubleHomeAction(newAction: Action) {
                    doubleHomeAction = newAction
                    prefs.edit().putString(KEY_DOUBLE_HOME_ACTION, newAction.name).apply()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Double-tap Home",
                        modifier = Modifier.weight(1f)
                    )

                    var expanded by remember { mutableStateOf(false) }

                    Box {
                        Text(
                            text = actionLabel(doubleHomeAction),
                            modifier = Modifier
                                .clickable { expanded = true }
                                .padding(8.dp)
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            Action.values().forEach { action ->
                                DropdownMenuItem(
                                    onClick = {
                                        updateDoubleHomeAction(action)
                                        expanded = false
                                    },
                                    text = { Text(actionLabel(action)) }
                                )
                            }
                        }
                    }
                }
            }

            item {

                // --- Triple-Tap Home Row ---
                val context = LocalContext.current

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var tripleHomeAction by remember {
                    mutableStateOf(
                        Action.valueOf(
                            prefs.getString(KEY_TRIPLE_HOME_ACTION, Action.NONE.name)!!
                        )
                    )
                }

                fun updateTripleHomeAction(newAction: Action) {
                    tripleHomeAction = newAction
                    prefs.edit().putString(KEY_TRIPLE_HOME_ACTION, newAction.name).apply()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Triple-tap Home",
                        modifier = Modifier.weight(1f)
                    )

                    var expanded by remember { mutableStateOf(false) }

                    Box {
                        Text(
                            text = actionLabel(tripleHomeAction),
                            modifier = Modifier
                                .clickable { expanded = true }
                                .padding(8.dp)
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            Action.values().forEach { action ->
                                DropdownMenuItem(
                                    onClick = {
                                        updateTripleHomeAction(action)
                                        expanded = false
                                    },
                                    text = { Text(actionLabel(action)) }
                                )
                            }
                        }
                    }
                }
            }

            item {

                // --- Long-Press Home Row ---
                val context = LocalContext.current

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var longHomeAction by remember {
                    mutableStateOf(
                        Action.valueOf(
                            prefs.getString(KEY_LONG_HOME_ACTION, Action.NONE.name)!!
                        )
                    )
                }

                fun updateLongHomeAction(newAction: Action) {
                    longHomeAction = newAction
                    prefs.edit().putString(KEY_LONG_HOME_ACTION, newAction.name).apply()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Long-press Home",
                        modifier = Modifier.weight(1f)
                    )

                    var expanded by remember { mutableStateOf(false) }

                    Box {
                        Text(
                            text = actionLabel(longHomeAction),
                            modifier = Modifier
                                .clickable { expanded = true }
                                .padding(8.dp)
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            Action.values().forEach { action ->
                                DropdownMenuItem(
                                    onClick = {
                                        updateLongHomeAction(action)
                                        expanded = false
                                    },
                                    text = { Text(actionLabel(action)) }
                                )
                            }
                        }
                    }
                }
            }

            // --- DOUBLE-TAP DELAY SETTINGS (Toggle + Slider share state) ---
            item {

                val context = LocalContext.current
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                val systemDoubleTap = ViewConfiguration.getDoubleTapTimeout()

                // Shared state used by BOTH rows
                var useSystemDoubleTapDelay by remember {
                    mutableStateOf(
                        prefs.getBoolean(KEY_USE_SYSTEM_DOUBLE_TAP_DELAY, true)
                    )
                }

                var customDoubleTapDelayMs by remember {
                    mutableStateOf(
                        prefs.getInt(KEY_CUSTOM_DOUBLE_TAP_DELAY, systemDoubleTap)
                    )
                }

                fun updateUseSystemDoubleTapDelay(newValue: Boolean) {
                    useSystemDoubleTapDelay = newValue
                    prefs.edit().putBoolean(KEY_USE_SYSTEM_DOUBLE_TAP_DELAY, newValue).apply()
                }

                fun updateCustomDoubleTapDelay(newValue: Int) {
                    customDoubleTapDelayMs = newValue
                    prefs.edit().putInt(KEY_CUSTOM_DOUBLE_TAP_DELAY, newValue).apply()
                }

                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp))
                {

                    // --- TOGGLE ROW ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Use system double-tap delay (${systemDoubleTap} ms)",
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = useSystemDoubleTapDelay,
                            onCheckedChange = { updateUseSystemDoubleTapDelay(it) }
                        )
                    }

                    // --- SLIDER (only visible if toggle = false) ---
                    if (!useSystemDoubleTapDelay) {

                        val minDelay = 100
                        val maxDelay = 500
                        val stepSize = 25
                        val steps = (maxDelay - minDelay) / stepSize - 1

                        Text(
                            text = "Custom double-tap delay (${customDoubleTapDelayMs} ms)",
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )

                        Slider(
                            value = customDoubleTapDelayMs.toFloat(),
                            onValueChange = { newValue ->
                                val stepped = ((newValue - minDelay) / stepSize)
                                    .roundToInt() * stepSize + minDelay
                                updateCustomDoubleTapDelay(stepped)
                            },
                            valueRange = minDelay.toFloat()..maxDelay.toFloat(),
                            steps = steps,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // In HomeLauncherSettingsMenu's LazyColumn, after the onSetDefaultHome button's item block

            item {
                Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "For troubleshooting only. Logs do not contain personal data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add this item right after the "Diagnostics" header item

            item {
                Row(        verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Capture diagnostics", style = MaterialTheme.typography.bodyLarge)
                        Text("Enable or disable logging", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = diagnosticsEnabled.value,
                        onCheckedChange = { isChecked ->
                            diagnosticsEnabled.value = isChecked
                            DiagnosticsConfig.setEnabled(context, isChecked)
                            if (isChecked) {
                                // This is the first action after enabling diagnostics
                                DiagnosticsLogger.logHeader(context)
                            }
                        }
                    )
                }
            }

            // Add this item after the "Capture diagnostics" item

            item {
                val logSizeOptions = listOf("0.5 MB", "1 MB", "2 MB", "5 MB")
                val logSizeMap = mapOf(
                    "0.5 MB" to 512 * 1024L,
                    "1 MB" to 1 * 1024 * 1024L,
                    "2 MB" to 2 * 1024 * 1024L,
                    "5 MB" to 5 * 1024 * 1024L
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text("Max log size", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "The log will trim itself when it exceeds this size",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                        space = 8.dp // Visually separates the buttons a bit
                    ) {
                        logSizeOptions.forEachIndexed { index, label ->
                            val sizeInBytes = logSizeMap[label]!!
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = logSizeOptions.size),
                                onClick = {
                                    maxLogSize.value = sizeInBytes
                                    DiagnosticsConfig.setMaxBytes(context, sizeInBytes)
                                },
                                selected = maxLogSize.value == sizeInBytes,
                                // Disable the buttons if logging is off
                                enabled = diagnosticsEnabled.value
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            // Add this as the final item in the "Diagnostics" section
/*
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // EXPORT BUTTON
                    Button(
                        onClick = {
                            try {
                                val exportedFile = DiagnosticsLogger.userExport(context)
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", exportedFile)

                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    type = "text/plain"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export Log"))
                            } catch (e: Exception) {
                                // This can happen if the file provider isn't set up.
                                // A toast is a simple way to inform the user.
                                Toast.makeText(context, "Error exporting file: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = diagnosticsEnabled.value
                    ) {
                        Text("Export Log")
                    }

                    // CLEAR BUTTON
                    Button(
                        onClick = {
                            DiagnosticsLogger.userClear(context)
                            Toast.makeText(context, "Diagnostics log cleared", Toast.LENGTH_SHORT).show()
                        },
                        enabled = diagnosticsEnabled.value
                    ) {
                        Text("Clear Log")
                    }
                }
            }*/

            // REPLACED: Consolidated Export/Clear into a single Summary View button
            item {
                Button(
                    onClick = { navController.navigate("diagnostics_summary") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    enabled = diagnosticsEnabled.value
                ) {
                    Text("View / Export Diagnostics")
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fix Focus-Lock Top input bug (experimental)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Workaround for controller input failure on Top screen. Briefly shows an invisible overlay to force focus reset.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = focusLockWorkaroundState.value,
                        onCheckedChange = { isChecked ->
                            if (isChecked && !Settings.canDrawOverlays(context)) {
                                // Request permission if enabling
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                overlayPermissionLauncher.launch(intent)
                                // You might want to wait to set true until verified,
                                // but for a simple toggle, setting it here + sharedPrefs is okay
                                // provided the user actually grants it.
                            }

                            focusLockWorkaroundState.value = isChecked
                            prefs.edit().putBoolean(KEY_ENABLE_FOCUS_LOCK_WORKAROUND, isChecked).apply()
                        }
                    )
                }
            }


            item { HorizontalDivider() }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Button(
                        onClick = onSetDefaultHome,
                        //modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set default home")
                    }
                }
            }

            /*item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Button(
                        onClick = onLaunchDualScreen,
                        enabled = topApp != null && bottomApp != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Dual-Screen Launch")
                    }
                }
            }*/
        }
    }
}

/**
 * Developer-mode settings screen containing advanced toggles intended only
 * for debugging or internal diagnostics.
 *
 * @param navController For back navigation.
 * @param devMode Whether developer mode is active.
 * @param onDevModeChange Callback to update developer mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperSettingsScreen(
    navController: NavController,
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Developer Mode") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) {
        innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Developer Mode", style = MaterialTheme.typography.bodyLarge)
                        Text("Access advanced developer options", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = devMode, onCheckedChange = onDevModeChange)
                }
            }
        }
    }
}

/**
 * Modal dialog presenting basic information about the Mjolnir application,
 * including its version number and a link to the project's GitHub page.
 *
 * Behavior:
 * - Appears as an AlertDialog with title, body text, and confirm button.
 * - GitHub URL is tappable and opens in a browser using ACTION_VIEW.
 * - Dialog closes when the user presses the confirm button.
 *
 * Use Cases:
 * - Accessed from the MainSettingsScreen via navigation.
 * - Provides transparency about the app version and open-source availability.
 *
 * @param onDismissRequest Callback invoked when the dialog is dismissed,
 *                         either via outside tap or confirm button.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "N/A"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    val uriHandler = LocalUriHandler.current
    val githubUrl = "https://github.com/blacksheepmvp/mjolnir"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Mjolnir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version $versionName")
                Text(
                    text = githubUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    ),
                    modifier = Modifier.clickable {
                        uriHandler.openUri(githubUrl)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Handles logic for assigning an app to either the Top or Bottom launcher slot.
 *
 * ## Behavior
 * - When selecting for TOP:
 *   - If selected app matches current bottom app, the two apps are swapped.
 *   - Otherwise, the selected app becomes the new topApp.
 *
 * - When selecting for BOTTOM:
 *   - If selected app matches current top app, the two apps are swapped.
 *   - Otherwise, the selected app becomes bottomApp.
 *
 * ## Why Swapping?
 * Prevents selecting the same package for both slots while preserving the user’s
 * previous assignment. This avoids silent overwrites and maintains consistency.
 *
 * @param selectedAppPackage The package the user selected.
 * @param isForTopSlot Whether this selection targets the top slot.
 * @param currentTopApp Existing top slot package.
 * @param currentBottomApp Existing bottom slot package.
 * @param onTopAppChange Callback invoked if top slot is changed.
 * @param onBottomAppChange Callback invoked if bottom slot is changed.
 */
private fun handleAppSelection(
    selectedAppPackage: String,
    isForTopSlot: Boolean,
    currentTopApp: String?,
    currentBottomApp: String?,
    onTopAppChange: (String?) -> Unit,
    onBottomAppChange: (String?) -> Unit,
    context: Context
) {
    val newTopApp: String?
    val newBottomApp: String?

    if (isForTopSlot) {
        // User is selecting an app for the TOP slot
        newTopApp = if (selectedAppPackage == "NOTHING") null else selectedAppPackage
        if (selectedAppPackage == currentBottomApp) {
            // The selected app is already in the bottom slot, so swap
            newBottomApp = currentTopApp // Old top app moves to the bottom
        } else {
            newBottomApp = currentBottomApp
        }
    } else {
        // User is selecting an app for the BOTTOM slot
        newBottomApp = if (selectedAppPackage == "NOTHING") null else selectedAppPackage
        if (selectedAppPackage == currentTopApp) {
            // The selected app is already in the top slot, so swap
            newTopApp = currentBottomApp // Old bottom app moves to the top
        } else {
            newTopApp = currentTopApp
        }
    }

    onTopAppChange(newTopApp)
    onBottomAppChange(newBottomApp)

    // --- "NO-HOME" INVARIANT CHECK ---
    if (newTopApp == null && newBottomApp == null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)) {
            prefs.edit().putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false).apply()
            // The tile will update automatically via its listener.
            // We can show a toast for immediate feedback.
            Toast.makeText(
                context,
                "Home capture disabled: No apps selected.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

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
        packageName = "NOTHING", // This matches the backend check I added
        launchIntent = Intent()  // Empty intent
    )

    // Return the list with <Nothing> at the top
    return listOf(nothingOption) + apps
    // --- MANUAL CHANGE END ---
}

fun launchOnDualScreens(context: Context, topIntent: Intent, bottomIntent: Intent) {
    DualScreenLauncher.launchOnDualScreens(context, topIntent, bottomIntent, MainScreen.TOP)
}

/**
 * Guided onboarding screen shown when Mjolnir detects that required permissions
 * or setup steps are incomplete.
 *
 * ## Steps Included
 * 1. **Notification Permission** — required for the persistent service.
 * 2. **Accessibility Service** — required for intercepting Home button events.
 * 3. **Home Interception Toggle** — enables special Home-launch behaviors.
 *
 * ## Additional Tools
 * - “Test Notification” button to verify persistent-notification behavior.
 * - “Close” button for aborting setup flow.
 *
 * ## Behavior
 * - Stateless; all actions are emitted upward through provided callbacks.
 * - Scrollable to accommodate all steps on small screens.
 *
 * @param onGrantPermissionClick Opens the Notification Permission screen.
 * @param onEnableAccessibilityClick Opens Accessibility Service settings.
 * @param onEnableHomeInterceptionClick Opens app-specific interception settings.
 * @param onTestNotificationClick Fires a test notification for verification.
 * @param onClose Closes the setup flow.
 */

@Composable
fun HomeSetup(
    onGrantPermissionClick: () -> Unit,
    onEnableAccessibilityClick: () -> Unit,
    onEnableHomeInterceptionClick: () -> Unit,
    onTestNotificationClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Home Launcher Setup",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Step 1: Grant Notification Permission",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Mjolnir requires notification permission for its background service to function correctly.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGrantPermissionClick) {
                Text("Grant Notification Permission")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Step 2: Enable Accessibility Service",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Mjolnir uses an accessibility service to intercept the Home button press.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onEnableAccessibilityClick) {
                Text("Enable Accessibility Service")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Step 3: Enable Home Interception",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "This allows Mjolnir to perform its special action when you press the home button.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onEnableHomeInterceptionClick) {
                Text("Enable Home Interception")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onTestNotificationClick) {
                Text("Test Notification")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    }
}

/**
 * Initial setup UI for the Steam File Generator workflow.
 *
 * ## Responsibilities
 * - Requests the user’s Steam ROMs directory.
 * - Provides a “Skip” option to bypass setup temporarily.
 *
 * ## Behavior
 * - Displays simple explanatory text to inform the user about why the directory
 *   is needed (Steam file generation output path).
 *
 * @param onPickDirectory Triggers directory-picker intent.
 * @param onClose Closes setup flow without selecting a directory.
 * @param modifier Optional modifier for layout customization.
 */
@Composable
fun SetupScreen(onPickDirectory: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Steam File Generator Setup", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 16.dp))
            Text("To begin, please select the directory where you store your Steam ROM files.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPickDirectory) { Text("Choose Directory") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose) { Text("Skip") }
        }
    }
}

/**
 * Modal dialog shown when the user attempts to generate a `.steam` file but an
 * existing file with the same name already exists and contains a **different**
 * AppID.
 *
 * ## Responsibilities
 * - Displays both old and new AppID values.
 * - Ensures the user explicitly acknowledges overwriting mismatched game data.
 * - Allows user to cancel or confirm the overwrite operation.
 *
 * ## Behavior
 * - Uses provided [OverwriteInfo] to populate dialog fields.
 * - Confirm flows upward via onConfirm().
 * - Cancel/dismiss flows via onDismiss().
 *
 * @param overwriteInfo Metadata describing old vs new AppID state.
 * @param onDismiss Dismiss callback.
 * @param onConfirm Confirms overwriting the existing mismatched file.
 */
@Composable
fun OverwriteConfirmationDialog(
    overwriteInfo: OverwriteInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Overwrite File?") },
        text = {
            Column {
                Text("A file named '''${overwriteInfo.gameInfo.name}.steam''' already exists with a different AppID.")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Old AppID: ${overwriteInfo.oldAppId}")
                Text("New AppID: ${overwriteInfo.gameInfo.appId}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(onClick = onConfirm) { Text("Overwrite") } }
    )
}

/**
 * Simplified UI for manually typing a Steam AppID when automatic search is not
 * desired or the user already knows the AppID.
 *
 * ## Responsibilities
 * - Collects numeric AppID input.
 * - Emits onSearch(appIdString) when the search icon or IME search action is
 *   pressed.
 *
 * ## Behavior
 * - Filters input to digits only.
 * - Stores state through rememberSaveable for rotation/process safety.
 *
 * @param onSearch Invoked with the raw numeric string when the user submits.
 * @param modifier External layout modifier.
 */

@Composable
fun ManualInputUi(onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var manualAppId by rememberSaveable { mutableStateOf("") }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = manualAppId,
            onValueChange = { manualAppId = it.filter(Char::isDigit) },
            label = { Text("Enter AppID") },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(manualAppId) })
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { onSearch(manualAppId) }) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }
    }
}

/**
 * Helper function to retrieve the application icon for the given package name.
 *
 * ## Behavior
 * - Uses PackageManager.getApplicationIcon().
 * - Returns null if the package does not exist or icon loading fails.
 *
 * Used in:
 * - Blacklist UI
 * - Dropdown selectors
 *
 * @param context Context for package lookup.
 * @param packageName Target package.
 * @return Drawable icon or null on failure.
 */
fun getAppIcon(context: Context, packageName: String): Drawable? {
    return try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) {
        null
    }
}

/**
 * Search interface for Steam metadata lookups with support for generating
 * `.steam` shortcut files after a successful query.
 *
 * ## Visual Behavior
 * - When UiState.Success: displays blurred header image background from metadata.
 * - Otherwise: shows instructional text, a loading indicator, or error message.
 *
 * ## Responsibilities
 * - Displays AppID resolution results via UiState.
 * - Calls onCreateFile() when the user chooses to create the shortcut file.
 * - Shows success / error messages returned by the file creation stage.
 *
 * @param uiState The current Steam metadata query state.
 * @param fileCreationResult Optional result string from file creation step.
 * @param onCreateFile Called when user confirms saving the .steam file.
 * @param modifier Optional layout modifier.
 */

@Composable
fun SearchUi(
    uiState: UiState,
    fileCreationResult: String?,
    onCreateFile: (GameInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (uiState is UiState.Success) {
            AsyncImage(
                model = uiState.gameInfo.headerImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(16.dp),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            when (uiState) {
                is UiState.Idle -> Text("Enter a Steam AppID to begin.")
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Success -> {
                    Text(
                        text = uiState.gameInfo.name,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onCreateFile(uiState.gameInfo) }) { Text("Save .steam File") }
                    fileCreationResult?.let {
                        val color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error else LocalContentColor.current
                        Text(it, modifier = Modifier.padding(top = 8.dp), color = color, textAlign = TextAlign.Center)
                    }
                }
                is UiState.Failure -> Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Full diagnostics dashboard providing inspection and export controls for
 * Mjolnir’s internal logging system.
 *
 * ## Data Shown
 * - Diagnostics Enabled (Boolean)
 * - Log File Exists
 * - Current log size (bytes)
 * - Configured max size (bytes)
 * - Approximate entry count
 * - Last modified timestamp
 * - Absolute log file path
 *
 * All values come from [DiagnosticsSummary] and are refreshed whenever
 * refreshTrigger changes.
 *
 * ## Actions Provided
 * ### 1. **View Raw Log**
 * Opens the log file via an ACTION_VIEW intent.
 *
 * ### 2. **Export with Summary**
 * Creates an export bundle including metadata and log content.
 *
 * ### 3. **Delete Active Log**
 * Clears the file completely.
 *
 * Each action gracefully handles FileProvider mappings and error states.
 *
 * @param navController For back navigation.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsSummaryScreen(navController: NavController) {
    val context = LocalContext.current
    // Key to trigger UI refreshes (e.g. after deletion)
    var refreshTrigger by remember { mutableStateOf(0L) }

    // Fetch all data from backend
    // We re-fetch whenever refreshTrigger changes
    val isEnabled = remember(refreshTrigger) { DiagnosticsSummary.isEnabled(context) }
    val fileExists = remember(refreshTrigger) { DiagnosticsSummary.getLogFileExists(context) }
    val fileSize = remember(refreshTrigger) { DiagnosticsSummary.getLogFileSize(context) }
    val maxBytes = remember(refreshTrigger) { DiagnosticsSummary.getMaxBytes(context) }
    val lastMod = remember(refreshTrigger) { DiagnosticsSummary.getLastModified(context) }
    val entryCount = remember(refreshTrigger) { DiagnosticsSummary.getApproxEntryCount(context) }
    val filePath = remember(refreshTrigger) { DiagnosticsSummary.getLogFile(context).absolutePath }

    // Helper to format bytes
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.2f MB".format(mb)
    }

    // Helper to format date
    fun formatDate(millis: Long): String {
        if (millis == 0L) return "N/A"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(Date(millis))
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Diagnostics Summary") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- INFO CARD ---
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))

                    SummaryRow("Diagnostics Enabled", isEnabled.toString())
                    SummaryRow("Log File Exists", fileExists.toString())
                    SummaryRow("File Size", "${formatSize(fileSize)} / ${formatSize(maxBytes)}")
                    SummaryRow("Approx. Entries", if (entryCount >= 0) entryCount.toString() else "N/A")
                    SummaryRow("Last Modified", formatDate(lastMod))

                    Spacer(Modifier.height(8.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))

                    Text("Path:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(filePath, style = MaterialTheme.typography.bodySmall)
                }
            }

            Divider()

            Text("Actions", style = MaterialTheme.typography.titleMedium)

            // ACTION A: OPEN (View Raw)
            Button(
                onClick = {
                    try {
                        val file = DiagnosticsActions.getLogFileForViewing(context)
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "text/plain")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "View Log"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = fileExists,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Raw Log File")
            }

            // ACTION B: SHARE (Export with Summary)
            Button(
                onClick = {
                    try {
                        val exportFile = DiagnosticsActions.createExportWithSummary(context)
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", exportFile)

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export with Summary"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = fileExists,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export with Summary")
            }

            // ACTION C: DELETE
            OutlinedButton(
                onClick = {
                    DiagnosticsActions.deleteLog(context)
                    refreshTrigger = System.currentTimeMillis() // Force UI update
                    Toast.makeText(context, "Log deleted", Toast.LENGTH_SHORT).show()
                },
                enabled = fileExists,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Active Log")
            }
        }
    }
}

/**
 * Utility UI row for presenting a labeled value pair in the diagnostics
 * summary. Used for cleanly displaying key/value system information.
 *
 * ## Layout
 * - `label` displayed left-aligned
 * - `value` displayed right-aligned in bold
 *
 * @param label Text describing the metric.
 * @param value Metric value formatted as string.
 */

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}