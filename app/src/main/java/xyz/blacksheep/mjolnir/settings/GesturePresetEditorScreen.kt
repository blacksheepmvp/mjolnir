package xyz.blacksheep.mjolnir.settings

import android.content.Intent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.home.actionLabel
import xyz.blacksheep.mjolnir.home.orderedActions
import xyz.blacksheep.mjolnir.onboarding.ActionIcon
import kotlin.math.roundToInt

@Composable
fun GesturePresetEditorScreen(
    title: String,
    presetName: String,
    topAppPackage: String?,
    bottomAppPackage: String?,
    config: GestureConfigStore.GestureConfig,
    onConfigChange: (GestureConfigStore.GestureConfig) -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val systemLongPress = ViewConfiguration.getLongPressTimeout()
    val minLongPress = 250
    val maxLongPress = 1000
    val stepLongPress = 25
    val longPressSteps = (maxLongPress - minLongPress) / stepLongPress - 1
    val displayedLongPress = if (config.longPressDelayMs > 0) config.longPressDelayMs else systemLongPress

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onBackground, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = presetName,
                        onValueChange = onNameChange,
                        singleLine = true,
                        label = { Text("Preset Name") },
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                val file = GestureConfigStore.getConfigFile(context, config.fileName)
                                if (!file.exists()) {
                                    Toast.makeText(context, "Save preset before sharing.", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                try {
                                    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Mjolnir Gesture Preset: ${config.name}")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Preset"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share preset", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    GestureHeader("Single Tap", 1)
                    GestureHeader("Double Tap", 2)
                    GestureHeader("Triple Tap", 3)
                    GestureHeader("Long Press", 0)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GestureDropdown(config.single, topAppPackage, bottomAppPackage) { onConfigChange(config.copy(single = it)) }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GestureDropdown(config.double, topAppPackage, bottomAppPackage) { onConfigChange(config.copy(double = it)) }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GestureDropdown(config.triple, topAppPackage, bottomAppPackage) { onConfigChange(config.copy(triple = it)) }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GestureDropdown(config.long, topAppPackage, bottomAppPackage) { onConfigChange(config.copy(long = it)) }
                }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Long-press delay (${displayedLongPress} ms)",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Slider(
                    value = displayedLongPress.toFloat().coerceIn(minLongPress.toFloat(), maxLongPress.toFloat()),
                    onValueChange = { newValue ->
                        val stepped = ((newValue - minLongPress) / stepLongPress).roundToInt() * stepLongPress + minLongPress
                        onConfigChange(config.copy(longPressDelayMs = stepped))
                    },
                    valueRange = minLongPress.toFloat()..maxLongPress.toFloat(),
                    steps = longPressSteps,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { Text("Cancel") }
                OutlinedButton(onClick = onSave, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp)) { Text("Save") }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun RowScope.GestureHeader(text: String, count: Int) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (count > 0) {
                repeat(count) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GestureDropdown(
    currentAction: Action,
    topAppPackage: String?,
    bottomAppPackage: String?,
    onActionSelected: (Action) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val topLabel = remember(topAppPackage) { topAppPackage?.let { pkg -> runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() } }
    val bottomLabel = remember(bottomAppPackage) { bottomAppPackage?.let { pkg -> runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() } }
    Box {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ActionIcon(action = currentAction, topApp = topAppPackage, bottomApp = bottomAppPackage, size = 32.dp)
            Text(
                text = actionLabel(currentAction, topLabel, bottomLabel),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { expanded = true }
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            orderedActions().forEach { action ->
                DropdownMenuItem(
                    onClick = {
                        onActionSelected(action)
                        expanded = false
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActionIcon(action = action, topApp = topAppPackage, bottomApp = bottomAppPackage, size = 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(actionLabel(action, topLabel, bottomLabel))
                        }
                    }
                )
            }
        }
    }
}
