package xyz.blacksheep.mjolnir

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import xyz.blacksheep.mjolnir.ui.theme.MjolnirTheme

data class GameInfo(val appId: String, val name: String, val headerImage: String)
data class OverwriteInfo(val gameInfo: GameInfo, val oldAppId: String)

private const val PREFS_NAME = "MjolnirPrefs"
private const val KEY_ROM_DIR_URI = "romDirUri"
private const val KEY_THEME = "theme"
private const val KEY_CONFIRM_DELETE = "confirmDelete"
private const val KEY_AUTO_CREATE_FILE = "autoCreateFile"

enum class AppTheme { LIGHT, DARK, SYSTEM }

sealed interface UiState {
    object Idle : UiState
    data class Loading(val appId: String) : UiState
    data class Success(val gameInfo: GameInfo) : UiState
    data class Failure(val error: String) : UiState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    private val intentState = mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        intentState.value = intent

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setContent {
            val initialThemeName = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name)
            var theme by remember { mutableStateOf(AppTheme.valueOf(initialThemeName ?: AppTheme.SYSTEM.name)) }
            val useDarkTheme = when (theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    window.statusBarColor = Color.Transparent.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
                }
            }

            MjolnirTheme(darkTheme = useDarkTheme) {
                var romsDirectoryUri by remember { mutableStateOf(prefs.getString(KEY_ROM_DIR_URI, null)?.toUri()) }

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intentState.value = intent
    }

    @Composable
    private fun MainContent(
        romsDirUri: Uri,
        onPickDirectory: () -> Unit,
        onThemeChange: (AppTheme) -> Unit,
        intent: Intent?,
        onIntentConsumed: () -> Unit
    ) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        var romsDirectoryUri by remember { mutableStateOf(romsDirUri) }

        var uiState by remember { mutableStateOf<UiState>(UiState.Idle) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        var selectedFile by remember { mutableStateOf<Pair<String, String>?>(null) }
        var overwriteInfo by remember { mutableStateOf<OverwriteInfo?>(null) }
        var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }
        var confirmDelete by remember { mutableStateOf(prefs.getBoolean(KEY_CONFIRM_DELETE, true)) }
        var autoCreateFile by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_CREATE_FILE, true)) }

        var multiSelectMode by remember { mutableStateOf(false) }
        var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
        var showMassDeleteDialog by remember { mutableStateOf(false) }

        var showSettings by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }
        var menuExpanded by remember { mutableStateOf(false) }
        var steamFiles by remember { mutableStateOf<List<String>>(emptyList()) }
        var fileCreationResult by remember { mutableStateOf<String?>(null) }

        val versionName = remember {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: "N/A"
            } catch (e: Exception) {
                "N/A"
            }
        }

        suspend fun refreshFileList(uri: Uri) {
            try {
                val files = withContext(Dispatchers.IO) {
                    val dir = DocumentFile.fromTreeUri(context, uri)
                    dir?.listFiles()
                        ?.filter { it.name?.endsWith(".steam", true) == true }
                        ?.mapNotNull { it.name }
                        ?.sorted()
                        ?: emptyList()
                }
                steamFiles = files
            } catch (e: Exception) {
                steamFiles = emptyList()
                fileCreationResult = "Error: ${e.message}"
            }
        }

        LaunchedEffect(romsDirectoryUri) {
            romsDirectoryUri.let { refreshFileList(it) }
        }

        val searchForGame: (String) -> Unit = { appId ->
            scope.launch {
                fileCreationResult = null
                if (appId.isBlank()) {
                    uiState = UiState.Failure("AppID cannot be empty.")
                    return@launch
                }
                uiState = UiState.Loading(appId)
                runCatching { fetchGameInfo(appId) }
                    .onSuccess { gameInfo -> uiState = UiState.Success(gameInfo) }
                    .onFailure { e -> uiState = UiState.Failure(e.message ?: "Unknown error") }
            }
        }

        suspend fun createSteamFile(gameInfo: GameInfo) {
            romsDirectoryUri.let { dirUri ->
                val existingContent = readSteamFileContent(context, dirUri, "${gameInfo.name}.steam")
                if (existingContent == null) {
                    val result = forceCreateSteamFile(context, dirUri, gameInfo)
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
            if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { url ->
                    extractAppIdFromUrl(url)?.let { appId ->
                        searchForGame(appId)
                    } ?: run {
                        uiState = UiState.Failure("No AppID in URL")
                    }
                }
                onIntentConsumed()
            }
        }

        val performDelete: (String) -> Unit = {
            fileName ->
            scope.launch {
                romsDirectoryUri.let {
                    val result = deleteSteamFile(context, it, fileName)
                    fileCreationResult = result
                    if(result.startsWith("Success")) refreshFileList(it)
                }
            }
        }

        val performMassDelete = {
            scope.launch {
                romsDirectoryUri.let { uri ->
                    for (fileName in selectedFiles) {
                        deleteSteamFile(context, uri, fileName)
                    }
                    fileCreationResult = "Deleted ${selectedFiles.size} files."
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
                            currentTheme = AppTheme.valueOf(prefs.getString(KEY_THEME, AppTheme.SYSTEM.name)!!),
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
                            }
                        )
                    } else {
                        MainScreen(
                            uiState = uiState,
                            onSearch = searchForGame,
                            steamFiles = steamFiles,
                            fileCreationResult = fileCreationResult,
                            onRefresh = { scope.launch { romsDirectoryUri.let { refreshFileList(it) } } },
                            onCreateFile = { gameInfo ->
                                scope.launch { createSteamFile(gameInfo) }
                            },
                            onFileClick = {
                                fileName ->
                                if (multiSelectMode) {
                                    selectedFiles = if (selectedFiles.contains(fileName)) selectedFiles - fileName else selectedFiles + fileName
                                } else {
                                    scope.launch {
                                        val content = romsDirectoryUri.let { readSteamFileContent(context, it, fileName) } ?: "Error: Directory URI is null."
                                        selectedFile = Pair(fileName, content)
                                    }
                                }
                            },
                            onFileLongClick = {
                                fileName ->
                                multiSelectMode = true
                                selectedFiles = selectedFiles + fileName
                            },
                            inMultiSelectMode = multiSelectMode,
                            selectedFiles = selectedFiles
                        )
                    }

                    if (!showSettings && !multiSelectMode) {
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
                        romsDirectoryUri.let {
                            val result = forceCreateSteamFile(context, it, overwriteInfo!!.gameInfo)
                            fileCreationResult = result
                            if (result.startsWith("Success")) refreshFileList(it)
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

    private fun extractAppIdFromUrl(url: String): String? {
        return url.substringAfter("/app/", "").split("/").firstOrNull()?.filter { it.isDigit() }
    }

    private suspend fun readSteamFileContent(context: Context, dirUri: Uri, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val dir = DocumentFile.fromTreeUri(context, dirUri)
                val file = dir?.findFile(fileName)

                if (file == null || !file.exists()) {
                    return@withContext null
                }

                context.contentResolver.openInputStream(file.uri)?.use {
                    it.bufferedReader().readText()
                } ?: "Error: Could not open file for reading."
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private suspend fun deleteSteamFile(context: Context, dirUri: Uri, fileName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val dir = DocumentFile.fromTreeUri(context, dirUri)
                    ?: return@withContext "Error: Failed to access directory."
                val file = dir.findFile(fileName)
                if (file?.delete() == true) {
                    "Successfully deleted $fileName"
                } else {
                    "Error: Could not delete $fileName"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private suspend fun forceCreateSteamFile(context: Context, dirUri: Uri, gameInfo: GameInfo): String {
        return withContext(Dispatchers.IO) {
            try {
                val dir = DocumentFile.fromTreeUri(context, dirUri)
                    ?: return@withContext "Error: Failed to access directory."

                val fileName = "${gameInfo.name}.steam"
                dir.findFile(fileName)?.delete()
                val file = dir.createFile("application/octet-stream", fileName)
                    ?: return@withContext "Error: Could not create file."

                context.contentResolver.openOutputStream(file.uri)?.use {
                    it.write(gameInfo.appId.toByteArray())
                }
                "Successfully created $fileName"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private suspend fun fetchGameInfo(appId: String): GameInfo {
        return withContext(Dispatchers.IO) {
            val client = HttpClient(Android)
            try {
                val apiUrl = "https://store.steampowered.com/api/appdetails?appids=$appId"
                val jsonResponse = client.get(apiUrl).bodyAsText()
                val root = JSONObject(jsonResponse).getJSONObject(appId)
                if (!root.getBoolean("success")) throw Exception("Invalid AppID. No game found on Steam.")
                val data = root.getJSONObject("data")
                val gameName = data.getString("name").replace(Regex("[/:*?\"<>|]"), "").trim()
                val headerImage = data.getString("header_image")
                GameInfo(appId, gameName, headerImage)
            } finally {
                client.close()
            }
        }
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
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }
    val themeOptions = AppTheme.entries.map { it.name }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Text("Theme", style = MaterialTheme.typography.titleMedium)
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
        Spacer(Modifier.height(24.dp))
        Text("File Operations", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm file deletions", modifier = Modifier.weight(1f))
            Switch(checked = confirmDelete, onCheckedChange = onConfirmDeleteChange)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Auto-create file on search success", modifier = Modifier.weight(1f))
            Switch(checked = autoCreateFile, onCheckedChange = onAutoCreateFileChange)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))
        Text("Current ROMs Directory", style = MaterialTheme.typography.titleMedium)
        Text(currentPath, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onChangeDirectory) { Text("Change Directory") }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
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
fun AboutDialog(versionName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Mjolnir") },
        text = { Text("Version $versionName") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
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
                Text("A file named \"${overwriteInfo.gameInfo.name}.steam\" already exists with a different AppID.")
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
                is UiState.Failure -> Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ManualInputUi(
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var manualAppId by remember { mutableStateOf("") }

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
    onFileClick: (String) -> Unit, onFileLongClick: (String) -> Unit,
    inMultiSelectMode: Boolean, selectedFiles: Set<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://steamdb.info/".toUri())) }) {
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
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://steamdb.info/".toUri())) },
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
            inMultiSelectMode = false, selectedFiles = emptySet(), onFileLongClick = {}
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
            inMultiSelectMode = false, selectedFiles = emptySet(), onFileLongClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    MjolnirTheme { SettingsScreen("/path/to/roms", AppTheme.SYSTEM, {}, {}, {}, true, {}, true, {}) }
}

@Preview(showBackground = true)
@Composable
fun SetupPreview() {
    MjolnirTheme { SetupScreen({}) }
}
