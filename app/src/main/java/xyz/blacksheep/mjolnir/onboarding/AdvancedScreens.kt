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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_CUSTOM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.KEY_DOUBLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_DSS_AUTO_STITCH
import xyz.blacksheep.mjolnir.KEY_ENABLE_FOCUS_LOCK_WORKAROUND
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_LAUNCH_FAILURE_COUNT
import xyz.blacksheep.mjolnir.KEY_LONG_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_ONBOARDING_COMPLETE
import xyz.blacksheep.mjolnir.KEY_SINGLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.KEY_TRIPLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_USE_SYSTEM_DOUBLE_TAP_DELAY
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.home.actionLabel
import xyz.blacksheep.mjolnir.settings.rememberDrawablePainter
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import kotlin.math.roundToInt

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
                        modifier = Modifier.fillMaxWidth(0.7f),
                        colors = buttonColors,
                        enabled = !isNavigating
                    ) { Text("Grant Permission") }
                }
            }

            OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { onNavigate { navController.navigate("advanced_accessibility") } }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Next") }
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
                        modifier = Modifier.fillMaxWidth(0.7f),
                        colors = buttonColors,
                        enabled = !isNavigating
                    ) { Text("Enable Mjolnir Service") }
                }
            }

            OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { onNavigate { navController.navigate("advanced_home_selection") } }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp), enabled = !isNavigating) { Text(if (isEnabled) "Next" else "Skip") }
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
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var showInfoDialog by remember { mutableStateOf(false) }

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

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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

                // --- GRID LAYOUT: 4 Cols, 2 Rows ---
                // Row 1: Headers
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    GestureHeader("Single Tap", 1)
                    GestureHeader("Double Tap", 2)
                    GestureHeader("Triple Tap", 3)
                    GestureHeader("Long Press", 0)
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: Actions (Dropdowns)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GestureDropdown(state.singleHomeAction, isNavigating, state.topAppPackage, state.bottomAppPackage) { viewModel.setGestureAction(Gesture.SINGLE, it) }
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GestureDropdown(state.doubleHomeAction, isNavigating, state.topAppPackage, state.bottomAppPackage) { viewModel.setGestureAction(Gesture.DOUBLE, it) }
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GestureDropdown(state.tripleHomeAction, isNavigating, state.topAppPackage, state.bottomAppPackage) { viewModel.setGestureAction(Gesture.TRIPLE, it) }
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GestureDropdown(state.longHomeAction, isNavigating, state.topAppPackage, state.bottomAppPackage) { viewModel.setGestureAction(Gesture.LONG, it) }
                    }
                }
                
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
                OutlinedButton(onClick = { onNavigate { viewModel.setHomeInterception(true); navController.navigate("advanced_dss") } }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Next") }
            }
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
    Box(modifier = Modifier.clickable(enabled = !isNavigating) { expanded = true }.padding(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Current Selection Icon
            ActionIcon(action = currentAction, topApp = topApp, bottomApp = bottomApp, size = 32.dp)
            
            Text(
                text = actionLabel(currentAction), 
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
            Action.values().forEach { action ->
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActionIcon(action = action, topApp = topApp, bottomApp = bottomApp, size = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(actionLabel(action)) 
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
                    modifier = Modifier.fillMaxWidth(0.7f),
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

    val SPECIAL_HOME_APPS = remember { setOf("com.android.launcher3", "com.odin.odinlauncher") }
    val specialAppSelected = state.topAppPackage in SPECIAL_HOME_APPS
    val defaultHomeMatchesSpecialApp = specialAppSelected && currentHomePkg == state.topAppPackage
    
    val homePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Result doesn't matter, ON_RESUME will handle the state check.
    }

    LaunchedEffect(Unit) {
        val isValid = !specialAppSelected || defaultHomeMatchesSpecialApp
        if(isValid) {
            DiagnosticsLogger.logEvent("Onboarding", "VALID_CONFIG_DETECTED", "Committing Advanced prefs", context)
            val finalInterception = !(state.topAppPackage == null && state.bottomAppPackage == null) && state.homeInterceptionActive
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Check if Focus Lock should be auto-enabled
            val autoEnableFocusLock = !state.topAppPackage.isNullOrEmpty() && state.bottomAppPackage.isNullOrEmpty()
            
            val success = prefs.edit().apply {
                putString(KEY_TOP_APP, state.topAppPackage)
                putString(KEY_BOTTOM_APP, state.bottomAppPackage)
                putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, finalInterception)
                putString(KEY_SINGLE_HOME_ACTION, state.singleHomeAction.name)
                putString(KEY_DOUBLE_HOME_ACTION, state.doubleHomeAction.name)
                putString(KEY_TRIPLE_HOME_ACTION, state.tripleHomeAction.name)
                putString(KEY_LONG_HOME_ACTION, state.longHomeAction.name)
                putBoolean(KEY_DSS_AUTO_STITCH, state.dssAutoStitch)
                putInt(KEY_LAUNCH_FAILURE_COUNT, 0)
                putBoolean(KEY_ONBOARDING_COMPLETE, true)
                putBoolean(KEY_ENABLE_FOCUS_LOCK_WORKAROUND, true) // Always default to true
            }.commit()
            DiagnosticsLogger.logEvent("Onboarding", "PREFS_COMMIT_END", "Success=$success", context)
        } else {
            DiagnosticsLogger.logEvent("Onboarding", "INVALID_CONFIG_DETECTED", "Skipping auto-commit for Advanced flow", context)
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
                    "Your Default Home app is what you boot into and what the 'Default Home' gesture launches.\n\n" +
                    "IMPORTANT: If you chose a special Launcher (like Quickstep or Odin), you MUST set it as your Default Home here for Mjolnir to work correctly."
                ) 
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(modifier = Modifier.align(Alignment.Center).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (defaultHomeMatchesSpecialApp) "All Done!" else "Default Home App", // Changed from "Finally..."
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (specialAppSelected && !defaultHomeMatchesSpecialApp) {
                    Text("You must set '${getAppName(context, state.topAppPackage)}' as default to continue.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                } else if (specialAppSelected && defaultHomeMatchesSpecialApp) {
                    Text("Setup complete. Tap 'Finish' to exit.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Text("Choose your default Home app to complete setup.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (currentHomePkg != null) {
                    val pm = context.packageManager
                    val drawable = remember(currentHomePkg) { try { pm.getApplicationIcon(currentHomePkg!!) } catch(e: Exception) { null } }
                    Image(
                        painter = rememberDrawablePainter(drawable),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).padding(bottom = 8.dp)
                    )
                }

                Text("Current Default: $currentHomeLabel", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(32.dp))

                if (!defaultHomeMatchesSpecialApp) {
                    Button(
                        onClick = {
                            // FIX: Wrap string action in Intent()
                            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                            homePickerLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth(0.7f),
                        colors = buttonColors,
                        enabled = !isNavigating
                    ) { Text("Set Default Home") }
                }
            }
            
            OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(
                onClick = { onNavigate { onFinish() } }, 
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
                enabled = (!specialAppSelected || defaultHomeMatchesSpecialApp) && !isNavigating
            ) { 
                Text(if (specialAppSelected && defaultHomeMatchesSpecialApp) "Finish" else "Skip & Finish") 
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
