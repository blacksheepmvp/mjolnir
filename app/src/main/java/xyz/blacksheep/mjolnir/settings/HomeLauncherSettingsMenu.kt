package xyz.blacksheep.mjolnir.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Display
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.navigation.NavController
import xyz.blacksheep.mjolnir.KEY_CUSTOM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.KEY_AUTO_BOOT_BOTH_HOME
import xyz.blacksheep.mjolnir.KEY_BOTH_AUTO_NOTHING_TO_HOME
import xyz.blacksheep.mjolnir.KEY_ENABLE_FOCUS_LOCK_WORKAROUND
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_USE_SYSTEM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.KEY_ACTIVE_GESTURE_CONFIG
import xyz.blacksheep.mjolnir.SafetyNetManager
import xyz.blacksheep.mjolnir.launchers.LauncherApp
import xyz.blacksheep.mjolnir.launchers.getLaunchableApps
import xyz.blacksheep.mjolnir.launchers.getPackageIcon
import xyz.blacksheep.mjolnir.launchers.rememberDrawablePainter
import xyz.blacksheep.mjolnir.model.MainScreen
import xyz.blacksheep.mjolnir.utils.DiagnosticsConfig
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import kotlin.math.roundToInt
import xyz.blacksheep.mjolnir.settings.settingsPrefs
import xyz.blacksheep.mjolnir.settings.GestureConfigStore

/**
 * Displays a configurable launcher slot for either the Top or Bottom screen.
 */
