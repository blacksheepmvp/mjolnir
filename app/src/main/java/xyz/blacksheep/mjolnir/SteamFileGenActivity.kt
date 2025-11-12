package xyz.blacksheep.mjolnir

import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.blacksheep.mjolnir.ui.theme.MjolnirTheme

private const val ACTION_CCT_URL_RETURN = "xyz.blacksheep.mjolnir.ACTION_CCT_URL_RETURN"

enum class AppTheme { LIGHT, DARK, SYSTEM }
enum class MainScreen { TOP, BOTTOM }

val GameInfoSaver = Saver<GameInfo, List<String>>(
    save = { listOf(it.appId, it.name, it.headerImage) },
    restore = { GameInfo(it[0], it[1], it[2]) }
)

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

val OverwriteInfoSaver = Saver<OverwriteInfo?, Any>(
    save = { it?.let { with(GameInfoSaver) { save(it.gameInfo)?.let { list -> listOf(it.oldAppId) + list } } } },
    restore = {
        (it as? List<*>)?.let { value ->
            val oldAppId = value[0] as String
            @Suppress("UNCHECKED_CAST")
            val gameInfo = GameInfoSaver.restore(value.drop(1) as? List<String> ?: emptyList())
            if (gameInfo != null) {
                OverwriteInfo(gameInfo, oldAppId)
            } else {
                null
            }
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class SteamFileGenActivity : ComponentActivity() {
    private val intentState = mutableStateOf<Intent?>(null)
    private var isNewTask = false
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        isNewTask = isTaskRoot

        // If the activity is launched as a new task to handle a share intent, handle it headlessly
        if (intent?.action == Intent.ACTION_SEND && isNewTask) {
            CoroutineScope(Dispatchers.Main).launch {
                val result = handleShareIntentHeadless(intent)
                Toast.makeText(this@SteamFileGenActivity, result, Toast.LENGTH_SHORT).show()
                finish()
            }
            return // Skip UI setup
        }

        //window.setFlags(
        //    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
        //    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        //)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Check for the CCT custom button callback and transform the intent
        var intentToProcess = intent
        if (intent?.action == ACTION_CCT_URL_RETURN) {
            Log.e("MjolnirCCT_DEBUG", "CALLBACK RECEIVED! Full Intent Dump:")
            Log.e("MjolnirCCT_DEBUG", "Action: " + intent.action)
            Log.e("MjolnirCCT_DEBUG", "Data URI (Expected URL): " + intent.dataString)
            val extras = intent.extras
            if (extras != null) { for (key in extras.keySet()) {
                @Suppress("DEPRECATION")
                Log.e("MjolnirCCT_DEBUG", "Extra Key: " + key + " Value: " + extras[key])
            } }

            val url = intent.dataString
            if (url != null) {
                intentToProcess = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, url)
                }
            }
        }
        intentState.value = intentToProcess


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

                val onPickDirectory = { directoryPickerLauncher.launch(null) }

                if (romsDirectoryUri == null) {
                    SetupScreen(onPickDirectory = onPickDirectory)
                } else {
                    MainContent(
                        romsDirectoryUri!!,
                        onPickDirectory,
                        theme,
                        onThemeChange = { newTheme ->
                            prefs.edit { putString(KEY_THEME, newTheme.name) }
                            theme = newTheme
                        },
                        intent = intentState.value,
                        onIntentConsumed = { intentState.value = null }
                    )
                }
            }
        }
    }

    private suspend fun handleShareIntentHeadless(intent: Intent): String = withContext(Dispatchers.IO) {
        val url = intent.getStringExtra(Intent.EXTRA_TEXT)
        val appId = url?.let { SteamTool.extractAppIdFromUrl(it) }

        if (appId == null) {
            return@withContext "Invalid URL"
        }

        val romsDirUri =
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ROM_DIR_URI, null)?.toUri()
                ?: return@withContext "Error: ROMs directory not set."

        return@withContext try {
            val gameInfo = SteamTool.fetchGameInfo(appId)
            val existingContent = SteamTool.readSteamFileContent(this@SteamFileGenActivity, romsDirUri, "${'$'}{gameInfo.name}.steam")

            when (existingContent) {
                null -> {
                    SteamTool.createSteamFileFromDetails(
                        this@SteamFileGenActivity,
                        romsDirUri,
                        gameInfo.name,
                        gameInfo.appId,
                        "steam"
                    )
                }
                gameInfo.appId -> {
                    "${'$'}{gameInfo.name}.steam already exists"
                }
                else -> {
                    // Overwrite case - for headless we'll just report the conflict
                    "Conflict: ${'$'}{gameInfo.name}.steam exists with a different AppID"
                }
            }
        } catch (e: Exception) {
            e.message ?: "Unknown error"
        }
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        var intentToProcess = intent
        // Check for the CCT custom button callback and transform the intent
        if (intent?.action == ACTION_CCT_URL_RETURN) {
            Toast.makeText(this, "CCT", Toast.LENGTH_SHORT).show()
            Log.e("MjolnirCCT_DEBUG", "CALLBACK RECEIVED! Full Intent Dump:")
            Log.e("MjolnirCCT_DEBUG", "Action: " + intent.action)
            Log.e("MjolnirCCT_DEBUG", "Data URI (Expected URL): " + intent.dataString)
            val extras = intent.extras
            if (extras != null) { for (key in extras.keySet()) {
                @Suppress("DEPRECATION")
                Log.e("MjolnirCCT_DEBUG", "Extra Key: " + key + " Value: " + extras[key])
            } }

            val url = intent.dataString
            if (url != null) {
                intentToProcess = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, url)
                }
            }
        } else if (intent?.action == Intent.ACTION_SEND) {
            Toast.makeText(this, "Anywhere Else", Toast.LENGTH_SHORT).show()
        }
        intentState.value = intentToProcess
    }

    @Composable
    private fun MainContent(
        romsDirUri: Uri,
        onPickDirectory: () -> Unit,
        currentTheme: AppTheme,
        onThemeChange: (AppTheme) -> Unit,
        intent: Intent?,
        onIntentConsumed: () -> Unit
    ) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val romsDirectoryUri by rememberSaveable { mutableStateOf(romsDirUri) }

        var uiState by rememberSaveable(stateSaver = UiStateSaver) { mutableStateOf(UiState.Idle) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        var selectedFile by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
        var overwriteInfo by rememberSaveable(stateSaver = OverwriteInfoSaver) { mutableStateOf(null) }
        var showDeleteConfirmDialog by rememberSaveable { mutableStateOf<String?>(null) }
        var confirmDelete by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_CONFIRM_DELETE, true)) }
        var autoCreateFile by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_AUTO_CREATE_FILE, true)) }
        var devMode by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_DEV_MODE, false)) }
        var topApp by rememberSaveable { mutableStateOf(prefs.getString(KEY_TOP_APP, null)) }
        var bottomApp by rememberSaveable { mutableStateOf(prefs.getString(KEY_BOTTOM_APP, null)) }
        var showAllApps by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_SHOW_ALL_APPS, false)) }
        val initialMainScreenName = prefs.getString(KEY_MAIN_SCREEN, MainScreen.TOP.name)
        var mainScreen by rememberSaveable { mutableStateOf(MainScreen.valueOf(initialMainScreenName ?: MainScreen.TOP.name)) }

        var multiSelectMode by rememberSaveable { mutableStateOf(false) }
        var selectedFiles by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
        var showMassDeleteDialog by rememberSaveable { mutableStateOf(false) }

        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showAboutDialog by rememberSaveable { mutableStateOf(false) }
        var steamFiles by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
        var fileCreationResult by rememberSaveable { mutableStateOf<String?>(null) }

        val versionName = remember {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: "N/A"
            } catch (e: Exception) {
                e.toString()
            }
        }

        suspend fun refreshFileList(uri: Uri) {
            try {
                steamFiles = SteamTool.refreshFileList(context, uri)
            } catch (_: Exception) {
                steamFiles = emptyList()
                fileCreationResult = "Error: Could not refresh file list."
            }
        }

        LaunchedEffect(romsDirectoryUri) {
            refreshFileList(romsDirectoryUri)
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

        suspend fun createSteamFile(gameInfo: GameInfo) {
            romsDirectoryUri.let { dirUri ->
                val existingContent = SteamTool.readSteamFileContent(context, dirUri, "${'$'}{gameInfo.name}.steam")
                if (existingContent == null) {
                    val result = SteamTool.createSteamFileFromDetails(context, dirUri, gameInfo.name, gameInfo.appId, "steam")
                    fileCreationResult = result
                    if (result.startsWith("Success")) refreshFileList(dirUri)
                } else if (existingContent == gameInfo.appId) {
                    fileCreationResult = "File is already up to date."
                } else {
                    overwriteInfo = OverwriteInfo(gameInfo, existingContent)
                }
            }
        }

        LaunchedEffect(uiState) {
            val currentState = uiState
            if (currentState is UiState.Success && autoCreateFile) {
                createSteamFile(currentState.gameInfo)
            }
        }

        LaunchedEffect(intent) {
            if (intent == null) return@LaunchedEffect

            val url = when (intent.action) {
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            }

            url?.let {
                SteamTool.extractAppIdFromUrl(it)?.let { appId ->
                    searchForGame(appId)
                }
            } ?: run {
                if (intent.action == Intent.ACTION_SEND || intent.action == ACTION_CCT_URL_RETURN) {
                    uiState = UiState.Failure("Invalid URL received")
                }
            }

            onIntentConsumed()
        }

        val performDelete: (String) -> Unit = {
            fileName ->
            scope.launch {
                romsDirectoryUri.let {
                    val result = SteamTool.deleteSteamFile(context, it, fileName)
                    fileCreationResult = result
                    if(result.startsWith("Success")) refreshFileList(it)
                }
            }
        }

        val performMassDelete = {
            scope.launch {
                romsDirectoryUri.let { uri ->
                    for (fileName in selectedFiles) {
                        SteamTool.deleteSteamFile(context, uri, fileName)
                    }
                    fileCreationResult = "Deleted ${'$'}{selectedFiles.size} files."
                    refreshFileList(uri)
                    multiSelectMode = false
                    selectedFiles = emptySet()
                }
            }
        }

        Scaffold(
            topBar = {
                if (multiSelectMode && !showSettings) {
                    MultiSelectTopBar(
                        selectedCount = selectedFiles.size,
                        onCancel = {
                            multiSelectMode = false
                            selectedFiles = emptySet()
                        },
                        onDelete = { showMassDeleteDialog = true }
                    )
                }
            }
        ) { innerPadding ->
            Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (showSettings) {
                        SettingsScreen(
                            currentPath = romsDirectoryUri.toString(),
                            currentTheme = currentTheme,
                            onThemeChange = onThemeChange,
                            onChangeDirectory = onPickDirectory,
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
                                context.startActivity(intent)
                            },
                            onLaunchDualScreen = {
                                val launcherApps = getLaunchableApps(context, showAllApps)
                                val top = launcherApps.find { it.packageName == topApp }
                                val bottom = launcherApps.find { it.packageName == bottomApp }
                                if (top != null && bottom != null) {
                                    DualScreenLauncher.launchOnDualScreens(context, top.launchIntent, bottom.launchIntent, mainScreen)
                                }
                            },
                            mainScreen = mainScreen,
                            onMainScreenChange = {
                                newMainScreen ->
                                prefs.edit { putString(KEY_MAIN_SCREEN, newMainScreen.name) }
                                mainScreen = newMainScreen
                            }
                        )
                    } else {
                        MainScreen(
                            uiState = uiState,
                            onSearch = searchForGame,
                            steamFiles = steamFiles,
                            fileCreationResult = fileCreationResult,
                            onRefresh = { scope.launch { refreshFileList(romsDirectoryUri) } },
                            onCreateFile = { gameInfo ->
                                scope.launch { createSteamFile(gameInfo) }
                            },
                            onFileClick = {
                                fileName ->
                                if (multiSelectMode) {
                                    selectedFiles = if (selectedFiles.contains(fileName)) selectedFiles - fileName else selectedFiles + fileName
                                } else {
                                    scope.launch {
                                        val content = romsDirectoryUri.let { SteamTool.readSteamFileContent(context, it, fileName) } ?: "Error: Directory URI is null."
                                        selectedFile = Pair(fileName, content)
                                    }
                                }
                            },
                            onFileLongClick = {
                                fileName ->
                                multiSelectMode = true
                                selectedFiles = selectedFiles + fileName
                            },
                            onOpenSteamDb = {
                                val largeBitmap = BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_menu_share)
                                val scaledIcon = largeBitmap.scale(96, 96, true)

                                val shareIntent = Intent(context, SteamFileGenActivity::class.java).apply {
                                    action = ACTION_CCT_URL_RETURN
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                val pendingIntent = PendingIntent.getActivity(
                                    context, 0, shareIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                                )

                                val customTabsIntent = CustomTabsIntent.Builder()
                                    .setActionButton(scaledIcon, "Share to Mjolnir", pendingIntent, true)
                                    .build()
                                customTabsIntent.launchUrl(context, "https://steamdb.info/".toUri())
                             },
                            inMultiSelectMode = multiSelectMode,
                            selectedFiles = selectedFiles
                        )
                    }

                    if (!showSettings && !multiSelectMode) {
                        HamburgerMenu(
                            onSettingsClick = { showSettings = true },
                            onAboutClick = { showAboutDialog = true },
                            onQuitClick = { finish() }
                        )
                    }
                }
            }
        }

        if (selectedFile != null) {
            FileContentDialog(
                fileName = selectedFile!!.first,
                content = selectedFile!!.second,
                onDismiss = { selectedFile = null },
                onDelete = {
                    val fileToDelete = selectedFile!!.first
                    selectedFile = null
                    if (confirmDelete) {
                        showDeleteConfirmDialog = fileToDelete
                    } else {
                        performDelete(fileToDelete)
                    }
                }
            )
        }

        if (showDeleteConfirmDialog != null) {
            DeleteConfirmationDialog(
                fileName = showDeleteConfirmDialog!!,
                onDismiss = { showDeleteConfirmDialog = null },
                onConfirm = {
                    performDelete(showDeleteConfirmDialog!!)
                    showDeleteConfirmDialog = null
                }
            )
        }

        if (showMassDeleteDialog) {
            MassDeleteConfirmationDialog(
                quantity = selectedFiles.size,
                onDismiss = { showMassDeleteDialog = false },
                onConfirm = {
                    performMassDelete()
                    showMassDeleteDialog = false
                }
            )
        }

        if (overwriteInfo != null) {
            OverwriteConfirmationDialog(
                overwriteInfo = overwriteInfo!!,
                onDismiss = { overwriteInfo = null },
                onConfirm = {
                    scope.launch {
                        romsDirectoryUri.let { uri ->
                            val result = SteamTool.createSteamFileFromDetails(context, uri, overwriteInfo!!.gameInfo.name, overwriteInfo!!.gameInfo.appId, "steam")
                            fileCreationResult = result
                            if (result.startsWith("Success")) refreshFileList(uri)
                        }
                        overwriteInfo = null
                    }
                }
            )
        }

        if (showAboutDialog) {
            AboutDialog(
                versionName = versionName,
                onDismiss = { showAboutDialog = false }
            )
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
        const val KEY_HOME_INTERCEPTION_ACTIVE = "home_interception_active"
        const val KEY_SWAP_SCREENS_REQUESTED = "swap_screens_requested"
    }
}

