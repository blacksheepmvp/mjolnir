package xyz.blacksheep.mjolnir

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.ui.theme.MjolnirTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            MjolnirTheme(darkTheme = useDarkTheme) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showAboutDialog by rememberSaveable { mutableStateOf(false) }
                var showManualEntry by rememberSaveable { mutableStateOf(false) }

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

                if (devMode) {
                    BackHandler(enabled = showSettings || showManualEntry) {
                        when {
                            showSettings -> showSettings = false
                            showManualEntry -> showManualEntry = false
                        }
                    }

                    Scaffold {
                        innerPadding ->
                        Surface(
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (showSettings) {
                                    SettingsScreen(
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
                                                        prefs.edit { putInt(HomeActivity.KEY_LAUNCH_FAILURE_COUNT, 0) }
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
                                } else if (showManualEntry) {
                                    ManualEntryScreen(
                                        defaultRomPath = prefs.getString(KEY_ROM_DIR_URI, "") ?: "",
                                        onClose = { showManualEntry = false }
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Button(onClick = {
                                            val intent = Intent(this@MainActivity, SteamFileGenActivity::class.java)
                                            startActivity(intent)
                                        }) {
                                            Text("Launch Steam File Generator")
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        Button(onClick = { showManualEntry = true }) {
                                            Text("Manual Entry")
                                        }
                                    }
                                }

                                if (showAboutDialog) {
                                    val versionName = try {
                                        packageManager.getPackageInfo(packageName, 0).versionName
                                    } catch (e: Exception) {
                                        "N/A"
                                    }
                                    AboutDialog(
                                        versionName = versionName ?: "N/A",
                                        onDismiss = { showAboutDialog = false }
                                    )
                                }

                                if (!showSettings && !showManualEntry) {
                                    HamburgerMenu(
                                        onSettingsClick = { showSettings = true },
                                        onAboutClick = { showAboutDialog = true },
                                        onQuitClick = { finish() }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val intent = Intent(this, SteamFileGenActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    companion object {
        const val PREFS_NAME = "MjolnirPrefs"
        const val KEY_THEME = "theme"
        const val KEY_ROM_DIR_URI = "rom_dir_uri"
        const val KEY_CONFIRM_DELETE = "confirm_delete"
        const val KEY_AUTO_CREATE_FILE = "auto_create_file"
        const val KEY_DEV_MODE = "dev_mode"
        const val KEY_TOP_APP = "top_app"
        const val KEY_BOTTOM_APP = "bottom_app"
        const val KEY_SHOW_ALL_APPS = "show_all_apps"
        const val KEY_MAIN_SCREEN = "main_screen"
    }
}
