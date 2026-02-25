package xyz.blacksheep.mjolnir.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.core.content.FileProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_CUSTOM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.KEY_DSS_AUTO_STITCH
import xyz.blacksheep.mjolnir.KEY_ENABLE_FOCUS_LOCK_WORKAROUND
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_LAUNCH_FAILURE_COUNT
import xyz.blacksheep.mjolnir.KEY_ONBOARDING_COMPLETE
import xyz.blacksheep.mjolnir.KEY_AUTO_BOOT_BOTH_HOME
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.KEY_USE_SYSTEM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.KEY_ACTIVE_GESTURE_CONFIG
import xyz.blacksheep.mjolnir.KEY_THEME
import xyz.blacksheep.mjolnir.model.AppTheme
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.home.actionLabel
import xyz.blacksheep.mjolnir.home.orderedActions
import xyz.blacksheep.mjolnir.launchers.rememberDrawablePainter
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.settings.GestureConfigStore
import xyz.blacksheep.mjolnir.settings.GesturePresetCardRow
import kotlin.math.roundToInt
import xyz.blacksheep.mjolnir.settings.settingsPrefs

@Composable
fun AdvancedPermissionScreen(navController: NavController, isNavigating: Boolean, onNavigate: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else { true }
        )
    }
    var showInfoDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasPermission = isGranted }
    )

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onBackground
    )
    val grantFocusRequester = remember { FocusRequester() }
    val nextFocusRequester = remember { FocusRequester() }
    val canNext = !isNavigating && hasPermission

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            nextFocusRequester.requestFocus()
        } else {
            grantFocusRequester.requestFocus()
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Why Notifications?") },
            text = { Text("Mjolnir uses a persistent notification to keep its Home button service running. It also provides feedback for features like DualShot.") },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } }
        )
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Notification Permission", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("The background service requires a persistent notification.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(32.dp))

                if (hasPermission) {
                    Text("Permission Granted!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                } else {
                    Button(
                        onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(0.7f).focusRequester(grantFocusRequester).focusable(),
                        colors = buttonColors,
                        enabled = !isNavigating
                    ) { Text("Grant Permission") }
                }
            }

            OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(
                onClick = { onNavigate { navController.navigate("advanced_accessibility") } }, 
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp).focusRequester(nextFocusRequester).focusable(), 
                enabled = canNext
            ) { Text("Next") }
        }
    }
}

