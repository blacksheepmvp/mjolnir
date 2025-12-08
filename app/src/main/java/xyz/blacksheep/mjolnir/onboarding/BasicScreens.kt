package xyz.blacksheep.mjolnir.onboarding

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_ENABLE_FOCUS_LOCK_WORKAROUND
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_LAUNCH_FAILURE_COUNT
import xyz.blacksheep.mjolnir.KEY_ONBOARDING_COMPLETE
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.settings.LauncherApp
import xyz.blacksheep.mjolnir.settings.getLaunchableApps
import xyz.blacksheep.mjolnir.settings.getPackageIcon
import xyz.blacksheep.mjolnir.settings.rememberDrawablePainter
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger


@Composable
fun BasicHomeSelectionScreen(
    navController: NavController,
    viewModel: OnboardingViewModel,
    isNavigating: Boolean,
    onNavigate: (() -> Unit) -> Unit
) {
    val state by viewModel.uiState
    val SPECIAL_HOME_APPS = remember { setOf("com.android.launcher3", "com.odin.odinlauncher") }

    LaunchedEffect(Unit) {
        if (state.topAppPackage in SPECIAL_HOME_APPS) {
            viewModel.setTopApp(null)
        }
    }
    
    HomeSelectionUI(
        topAppPackage = state.topAppPackage,
        bottomAppPackage = state.bottomAppPackage,
        onTopAppSelected = { viewModel.setTopApp(it) },
        onBottomAppSelected = { viewModel.setBottomApp(it) },
        onNext = {
            onNavigate {
                viewModel.setHomeInterception(false)
                navController.navigate("basic_set_default")
            }
        },
        onPrev = { onNavigate { navController.popBackStack() } },
        isBasicFlow = true,
        onSwitchToAdvanced = { onNavigate { navController.navigate("advanced_permissions") } },
        isNavigating = isNavigating,
        onManageBlacklist = { 
            DiagnosticsLogger.logEvent("Onboarding", "BLACKLIST_CLICKED", "Navigating to app_blacklist", navController.context)
            onNavigate { navController.navigate("app_blacklist") } 
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSelectionUI(
    topAppPackage: String?,
    bottomAppPackage: String?,
    onTopAppSelected: (String?) -> Unit,
    onBottomAppSelected: (String?) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    isBasicFlow: Boolean = false,
    onSwitchToAdvanced: () -> Unit = {},
    isNavigating: Boolean,
    onManageBlacklist: () -> Unit = {}
) {
    val context = LocalContext.current
    var showAllApps by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSpecialAppDialog by remember { mutableStateOf(false) }
    var pendingSpecialApp by remember { mutableStateOf<LauncherApp?>(null) }
    var launcherApps by remember { mutableStateOf(emptyList<LauncherApp>()) }
    var isLoading by remember { mutableStateOf(true) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant
    val cardContentColor = MaterialTheme.colorScheme.onSurface
    val headerColor = MaterialTheme.colorScheme.onBackground

    LaunchedEffect(showAllApps) {
        isLoading = true
        launcherApps = withContext(Dispatchers.IO) { getLaunchableApps(context, showAll = showAllApps) }
        isLoading = false
    }
    
    // --- MANUAL CHANGE START: Requirement 1 ---
    // In Basic-like conditions (Basic Flow), do NOT show <Nothing>.
    // Basic = frontend/frontend only.
    val displayedApps = remember(launcherApps, isBasicFlow) {
        if (isBasicFlow) {
            launcherApps.filter { it.packageName != "NOTHING" }
        } else {
            launcherApps
        }
    }
    // --- MANUAL CHANGE END ---

    // --- NEW: Helper to prevent duplicates by swapping ---
    fun onSelectApp(pkg: String?, isTop: Boolean) {
        if (isTop) {
            if (pkg != null && pkg == bottomAppPackage) {
                // Swap: Set Bottom to current Top
                onBottomAppSelected(topAppPackage)
            }
            onTopAppSelected(pkg)
        } else {
            if (pkg != null && pkg == topAppPackage) {
                // Swap: Set Top to current Bottom
                onTopAppSelected(bottomAppPackage)
            }
            onBottomAppSelected(pkg)
        }
    }

    val SPECIAL_HOME_APPS = remember { setOf("com.android.launcher3", "com.odin.odinlauncher") }

    val selectedTopApp = remember(topAppPackage, launcherApps) { launcherApps.find { it.packageName == topAppPackage } }
    val selectedBottomApp = remember(bottomAppPackage, launcherApps) { launcherApps.find { it.packageName == bottomAppPackage } }

    var topExpanded by remember { mutableStateOf(false) }
    var bottomExpanded by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Choosing Your Frontends") },
            text = {
                Text("You can add almost any launcher, frontend or app to your top and bottom home screens with Mjolnir. In a Basic setup, both activate when you press Home. In an Advanced setup, you have finer control over exactly how each screen reacts.\n\n" +
                        "App Blacklist: Ban or unban apps from the app picker\n" +
                        "App Filter: Toggle between 'All apps' and 'Only launchers and frontends'")
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    if (showSpecialAppDialog) {
        AlertDialog(
            onDismissRequest = { showSpecialAppDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Special Launcher Detected") },
            text = { Text("In order to use ${pendingSpecialApp?.label ?: "this app"} here, you need to:\n• Run the advanced home setup\n• Set ${pendingSpecialApp?.label ?: "this app"} as your default home\n\nWould you like to switch to advanced?") },
            confirmButton = {
                TextButton(onClick = {
                    // CHANGED: Use onSelectApp to handle swap
                    onSelectApp(pendingSpecialApp?.packageName, isTop = true)
                    onSwitchToAdvanced()
                    showSpecialAppDialog = false
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = {
                    // CHANGED: Use onSelectApp to handle swap
                    onSelectApp(null, isTop = true) 
                    showSpecialAppDialog = false
                }) { Text("No") }
            }
        )
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select Home Apps", style = MaterialTheme.typography.headlineMedium, color = headerColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))

                val cardHeight = 140.dp
                val topCardWidth = cardHeight * (16f / 9f)
                val bottomCardWidth = cardHeight * (4f / 3f)

                Box {
                    AppSlotCard(
                        modifier = Modifier.size(width = topCardWidth, height = cardHeight),
                        app = selectedTopApp,
                        label = if (isLoading) "Loading..." else "Select Top Screen App",
                        onClick = { if (!isNavigating) topExpanded = true },
                        backgroundColor = cardBgColor,
                        contentColor = cardContentColor,
                        shape = MaterialTheme.shapes.large
                    )
                    DropdownMenu(
                        expanded = topExpanded,
                        onDismissRequest = { topExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).heightIn(max = screenHeight * 0.6f)
                    ) {
                        displayedApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.label) },
                                leadingIcon = { Image(painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()), contentDescription = null, modifier = Modifier.size(24.dp)) },
                                onClick = {
                                    val pkg = if (app.packageName == "NOTHING") null else app.packageName
                                    if (isBasicFlow && pkg in SPECIAL_HOME_APPS) {
                                        pendingSpecialApp = app
                                        showSpecialAppDialog = true
                                    } else {
                                        // CHANGED: Use onSelectApp
                                        onSelectApp(pkg, isTop = true)
                                    }
                                    topExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box {
                    AppSlotCard(
                        modifier = Modifier.size(width = bottomCardWidth, height = cardHeight),
                        app = selectedBottomApp,
                        label = if (isLoading) "Loading..." else "Select Bottom Screen App",
                        onClick = { if (!isNavigating) bottomExpanded = true },
                        backgroundColor = cardBgColor,
                        contentColor = cardContentColor,
                        shape = MaterialTheme.shapes.large
                    )
                    DropdownMenu(
                        expanded = bottomExpanded,
                        onDismissRequest = { bottomExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).heightIn(max = screenHeight * 0.6f)
                    ) {
                        displayedApps.filterNot { it.packageName in SPECIAL_HOME_APPS }.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.label) },
                                leadingIcon = { Image(painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()), contentDescription = null, modifier = Modifier.size(24.dp)) },
                                // CHANGED: Use onSelectApp
                                onClick = { 
                                    val pkg = if (app.packageName == "NOTHING") null else app.packageName
                                    onSelectApp(pkg, isTop = false)
                                    bottomExpanded = false 
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                
                
                Spacer(Modifier.height(64.dp))
            }

            OutlinedButton(onClick = onPrev, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            
            // --- CHANGED: Next Button Logic (Trap Click in Basic Mode & Duplicate Check) ---
            val isDuplicate = topAppPackage != null && topAppPackage == bottomAppPackage
            val isBasicComplete = topAppPackage != null && bottomAppPackage != null
            val isAdvancedComplete = topAppPackage != null || bottomAppPackage != null

            val isConfigValid = !isDuplicate && if (isBasicFlow) isBasicComplete else isAdvancedComplete

            val shouldTrapClick = isBasicFlow && !isConfigValid
            val isButtonEnabled = !isNavigating && (isConfigValid || shouldTrapClick)

            val nextColors = if (shouldTrapClick) {
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            } else {
                ButtonDefaults.outlinedButtonColors()
            }
            
            val nextBorder = if (shouldTrapClick) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            } else {
                ButtonDefaults.outlinedButtonBorder
            }

            OutlinedButton(
                onClick = { 
                    if (isConfigValid) {
                        onNext() 
                    } else if (shouldTrapClick) {
                        // Basic Mode: Toast if missing selection
                        val msg = when {
                            isDuplicate -> "You cannot select the same app for both screens."
                            topAppPackage == null -> "Please select a top screen app."
                            bottomAppPackage == null -> "Please select a bottom screen app."
                            else -> "Incomplete configuration."
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }, 
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp), 
                enabled = isButtonEnabled,
                colors = nextColors,
                border = nextBorder
            ) { 
                Text("Next") 
            }

            // Top-right controls
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = onManageBlacklist,
                    containerColor = Color(0xFF424242), // Solid dark grey
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Block, contentDescription = "Blacklist")
                }

                FloatingActionButton(
                    onClick = { showAllApps = !showAllApps },
                    containerColor = if (showAllApps) MaterialTheme.colorScheme.primary else Color(0xFF424242), // Solid dark grey
                    contentColor = if (showAllApps) MaterialTheme.colorScheme.onPrimary else Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(if (showAllApps) Icons.Filled.FilterList else Icons.Filled.Tune, contentDescription = "Filter Apps")
                }
            }
        }
    }
}

@Composable
fun BasicSetDefaultHomeScreen(
    navController: NavController,
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit,
    isNavigating: Boolean,
    onNavigate: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentHome by remember { mutableStateOf("Checking...") }
    val isMjolnirDefault by remember(currentHome) { mutableStateOf(currentHome.contains("Mjolnir", ignoreCase = true)) }
    
    val SPECIAL_HOME_APPS = remember { setOf("com.android.launcher3", "com.odin.odinlauncher") }
    val hasInvalidSpecialApp = state.topAppPackage in SPECIAL_HOME_APPS

    val homePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Result doesn't matter, ON_RESUME will handle the state check.
    }

    LaunchedEffect(Unit) {
        val isValid = !hasInvalidSpecialApp
        if (isValid) {
            DiagnosticsLogger.logEvent("Onboarding", "VALID_CONFIG_DETECTED", "Committing Basic prefs", context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val success = prefs.edit() 
                .putString(KEY_TOP_APP, state.topAppPackage)
                .putString(KEY_BOTTOM_APP, state.bottomAppPackage)
                .putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
                .putInt(KEY_LAUNCH_FAILURE_COUNT, 0)
                .putBoolean(KEY_ONBOARDING_COMPLETE, true)
                .putBoolean(KEY_ENABLE_FOCUS_LOCK_WORKAROUND, true) // Always default to true
                .commit()
            DiagnosticsLogger.logEvent("Onboarding", "PREFS_COMMIT_END", "Success=$success", context)
        } else {
            DiagnosticsLogger.logEvent("Onboarding", "INVALID_CONFIG_DETECTED", "Skipping auto-commit for Basic flow", context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentHome = getCurrentDefaultHome(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(modifier = Modifier.align(Alignment.Center).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isMjolnirDefault) "All Done!" else "Almost Done!",
                    style = MaterialTheme.typography.headlineMedium, 
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (hasInvalidSpecialApp) {
                    Text("This configuration is not supported in Basic Mode.\nPlease go back and choose a different Top App.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                } else {
                    if(isMjolnirDefault) {
                        Text("Mjolnir is already your default home. You're all set!", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text("On the next screen, set Mjolnir as your default home app.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Current Default: $currentHome", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                homePickerLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth(0.7f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            enabled = !isNavigating
                        ) { Text("Set Default Home") }
                    }
                }
            }
            
            OutlinedButton(onClick = { onNavigate { navController.popBackStack() } }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp), enabled = !isNavigating) { Text("Back") }
            
            OutlinedButton(
                onClick = { onNavigate { onFinish() } }, 
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
                // CHANGED: Force user to set Mjolnir as default in Basic Mode
                enabled = !isNavigating && !hasInvalidSpecialApp && isMjolnirDefault
            ) { 
                Text("Finish") 
            }
        }
    }
}

private fun getCurrentDefaultHome(context: Context): String {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolveInfo: ResolveInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return resolveInfo?.activityInfo?.loadLabel(pm)?.toString() ?: "Unknown"
}

@Composable
fun AppSlotCard(
    modifier: Modifier = Modifier,
    app: LauncherApp?,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    shape: Shape = MaterialTheme.shapes.medium
) {
    val borderModifier = if (backgroundColor.alpha < 1f) {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            shape = shape
        )
    } else { Modifier }

    Surface(
        modifier = modifier.then(borderModifier).clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = if (backgroundColor.alpha < 1f) 0.dp else 3.dp,
        shadowElevation = if (backgroundColor.alpha < 1f) 0.dp else 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (app == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(text = label, color = contentColor, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()), contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(text = app.label, color = contentColor, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
            }
        }
    }
}