@Composable
fun SetupScreen(onPickDirectory: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Mjolnir Setup", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 16.dp))
            Text("To begin, please select the directory where you store your Steam ROM files.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPickDirectory) { Text("Choose Directory") }
        }
    }
}

@Composable
fun FileContentDialog(fileName: String, content: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fileName) },
        text = { Text("AppID: $content") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(fileName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Are you sure?") },
        text = {
            Column {
                Text("This will permanently delete the file \"$fileName\".")
                Spacer(modifier = Modifier.height(16.dp))
                Text("The game's files, settings, and saves will not be affected.", style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Delete") }
        }
    )
}

@Composable
fun MassDeleteConfirmationDialog(quantity: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Are you sure?") },
        text = { Text("You are about to permanently delete $quantity files.") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Delete All") }
        }
    )
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
                Text("A file named \"${'$'}{overwriteInfo.gameInfo.name}.steam\" already exists with a different AppID.")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Old AppID: ${'$'}{overwriteInfo.oldAppId}")
                Text("New AppID: ${'$'}{overwriteInfo.gameInfo.appId}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(onClick = onConfirm) { Text("Overwrite") } }
    )
}

@Composable
private fun SearchUi(
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
                is UiState.Idle -> Spacer(Modifier.height(0.dp))
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Success -> {
                    Text(
                        text = uiState.gameInfo.name,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onCreateFile(uiState.gameInfo) }) { Text("Save .steam File") }
                    fileCreationResult?.let {
                        val color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error else LocalContentColor.current
                        Text(it, modifier = Modifier.padding(top = 8.dp), color = color, textAlign = TextAlign.Center)
                    }
                }
                is UiState.Failure -> Text("Error: ${'$'}{uiState.error}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ManualInputUi(
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListUi(
    steamFiles: List<String>,
    onFileClick: (String) -> Unit,
    onFileLongClick: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    inMultiSelectMode: Boolean,
    selectedFiles: Set<String>
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Existing .steam Files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh file list")
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        if (steamFiles.isEmpty()) {
            Text("No .steam files found.")
        } else {
            LazyColumn {
                items(steamFiles) { fileName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onFileClick(fileName) },
                                onLongClick = { onFileLongClick(fileName) }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (inMultiSelectMode) {
                            Checkbox(
                                checked = selectedFiles.contains(fileName),
                                onCheckedChange = { onFileClick(fileName) }, // Click toggles selection in this mode
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                        Text(fileName)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopBar(selectedCount: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
            }
        },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
            }
        }
    )
}


@Composable
fun MainScreen(
    uiState: UiState, onSearch: (String) -> Unit, steamFiles: List<String>,
    fileCreationResult: String?, onRefresh: () -> Unit, onCreateFile: (GameInfo) -> Unit,
    onFileClick: (String) -> Unit, onFileLongClick: (String) -> Unit, onOpenSteamDb: () -> Unit,
    inMultiSelectMode: Boolean, selectedFiles: Set<String>,
    modifier: Modifier = Modifier
) {
    @Suppress("unused", "UnusedVariable") val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp.dp > 600.dp

    if (isLandscape) {
        Row(modifier = modifier) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SearchUi(
                    uiState = uiState,
                    fileCreationResult = fileCreationResult,
                    onCreateFile = onCreateFile,
                    modifier = Modifier.weight(1f)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                ManualInputUi(onSearch = onSearch)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onOpenSteamDb) {
                    Text("Open SteamDB.info")
                }
            }
            VerticalDivider(modifier = Modifier.fillMaxHeight())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                FileListUi(
                    steamFiles = steamFiles, onFileClick = onFileClick, onRefresh = onRefresh, modifier = Modifier.fillMaxSize(),
                    inMultiSelectMode = inMultiSelectMode, selectedFiles = selectedFiles, onFileLongClick = onFileLongClick
                )
            }
        }
    } else {
        Column(modifier = modifier.padding(16.dp)) {
            SearchUi(
                uiState = uiState,
                fileCreationResult = fileCreationResult,
                onCreateFile = onCreateFile,
                modifier = Modifier.height(180.dp) // Give a bit more space for the image
            )
            ManualInputUi(onSearch = onSearch)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            FileListUi(
                steamFiles = steamFiles,
                onFileClick = onFileClick,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
                inMultiSelectMode = inMultiSelectMode,
                selectedFiles = selectedFiles,
                onFileLongClick = onFileLongClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onOpenSteamDb,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open SteamDB.info")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun MainScreenPreviewPortrait() {
    MjolnirTheme {
        MainScreen(
            uiState = UiState.Success(GameInfo("123", "Test Game Name That is Quite Long", "")), onSearch = {},
            steamFiles = listOf("A.steam", "B.steam"), fileCreationResult = "Success",
            onRefresh = {}, onCreateFile = {}, onFileClick = {},
            inMultiSelectMode = false, selectedFiles = emptySet(), onFileLongClick = {}, onOpenSteamDb = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 720, heightDp = 360)
@Composable
fun MainScreenPreviewLandscape() {
    MjolnirTheme {
        MainScreen(
            uiState = UiState.Success(GameInfo("123", "Test Game Name That is Quite Long", "")), onSearch = {},
            steamFiles = listOf("A.steam", "B.steam", "C.steam", "D.steam", "E.steam"), fileCreationResult = "Success",
            onRefresh = {}, onCreateFile = {}, onFileClick = {},
            inMultiSelectMode = false, selectedFiles = emptySet(), onFileLongClick = {}, onOpenSteamDb = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    MjolnirTheme { SettingsScreen("/path/to/roms", AppTheme.SYSTEM, {}, {}, {}, true, {}, true, {}, false, {}, null, {}, null, {}, false, {}, {}, {}, MainScreen.TOP, {}) }
}

@Preview(showBackground = true)
@Composable
fun SetupPreview() {
    MjolnirTheme { SetupScreen({}) }
}