@Composable
fun AdvancedAccessibilityScreen(navController: NavController, isNavigating: Boolean, onNavigate: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun isAccessibilityEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    var isEnabled by remember { mutableStateOf(isAccessibilityEnabled()) }
    var showInfoDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { isEnabled = isAccessibilityEnabled() } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onBackground
    )
    val enableFocusRequester = remember { FocusRequester() }
    val nextFocusRequester = remember { FocusRequester() }
    val canNext = !isNavigating && isEnabled

    LaunchedEffect(isEnabled) {
        if (isEnabled) {
            nextFocusRequester.requestFocus()
        } else {
            enableFocusRequester.requestFocus()
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Why Accessibility?") },
            text = { Text("This service lets Mjolnir:\n" + "• Detect Home button presses for gestures.\n" + "• Help the DualShot feature work correctly.") },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Accessibility Service", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Required for Home gestures and DualShot functionality.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(32.dp))

                if (isEnabled) {
                    Text("Service Enabled!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                } else {
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth(0.7f).focusRequester(enableFocusRequester).focusable(),
                        colors = buttonColors,
                        enabled = !isNavigating
                    ) { Text("Enable Mjolnir Service") }
                }
            }

            OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(
                onClick = { onNavigate { navController.navigate("advanced_home_selection") } }, 
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp).focusRequester(nextFocusRequester).focusable(), 
                enabled = canNext
            ) { Text("Next") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedHomeSelectionScreen(navController: NavController, viewModel: OnboardingViewModel, isNavigating: Boolean, onNavigate: (() -> Unit) -> Unit) {
    val state by viewModel.uiState
    HomeSelectionUI(
        topAppPackage = state.topAppPackage,
        bottomAppPackage = state.bottomAppPackage,
        onTopAppSelected = { viewModel.setTopApp(it) },
        onBottomAppSelected = { viewModel.setBottomApp(it) },
        onNext = { onNavigate { navController.navigate("advanced_gestures") } },
        onPrev = { onNavigate { navController.popBackStack() } },
        isBasicFlow = false,
        onSwitchToAdvanced = {},
        isNavigating = isNavigating,
        // FIX: Pass the blacklist handler here too!
        onManageBlacklist = { 
            DiagnosticsLogger.logEvent("Onboarding", "BLACKLIST_CLICKED_ADV", "Navigating to app_blacklist", navController.context)
            onNavigate { navController.navigate("app_blacklist") } 
        }
    )
}

@Composable
fun AdvancedGestureScreen(navController: NavController, viewModel: OnboardingViewModel, isNavigating: Boolean, onNavigate: (() -> Unit) -> Unit) {
    val state by viewModel.uiState
    val context = LocalContext.current
    val prefs = remember { context.settingsPrefs() }
    val themeName = remember { prefs.getString(KEY_THEME, AppTheme.SYSTEM.name) }
    val isDarkTheme = when (runCatching { AppTheme.valueOf(themeName ?: AppTheme.SYSTEM.name) }.getOrNull() ?: AppTheme.SYSTEM) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val backgroundBrush = if (isDarkTheme) {
        Brush.verticalGradient(listOf(Color(0xFF0B1C38), Color(0xFF05080F)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF4C86E8), Color(0xFFE9F1FF)))
    }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<GestureConfigStore.GestureConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<GestureConfigStore.GestureConfig?>(null) }
    var presetRefreshTick by remember { mutableStateOf(0) }

    // --- DOUBLE-TAP DELAY SETTINGS (Re-added) ---
    val systemDoubleTap = ViewConfiguration.getDoubleTapTimeout()

    var useSystemDoubleTapDelay by remember {
        mutableStateOf(prefs.getBoolean(KEY_USE_SYSTEM_DOUBLE_TAP_DELAY, true))
    }

    var customDoubleTapDelayMs by remember {
        mutableStateOf(prefs.getInt(KEY_CUSTOM_DOUBLE_TAP_DELAY, systemDoubleTap))
    }

    fun updateUseSystemDoubleTapDelay(newValue: Boolean) {
        useSystemDoubleTapDelay = newValue
        prefs.edit { putBoolean(KEY_USE_SYSTEM_DOUBLE_TAP_DELAY, newValue) }
    }

    fun updateCustomDoubleTapDelay(newValue: Int) {
        customDoubleTapDelayMs = newValue
        prefs.edit { putInt(KEY_CUSTOM_DOUBLE_TAP_DELAY, newValue) }
    }

    val topLabel = remember(state.topAppPackage) { getAppName(context, state.topAppPackage) }
    val bottomLabel = remember(state.bottomAppPackage) { getAppName(context, state.bottomAppPackage) }
    val defaultHomePkg = remember { getCurrentDefaultHomePackage(context) }
    val defaultHomeLabel = remember(defaultHomePkg) { getAppName(context, defaultHomePkg) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Actions") },
            text = {
                Text("Recent apps: opens the app switcher\n" +
                        "Default home: sends the home button to $defaultHomeLabel\n" +
                        "Top screen home: opens $topLabel on the top screen\n" +
                        "Bottom screen home: opens $bottomLabel on the bottom screen\n" +
                        "Both screens home*: executes both Top screen home and Bottom screen home.\n\n" +
                        "* Note: this is the default Single Tap behavior for Basic")
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    if (showRenameDialog && renameTarget != null) {
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
                    val target = renameTarget ?: return@TextButton
                    val updated = GestureConfigStore.renamePreset(context, target, renameValue)
                    viewModel.setGesturePreset(updated.fileName)
                    presetRefreshTick++
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete Preset") },
            text = { Text("This will permanently delete \"${deleteTarget?.name}\".") },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTarget ?: return@TextButton
                    GestureConfigStore.deletePreset(context, target.fileName)
                    val active = GestureConfigStore.getActiveConfig(context, forceRefresh = true)
                    viewModel.setGesturePreset(active.fileName)
                    presetRefreshTick++
                    showDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
        ) {
            // Content column with scroll and increased padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // Use 16.dp standard padding, we will rely on "density" now instead of massive padding
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Home Button Behavior", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                
                Spacer(modifier = Modifier.height(32.dp))

                val gestureConfigs = remember(state.gesturePresetFile, presetRefreshTick) { GestureConfigStore.listConfigs(context) }
                val topPackage = state.topAppPackage
                val bottomPackage = state.bottomAppPackage

                Text(text = "Gesture Presets", modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium)

                GesturePresetCardRow(
                    presets = gestureConfigs,
                    activeFileName = state.gesturePresetFile,
                    topAppPackage = topPackage,
                    bottomAppPackage = bottomPackage,
                    onSelect = {
                        viewModel.setGesturePreset(it.fileName)
                        Toast.makeText(context, "${it.name} is now active.", Toast.LENGTH_SHORT).show()
                    },
                    onEdit = {
                        viewModel.setGesturePreset(it.fileName)
                        onNavigate { navController.navigate("advanced_gesture_edit") }
                    },
                    onCopy = {
                        GestureConfigStore.createDraftFromPreset(context, it)
                        onNavigate { navController.navigate("advanced_gesture_edit") }
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
                        renameTarget = it
                        renameValue = it.name
                        showRenameDialog = true
                    },
                    onDelete = {
                        deleteTarget = it
                        showDeleteDialog = true
                    },
                    onNew = {
                        val created = GestureConfigStore.createPresetFromActive(context)
                        viewModel.setGesturePreset(created.fileName)
                        presetRefreshTick++
                        onNavigate { navController.navigate("advanced_gesture_edit") }
                    },
                    enableContextMenu = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Spacer(modifier = Modifier.height(24.dp))

                // --- GRID LAYOUT: 4 Cols, 2 Rows ---
                // Row 1: Headers
                Spacer(modifier = Modifier.height(32.dp))

                // --- Double Tap Delay Controls ---
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !isNavigating) { updateUseSystemDoubleTapDelay(!useSystemDoubleTapDelay) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toggle on LEFT
                    Switch(
                        checked = useSystemDoubleTapDelay,
                        onCheckedChange = { updateUseSystemDoubleTapDelay(it) },
                        enabled = !isNavigating
                    )
                    Spacer(Modifier.width(16.dp))
                    // Text on RIGHT
                    Text(
                        text = "Use system double-tap delay (${systemDoubleTap} ms)",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!useSystemDoubleTapDelay) {
                    val minDelay = 100
                    val maxDelay = 500
                    val stepSize = 25
                    val steps = (maxDelay - minDelay) / stepSize - 1

                    Text(
                        text = "Custom delay (${customDoubleTapDelayMs} ms)",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Slider(
                        value = customDoubleTapDelayMs.toFloat(),
                        onValueChange = { newValue ->
                            val stepped = ((newValue - minDelay) / stepSize).roundToInt() * stepSize + minDelay
                            updateCustomDoubleTapDelay(stepped)
                        },
                        valueRange = minDelay.toFloat()..maxDelay.toFloat(),
                        steps = steps,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isNavigating
                    )
                }
            }
            
            // Buttons fixed at bottom, outside scroll
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
                IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedButton(onClick = { onNavigate { viewModel.setHomeInterception(true); navController.navigate("advanced_start_on_boot") } }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Next") }
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
fun AdvancedStartOnBootScreen(navController: NavController, viewModel: OnboardingViewModel, isNavigating: Boolean, onNavigate: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.settingsPrefs() }
    var startOnBootAuto by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_BOOT_BOTH_HOME, true)) }
    val canNavigate = !isNavigating
    val switchFocusRequester = remember { FocusRequester() }
    val pm = context.packageManager
    val state by viewModel.uiState
    val topPkg = remember(state.topAppPackage) { state.topAppPackage ?: prefs.getString(KEY_TOP_APP, null) }
    val bottomPkg = remember(state.bottomAppPackage) { state.bottomAppPackage ?: prefs.getString(KEY_BOTTOM_APP, null) }

    fun resolveAppLabel(pkg: String?): String {
        if (pkg.isNullOrBlank()) return "<None>"
        return runCatching {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrNull() ?: pkg
    }

    fun resolveAppIcon(pkg: String?): android.graphics.drawable.Drawable? {
        if (pkg.isNullOrBlank()) return null
        return runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
    }

    val defaultHomePkg = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo: ResolveInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        resolveInfo?.activityInfo?.packageName
    }

    val topMissing = startOnBootAuto && topPkg.isNullOrBlank()
    val bottomMissing = startOnBootAuto && bottomPkg.isNullOrBlank()
    val topLabel = if (startOnBootAuto) resolveAppLabel(topPkg ?: defaultHomePkg) else resolveAppLabel(defaultHomePkg)
    val bottomLabel = if (startOnBootAuto) resolveAppLabel(bottomPkg ?: defaultHomePkg) else resolveAppLabel(defaultHomePkg)
    val topIcon = if (startOnBootAuto) resolveAppIcon(topPkg ?: defaultHomePkg) else resolveAppIcon(defaultHomePkg)
    val bottomIcon = if (startOnBootAuto) resolveAppIcon(bottomPkg ?: defaultHomePkg) else resolveAppIcon(defaultHomePkg)

    LaunchedEffect(Unit) {
        switchFocusRequester.requestFocus()
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Start on Boot", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))

                val cardHeight = 140.dp
                val topCardWidth = cardHeight * (16f / 9f)
                val bottomCardWidth = cardHeight * (4f / 3f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column A spacer to center Column B
                    Box(modifier = Modifier.width(120.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IndicatorDisplayBox(
                            title = null,
                            label = topLabel,
                            icon = topIcon,
                            faded = topMissing,
                            width = topCardWidth,
                            height = cardHeight
                        )
                        IndicatorDisplayBox(
                            title = null,
                            label = bottomLabel,
                            icon = bottomIcon,
                            faded = !startOnBootAuto || bottomMissing,
                            width = bottomCardWidth,
                            height = cardHeight
                        )
                    }

                    Box(
                        modifier = Modifier.width(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Switch(
                            checked = startOnBootAuto,
                            onCheckedChange = {
                                startOnBootAuto = it
                                prefs.edit().putBoolean(KEY_AUTO_BOOT_BOTH_HOME, it).apply()
                            },
                            enabled = !isNavigating,
                            modifier = Modifier.focusRequester(switchFocusRequester).focusable()
                        )
                    }
                }
            }

            OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            OutlinedButton(onClick = { onNavigate { navController.navigate("advanced_dss") } }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Next") }
        }
    }
}

@Composable
private fun IndicatorDisplayBox(
    title: String?,
    label: String,
    icon: android.graphics.drawable.Drawable?,
    faded: Boolean,
    width: Dp,
    height: Dp
) {
    val shape = MaterialTheme.shapes.large
    val alpha = if (faded) 0.55f else 1f
    Surface(
        modifier = Modifier
            .width(width)
            .height(height),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!title.isNullOrBlank()) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.height(4.dp))
            }
            if (icon != null) {
                Image(
                    painter = rememberDrawablePainter(icon),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).alpha(alpha)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RowScope.GestureHeader(text: String, count: Int) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        // Icons Row moved ABOVE Text
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (count > 0) {
                repeat(count) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Long Press: Home + TouchApp (Finger)
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Text moved BELOW Icons
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GestureDropdown(currentAction: Action, isNavigating: Boolean, topApp: String?, bottomApp: String?, onActionSelected: (Action) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val topLabel = remember(topApp) { topApp?.let { pkg -> runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() } }
    val bottomLabel = remember(bottomApp) { bottomApp?.let { pkg -> runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() } }
    Box(modifier = Modifier.clickable(enabled = !isNavigating) { expanded = true }.padding(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Current Selection Icon
            ActionIcon(action = currentAction, topApp = topApp, bottomApp = bottomApp, size = 32.dp)
            
            Text(
                text = actionLabel(currentAction, topLabel, bottomLabel),
                color = MaterialTheme.colorScheme.primary, 
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        
        DropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            orderedActions().forEach { action ->
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActionIcon(action = action, topApp = topApp, bottomApp = bottomApp, size = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(actionLabel(action, topLabel, bottomLabel))
                        }
                    }, 
                    onClick = { onActionSelected(action); expanded = false }
                )
            }
        }
    }
}


@Composable
fun ActionIcon(action: Action, topApp: String?, bottomApp: String?, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val iconModifier = Modifier.size(size)
    
    when(action) {
        Action.TOP_HOME -> {
            if (topApp != null) {
                val pm = context.packageManager
                val drawable = remember(topApp) { try { pm.getApplicationIcon(topApp) } catch(e: Exception) { null } }
                Image(painter = rememberDrawablePainter(drawable), contentDescription = null, modifier = iconModifier)
            } else {
                Icon(Icons.Default.Home, contentDescription = null, modifier = iconModifier)
            }
        }
        Action.BOTTOM_HOME -> {
            if (bottomApp != null) {
                val pm = context.packageManager
                val drawable = remember(bottomApp) { try { pm.getApplicationIcon(bottomApp) } catch(e: Exception) { null } }
                Image(painter = rememberDrawablePainter(drawable), contentDescription = null, modifier = iconModifier)
            } else {
                Icon(Icons.Default.Home, contentDescription = null, modifier = iconModifier)
            }
        }
        Action.BOTH_HOME -> {
            // Use app icon or generic home
            val drawable = remember { try { context.packageManager.getApplicationIcon(context.packageName) } catch(e: Exception) { null } }
            if (drawable != null) {
                Image(painter = rememberDrawablePainter(drawable), contentDescription = null, modifier = iconModifier)
            } else {
                Icon(Icons.Default.Home, contentDescription = null, modifier = iconModifier)
            }
        }
        Action.DEFAULT_HOME -> {
            // Always use generic Home icon
            Icon(Icons.Default.Home, contentDescription = null, modifier = iconModifier)
        }
        Action.TOP_HOME_DEFAULT -> Icon(Icons.Default.Home, contentDescription = null, modifier = iconModifier)
        Action.BOTTOM_HOME_DEFAULT -> Icon(Icons.Default.Home, contentDescription = null, modifier = iconModifier)
        Action.BOTH_HOME_DEFAULT -> Icon(Icons.Default.Home, contentDescription = null, modifier = iconModifier)
        Action.FOCUS_AUTO -> Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = iconModifier)
        Action.FOCUS_TOP_APP -> {
            if (topApp != null) {
                val pm = context.packageManager
                val drawable = remember(topApp) { try { pm.getApplicationIcon(topApp) } catch(e: Exception) { null } }
                Image(painter = rememberDrawablePainter(drawable), contentDescription = null, modifier = iconModifier)
            } else {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = iconModifier)
            }
        }
        Action.APP_SWITCH -> Icon(Icons.Default.ViewCarousel, contentDescription = null, modifier = iconModifier)
        Action.NONE -> Icon(Icons.Default.Close, contentDescription = null, modifier = iconModifier)
    }
}

