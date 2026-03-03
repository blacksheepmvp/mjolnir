package xyz.blacksheep.mjolnir.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import xyz.blacksheep.mjolnir.model.AppTheme
import xyz.blacksheep.mjolnir.model.MainScreen

/**
 * Internal navigation graph defining all available settings routes.
 */
@Composable
fun SettingsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
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
    NavHost(modifier = modifier, navController = navController, startDestination = startDestination) {
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
        composable("gesture_preset_edit") {
            val context = LocalContext.current
            val configState = remember { mutableStateOf(GestureConfigStore.peekDraft() ?: GestureConfigStore.getActiveConfig(context)) }
            val nameState = remember { mutableStateOf(configState.value.name) }
            val isDraft = remember { GestureConfigStore.peekDraft() != null }

            GesturePresetEditorScreen(
                title = "Edit Gesture Preset",
                presetName = nameState.value,
                topAppPackage = topApp,
                bottomAppPackage = bottomApp,
                config = configState.value,
                onConfigChange = {
                    configState.value = it
                },
                onNameChange = { nameState.value = it },
                onSave = {
                    val saved = if (isDraft) {
                        GestureConfigStore.saveDraft(context, configState.value, nameState.value)
                    } else {
                        val renamed = GestureConfigStore.renamePreset(context, configState.value, nameState.value)
                        GestureConfigStore.saveConfig(context, renamed)
                        GestureConfigStore.setActiveConfig(context, renamed.fileName)
                        renamed
                    }
                    configState.value = saved
                    navController.popBackStack()
                },
                onCancel = {
                    if (isDraft) GestureConfigStore.clearDraft()
                    navController.popBackStack()
                }
            )
        }
        composable("app_blacklist") {
            BlacklistSettingsScreen(navController = navController)
        }
        composable("developer_mode") {
            DeveloperSettingsScreen(navController = navController, devMode = devMode, onDevModeChange = onDevModeChange)
        }
        composable("about") {
            AboutDialog { navController.popBackStack() }
        }
        composable("diagnostics_summary") {
            DiagnosticsSummaryScreen(navController = navController)
        }
    }
}

private fun getAppLabel(context: android.content.Context, packageName: String?): String? {
    if (packageName == null) return null
    return try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        packageName
    }
}
