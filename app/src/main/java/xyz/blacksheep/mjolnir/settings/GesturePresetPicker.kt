package xyz.blacksheep.mjolnir.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent as AndroidKeyEvent
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.home.actionLabel
import xyz.blacksheep.mjolnir.onboarding.ActionIcon
import xyz.blacksheep.mjolnir.KEY_THEME
import xyz.blacksheep.mjolnir.model.AppTheme
import xyz.blacksheep.mjolnir.settings.settingsPrefs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GesturePresetCardRow(
    presets: List<GestureConfigStore.GestureConfig>,
    activeFileName: String,
    topAppPackage: String?,
    bottomAppPackage: String?,
    onSelect: (GestureConfigStore.GestureConfig) -> Unit,
    onEdit: (GestureConfigStore.GestureConfig) -> Unit,
    onCopy: (GestureConfigStore.GestureConfig) -> Unit,
    onShare: (GestureConfigStore.GestureConfig) -> Unit,
    onRename: (GestureConfigStore.GestureConfig) -> Unit,
    onDelete: (GestureConfigStore.GestureConfig) -> Unit,
    onNew: () -> Unit,
    enableContextMenu: Boolean = true
) {
    val activeKey = remember(activeFileName) { java.io.File(activeFileName).name.lowercase() }
    val focusTarget = remember(presets, activeFileName) {
        presets.firstOrNull { it.fileName == activeFileName } ?: presets.firstOrNull()
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(presets, key = { it.fileName }) { preset ->
            GesturePresetCard(
                preset = preset,
                isActive = java.io.File(preset.fileName).name.lowercase() == activeKey,
                topLabel = topAppPackage,
                bottomLabel = bottomAppPackage,
                modifier = Modifier,
                onSelect = { onSelect(preset) },
                onEdit = { onEdit(preset) },
                onCopy = { onCopy(preset) },
                onShare = { onShare(preset) },
                onRename = { onRename(preset) },
                onDelete = { onDelete(preset) },
                enableContextMenu = enableContextMenu
            )
        }

        item {
            NewPresetCard(onClick = onNew)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GesturePresetCard(
    preset: GestureConfigStore.GestureConfig,
    isActive: Boolean,
    topLabel: String?,
    bottomLabel: String?,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    enableContextMenu: Boolean
) {
    var menuOpen by remember { mutableStateOf(false) }
    var suppressClick by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val isReserved = GestureConfigStore.isReserved(preset.fileName)

    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val context = LocalContext.current
    val themeName = remember { context.settingsPrefs().getString(KEY_THEME, AppTheme.SYSTEM.name) }
    val isDark = when (runCatching { AppTheme.valueOf(themeName ?: AppTheme.SYSTEM.name) }.getOrNull() ?: AppTheme.SYSTEM) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val activeBrush = if (isDark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF0B1C38),
                Color(0xFF123060)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFF4C86E8),
                Color(0xFFCFE0FF)
            )
        )
    }
    val inactiveBrush = if (isDark) {
        Brush.linearGradient(listOf(Color.Black, Color.Black))
    } else {
        Brush.linearGradient(listOf(Color.White, Color.White))
    }


    Box {
        Surface(
            modifier = modifier
                .width(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .onFocusChanged { state -> isFocused = state.isFocused || state.hasFocus }
                .onPreviewKeyEvent { event ->
                    if (!enableContextMenu) return@onPreviewKeyEvent false
                    if (!isFocused) return@onPreviewKeyEvent false
                    val keyCode = event.nativeKeyEvent.keyCode
                    val isDown = event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN
                    if (isDown && (keyCode == AndroidKeyEvent.KEYCODE_BUTTON_X || keyCode == AndroidKeyEvent.KEYCODE_X)) {
                        suppressClick = true
                        menuOpen = true
                        return@onPreviewKeyEvent true
                    }
                    false
                }
                .combinedClickable(
                    onClick = {
                        if (suppressClick) {
                            suppressClick = false
                        } else {
                            onSelect()
                        }
                    },
                    onLongClick = if (enableContextMenu) ({
                        suppressClick = true
                        menuOpen = true
                    }) else null
                )
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .background(if (isActive) activeBrush else inactiveBrush),
            color = Color.Transparent
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isDark) Color.Black.copy(alpha = 0.35f)
                                    else Color.White.copy(alpha = 0.6f)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Color.White else Color(0xFF1A1A1A)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

        ActionRow("1", preset.single, topLabel, bottomLabel)
        ActionRow("2", preset.double, topLabel, bottomLabel)
        ActionRow("3", preset.triple, topLabel, bottomLabel)
        ActionRow("L", preset.long, topLabel, bottomLabel)
            }
        }

        if (enableContextMenu) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                DropdownMenuItem(text = { Text("Select") }, onClick = { onSelect(); menuOpen = false })
                DropdownMenuItem(text = { Text("Edit") }, onClick = { onEdit(); menuOpen = false }, enabled = !isReserved)
                DropdownMenuItem(text = { Text("Copy") }, onClick = { onCopy(); menuOpen = false })
                DropdownMenuItem(text = { Text("Share") }, onClick = { onShare(); menuOpen = false })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { onRename(); menuOpen = false }, enabled = !isReserved)
                DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(); menuOpen = false }, enabled = !isReserved)
            }
        }
    }
}

@Composable
private fun ActionRow(
    prefix: String,
    action: Action,
    topAppPackage: String?,
    bottomAppPackage: String?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val topLabel = remember(topAppPackage) { topAppPackage?.let { pkg -> runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() } }
    val bottomLabel = remember(bottomAppPackage) { bottomAppPackage?.let { pkg -> runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$prefix:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        ActionIcon(action = action, topApp = topAppPackage, bottomApp = bottomAppPackage, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = actionLabel(action, topLabel, bottomLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewPresetCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = {}),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .height(92.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text("New…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