@Composable
fun AdvancedDssScreen(navController: NavController, viewModel: OnboardingViewModel, isNavigating: Boolean, onNavigate: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState
    var showInfoDialog by remember { mutableStateOf(false) }
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.WRITE_EXTERNAL_STORAGE
    val toggleFocusRequester = remember { FocusRequester() }
    val canNavigate = !isNavigating

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) viewModel.setDssAutoStitch(true) }

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onBackground
    )

    fun toggleDss() {
        if (state.dssAutoStitch) {
            viewModel.setDssAutoStitch(false)
        } else {
            if (ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED) viewModel.setDssAutoStitch(true) else permissionLauncher.launch(storagePermission)
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Using DualShot") },text = {
                Text("To use DualShot:\n" +
                        "1. Make sure the DualShot tile is Active\n" +
                        "2. Use the Screenshot: Bottom tile\n\n" +
                        "Note: Tiles can be found in your notifications bar. You may need to edit your quick tiles and reposition them.")
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    LaunchedEffect(Unit) {
        toggleFocusRequester.requestFocus()
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(modifier = Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // CHANGED: Title
                Text("Dual Screenshots", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                
                // CHANGED: Subtext
                Text(
                    "When you use 'Screenshot: bottom' while DualShot is active, Mjolnir will generate a dual-screen-screenshot.", 
                    textAlign = TextAlign.Center, 
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                // CHANGED: Status Text with Styling
                val statusText = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Normal)) {
                        append("DualShot: ")
                    }
                    if (state.dssAutoStitch) {
                        withStyle(style = SpanStyle(color = Color.Green, fontWeight = FontWeight.Bold)) {
                            append("Active")
                        }
                    } else {
                        withStyle(style = SpanStyle(color = Color.Gray, fontWeight = FontWeight.Bold)) {
                            append("Inactive")
                        }
                    }
                }
                
                Text(text = statusText, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { toggleDss() }, 
                    modifier = Modifier.fillMaxWidth(0.7f).focusRequester(toggleFocusRequester).focusable(),
                    colors = buttonColors,
                    enabled = !isNavigating
                ) { Text(if (state.dssAutoStitch) "Disable DualShot" else "Enable DualShot") }
            }
            
                OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
                IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedButton(onClick = { onNavigate { navController.navigate("advanced_set_default") } }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Next") }
        }
    }
}

