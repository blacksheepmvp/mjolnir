package xyz.blacksheep.mjolnir.settings

//import androidx.compose.material.icons.Icons
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.text.style.TextAlign
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher
import xyz.blacksheep.mjolnir.utils.GameInfo
import xyz.blacksheep.mjolnir.utils.GameInfoSaver

enum class AppTheme { LIGHT, DARK, SYSTEM }
enum class MainScreen { TOP, BOTTOM }

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
sealed interface UiState {
    object Idle : UiState
    data class Loading(val appId: String) : UiState
    data class Success(val gameInfo: GameInfo) : UiState
    data class Failure(val error: String) : UiState
}

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
    onTopAppChange: (String) -> Unit,
    bottomApp: String?,
    onBottomAppChange: (String) -> Unit,
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
    onTopAppChange: (String) -> Unit,
    bottomApp: String?,
    onBottomAppChange: (String) -> Unit,
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
        composable("developer_mode") {
            DeveloperSettingsScreen(navController = navController, devMode = devMode, onDevModeChange = onDevModeChange)
        }
        composable("about") {
            AboutDialog() { navController.popBackStack() }
        }
    }
}

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
                    icon = Icons.Default.Build,
                    title = "Steam File Generator",
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
            item {
                SettingsItem(
                    icon = Icons.Default.Home,
                    title = "Home Launcher",
                    subtitle = "Configure dual-screen app launching"
                ) { navController.navigate("home_launcher") }
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "Developer Mode",
                    subtitle = "Access advanced developer options"
                ) { navController.navigate("developer_mode") }
            }
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

private const val TAG = "MjolnirHomeLauncher"

data class LauncherApp(
    val label: String,
    val packageName: String,
    val launchIntent: Intent
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeLauncherSettingsMenu(
    navController: NavController,
    topApp: String?,
    onTopAppChange: (String) -> Unit,
    bottomApp: String?,
    onBottomAppChange: (String) -> Unit,
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

    val mainScreenOptions = MainScreen.entries.map { it.name }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Home Launcher") },
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
                    Text(
                        "Top Screen App",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        selectedTopApp?.label ?: "Select an app to launch on the top screen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = topExpanded,
                        onExpandedChange = { topExpanded = !topExpanded }
                    ) {
                        TextField(
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            readOnly = true,
                            value = selectedTopApp?.label ?: "Select App",
                            onValueChange = {},
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = topExpanded) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = topExpanded,
                            onDismissRequest = { topExpanded = false }
                        ) {
                            launcherApps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app.label) },
                                    onClick = {
                                        onTopAppChange(app.packageName)
                                        topExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text(
                        "Bottom Screen App",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        selectedBottomApp?.label ?: "Select an app to launch on the bottom screen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = bottomExpanded,
                        onExpandedChange = { bottomExpanded = !bottomExpanded }
                    ) {
                        TextField(
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            readOnly = true,
                            value = selectedBottomApp?.label ?: "Select App",
                            onValueChange = {},
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bottomExpanded) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = bottomExpanded,
                            onDismissRequest = { bottomExpanded = false }
                        ) {
                            launcherApps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app.label) },
                                    onClick = {
                                        onBottomAppChange(app.packageName)
                                        bottomExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text("Main Screen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Determines which app has focus after launching",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        mainScreenOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = mainScreenOptions.size),
                                onClick = { onMainScreenChange(MainScreen.valueOf(label)) },
                                selected = mainScreen.name == label
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .clickable { onShowAllAppsChange(!showAllApps) }
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show all installed apps", style = MaterialTheme.typography.bodyLarge)
                        Text("Disable to only show apps detected as launchers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = showAllApps, onCheckedChange = onShowAllAppsChange)
                }
            }

            item { HorizontalDivider() }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Button(
                        onClick = onSetDefaultHome,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set as Default Home Launcher")
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Button(
                        onClick = onLaunchDualScreen,
                        enabled = topApp != null && bottomApp != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Dual-Screen Launch")
                    }
                }
            }
        }
    }
}

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

fun getLaunchableApps(context: Context, showAll: Boolean): List<LauncherApp> {
    val pm = context.packageManager
    val apps = mutableListOf<LauncherApp>()

    // --- Query all launchable (CATEGORY_LAUNCHER) apps ---
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val launcherActivities = pm.queryIntentActivities(launcherIntent, 0)

    // --- Query home (CATEGORY_HOME) apps, e.g. Quickstep ---
    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    val homeActivities = pm.queryIntentActivities(homeIntent, 0)

    // Combine both
    val allActivities = (launcherActivities + homeActivities).distinctBy {
        it.activityInfo.packageName
    }

    for (ri in allActivities) {
        val label = ri.loadLabel(pm).toString()
        val pkg = ri.activityInfo.packageName
        val activityName = ri.activityInfo.name

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(pkg, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        apps.add(LauncherApp(label, pkg, intent))
    }

    // --- Apply optional filter ---
    val filtered = if (!showAll) {
        val homePkgs = homeActivities.map { it.activityInfo.packageName }
        apps.filter { app ->
            val name = app.packageName.lowercase()
            homePkgs.contains(app.packageName) ||
                    listOf("launcher", "home", "quickstep", "daijisho", "beacon", "es-de", "nova", "odin")
                        .any { name.contains(it) }
        }
    } else apps

    val distinct = filtered.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

    Log.d(TAG, "Found ${distinct.size} apps (showAll=$showAll)")
    return distinct
}

fun launchOnDualScreens(context: Context, topIntent: Intent, bottomIntent: Intent) {
    DualScreenLauncher.launchOnDualScreens(context, topIntent, bottomIntent, MainScreen.TOP)
}

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

@Composable
fun OverwriteConfirmationDialog(
    overwriteInfo: xyz.blacksheep.mjolnir.utils.OverwriteInfo,
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
                modifier = Modifier.fillMaxSize().blur(16.dp),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), MaterialTheme.colorScheme.surface))))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp).fillMaxSize()
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
