package xyz.blacksheep.mjolnir.settings

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import xyz.blacksheep.mjolnir.KEY_CUSTOM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.KEY_DOUBLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_LONG_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_SINGLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_TRIPLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_USE_SYSTEM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.home.actionLabel
import xyz.blacksheep.mjolnir.utils.AppQueryHelper
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher
import xyz.blacksheep.mjolnir.utils.GameInfo
import xyz.blacksheep.mjolnir.utils.GameInfoSaver
import xyz.blacksheep.mjolnir.utils.OverwriteInfo
import kotlin.math.roundToInt

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

@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter {
    return remember(drawable) { drawable?.toBitmap()?.let { BitmapPainter(it.asImageBitmap()) } ?: ColorPainter(Color.Transparent) }
}

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
                                                onTopAppChange(app.packageName)
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
                                                onBottomAppChange(app.packageName)
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
    val queryHelper = AppQueryHelper(context)
    val appInfoList = if (showAll) {
        queryHelper.queryAllApps()
    } else {
        queryHelper.queryLauncherApps()
    }

    val pm = context.packageManager

    return appInfoList.map { appInfo ->

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

fun getAppIcon(context: Context, packageName: String): Drawable? {
    return try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) {
        null
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