@Composable
fun AdvancedSetDefaultHomeScreen(navController: NavController, viewModel: OnboardingViewModel, onFinish: () -> Unit, isNavigating: Boolean, onNavigate: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState
    var showInfoDialog by remember { mutableStateOf(false) }
    var currentHomePkg by remember { mutableStateOf<String?>(null) }
    var currentHomeLabel by remember { mutableStateOf("Checking...") }
    val lifecycleOwner = LocalLifecycleOwner.current
    val setDefaultFocusRequester = remember { FocusRequester() }
    val finishFocusRequester = remember { FocusRequester() }

    // --- REVISED LOGIC BLOCK ---
    val QUICKSTEP_PKG = "com.android.launcher3"
    val SPECIAL_HOME_APPS = remember { setOf(QUICKSTEP_PKG, "com.odin.odinlauncher") }

    // 1. Determine if a specific default is REQUIRED
    val requiredDefaultPkg: String? = remember(state.topAppPackage, state.bottomAppPackage) {
        when {
            state.bottomAppPackage == null -> QUICKSTEP_PKG
            state.topAppPackage in SPECIAL_HOME_APPS -> state.topAppPackage
            else -> null // No specific default is required
        }
    }

    // 2. Check if the current default matches the requirement (if one exists)
    val requirementMet = (requiredDefaultPkg == null) || (currentHomePkg == requiredDefaultPkg)

    // 3. Define UI strings and states
    val headingText = if (requirementMet && requiredDefaultPkg != null) "All Done!" else "Default Home App"
    val showSetDefaultButton = !requirementMet || requiredDefaultPkg == null
    val finishButtonText = if (requirementMet) "Finish" else "Skip & Finish"
    val isFinishButtonEnabled = !isNavigating && requirementMet

    val homePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Result doesn't matter, ON_RESUME will handle the state check.
    }

    // This effect will AUTO-SAVE only when a specific requirement is met.
    LaunchedEffect(requirementMet) {
        if (requirementMet && requiredDefaultPkg != null) {
            DiagnosticsLogger.logEvent("Onboarding", "AUTO_SAVE_VALID_CONFIG", "Committing Advanced prefs", context)
            val finalInterception = !(state.topAppPackage == null && state.bottomAppPackage == null) && state.homeInterceptionActive
            val prefs = context.settingsPrefs()
            prefs.edit().apply {
                putString(KEY_TOP_APP, state.topAppPackage)
                putString(KEY_BOTTOM_APP, state.bottomAppPackage)
                putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, finalInterception)
                putString(KEY_ACTIVE_GESTURE_CONFIG, state.gesturePresetFile)
                putBoolean(KEY_DSS_AUTO_STITCH, state.dssAutoStitch)
                putInt(KEY_LAUNCH_FAILURE_COUNT, 0)
                putBoolean(KEY_ONBOARDING_COMPLETE, true)
                putBoolean(KEY_ENABLE_FOCUS_LOCK_WORKAROUND, true)
            }.commit()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentHomePkg = getCurrentDefaultHomePackage(context)
                currentHomeLabel = getAppName(context, currentHomePkg)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(showSetDefaultButton, isFinishButtonEnabled) {
        if (showSetDefaultButton) {
            setDefaultFocusRequester.requestFocus()
        } else if (isFinishButtonEnabled) {
            finishFocusRequester.requestFocus()
        }
    }

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onBackground
    )

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Default Home") },
            text = {
                Text(
                    "Your Default Home app is what you boot into and what the 'Default Home' gesture launches.\\n\\n" +
                            "IMPORTANT: If you chose a special Launcher (like Quickstep or Odin), you MUST set it as your Default Home here for Mjolnir to work correctly."
                )
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    val handleFinish: () -> Unit = {
        if (isFinishButtonEnabled) {
            if (requiredDefaultPkg == null) {
                DiagnosticsLogger.logEvent("Onboarding", "MANUAL_SAVE_ANY_DEFAULT", "Committing Advanced prefs", context)
                val finalInterception = !(state.topAppPackage == null && state.bottomAppPackage == null) && state.homeInterceptionActive
                val prefs = context.settingsPrefs()
                prefs.edit().apply {
                    putString(KEY_TOP_APP, state.topAppPackage)
                    putString(KEY_BOTTOM_APP, state.bottomAppPackage)
                    putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, finalInterception)
                    putString(KEY_ACTIVE_GESTURE_CONFIG, state.gesturePresetFile)
                    putBoolean(KEY_DSS_AUTO_STITCH, state.dssAutoStitch)
                    putInt(KEY_LAUNCH_FAILURE_COUNT, 0)
                    putBoolean(KEY_ONBOARDING_COMPLETE, true)
                    putBoolean(KEY_ENABLE_FOCUS_LOCK_WORKAROUND, true)
                }.commit()
            }
            onNavigate { onFinish() }
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = headingText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- Corrected UI Text Logic ---
                if (requiredDefaultPkg != null && !requirementMet) {
                    val targetLabel = getAppName(context, requiredDefaultPkg)
                    Text("You must set '$targetLabel' as default to continue.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                } else if (requirementMet && requiredDefaultPkg != null) {
                    Text("Setup complete. Tap 'Finish' to exit.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                } else { // This is the "Any Default" path
                    Text("Optionally, set your default Home app.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (currentHomePkg != null) {
                    val pm = context.packageManager
                    val drawable = remember(currentHomePkg) { try { pm.getApplicationIcon(currentHomePkg!!) } catch (e: Exception) { null } }
                    Image(
                        painter = rememberDrawablePainter(drawable),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).padding(bottom = 8.dp)
                    )
                }

                Text("Current Default: $currentHomeLabel", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(32.dp))

                if (showSetDefaultButton) {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                            homePickerLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth(0.7f).focusRequester(setDefaultFocusRequester).focusable(),
                        colors = buttonColors,
                        enabled = !isNavigating
                    ) { Text("Set Default Home") }
                }
            }

            OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }

                OutlinedButton(
                    onClick = handleFinish,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp).focusRequester(finishFocusRequester).focusable(),
                    enabled = isFinishButtonEnabled
                ) {
                    Text(finishButtonText)
                }
            }
    }
}

private fun getCurrentDefaultHomePackage(context: Context): String? {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolveInfo: ResolveInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return resolveInfo?.activityInfo?.packageName
}

private fun getAppName(context: Context, packageName: String?): String {
    if (packageName == null) return "Unknown"
    return try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        packageName // Fallback to package name
    }
}
