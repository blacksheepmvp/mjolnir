package xyz.blacksheep.mjolnir.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Settings screen for configuring the Steam File Generator (Steam FG).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSettingsScreen(
    navController: NavController,
    confirmDelete: Boolean,
    onConfirmDeleteChange: (Boolean) -> Unit,
    autoCreateFile: Boolean,
    onAutoCreateFileChange: (Boolean) -> Unit,
    romsDirectory: String,
    onChangeDirectory: () -> Unit
) {
    Scaffold(
        containerColor = settingsSurfaceColor(),
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Steam File Generator") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) {
        innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Confirm file deletions", style = MaterialTheme.typography.bodyLarge)
                        Text("Show a confirmation dialog before deleting files", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = confirmDelete, onCheckedChange = onConfirmDeleteChange)
                }
            }
            item {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-create file on search success", style = MaterialTheme.typography.bodyLarge)
                        Text("Automatically generate the file on a single search result", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoCreateFile, onCheckedChange = onAutoCreateFileChange)
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text("Current ROMs Directory", style = MaterialTheme.typography.bodyLarge)
                    Text(romsDirectory, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Button(onClick = onChangeDirectory) { Text("Change Directory") }
                }
            }
        }
    }
}
