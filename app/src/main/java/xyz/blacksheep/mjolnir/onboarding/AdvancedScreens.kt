package xyz.blacksheep.mjolnir.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_DOUBLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_DSS_AUTO_STITCH
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_LAUNCH_FAILURE_COUNT
import xyz.blacksheep.mjolnir.KEY_LONG_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_SINGLE_HOME_ACTION
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.KEY_TRIPLE_HOME_ACTION
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.home.Action
import xyz.blacksheep.mjolnir.home.actionLabel

@Composable
fun AdvancedPermissionScreen(navController: NavController) {
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
                        colors = buttonColors
                    ) { Text("Grant Permission") }
                }
            }

            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { navController.navigate("advanced_accessibility") }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp)) { Text("Next") }
        }
    }
}

@Composable
fun AdvancedAccessibilityScreen(navController: NavController) {
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
                        colors = buttonColors
                    ) { Text("Enable Mjolnir Service") }
                }
            }

            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { navController.navigate("advanced_home_selection") }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp)) { Text(if (isEnabled) "Next" else "Skip") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedHomeSelectionScreen(navController: NavController, viewModel: OnboardingViewModel) {
    val state by viewModel.uiState
    HomeSelectionUI(
        topAppPackage = state.topAppPackage,
        bottomAppPackage = state.bottomAppPackage,
        onTopAppSelected = { viewModel.setTopApp(it) },
        onBottomAppSelected = { viewModel.setBottomApp(it) },
        onNext = { navController.navigate("advanced_gestures") },
        onPrev = { navController.popBackStack() },
        isBasicFlow = false,
        onSwitchToAdvanced = {}
    )
}

@Composable
fun AdvancedGestureScreen(navController: NavController, viewModel: OnboardingViewModel) {
    val state by viewModel.uiState
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Gesture Actions") },
            text = { Text("• Default Home: Let Android run its usual Home behavior.\n" + "• Top Home: Open your top-screen app.\n" + "• Bottom Home: Open your bottom-screen app.\n" + "• Both Home: Open both apps at once.\n" + "• App Switcher: Open the recent apps screen.") },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Gesture Setup", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Map Home button presses to actions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp)) { 
                    GestureRow("Single Press", state.singleHomeAction) { viewModel.setGestureAction(Gesture.SINGLE, it) }
                    GestureRow("Double Tap", state.doubleHomeAction) { viewModel.setGestureAction(Gesture.DOUBLE, it) }
                    GestureRow("Triple Tap", state.tripleHomeAction) { viewModel.setGestureAction(Gesture.TRIPLE, it) }
                    GestureRow("Long Press", state.longHomeAction) { viewModel.setGestureAction(Gesture.LONG, it) }
                }
            }
            
            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { viewModel.setHomeInterception(true); navController.navigate("advanced_dss") }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp)) { Text("Next") }
        }
    }
}

@Composable
fun GestureRow(label: String, currentAction: Action, onActionSelected: (Action) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Box {
            Text(text = actionLabel(currentAction), color = MaterialTheme.colorScheme.primary)
            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Action.values().forEach { action ->
                    DropdownMenuItem(text = { Text(actionLabel(action)) }, onClick = { onActionSelected(action); expanded = false })
                }
            }
        }
    }
}

@Composable
fun AdvancedDssScreen(navController: NavController, viewModel: OnboardingViewModel) {
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
            title = { Text("Using DualShot") },
            text = { Text("To use DualShot:\n" + "• Turn on the DualShot quick tile.\n" + "• Use the system screenshot button on the bottom screen.\n\n" + "Many people place the DualShot tile near their usual screenshot control.") },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(modifier = Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DualShot (Auto-Stitch)", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("DualShot creates a combined top + bottom screenshot.\n" + "When you take a bottom screenshot, Mjolnir captures the top screen and stitches them together.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(32.dp))
                Text(text = if (state.dssAutoStitch) "DualShot Active" else "DualShot Inactive", style = MaterialTheme.typography.titleMedium, color = if (state.dssAutoStitch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { toggleDss() }, 
                    modifier = Modifier.fillMaxWidth(0.7f),
                    colors = buttonColors
                ) { Text(if (state.dssAutoStitch) "Disable DualShot" else "Enable DualShot") }
            }
            
            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { navController.navigate("advanced_set_default") }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp)) { Text("Next") }
        }
    }
}

@Composable
fun AdvancedSetDefaultHomeScreen(navController: NavController, viewModel: OnboardingViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState
    var showInfoDialog by remember { mutableStateOf(false) }
    var currentHomePkg by remember { mutableStateOf<String?>(null) }
    var currentHomeLabel by remember { mutableStateOf("Checking...") }
    val lifecycleOwner = LocalLifecycleOwner.current

    val SPECIAL_HOME_APPS = remember { setOf("com.android.launcher3", "com.odin.odinlauncher") }
    val specialAppSelected = state.topAppPackage in SPECIAL_HOME_APPS
    val defaultHomeMatchesSpecialApp = specialAppSelected && currentHomePkg == state.topAppPackage

    fun commitPrefs() {
        val finalInterception = !(state.topAppPackage == null && state.bottomAppPackage == null) && state.homeInterceptionActive
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_TOP_APP, state.topAppPackage)
            putString(KEY_BOTTOM_APP, state.bottomAppPackage)
            putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, finalInterception)
            putString(KEY_SINGLE_HOME_ACTION, state.singleHomeAction.name)
            putString(KEY_DOUBLE_HOME_ACTION, state.doubleHomeAction.name)
            putString(KEY_TRIPLE_HOME_ACTION, state.tripleHomeAction.name)
            putString(KEY_LONG_HOME_ACTION, state.longHomeAction.name)
            putBoolean(KEY_DSS_AUTO_STITCH, state.dssAutoStitch)
            putInt(KEY_LAUNCH_FAILURE_COUNT, 0)
        }.apply()
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
                    text = if (defaultHomeMatchesSpecialApp) "All Done!" else "Finally...",
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
                Text("Current Default: $currentHomeLabel", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(32.dp))

                if (!defaultHomeMatchesSpecialApp) {
                    Button(
                        onClick = {
                            commitPrefs()
                            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                            context.startActivity(intent)
                            onFinish()
                        }, 
                        modifier = Modifier.fillMaxWidth(0.7f),
                        colors = buttonColors
                    ) { Text("Set Default Home") }
                }
            }
            
            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(
                onClick = { 
                    commitPrefs()
                    onFinish() 
                }, 
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
                enabled = !specialAppSelected || defaultHomeMatchesSpecialApp
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
