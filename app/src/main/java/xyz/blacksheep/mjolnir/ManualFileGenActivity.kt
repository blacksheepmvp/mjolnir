package xyz.blacksheep.mjolnir

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.settings.AppTheme
import xyz.blacksheep.mjolnir.ui.theme.MjolnirTheme
import xyz.blacksheep.mjolnir.utils.SteamTool

@OptIn(ExperimentalMaterial3Api::class)
class ManualFileGenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setContent {
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
                ManualEntryScreen(
                    defaultRomPath = prefs.getString(KEY_ROM_DIR_URI, "") ?: "",
                    onClose = { finish() } // Close the activity when ManualEntryScreen requests close
                )
            }
        }
    }

    @Suppress("AssignedValueIsNeverRead")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ManualEntryScreen(
        defaultRomPath: String,
        onClose: () -> Unit,
    ) {
        var gameTitle by rememberSaveable { mutableStateOf("") }
        var fileContents by rememberSaveable { mutableStateOf("") }
        var fileExtension by rememberSaveable { mutableStateOf(".steam") }
        var useDefaultDir by rememberSaveable { mutableStateOf(true) }
        var showInfoDialog by rememberSaveable { mutableStateOf(false) }
        var customDir by rememberSaveable { mutableStateOf<String?>(null) }

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        val isExtensionValid = rememberSaveable(fileExtension) { fileExtension.matches(Regex("^\\.[a-zA-Z0-9]+$")) }
        val isSaveEnabled = gameTitle.isNotBlank() && isExtensionValid && (useDefaultDir || customDir != null)

        val directoryPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                uri?.let { customDir = it.toString() }
            }
        )

        BackHandler { onClose() }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("About File Extensions") },
                text = { Text("The file extension determines how your frontend will treat the file. For most cases, '.steam' is the correct choice. However, you can use other extensions like '.iso', '.txt', or any other extension your frontend supports.") },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    TopAppBar(
                        title = { Text("Manual File Generator") },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val sanitizedTitle = gameTitle.replace(Regex("[\\/:*? \" <>|]"), "")
                                        val directory = if (useDefaultDir) defaultRomPath.toUri() else customDir?.toUri()
                                        val extension = fileExtension.removePrefix(".")

                                        if (directory != null) {
                                            val result = SteamTool.createSteamFileFromDetails(context, directory, sanitizedTitle, fileContents, extension)
                                            if (result.startsWith("Success")) {
                                                Toast.makeText(context, "$sanitizedTitle.$extension created.", Toast.LENGTH_SHORT).show()
                                                gameTitle = ""
                                                fileContents = ""
                                            } else {
                                                Toast.makeText(context, "Not created: $sanitizedTitle.$extension. Error: $result", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Error: No directory selected.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = isSaveEnabled
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Save")
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 🛠 Modified
                OutlinedTextField(
                    value = gameTitle,
                    onValueChange = { gameTitle = it },
                    label = { Text("Game Title") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = gameTitle.isBlank(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                // 🛠 Modified
                OutlinedTextField(
                    value = fileContents,
                    onValueChange = { fileContents = it },
                    label = { Text("Contents") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Default,
                    )
                )

                OutlinedTextField(
                    value = fileExtension,
                    onValueChange = { fileExtension = it },
                    label = { Text("File Extension") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = !isExtensionValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    trailingIcon = {
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Info")
                        }
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use default ROMs directory", modifier = Modifier.weight(1f))
                    Switch(checked = useDefaultDir, onCheckedChange = { useDefaultDir = it })
                }

                if (useDefaultDir) {
                    Text(text = defaultRomPath, style = MaterialTheme.typography.bodySmall)
                } else {
                    Button(onClick = { directoryPickerLauncher.launch(null) }) {
                        Text(customDir ?: "Choose Directory")
                    }
                }
            }
        }
    }

    @Composable
    private fun NoExtractOutlinedTextField(
        value: String,
        onValueChange: (String) -> Unit,
        label: @Composable (() -> Unit)? = null,
        modifier: Modifier = Modifier,
        isError: Boolean = false,
        singleLine: Boolean = false,
        minLines: Int = 1,
        keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
        keyboardActions: KeyboardActions = KeyboardActions.Default,
        trailingIcon: @Composable (() -> Unit)? = null,
    ) {
        val view = LocalView.current

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = modifier.onGloballyPositioned {
                // Set the IME flag directly when the native EditText is created
                view.findFocus()?.let { focusedView ->
                    if (focusedView is android.widget.EditText) {
                        focusedView.imeOptions = focusedView.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    }
                }
            },
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            trailingIcon = trailingIcon
        )
    }
}