@Composable
fun AppSlotCard(
    modifier: Modifier = Modifier,
    app: LauncherApp?,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
) {
    val borderModifier = if (backgroundColor.alpha < 1f) {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            shape = shape
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .then(borderModifier)
            .clickable { onClick() },
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = if (backgroundColor.alpha < 1f) 0.dp else 3.dp,
        shadowElevation = if (backgroundColor.alpha < 1f) 0.dp else 4.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (app == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = app.label,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Configuration screen for customizing Mjolnir’s dual-screen launcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLauncherSettingsMenu(
    navController: NavController,
    topApp: String?,
    onTopAppChange: (String?) -> Unit,
    bottomApp: String?,
    onBottomAppChange: (String?) -> Unit,
    showAllApps: Boolean,
    onShowAllAppsChange: (Boolean) -> Unit,
    onSetDefaultHome: () -> Unit,
    onLaunchDualScreen: () -> Unit,
    mainScreen: MainScreen,
    onMainScreenChange: (MainScreen) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcherApps = remember(showAllApps) { getLaunchableApps(context, showAllApps) }
    val prefs = remember { context.settingsPrefs() }
    var safetyNetStatus by remember { mutableStateOf(SafetyNetManager.getSafetyNetStatus(context)) }
    val bottomDisplayId = safetyNetStatus.activeDisplayIds.firstOrNull { it != Display.DEFAULT_DISPLAY }
    val isDefaultHome = remember(context) { SafetyNetManager.isDefaultHome(context) }
    val topProtected = safetyNetStatus.runningDisplayIds.contains(Display.DEFAULT_DISPLAY)
    val bottomProtected = bottomDisplayId?.let { safetyNetStatus.runningDisplayIds.contains(it) } ?: false

    val isInterceptionActive = remember { prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false) }

    val displayedApps = remember(launcherApps, isInterceptionActive) {
        if (isInterceptionActive) launcherApps else launcherApps.filter { it.packageName != "NOTHING" }
    }

    var topExpanded by remember { mutableStateOf(false) }
    var bottomExpanded by remember { mutableStateOf(false) }

    val selectedTopApp = remember(topApp) { launcherApps.find { it.packageName == topApp } }
    val selectedBottomApp = remember(bottomApp) { launcherApps.find { it.packageName == bottomApp } }

    var activeGestureConfig by remember { mutableStateOf(GestureConfigStore.getActiveConfig(context)) }
    var presetRefreshTick by remember { mutableStateOf(0) }
    val gestureConfigs = remember(activeGestureConfig.fileName, presetRefreshTick) { GestureConfigStore.listConfigs(context) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(activeGestureConfig.name) }
    var dialogTarget by remember { mutableStateOf<GestureConfigStore.GestureConfig?>(null) }

    val diagnosticsEnabled = remember { mutableStateOf(DiagnosticsConfig.isEnabled(context)) }
    val maxLogSize = remember { mutableStateOf(DiagnosticsConfig.getMaxBytes(context)) }

    val focusLockWorkaroundState = remember { mutableStateOf(prefs.getBoolean(KEY_ENABLE_FOCUS_LOCK_WORKAROUND, false)) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // optional: check Settings.canDrawOverlays(context) here
    }

    val SPECIAL_HOME_APPS = remember { setOf("com.android.launcher3", "com.odin.odinlauncher") }

    var pendingPackage by remember { mutableStateOf<String?>(null) }

    fun setActiveGestureConfig(fileName: String) {
        GestureConfigStore.setActiveConfig(context, fileName)
        activeGestureConfig = GestureConfigStore.getActiveConfig(context, forceRefresh = true)
        prefs.edit().putString(KEY_ACTIVE_GESTURE_CONFIG, activeGestureConfig.fileName).apply()
        Toast.makeText(context, "${activeGestureConfig.name} is now active.", Toast.LENGTH_SHORT).show()
    }

    fun refreshActivePreset() {
        activeGestureConfig = GestureConfigStore.getActiveConfig(context, forceRefresh = true)
        renameValue = activeGestureConfig.name
    }

    fun isSystemDefaultHome(pkg: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == pkg
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400L)
        safetyNetStatus = SafetyNetManager.getSafetyNetStatus(context)
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (pendingPackage != null) {
                    pendingPackage = null
                }
                safetyNetStatus = SafetyNetManager.getSafetyNetStatus(context)
                activeGestureConfig = GestureConfigStore.getActiveConfig(context, forceRefresh = true)
                presetRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showRenameDialog && dialogTarget != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Rename Preset") },
            text = {
                Column {
                    Text("Enter a new name for this preset.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = dialogTarget ?: activeGestureConfig
                    activeGestureConfig = GestureConfigStore.renamePreset(context, target, renameValue)
                    refreshActivePreset()
                    presetRefreshTick++
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog && dialogTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete Preset") },
            text = { Text("This will permanently delete \"${dialogTarget?.name}\".") },
            confirmButton = {
                TextButton(onClick = {
                    val target = dialogTarget ?: activeGestureConfig
                    GestureConfigStore.deletePreset(context, target.fileName)
                    val active = GestureConfigStore.getActiveConfig(context, forceRefresh = true)
                    setActiveGestureConfig(active.fileName)
                    activeGestureConfig = GestureConfigStore.getActiveConfig(context, forceRefresh = true)
                    refreshActivePreset()
                    presetRefreshTick++
                    showDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (pendingPackage != null) {
        AlertDialog(
            onDismissRequest = {
                pendingPackage = null
            },
            title = { Text("Requires default home") },
            text = {
                val label = launcherApps.find { it.packageName == pendingPackage }?.label ?: pendingPackage ?: "App"
                Text("In order to use $label with Mjolnir, you must also set $label as your default home.")
            },
            confirmButton = {
                TextButton(onClick = { onSetDefaultHome() }) { Text("Set Default Home") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPackage = null }) { Text("Cancel") }
            }
        )
    }

    fun onAppSelectRequest(pkg: String, isTop: Boolean) {
        handleAppSelection(
            selectedAppPackage = pkg,
            isForTopSlot = isTop,
            currentTopApp = topApp,
            currentBottomApp = bottomApp,
            onTopAppChange = onTopAppChange,
            onBottomAppChange = onBottomAppChange,
            context = context
        )

        if (pkg in SPECIAL_HOME_APPS && !isSystemDefaultHome(pkg)) {
            pendingPackage = pkg
        }
    }

    Scaffold(
        containerColor = settingsSurfaceColor(),
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Home Launcher") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onShowAllAppsChange(!showAllApps) }) {
                            Text(
                                text = if (showAllApps) "Filter Apps" else "Remove App Filter",
                                color = if (showAllApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "Select Home Apps",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 0.dp, top = 4.dp, end = 0.dp, bottom = 12.dp)
                    )

                    val cardHeight = 140.dp
                    val topCardWidth = cardHeight * (16f / 9f)
                    val bottomCardWidth = cardHeight * (4f / 3f)

                    val mainTopLabel = if (mainScreen == MainScreen.TOP) "Main Screen" else ""
                    val mainBottomLabel = if (mainScreen == MainScreen.BOTTOM) "Main Screen" else ""

                    ConstraintLayout(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        val c1 = createGuidelineFromStart(0.0f)
                        val c2 = createGuidelineFromStart(0.33f)
                        val c3 = createGuidelineFromEnd(0.33f)
                        val c4 = createGuidelineFromEnd(0.0f)

                        val (
                            radioTop, cardTop, labelTop,
                            radioBottom, cardBottom, labelBottom
                        ) = createRefs()

                        RadioButton(
                            selected = mainScreen == MainScreen.TOP,
                            onClick = { onMainScreenChange(MainScreen.TOP) },
                            modifier = Modifier.constrainAs(radioTop) {
                                linkTo(start = c1, end = c2)
                                centerVerticallyTo(cardTop)
                            }
                        )

                        Box(
                            modifier = Modifier
                                .width(topCardWidth)
                                .height(cardHeight)
                                .constrainAs(cardTop) {
                                    start.linkTo(c2)
                                    end.linkTo(c3)
                                    top.linkTo(parent.top)
                                }
                        ) {
                            AppSlotCard(
                                app = selectedTopApp,
                                label = "Select Top Screen App",
                                onClick = { topExpanded = true }
                            )
                            SafetyNetIndicator(
                                isActive = topProtected,
                                isVisible = isDefaultHome,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            )

                            ExposedDropdownMenuBox(
                                expanded = topExpanded,
                                onExpandedChange = { topExpanded = it }
                            ) {
                                TextField(
                                    value = selectedTopApp?.label ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .menuAnchor()
                                        .alpha(0f),
                                    enabled = false,
                                    colors = ExposedDropdownMenuDefaults.textFieldColors(
                                        disabledIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent
                                    )
                                )

                                ExposedDropdownMenu(
                                    expanded = topExpanded,
                                    onDismissRequest = { topExpanded = false }
                                ) {
                                    displayedApps.forEach { app ->
                                        DropdownMenuItem(
                                            text = { Text(app.label) },
                                            leadingIcon = {
                                                Image(
                                                    painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            },
                                            onClick = {
                                                onAppSelectRequest(pkg = app.packageName, isTop = true)
                                                topExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = mainTopLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.constrainAs(labelTop) {
                                linkTo(start = c3, end = c4)
                                centerVerticallyTo(cardTop)
                            }
                        )

                        RadioButton(
                            selected = mainScreen == MainScreen.BOTTOM,
                            onClick = { onMainScreenChange(MainScreen.BOTTOM) },
                            modifier = Modifier.constrainAs(radioBottom) {
                                linkTo(start = c1, end = c2)
                                centerVerticallyTo(cardBottom)
                            }
                        )

                        Box(
                            modifier = Modifier
                                .width(bottomCardWidth)
                                .height(cardHeight)
                                .constrainAs(cardBottom) {
                                    start.linkTo(c2)
                                    end.linkTo(c3)
                                    top.linkTo(cardTop.bottom, margin = 24.dp)
                                }
                        ) {
                            AppSlotCard(
                                app = selectedBottomApp,
                                label = "Select Bottom Screen App",
                                onClick = { bottomExpanded = true }
                            )
                            SafetyNetIndicator(
                                isActive = bottomProtected,
                                isVisible = isDefaultHome,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            )

                            ExposedDropdownMenuBox(
                                expanded = bottomExpanded,
                                onExpandedChange = { bottomExpanded = it }
                            ) {
                                TextField(
                                    value = selectedBottomApp?.label ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .menuAnchor()
                                        .alpha(0f),
                                    enabled = false,
                                    colors = ExposedDropdownMenuDefaults.textFieldColors(
                                        disabledIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent
                                    )
                                )

                                ExposedDropdownMenu(
                                    expanded = bottomExpanded,
                                    onDismissRequest = { bottomExpanded = false }
                                ) {
                                    displayedApps
                                        .filterNot { it.packageName in SPECIAL_HOME_APPS }
                                        .forEach { app ->
                                            DropdownMenuItem(
                                                text = { Text(app.label) },
                                                leadingIcon = {
                                                    Image(
                                                        painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                },
                                                onClick = {
                                                    onAppSelectRequest(pkg = app.packageName, isTop = false)
                                                    bottomExpanded = false
                                                }
                                            )
                                        }
                                }
                            }
                        }

                        Text(
                            text = mainBottomLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.constrainAs(labelBottom) {
                                linkTo(start = c3, end = c4)
                                centerVerticallyTo(cardBottom)
                            }
                        )
                    }
                }
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Block,
                    title = "App Blacklist",
                    subtitle = "Hide apps from the launcher selection"
                ) { navController.navigate("app_blacklist") }
            }

            item {
                Text(
                    text = "Home Button Behavior",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                )
            }

            item {
                Divider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
            }

            item {
                val topPackage = selectedTopApp?.packageName
                val bottomPackage = selectedBottomApp?.packageName

                Text(
                    text = "Gesture Presets",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                GesturePresetCardRow(
                    presets = gestureConfigs,
                    activeFileName = activeGestureConfig.fileName,
                    topAppPackage = topPackage,
                    bottomAppPackage = bottomPackage,
                    onSelect = {
                        setActiveGestureConfig(it.fileName)
                        refreshActivePreset()
                    },
                    onEdit = {
                        setActiveGestureConfig(it.fileName)
                        refreshActivePreset()
                        navController.navigate("gesture_preset_edit")
                    },
                    onCopy = {
                        GestureConfigStore.createDraftFromPreset(context, it)
                        navController.navigate("gesture_preset_edit")
                    },
                    onShare = {
                        try {
                            val file = GestureConfigStore.getConfigFile(context, it.fileName)
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Mjolnir Gesture Preset: ${it.name}")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Preset"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onRename = {
                        dialogTarget = it
                        renameValue = it.name
                        showRenameDialog = true
                    },
                    onDelete = {
                        dialogTarget = it
                        showDeleteDialog = true
                    },
                    onNew = {
                        val created = GestureConfigStore.createPresetFromActive(context)
                        presetRefreshTick++
                        setActiveGestureConfig(created.fileName)
                        refreshActivePreset()
                        navController.navigate("gesture_preset_edit")
                    },
                    enableContextMenu = true
                )
            }

            item {
                val prefs = context.settingsPrefs()

                val systemDoubleTap = ViewConfiguration.getDoubleTapTimeout()

                var startOnBootAuto by remember {
                    mutableStateOf(prefs.getBoolean(KEY_AUTO_BOOT_BOTH_HOME, true))
                }

                var bothAutoNothingToHome by remember {
                    mutableStateOf(prefs.getBoolean(KEY_BOTH_AUTO_NOTHING_TO_HOME, true))
                }

                var useSystemDoubleTapDelay by remember {
                    mutableStateOf(prefs.getBoolean(KEY_USE_SYSTEM_DOUBLE_TAP_DELAY, true))
                }

                var customDoubleTapDelayMs by remember {
                    mutableStateOf(prefs.getInt(KEY_CUSTOM_DOUBLE_TAP_DELAY, systemDoubleTap))
                }

                fun updateUseSystemDoubleTapDelay(newValue: Boolean) {
                    useSystemDoubleTapDelay = newValue
                    prefs.edit().putBoolean(KEY_USE_SYSTEM_DOUBLE_TAP_DELAY, newValue).apply()
                }

                fun updateCustomDoubleTapDelay(newValue: Int) {
                    customDoubleTapDelayMs = newValue
                    prefs.edit().putInt(KEY_CUSTOM_DOUBLE_TAP_DELAY, newValue).apply()
                }

                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Start on boot (Advanced only)")
                            Text(
                                text = if (startOnBootAuto) "BOTH: Auto" else "BOTH: Home",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = startOnBootAuto,
                            onCheckedChange = {
                                startOnBootAuto = it
                                prefs.edit().putBoolean(KEY_AUTO_BOOT_BOTH_HOME, it).apply()
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Empty slot behavior (BOTH: Auto)")
                            Text(
                                text = if (bothAutoNothingToHome) "Launches Home" else "Does nothing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = bothAutoNothingToHome,
                            onCheckedChange = {
                                bothAutoNothingToHome = it
                                prefs.edit().putBoolean(KEY_BOTH_AUTO_NOTHING_TO_HOME, it).apply()
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Use system double-tap delay (${systemDoubleTap} ms)",
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = useSystemDoubleTapDelay,
                            onCheckedChange = { updateUseSystemDoubleTapDelay(it) }
                        )
                    }

                    if (!useSystemDoubleTapDelay) {
                        val minDelay = 100
                        val maxDelay = 500
                        val stepSize = 25
                        val steps = (maxDelay - minDelay) / stepSize - 1

                        Text(
                            text = "Custom double-tap delay (${customDoubleTapDelayMs} ms)",
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )

                        Slider(
                            value = customDoubleTapDelayMs.toFloat(),
                            onValueChange = { newValue ->
                                val stepped = ((newValue - minDelay) / stepSize)
                                    .roundToInt() * stepSize + minDelay
                                updateCustomDoubleTapDelay(stepped)
                            },
                            valueRange = minDelay.toFloat()..maxDelay.toFloat(),
                            steps = steps,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Config files",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "/Android/data/xyz.blacksheep.mjolnir/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "For troubleshooting only. Logs do not contain personal data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Capture diagnostics", style = MaterialTheme.typography.bodyLarge)
                        Text("Enable or disable logging", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = diagnosticsEnabled.value,
                        onCheckedChange = { isChecked ->
                            diagnosticsEnabled.value = isChecked
                            DiagnosticsConfig.setEnabled(context, isChecked)
                            if (isChecked) {
                                DiagnosticsLogger.logHeader(context)
                            }
                        }
                    )
                }
            }

            item {
                val logSizeOptions = listOf("0.5 MB", "1 MB", "2 MB", "5 MB")
                val logSizeMap = mapOf(
                    "0.5 MB" to 512 * 1024L,
                    "1 MB" to 1 * 1024 * 1024L,
                    "2 MB" to 2 * 1024 * 1024L,
                    "5 MB" to 5 * 1024 * 1024L
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text("Max log size", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "The log will trim itself when it exceeds this size",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                        space = 8.dp
                    ) {
                        logSizeOptions.forEachIndexed { index, label ->
                            val sizeInBytes = logSizeMap[label]!!
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = logSizeOptions.size),
                                onClick = {
                                    maxLogSize.value = sizeInBytes
                                    DiagnosticsConfig.setMaxBytes(context, sizeInBytes)
                                },
                                selected = maxLogSize.value == sizeInBytes,
                                enabled = diagnosticsEnabled.value
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { navController.navigate("diagnostics_summary") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    enabled = diagnosticsEnabled.value
                ) {
                    Text("View / Export Diagnostics")
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fix Focus-Lock Top input bug (experimental)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Workaround for controller input failure on Top screen. Briefly shows an invisible overlay to force focus reset.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = focusLockWorkaroundState.value,
                        onCheckedChange = { isChecked ->
                            if (isChecked && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                overlayPermissionLauncher.launch(intent)
                            }

                            focusLockWorkaroundState.value = isChecked
                            prefs.edit().putBoolean(KEY_ENABLE_FOCUS_LOCK_WORKAROUND, isChecked).apply()
                        }
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Button(onClick = onSetDefaultHome) {
                        Text("Set default home")
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyNetIndicator(isActive: Boolean, isVisible: Boolean, modifier: Modifier = Modifier) {
    if (!isVisible) return
    val color = if (isActive) Color(0xFF2ECC71) else Color.Black
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Handles logic for assigning an app to either the Top or Bottom launcher slot.
 */
private fun handleAppSelection(
    selectedAppPackage: String,
    isForTopSlot: Boolean,
    currentTopApp: String?,
    currentBottomApp: String?,
    onTopAppChange: (String?) -> Unit,
    onBottomAppChange: (String?) -> Unit,
    context: Context
) {
    val newTopApp: String?
    val newBottomApp: String?

    if (isForTopSlot) {
        newTopApp = if (selectedAppPackage == "NOTHING") null else selectedAppPackage
        newBottomApp = if (selectedAppPackage == currentBottomApp) currentTopApp else currentBottomApp
    } else {
        newBottomApp = if (selectedAppPackage == "NOTHING") null else selectedAppPackage
        newTopApp = if (selectedAppPackage == currentTopApp) currentBottomApp else currentTopApp
    }

    onTopAppChange(newTopApp)
    onBottomAppChange(newBottomApp)

    if (newTopApp == null && newBottomApp == null) {
        val prefs = context.settingsPrefs()
        if (prefs.getBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)) {
            prefs.edit().putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false).apply()
            Toast.makeText(
                context,
                "Home capture disabled: No apps selected.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
