package xyz.blacksheep.mjolnir

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.home.HomeActionLauncher
import xyz.blacksheep.mjolnir.ui.theme.MjolnirTheme
import xyz.blacksheep.mjolnir.settings.*
import xyz.blacksheep.mjolnir.utils.*

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle being launched as a Home app
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            val launcher = HomeActionLauncher(this)
            launcher.launchBoth()
            finish()
            return // Do not proceed to show UI
        }

        installSplashScreen()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val directoryPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit { putString(KEY_ROM_DIR_URI, it.toString()) }
            }
        }

        setContent {
            val initialThemeName = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name)
            var theme by rememberSaveable { mutableStateOf(AppTheme.valueOf(initialThemeName ?: AppTheme.SYSTEM.name)) }
            val useDarkTheme = when (theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
                }
            }

            val requestPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                }
            }

            var showHomeSetup by rememberSaveable { mutableStateOf(false) }

            val accessibilitySettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (isAccessibilityServiceEnabled(this, HomeKeyInterceptorService::class.java)) {
                    showHomeSetup = false
                }
            }

            MjolnirTheme(darkTheme = useDarkTheme) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var settingsStartDestination by rememberSaveable { mutableStateOf ("main") }
                var showAboutDialog by rememberSaveable { mutableStateOf(false) }
                var menuExpanded by remember { mutableStateOf(false) }

                var confirmDelete by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_CONFIRM_DELETE, true)) }
                var autoCreateFile by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_AUTO_CREATE_FILE, true)) }
                var devMode by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_DEV_MODE, false)) }

                var topApp by rememberSaveable { mutableStateOf(prefs.getString(KEY_TOP_APP, null)) }
                var bottomApp by rememberSaveable { mutableStateOf(prefs.getString(KEY_BOTTOM_APP, null)) }
                var showAllApps by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_SHOW_ALL_APPS, false)) }
                val initialMainScreenName = prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name)
                var mainScreen by rememberSaveable { mutableStateOf(MainScreen.valueOf(initialMainScreenName ?: MainScreen.TOP.name)) }

                val scope = rememberCoroutineScope()

                LaunchedEffect(intent) {
                    if (intent.getBooleanExtra("open_settings", false)) {
                        showSettings = true
                    }
                }

                Scaffold(
                    topBar = {
                        Surface(tonalElevation = 2.dp) {
                            TopAppBar(
                                title = { Text("Mjolnir") },
                                navigationIcon = {
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
                                                showSettings = true
                                                menuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("About") },
                                            onClick = {
                                                showAboutDialog = true
                                                menuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Quit") },
                                            onClick = { finish() }
                                        )
                                    }
                                }
                            )
                        }
                    }
                ) {
                    innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LazyColumn {
                            item {
                                Text("Setup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                            }
                            item {
                                SettingsItem(
                                    icon = Icons.Default.Home,
                                    title = "Setup Home Launcher",
                                    subtitle = "Configure the home launcher for dual-screen mode",
                                    onClick = { showHomeSetup = true }
                                )
                            }
                            item {
                                SettingsItem(
                                    icon = Icons.Default.Build,
                                    title = "Setup Steam File Generator",
                                    subtitle = "Initial setup for the Steam file generator",
                                    onClick = {
                                        startActivity(SteamFileGenActivity.createSetupIntent(this@MainActivity))
                                    }
                                )
                            }
                            item {
                                HorizontalDivider()
                            }
                            item {
                                Text("Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                            }
                            item {
                                SettingsItem(
                                    icon = Icons.Default.Build,
                                    title = "Steam File Generator",
                                    subtitle = "Generate Steam files for your ROMs",
                                    onClick = {
                                        val intent = Intent(this@MainActivity, SteamFileGenActivity::class.java)
                                        startActivity(intent)
                                    }
                                )
                            }
                            item {
                                SettingsItem(
                                    icon = Icons.Default.Build,
                                    title = "Manual File Generator",
                                    subtitle = "Manually generate a Steam file for a single ROM",
                                    onClick = {
                                        val intent = Intent(this@MainActivity, ManualFileGenActivity::class.java)
                                        startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }

                if (showHomeSetup) {
                    BackHandler { showHomeSetup = false }
                    HomeSetup(
                        onGrantPermissionClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onEnableAccessibilityClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            accessibilitySettingsLauncher.launch(intent)
                        },
                        onEnableHomeInterceptionClick = {
                            prefs.edit { putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, true) }
                        },
                        onTestNotificationClick = { showTestNotification(this) },
                        onClose = { showHomeSetup = false }
                    )
                }

                if (showSettings) {
                    SettingsScreen(
                        startDestination = settingsStartDestination,
                        currentPath = prefs.getString(KEY_ROM_DIR_URI, "") ?: "",
                        currentTheme = theme,
                        onThemeChange = { newTheme ->
                            prefs.edit { putString(KEY_THEME, newTheme.name) }
                            theme = newTheme
                        },
                        onChangeDirectory = { directoryPickerLauncher.launch(null) },
                        onClose = { showSettings = false },
                        confirmDelete = confirmDelete,
                        onConfirmDeleteChange = { newConfirm ->
                            prefs.edit { putBoolean(KEY_CONFIRM_DELETE, newConfirm) }
                            confirmDelete = newConfirm
                        },
                        autoCreateFile = autoCreateFile,
                        onAutoCreateFileChange = { newAutoCreate ->
                            prefs.edit { putBoolean(KEY_AUTO_CREATE_FILE, newAutoCreate) }
                            autoCreateFile = newAutoCreate
                        },
                        devMode = devMode,
                        onDevModeChange = { newDevMode ->
                            prefs.edit { putBoolean(KEY_DEV_MODE, newDevMode) }
                            devMode = newDevMode
                        },
                        topApp = topApp,
                        onTopAppChange = { newTopApp ->
                            prefs.edit { putString(KEY_TOP_APP, newTopApp) }
                            topApp = newTopApp
                        },
                        bottomApp = bottomApp,
                        onBottomAppChange = { newBottomApp ->
                            prefs.edit { putString(KEY_BOTTOM_APP, newBottomApp) }
                            bottomApp = newBottomApp
                        },
                        showAllApps = showAllApps,
                        onShowAllAppsChange = { newShowAllApps ->
                            prefs.edit { putBoolean(KEY_SHOW_ALL_APPS, newShowAllApps) }
                            showAllApps = newShowAllApps
                        },
                        onSetDefaultHome = {
                            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                            startActivity(intent)
                        },
                        onLaunchDualScreen = {
                            scope.launch {
                                val launcherApps = getLaunchableApps(this@MainActivity, showAllApps)
                                val top = launcherApps.find { it.packageName == topApp }
                                val bottom = launcherApps.find { it.packageName == bottomApp }
                                if (top != null && bottom != null) {
                                    val success = DualScreenLauncher.launchOnDualScreens(this@MainActivity, top.launchIntent, bottom.launchIntent, mainScreen)
                                    if (success) {
                                        prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, 0) }
                                    } else {
                                        Toast.makeText(this@MainActivity, "Launch failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        mainScreen = mainScreen,
                        onMainScreenChange = {
                                newMainScreen ->
                            prefs.edit { putString(KEY_MAIN_SCREEN, newMainScreen.name) }
                            mainScreen = newMainScreen
                        }
                    )
                }

                if (showAboutDialog) {
                    AboutDialog() { showAboutDialog = false }
                }
            }
        }
    }

    fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val serviceId = "${context.packageName}/${service.name}"
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(serviceId) == true
        } catch (e: Exception) {
            return false // Service is not enabled
        }
    }

}
