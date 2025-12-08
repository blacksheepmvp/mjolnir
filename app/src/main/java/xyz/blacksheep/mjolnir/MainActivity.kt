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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.onboarding.OnboardingActivity
import xyz.blacksheep.mjolnir.services.KeepAliveService
import xyz.blacksheep.mjolnir.settings.AboutDialog
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
    
    // Keep mutable state here so the result callback can update it
    private val _romDirUri = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // Initialize state from prefs
        _romDirUri.value = prefs.getString(KEY_ROM_DIR_URI, null)

        val directoryPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val uriString = it.toString()
                prefs.edit { putString(KEY_ROM_DIR_URI, uriString) }
                _romDirUri.value = uriString // Update state to trigger recomposition
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

            LaunchedEffect(Unit) {
                if (versionName != "Unknown") {
                    prefs.edit { putString(KEY_LAST_SEEN_VERSION, versionName) }
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
                            // Refresh ROM dir state on resume in case it was changed elsewhere
                            _romDirUri.value = prefs.getString(KEY_ROM_DIR_URI, null)
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

                // Use the observable state here
                val currentRomDir by _romDirUri
                val romDirectorySet = !currentRomDir.isNullOrBlank()
                val setupHomeGray = isAccessibilityEnabled && (topApp != null || bottomApp != null)
                
                var showSteamDialog by remember { mutableStateOf(false) }
                var showWhatsNewDialog by remember { mutableStateOf(false) }
                var showAboutDialog by remember { mutableStateOf(false) }

                // Check if configuration is valid (reusing KeepAliveService logic simplified)
                val SPECIAL_HOME_APPS = remember { setOf("com.android.launcher3", "com.odin.odinlauncher") }
                val isConfigValid = remember(topApp, bottomApp) {
                    val hasApps = !topApp.isNullOrEmpty() || !bottomApp.isNullOrEmpty()
                    if (!hasApps) return@remember false
                    // If using special launcher, it must be default home. 
                    // Simplified check: just check if apps are set for now, as deep validation is complex here.
                    true
                }
                
                // Override validity check based on Interception Active
                val isFullyValid = if (isInterceptionActive) {
                    isConfigValid && isAccessibilityEnabled
                } else {
                    isConfigValid // Basic mode just needs apps
                }

                // Title color logic
                val homeSetupTitleColor = if (!isFullyValid) MaterialTheme.colorScheme.primary else Color.Unspecified

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            Surface(tonalElevation = 2.dp) {
                                TopAppBar(
                                    title = { Text("Mjolnir") },
                                    navigationIcon = {
                                        IconButton(onClick = { 
                                            DiagnosticsLogger.logEvent("MainActivity", "MENU_CLICKED", "currentShowSettings=$showSettings", context)
                                            settingsSessionId++
                                            showSettings = true 
                                        }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                                    },
                                    actions = {
                                        // Replaced Icon Button with "About" Text Button
                                        TextButton(onClick = { showAboutDialog = true }) {
                                            Text("About")
                                        }
                                    }
                                )
                            }
                        }
                    ) {
                        innerPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item { Text("Setup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(onClick = {
                                                    startActivity(
                                                        Intent(
                                                            context,
                                                            OnboardingActivity::class.java
                                                        )
                                                    )
                                                })
                                                .padding(horizontal = 16.dp, vertical = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.padding(end = 24.dp))
                                            Column {
                                                Text("Home Setup", style = MaterialTheme.typography.bodyLarge, color = homeSetupTitleColor)
                                                Text("Configure your custom multi-launcher settings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    item { HorizontalDivider() }
                                    item { Text("Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
                                    item {
                                        SettingsItem(
                                            icon = Icons.Default.Build,
                                            title = "Create Steam Files",
                                            subtitle = "Add your Game Hub Lite and GameNative Steam games to supported frontends",
                                            onClick = {
                                                if (!romDirectorySet) {
                                                    directoryPickerLauncher.launch(null)
                                                } else {
                                                    showSteamDialog = true
                                                }
                                            }
                                        )
                                    }
                                }
                                
                                // What's New Icon Overlay
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 24.dp)
                                ) {
                                    IconButton(onClick = { showWhatsNewDialog = true }) {
                                        Icon(Icons.Default.Info, contentDescription = "What's New", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    
                    if (showSteamDialog) {
                        AlertDialog(
                            onDismissRequest = { showSteamDialog = false },
                            title = { Text("Create Steam Files") },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("Choose a method to generate steam shortcut files.", modifier = Modifier.padding(bottom = 16.dp))
                                    OutlinedButton(
                                        onClick = {
                                            showSteamDialog = false
                                            startActivity(Intent(context, SteamFileGenActivity::class.java))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("SteamDB.info") }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    OutlinedButton(
                                        onClick = {
                                            showSteamDialog = false
                                            startActivity(Intent(context, ManualFileGenActivity::class.java))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Custom") }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    OutlinedButton(
                                        onClick = {
                                            showSteamDialog = false
                                            directoryPickerLauncher.launch(null)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Edit Steam Folder") }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showSteamDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    if (showWhatsNewDialog) {
                        val changelogFiles = remember {
                            try {
                                context.assets.list("changelogs")?.sortedDescending() ?: emptyList()
                            } catch (e: Exception) {
                                emptyList<String>()
                            }
                        }
                        
                        val changelogs = remember(changelogFiles) {
                            changelogFiles.map { fileName ->
                                fileName to readChangelog(context, fileName)
                            }.toMap()
                        }

                        AlertDialog(
                            onDismissRequest = { showWhatsNewDialog = false },
                            title = { Text("What's New") },
                            text = {
                                val scrollState = rememberScrollState()
                                Column(modifier = Modifier.verticalScroll(scrollState)) {
                                    changelogFiles.forEach { fileName ->
                                        val version = fileName.replace(".txt", "")
                                        val content = changelogs[fileName] ?: "Loading..."
                                        ExpandableChangelogItem(
                                            version = version, 
                                            content = content
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showWhatsNewDialog = false }) { Text("Close") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/xyzblacksheep"))
                                    startActivity(intent)
                                }) { Text("Support on Ko-fi") }
                            }
                        )
                    }
                    
                    if (showAboutDialog) {
                        AboutDialog(onDismiss = { showAboutDialog = false })
                    }

                    if (showSettings) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            key(settingsSessionId) {
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

private fun readChangelog(context: Context, fileName: String): String {
    try {
        return context.assets.open("changelogs/$fileName").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        return "Error reading $fileName."
    }
}

@Composable
fun SimpleMarkdownText(markdown: String, modifier: Modifier = Modifier) {
    // FIX: Read theme values in the Composable context
    val titleLargeSize = MaterialTheme.typography.titleLarge.fontSize
    val titleMediumSize = MaterialTheme.typography.titleMedium.fontSize

    // FIX: Pass theme values as keys to remember so it updates on theme change
    val annotatedString = remember(markdown, titleLargeSize, titleMediumSize) {
        buildAnnotatedString {
            val lines = markdown.lines()
            lines.forEach { line ->
                // Handle Headers first as they are block-level and exclusive
                if (line.startsWith("# ")) {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = titleLargeSize)) {
                        // FIX: Use '\n' for newline
                        append(line.removePrefix("# ") + "\n")
                    }
                } else if (line.startsWith("## ")) {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = titleMediumSize)) {
                        // FIX: Use '\n' for newline
                        append(line.removePrefix("## ") + "\n")
                    }
                } else {
                    // Handle line content, including bullets and bold
                    var processedLine = line

                    // Handle bullets
                    if (processedLine.startsWith("- ")) {
                        append("• ")
                        processedLine = processedLine.removePrefix("- ")
                    }

                    // Handle bold within the rest of the line
                    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
                    var lastIndex = 0
                    boldRegex.findAll(processedLine).forEach { matchResult ->
                        val startIndex = matchResult.range.first
                        val endIndex = matchResult.range.last + 1
                        val boldText = matchResult.groupValues[1]

                        // Append text before the bold part
                        if (startIndex > lastIndex) {
                            append(processedLine.substring(lastIndex, startIndex))
                        }

                        // Append the bold text with style
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(boldText)
                        }
                        lastIndex = endIndex
                    }

                    // Append any remaining text after the last bold part
                    if (lastIndex < processedLine.length) {
                        append(processedLine.substring(lastIndex))
                    }

                    // Append newline
                    append("\n")
                }
            }
        }
    }

    Text(text = annotatedString, modifier = modifier, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ExpandableChangelogItem(version: String, content: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(version, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (expanded) {
            SimpleMarkdownText(
                markdown = content,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            )
        }
    }
}
