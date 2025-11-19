package xyz.blacksheep.mjolnir

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.home.HomeActionLauncher
import xyz.blacksheep.mjolnir.settings.AboutDialog
import xyz.blacksheep.mjolnir.settings.AppTheme
import xyz.blacksheep.mjolnir.settings.HomeSetup
import xyz.blacksheep.mjolnir.settings.MainScreen
import xyz.blacksheep.mjolnir.settings.SettingsItem
import xyz.blacksheep.mjolnir.settings.SettingsScreen
import xyz.blacksheep.mjolnir.settings.getLaunchableApps
import xyz.blacksheep.mjolnir.ui.theme.MjolnirTheme
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher
import xyz.blacksheep.mjolnir.utils.showTestNotification

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

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

                val versionName = remember {
                    try {
                        val packageInfo = packageManager.getPackageInfo(packageName, 0)
                        packageInfo.versionName ?: "N/A"
                    } catch (e: Exception) {
                        "Unknown"
                    }
                }

                var confirmDelete by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_CONFIRM_DELETE, true)) }
                var autoCreateFile by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_AUTO_CREATE_FILE, true)) }
                var devMode by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_DEV_MODE, false)) }

                var topApp by rememberSaveable { mutableStateOf(prefs.getString(KEY_TOP_APP, null)) }
                var bottomApp by rememberSaveable { mutableStateOf(prefs.getString(KEY_BOTTOM_APP, null)) }
                var showAllApps by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_SHOW_ALL_APPS, false)) }
                val initialMainScreenName = prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name)
                var mainScreen by rememberSaveable { mutableStateOf(MainScreen.valueOf(initialMainScreenName ?: MainScreen.TOP.name)) }
                var isInterceptionActive by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)) }

                val context = this@MainActivity
                val grayAlpha = 0.6f
                val normalAlpha = 1.0f

                val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true // Not required for older versions
                }
                val isAccessibilityEnabled = isAccessibilityServiceEnabled(context, HomeKeyInterceptorService::class.java)
                val romDirectorySet = !prefs.getString(KEY_ROM_DIR_URI, "").isNullOrBlank()

                val setupHomeGray = hasNotificationPermission && isAccessibilityEnabled
                val setupSteamGray = romDirectorySet
                val steamToolGray = !romDirectorySet

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
                                title = { Text("Mjolnir v$versionName") },
                                navigationIcon = {
                                    IconButton(onClick = { showSettings = true }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                },
                                actions = {
                                    // Check if user configured top/bottom apps
                                    val topHome = prefs.getString(KEY_TOP_APP, null)
                                    val bottomHome = prefs.getString(KEY_BOTTOM_APP, null)
                                    val homeAppsConfigured = !topHome.isNullOrEmpty() && !bottomHome.isNullOrEmpty()

                                    // Determine visible status
                                    val tileActive = isInterceptionActive && isAccessibilityEnabled
                                    val tileLabel = if (tileActive) "Home Enabled" else "Home Disabled"

                                    // Dialog state
                                    var showConfigDialog by remember { mutableStateOf(false) }

                                    if (showConfigDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showConfigDialog = false },
                                            confirmButton = {
                                                TextButton(onClick = { showConfigDialog = false }) {
                                                    Text("OK")
                                                }
                                            },
                                            title = { Text("Setup Required") },
                                            text = {
                                                Text("Please configure Top and Bottom Home Apps in Mjolnir Home Settings.")
                                            }
                                        )
                                    }

                                    // Actual button
                                    TextButton(onClick = {
                                        when {
                                            // Case A: Accessibility not enabled → go to setup screen
                                            !isAccessibilityEnabled -> {
                                                showHomeSetup = true
                                            }

                                            // Case B: Accessibility ON but top/bottom apps not set
                                            !homeAppsConfigured -> {
                                                showConfigDialog = true
                                            }

                                            // Case C: All setup done, toggle the active state
                                            else -> {
                                                val newValue = !isInterceptionActive
                                                prefs.edit { putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, newValue) }
                                                isInterceptionActive = newValue
                                            }
                                        }
                                    }) {
                                        Text(tileLabel)
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
                                Box(modifier = Modifier.alpha(if (setupHomeGray) grayAlpha else normalAlpha)) {
                                    SettingsItem(
                                        icon = Icons.Default.Home,
                                        title = "Initialize Mjolnir Home",
                                        subtitle = "Set permissions and activate the accessibility service",
                                        onClick = { showHomeSetup = true }
                                    )
                                }
                            }
                            item {
                                Box(modifier = Modifier.alpha(if (setupSteamGray) grayAlpha else normalAlpha)) {
                                    SettingsItem(
                                        icon = Icons.Default.Build,
                                        title = "Initialize Steam File Generator",
                                        subtitle = "Provide read/write access to your ROM directory",
                                        onClick = {
                                            startActivity(SteamFileGenActivity.createSetupIntent(this@MainActivity))
                                        }
                                    )
                                }
                            }

                            item {
                                HorizontalDivider()
                            }
                            item {
                                Text("Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                            }
                            item {
                                Box(modifier = Modifier.alpha(if (steamToolGray) grayAlpha else normalAlpha)) {
                                    SettingsItem(
                                        icon = Icons.Default.Build,
                                        title = "Steam File Generator",
                                        subtitle = "Generate .steam files by browsing SteamDB.info",
                                        onClick = {
                                            val intent = Intent(this@MainActivity, SteamFileGenActivity::class.java)
                                            startActivity(intent)
                                        }
                                    )
                                }
                            }

                            item {
                                SettingsItem(
                                    icon = Icons.Default.Build,
                                    title = "Manual File Generator",
                                    subtitle = "Manually generate a custom .steam file",
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
                            isInterceptionActive = true
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
                            val oldTheme = theme
                            if (oldTheme != newTheme) {
                                DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_THEME old=${oldTheme.name} new=${newTheme.name}", this@MainActivity)
                            }
                            prefs.edit { putString(KEY_THEME, newTheme.name) }
                            theme = newTheme
                        },
                        onChangeDirectory = { directoryPickerLauncher.launch(null) },
                        onClose = { showSettings = false },
                        confirmDelete = confirmDelete,
                        onConfirmDeleteChange = { newConfirm ->
                            val oldConfirm = confirmDelete
                             if (oldConfirm != newConfirm) {
                                DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_CONFIRM_DELETE old=$oldConfirm new=$newConfirm", this@MainActivity)
                            }
                            prefs.edit { putBoolean(KEY_CONFIRM_DELETE, newConfirm) }
                            confirmDelete = newConfirm
                        },
                        autoCreateFile = autoCreateFile,
                        onAutoCreateFileChange = { newAutoCreate ->
                            val oldAutoCreate = autoCreateFile
                            if (oldAutoCreate != newAutoCreate) {
                                DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_AUTO_CREATE_FILE old=$oldAutoCreate new=$newAutoCreate", this@MainActivity)
                            }
                            prefs.edit { putBoolean(KEY_AUTO_CREATE_FILE, newAutoCreate) }
                            autoCreateFile = newAutoCreate
                        },
                        devMode = devMode,
                        onDevModeChange = { newDevMode ->
                            val oldDevMode = devMode
                            if (oldDevMode != newDevMode) {
                                DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_DEV_MODE old=$oldDevMode new=$newDevMode", this@MainActivity)
                            }
                            prefs.edit { putBoolean(KEY_DEV_MODE, newDevMode) }
                            devMode = newDevMode
                        },
                        topApp = topApp,
                        onTopAppChange = { newTopApp ->
                            val oldTopApp = topApp
                            if (oldTopApp != newTopApp) {
                                DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_TOP_APP old=$oldTopApp new=$newTopApp", this@MainActivity)
                            }
                            prefs.edit { putString(KEY_TOP_APP, newTopApp) }
                            topApp = newTopApp
                        },
                        bottomApp = bottomApp,
                        onBottomAppChange = { newBottomApp ->
                            val oldBottomApp = bottomApp
                            if (oldBottomApp != newBottomApp) {
                                DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_BOTTOM_APP old=$oldBottomApp new=$newBottomApp", this@MainActivity)
                            }
                            prefs.edit { putString(KEY_BOTTOM_APP, newBottomApp) }
                            bottomApp = newBottomApp
                        },
                        showAllApps = showAllApps,
                        onShowAllAppsChange = { newShowAllApps ->
                            val oldShowAllApps = showAllApps
                            if (oldShowAllApps != newShowAllApps) {
                                DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_SHOW_ALL_APPS old=$oldShowAllApps new=$newShowAllApps", this@MainActivity)
                            }
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
                            val oldMainScreen = mainScreen
                            if (oldMainScreen != newMainScreen) {
                                DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_MAIN_SCREEN old=${oldMainScreen.name} new=${newMainScreen.name}", this@MainActivity)
                            }
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
