package xyz.blacksheep.mjolnir

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.settings.*
import xyz.blacksheep.mjolnir.ui.theme.*
import xyz.blacksheep.mjolnir.utils.*

@OptIn(ExperimentalMaterial3Api::class)
class ManualFileGenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val initialThemeName = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name)
            val theme by rememberSaveable { mutableStateOf(AppTheme.valueOf(initialThemeName ?: AppTheme.SYSTEM.name)) }
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
                var romsDirectoryUri by rememberSaveable { mutableStateOf(prefs.getString(KEY_ROM_DIR_URI, null)?.toUri()) }

                val directoryPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                    onResult = { uri: Uri? ->
                        if (uri != null) {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                            prefs.edit { putString(KEY_ROM_DIR_URI, uri.toString()) }
                            romsDirectoryUri = uri
                        }
                    }
                )

                if (romsDirectoryUri == null) {
                    SetupScreen(onPickDirectory = { directoryPickerLauncher.launch(null) })
                } else {
                    ManualEntryContent(romsDirectoryUri!!)
                }
            }
        }
    }

    @Composable
    private fun ManualEntryContent(romsDirUri: Uri) {
        var uiState by rememberSaveable(stateSaver = UiStateSaver) { mutableStateOf(UiState.Idle) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var overwriteInfo by rememberSaveable(stateSaver = OverwriteInfoSaver) { mutableStateOf<OverwriteInfo?>(null) }
        var fileCreationResult by rememberSaveable { mutableStateOf<String?>(null) }

        suspend fun createSteamFile(gameInfo: GameInfo) {
            val existingContent = SteamTool.readSteamFileContent(context, romsDirUri, "${gameInfo.name}.steam")
            if (existingContent == null) {
                val result = SteamTool.createSteamFileFromDetails(context, romsDirUri, gameInfo.name, gameInfo.appId, "steam")
                fileCreationResult = result
            } else if (existingContent == gameInfo.appId) {
                fileCreationResult = "File is already up to date."
            } else {
                overwriteInfo = OverwriteInfo(gameInfo, existingContent)
            }
        }

        val searchForGame: (String) -> Unit = { appId ->
            scope.launch {
                fileCreationResult = null
                if (appId.isBlank()) {
                    uiState = UiState.Failure("AppID cannot be empty.")
                    return@launch
                }
                uiState = UiState.Loading(appId)
                runCatching { SteamTool.fetchGameInfo(appId) }
                    .onSuccess { gameInfo -> uiState = UiState.Success(gameInfo) }
                    .onFailure { e -> uiState = UiState.Failure(e.message ?: "Unknown error") }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Manual File Generator") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SearchUi(
                        uiState = uiState,
                        fileCreationResult = fileCreationResult,
                        onCreateFile = { gameInfo -> scope.launch { createSteamFile(gameInfo) } },
                        modifier = Modifier.height(220.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    ManualInputUi(onSearch = searchForGame)
                }
            }
        }

        if (overwriteInfo != null) {
            OverwriteConfirmationDialog(
                overwriteInfo = overwriteInfo!!,
                onDismiss = { overwriteInfo = null },
                onConfirm = {
                    scope.launch {
                        val result = SteamTool.createSteamFileFromDetails(context, romsDirUri, overwriteInfo!!.gameInfo.name, overwriteInfo!!.gameInfo.appId, "steam")
                        fileCreationResult = result
                        overwriteInfo = null
                    }
                }
            )
        }
    }
}
    