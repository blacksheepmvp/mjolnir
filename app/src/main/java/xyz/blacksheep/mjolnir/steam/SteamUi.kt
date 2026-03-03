package xyz.blacksheep.mjolnir.steam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import xyz.blacksheep.mjolnir.utils.GameInfo
import xyz.blacksheep.mjolnir.utils.OverwriteInfo

@Composable
fun SetupScreen(onPickDirectory: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Steam File Generator Setup", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 16.dp))
            Text("To begin, please select the directory where you store your Steam ROM files.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPickDirectory) { Text("Choose Directory") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose) { Text("Skip") }
        }
    }
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
                Text("A file named '''${overwriteInfo.gameInfo.name}.steam''' already exists with a different AppID.")
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
fun ManualInputUi(onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
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

@Composable
fun SearchUi(
    uiState: SteamSearchState,
    fileCreationResult: String?,
    onCreateFile: (GameInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (uiState is SteamSearchState.Success) {
            AsyncImage(
                model = uiState.gameInfo.headerImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(16.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            when (uiState) {
                is SteamSearchState.Idle -> Text("Enter a Steam AppID to begin.")
                is SteamSearchState.Loading -> CircularProgressIndicator()
                is SteamSearchState.Success -> {
                    Text(
                        text = uiState.gameInfo.name,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onCreateFile(uiState.gameInfo) }) { Text("Save .steam File") }
                    fileCreationResult?.let {
                        val color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error else LocalContentColor.current
                        Text(it, modifier = Modifier.padding(top = 8.dp), color = color, textAlign = TextAlign.Center)
                    }
                }
                is SteamSearchState.Failure -> Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}
