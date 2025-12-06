package xyz.blacksheep.mjolnir.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun NoHomeSetupScreen(
    navController: NavController,
    onFinish: () -> Unit,
    isNavigating: Boolean,
    onNavigate: (() -> Unit) -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("About Skipping") },
            text = { Text("Mjolnir’s other tools, like the Steam File Generator, will still be available. You can run the home setup at any time from the main screen.") },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Skip Home Setup", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                Text("You can set up Mjolnir Home later from the main screen.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            }

            OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { onNavigate { onFinish() } }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Finish") }
        }
    }
}
