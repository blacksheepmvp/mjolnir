package xyz.blacksheep.mjolnir

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.onboarding.OnboardingActivity
import xyz.blacksheep.mjolnir.services.KeepAliveService
import xyz.blacksheep.mjolnir.settings.AppTheme
import xyz.blacksheep.mjolnir.settings.MainScreen
import xyz.blacksheep.mjolnir.settings.SettingsItem
import xyz.blacksheep.mjolnir.settings.SettingsScreen
import xyz.blacksheep.mjolnir.settings.getLaunchableApps
import xyz.blacksheep.mjolnir.ui.theme.MjolnirTheme
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.DualScreenLauncher

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            val versionName = remember {
                try {
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    packageInfo.versionName ?: "N/A"
                } catch (e: Exception) {
                    "Unknown"
                }
            }

            var topApp by rememberSaveable { mutableStateOf(prefs.getString(KEY_TOP_APP, null)) }
            var bottomApp by rememberSaveable { mutableStateOf(prefs.getString(KEY_BOTTOM_APP, null)) }
            var isInterceptionActive by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)) }

            LaunchedEffect(Unit) {
                val onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
                val homeAppsConfigured = !topApp.isNullOrEmpty() || !bottomApp.isNullOrEmpty()
                if (!onboardingComplete && !homeAppsConfigured) {
                    startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                }
            }

            MjolnirTheme(darkTheme = useDarkTheme) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var settingsSessionId by rememberSaveable { mutableStateOf(0) }
                var settingsStartDestination by rememberSaveable { mutableStateOf("main") }

                var confirmDelete by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_CONFIRM_DELETE, true)) }
                var autoCreateFile by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_AUTO_CREATE_FILE, true)) }
                var devMode by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_DEV_MODE, false)) }
                var showAllApps by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_SHOW_ALL_APPS, false)) }
                val initialMainScreenName = prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name)
                var mainScreen by rememberSaveable { mutableStateOf(MainScreen.valueOf(initialMainScreenName ?: MainScreen.TOP.name)) }

                val context = this@MainActivity
                var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context, HomeKeyInterceptorService::class.java)) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isAccessibilityEnabled = isAccessibilityServiceEnabled(context, HomeKeyInterceptorService::class.java)
                            isInterceptionActive = prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
                            topApp = prefs.getString(KEY_TOP_APP, null) 
                            bottomApp = prefs.getString(KEY_BOTTOM_APP, null)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val scope = rememberCoroutineScope()

                LaunchedEffect(intent) {
                    if (intent.getBooleanExtra("open_settings", false)) {
                        showSettings = true
                    }
                }

                LaunchedEffect(showSettings) {
                    DiagnosticsLogger.logEvent("MainActivity", "SETTINGS_VISIBILITY_CHANGED", "visible=$showSettings", context = context)
                }

                val romDirectorySet = !prefs.getString(KEY_ROM_DIR_URI, "").isNullOrBlank()
                val setupHomeGray = isAccessibilityEnabled && (topApp != null || bottomApp != null)
                val steamToolGray = !romDirectorySet

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            Surface(tonalElevation = 2.dp) {
                                TopAppBar(
                                    title = { Text("Mjolnir v$versionName") },
                                    navigationIcon = {
                                        IconButton(onClick = { 
                                            DiagnosticsLogger.logEvent("MainActivity", "MENU_CLICKED", "currentShowSettings=$showSettings", context)
                                            settingsSessionId++
                                            showSettings = true 
                                        }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                                    },
                                    actions = {
                                        val homeAppsConfigured = !topApp.isNullOrEmpty() || !bottomApp.isNullOrEmpty()
                                        val tileActive = isInterceptionActive && isAccessibilityEnabled
                                        val tileLabel = if (tileActive) "Home Enabled" else "Home Disabled"
                                        var showConfigDialog by remember { mutableStateOf(false) }

                                        if (showConfigDialog) {
                                            AlertDialog(
                                                onDismissRequest = { showConfigDialog = false },
                                                confirmButton = { TextButton(onClick = { showConfigDialog = false }) { Text("OK") } },
                                                title = { Text("Setup Required") },
                                                text = { Text("Please configure Top or Bottom Home App in Mjolnir Home Settings.") }
                                            )
                                        }

                                        TextButton(onClick = {
                                            when {
                                                !isAccessibilityEnabled -> startActivity(Intent(context, OnboardingActivity::class.java))
                                                !homeAppsConfigured -> showConfigDialog = true
                                                else -> {
                                                    val newValue = !isInterceptionActive
                                                    prefs.edit { putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, newValue) }
                                                    isInterceptionActive = newValue
                                                    
                                                    try {
                                                        val updateIntent = Intent(this@MainActivity, KeepAliveService::class.java).apply { action = KeepAliveService.ACTION_UPDATE_STATUS }
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(updateIntent) else startService(updateIntent)
                                                    } catch (e: Exception) {
                                                        DiagnosticsLogger.logEvent("Error", "FAILED_TO_SEND_UPDATE_STATUS", "msg=${e.message}", this@MainActivity)
                                                    }
                                                }
                                            }
                                        }) { Text(tileLabel) }

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
                                item { Text("Setup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
                                item {
                                    Box(modifier = Modifier.alpha(if (setupHomeGray) 0.6f else 1.0f)) {
                                        SettingsItem(
                                            icon = Icons.Default.Home,
                                            title = "Initialize Mjolnir Home",
                                            subtitle = "Set permissions and activate the accessibility service",
                                            onClick = { startActivity(Intent(context, OnboardingActivity::class.java)) }
                                        )
                                    }
                                }
                                item {
                                    Box(modifier = Modifier.alpha(if (romDirectorySet) 0.6f else 1.0f)) {
                                        SettingsItem(
                                            icon = Icons.Default.Build,
                                            title = "Initialize Steam File Generator",
                                            subtitle = "Provide read/write access to your ROM directory",
                                            onClick = { startActivity(SteamFileGenActivity.createSetupIntent(this@MainActivity)) }
                                        )
                                    }
                                }
                                item { HorizontalDivider() }
                                item { Text("Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
                                item {
                                    Box(modifier = Modifier.alpha(if (steamToolGray) 0.6f else 1.0f)) {
                                        SettingsItem(
                                            icon = Icons.Default.Build,
                                            title = "Steam File Generator",
                                            subtitle = "Generate .steam files by browsing SteamDB.info",
                                            onClick = { startActivity(Intent(this@MainActivity, SteamFileGenActivity::class.java)) }
                                        )
                                    }
                                }
                                item {
                                    SettingsItem(
                                        icon = Icons.Default.Build,
                                        title = "Manual File Generator",
                                        subtitle = "Manually generate a custom .steam file",
                                        onClick = { startActivity(Intent(this@MainActivity, ManualFileGenActivity::class.java)) }
                                    )
                                }
                            }
                        }
                    }

                    if (showSettings) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            androidx.compose.runtime.key(settingsSessionId) { 
                                SettingsScreen(
                                    startDestination = settingsStartDestination,
                                    currentPath = prefs.getString(KEY_ROM_DIR_URI, "") ?: "",
                                    currentTheme = theme,
                                    onThemeChange = { newTheme ->
                                        val oldTheme = theme
                                        if (oldTheme != newTheme) DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_THEME old=${oldTheme.name} new=${newTheme.name}", this@MainActivity)
                                        prefs.edit { putString(KEY_THEME, newTheme.name) }
                                        theme = newTheme
                                    },
                                    onChangeDirectory = { directoryPickerLauncher.launch(null) },
                                    onClose = { 
                                        DiagnosticsLogger.logEvent("MainActivity", "SETTINGS_CLOSE_REQUESTED", context = context)
                                        showSettings = false 
                                    },
                                    confirmDelete = confirmDelete,
                                    onConfirmDeleteChange = { newConfirm ->
                                        val oldConfirm = confirmDelete
                                         if (oldConfirm != newConfirm) DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_CONFIRM_DELETE old=$oldConfirm new=$newConfirm", this@MainActivity)
                                        prefs.edit { putBoolean(KEY_CONFIRM_DELETE, newConfirm) }
                                        confirmDelete = newConfirm
                                    },
                                    autoCreateFile = autoCreateFile,
                                    onAutoCreateFileChange = { newAutoCreate ->
                                        val oldAutoCreate = autoCreateFile
                                        if (oldAutoCreate != newAutoCreate) DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_AUTO_CREATE_FILE old=$oldAutoCreate new=$newAutoCreate", this@MainActivity)
                                        prefs.edit { putBoolean(KEY_AUTO_CREATE_FILE, newAutoCreate) }
                                        autoCreateFile = newAutoCreate
                                    },
                                    devMode = devMode,
                                    onDevModeChange = { newDevMode ->
                                        val oldDevMode = devMode
                                        if (oldDevMode != newDevMode) DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_DEV_MODE old=$oldDevMode new=$newDevMode", this@MainActivity)
                                        prefs.edit { putBoolean(KEY_DEV_MODE, newDevMode) }
                                        devMode = newDevMode
                                    },
                                    topApp = topApp,
                                    onTopAppChange = { newTopApp ->
                                        val oldTopApp = topApp
                                        if (oldTopApp != newTopApp) DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_TOP_APP old=$oldTopApp new=$newTopApp", this@MainActivity)
                                        prefs.edit { putString(KEY_TOP_APP, newTopApp) }
                                        topApp = newTopApp
                                        
                                        val updateIntent = Intent(this@MainActivity, KeepAliveService::class.java).apply { action = KeepAliveService.ACTION_UPDATE_STATUS }
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(updateIntent) else startService(updateIntent)
                                        } catch (e: Exception) {
                                            DiagnosticsLogger.logEvent("Error", "FAILED_TO_SEND_UPDATE_STATUS", "msg=${e.message}", this@MainActivity)
                                        }
                                    },
                                    bottomApp = bottomApp,
                                    onBottomAppChange = { newBottomApp ->
                                        val oldBottomApp = bottomApp
                                        if (oldBottomApp != newBottomApp) DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_BOTTOM_APP old=$oldBottomApp new=$newBottomApp", this@MainActivity)
                                        prefs.edit { putString(KEY_BOTTOM_APP, newBottomApp) }
                                        bottomApp = newBottomApp

                                        val updateIntent = Intent(this@MainActivity, KeepAliveService::class.java).apply { action = KeepAliveService.ACTION_UPDATE_STATUS }
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(updateIntent) else startService(updateIntent)
                                        } catch (e: Exception) {
                                            DiagnosticsLogger.logEvent("Error", "FAILED_TO_SEND_UPDATE_STATUS", "msg=${e.message}", this@MainActivity)
                                        }
                                    },
                                    showAllApps = showAllApps,
                                    onShowAllAppsChange = { newShowAllApps ->
                                        val oldShowAllApps = showAllApps
                                        if (oldShowAllApps != newShowAllApps) DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_SHOW_ALL_APPS old=$oldShowAllApps new=$newShowAllApps", this@MainActivity)
                                        prefs.edit { putBoolean(KEY_SHOW_ALL_APPS, newShowAllApps) }
                                        showAllApps = newShowAllApps
                                    },
                                    onSetDefaultHome = {
                                        DiagnosticsLogger.logEvent("Settings", "SET_DEFAULT_HOME_CLICKED", context = this@MainActivity)
                                        try {
                                            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                        } catch (e: Exception) {
                                            DiagnosticsLogger.logEvent("Error", "SET_DEFAULT_HOME_FAILED", "msg=${e.message}", this@MainActivity)
                                            Toast.makeText(this@MainActivity, "Could not open Home Settings.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onLaunchDualScreen = {
                                        scope.launch {
                                            val launcherApps = getLaunchableApps(this@MainActivity, showAllApps)
                                            val top = launcherApps.find { it.packageName == topApp }
                                            val bottom = launcherApps.find { it.packageName == bottomApp }
                                            if (top != null && bottom != null) {
                                                val success = DualScreenLauncher.launchOnDualScreens(this@MainActivity, top.launchIntent, bottom.launchIntent, mainScreen)
                                                if (success) prefs.edit { putInt(KEY_LAUNCH_FAILURE_COUNT, 0) } else Toast.makeText(this@MainActivity, "Launch failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    mainScreen = mainScreen,
                                    onMainScreenChange = {
                                            newMainScreen ->
                                        val oldMainScreen = mainScreen
                                        if (oldMainScreen != newMainScreen) DiagnosticsLogger.logEvent("Prefs", "PREF_CHANGED", "key=$KEY_MAIN_SCREEN old=${oldMainScreen.name} new=${newMainScreen.name}", this@MainActivity)
                                        prefs.edit { putString(KEY_MAIN_SCREEN, newMainScreen.name) }
                                        mainScreen = newMainScreen
                                    }
                                )
                            } 
                        }
                    }
                }
            }
        }
    }

    fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val serviceId = "${context.packageName}/${service.name}"
        try {
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabledServices?.contains(serviceId) == true
        } catch (e: Exception) {
            return false
        }
    }
}
