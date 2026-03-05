package xyz.blacksheep.mjolnir.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavController
import xyz.blacksheep.mjolnir.R

/**
 * The top-level settings landing page. Displays the primary categories of
 * configuration using a vertical list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(navController: NavController, onClose: () -> Unit) {
    val firstItemFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val discordInviteUrl = "https://discord.gg/SByZdew8Kw"

    LaunchedEffect(Unit) {
        firstItemFocus.requestFocus()
    }

    Scaffold(
        containerColor = settingsSurfaceColor(),
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
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
                Text("Tool Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Home,
                    title = "Mjolnir Home Settings",
                    subtitle = "Customize your DS-home environment",
                    modifier = Modifier.focusRequester(firstItemFocus)
                ) { navController.navigate("home_launcher") }
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Build,
                    title = "Steam File Settings",
                    subtitle = "Manage file creation and directory settings"
                ) { navController.navigate("tool_settings") }
            }
            item {
                HorizontalDivider()
            }
            item {
                Text("System", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Add,
                    title = "Appearance",
                    subtitle = "Adjust themes and colors"
                ) { navController.navigate("appearance") }
            }
            item {
                SettingsItem(
                    iconRes = R.drawable.ic_discord,
                    title = "Help & Community",
                    subtitle = "Get support, updates, and troubleshooting help"
                ) {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(discordInviteUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open Discord invite.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

/**
 * Reusable list item representing a clickable entry within the settings menu.
 */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsItem(
    iconRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.padding(end = 24.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
