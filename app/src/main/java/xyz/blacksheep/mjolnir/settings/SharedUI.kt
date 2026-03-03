package xyz.blacksheep.mjolnir.settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HamburgerMenu(
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onQuitClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

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
                    onSettingsClick()
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("About") },
                onClick = {
                    onAboutClick()
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Quit") },
                onClick = {
                    onQuitClick()
                    menuExpanded = false
                }
            )
        }
    }
}

@Composable
fun HomeSetup(
    onGrantPermissionClick: () -> Unit,
    onEnableAccessibilityClick: () -> Unit,
    onEnableHomeInterceptionClick: () -> Unit,
    onTestNotificationClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Home Launcher Setup",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Step 1: Enable Notifications",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "This keeps Mjolnir alive and working reliably in the background.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGrantPermissionClick) {
                Text("Enable Notifications")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Step 2: Enable Accessibility",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "This allows Mjolnir to intercept the Home button and control your setup.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onEnableAccessibilityClick) {
                Text("Enable Accessibility")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Step 3: Enable Home Interception",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "This allows Mjolnir to perform its special action when you press the home button.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onEnableHomeInterceptionClick) {
                Text("Enable Home Interception")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onTestNotificationClick) {
                Text("Test Notification")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    }
}

@Composable
fun settingsSurfaceColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
}
