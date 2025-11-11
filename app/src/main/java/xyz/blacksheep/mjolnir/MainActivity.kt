package xyz.blacksheep.mjolnir

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import xyz.blacksheep.mjolnir.ui.theme.MjolnirTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //window.setFlags(
        //    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
        //    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        //)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

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
                    val window = (view.context as android.app.Activity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
                }
            }

            MjolnirTheme(darkTheme = useDarkTheme) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showAboutDialog by rememberSaveable { mutableStateOf(false) }
                var showManualEntry by rememberSaveable { mutableStateOf(false) }
                var showExperimentalUi by rememberSaveable { mutableStateOf(false) }
                var menuExpanded by rememberSaveable { mutableStateOf(false) }

                var confirmDelete by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_CONFIRM_DELETE, true)) }
                var autoCreateFile by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_AUTO_CREATE_FILE, true)) }
                var devMode by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_DEV_MODE, false)) }

                if (devMode) {
                    Scaffold {
                        innerPadding ->
                        Surface(
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (showExperimentalUi) {
                                    ExperimentalUiScreen(onClose = { showExperimentalUi = false })
                                } else if (showSettings) {
                                    SettingsScreen(
                                        currentPath = prefs.getString(KEY_ROM_DIR_URI, "") ?: "",
                                        currentTheme = theme,
                                        onThemeChange = { newTheme ->
                                            prefs.edit { putString(KEY_THEME, newTheme.name) }
                                            theme = newTheme
                                        },
                                        onChangeDirectory = { startActivity(Intent(this@MainActivity, SteamFileGenActivity::class.java)) },
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
                                        Spacer(Modifier.height(16.dp))
                                        Button(onClick = { showExperimentalUi = true }) {
                                            Text("Experimental UI")
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

                                if (!showSettings && !showManualEntry && !showExperimentalUi) {
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
                                        }
                                    }
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
}
