package xyz.blacksheep.mjolnir.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import xyz.blacksheep.mjolnir.model.AppTheme
import xyz.blacksheep.mjolnir.model.MainScreen
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger

/**
 * Public entry point for Settings. Uses the default start destination.
 */
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
 * Core Settings implementation backed by an internal NavController + NavHost.
 */
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
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        DiagnosticsLogger.logEvent("SettingsScreen", "LIFECYCLE", "Composition START", context)
    }

    val navController = rememberNavController()

    DisposableEffect(Unit) {
        onDispose {
            DiagnosticsLogger.logEvent("SettingsScreen", "LIFECYCLE", "Composition END", context)
        }
    }

    val handleBack: () -> Unit = {
        val hasBackStack = navController.previousBackStackEntry != null
        DiagnosticsLogger.logEvent("SettingsScreen", "BACK_PRESSED", "hasStack=$hasBackStack", context)

        if (hasBackStack) {
            navController.popBackStack()
        } else {
            onClose()
        }
    }

    BackHandler { handleBack() }

    SettingsNavHost(
        modifier = Modifier.fillMaxSize(),
        navController = navController,
        startDestination = startDestination,
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
