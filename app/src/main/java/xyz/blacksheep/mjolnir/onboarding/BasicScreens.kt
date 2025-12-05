package xyz.blacksheep.mjolnir.onboarding

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.blacksheep.mjolnir.KEY_BOTTOM_APP
import xyz.blacksheep.mjolnir.KEY_HOME_INTERCEPTION_ACTIVE
import xyz.blacksheep.mjolnir.KEY_LAUNCH_FAILURE_COUNT
import xyz.blacksheep.mjolnir.KEY_TOP_APP
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.settings.LauncherApp
import xyz.blacksheep.mjolnir.settings.getLaunchableApps
import xyz.blacksheep.mjolnir.settings.getPackageIcon
import xyz.blacksheep.mjolnir.settings.rememberDrawablePainter


@Composable
fun BasicHomeSelectionScreen(
    navController: NavController,
    viewModel: OnboardingViewModel
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
            viewModel.setHomeInterception(false)
            navController.navigate("basic_set_default")
        },
        onPrev = { navController.popBackStack() },
        isBasicFlow = true,
        onSwitchToAdvanced = { navController.navigate("advanced_permissions") }
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
    onSwitchToAdvanced: () -> Unit = {}
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

    val SPECIAL_HOME_APPS = remember { setOf("com.android.launcher3", "com.odin.odinlauncher") }

    val selectedTopApp = remember(topAppPackage, launcherApps) { launcherApps.find { it.packageName == topAppPackage } }
    val selectedBottomApp = remember(bottomAppPackage, launcherApps) { launcherApps.find { it.packageName == bottomAppPackage } }

    var topExpanded by remember { mutableStateOf(false) }
    var bottomExpanded by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Mjolnir Homes") },
            text = { Text("• Top Home: The app that opens on your top screen when you press Home.\n\n• Bottom Home: The app that opens on your bottom screen.\n\nTypically, users pick a game launcher for the top screen and Android settings or a standard launcher for the bottom.") },
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
                    onTopAppSelected(pendingSpecialApp?.packageName)
                    onSwitchToAdvanced()
                    showSpecialAppDialog = false
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onTopAppSelected(null) 
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
                        onClick = { topExpanded = true },
                        backgroundColor = cardBgColor,
                        contentColor = cardContentColor,
                        shape = MaterialTheme.shapes.large
                    )
                    DropdownMenu(
                        expanded = topExpanded,
                        onDismissRequest = { topExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).heightIn(max = screenHeight * 0.6f)
                    ) {
                        launcherApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.label) },
                                leadingIcon = { Image(painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()), contentDescription = null, modifier = Modifier.size(24.dp)) },
                                onClick = {
                                    val pkg = if (app.packageName == "NOTHING") null else app.packageName
                                    if (isBasicFlow && pkg in SPECIAL_HOME_APPS) {
                                        pendingSpecialApp = app
                                        showSpecialAppDialog = true
                                    } else {
                                        onTopAppSelected(pkg)
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
                        onClick = { bottomExpanded = true },
                        backgroundColor = cardBgColor,
                        contentColor = cardContentColor,
                        shape = MaterialTheme.shapes.large
                    )
                    DropdownMenu(
                        expanded = bottomExpanded,
                        onDismissRequest = { bottomExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).heightIn(max = screenHeight * 0.6f)
                    ) {
                        launcherApps.filterNot { it.packageName in SPECIAL_HOME_APPS }.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.label) },
                                leadingIcon = { Image(painter = rememberDrawablePainter(app.launchIntent.getPackageIcon()), contentDescription = null, modifier = Modifier.size(24.dp)) },
                                onClick = { onBottomAppSelected(if (app.packageName == "NOTHING") null else app.packageName); bottomExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }

            OutlinedButton(onClick = onPrev, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { Text("Back") }
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = onNext, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp), enabled = topAppPackage != null || bottomAppPackage != null) { Text("Next") }

            FloatingActionButton(
                onClick = { showAllApps = !showAllApps },
                modifier = Modifier.align(Alignment.TopEnd),
                containerColor = if (showAllApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (showAllApps) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(if (showAllApps) Icons.Filled.FilterList else Icons.Filled.Tune, contentDescription = "Filter Apps")
            }
        }
    }
}

@Composable
fun BasicSetDefaultHomeScreen(
    navController: NavController,
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState
    var currentHome by remember { mutableStateOf("Checking...") }
    val isMjolnirDefault by remember(currentHome) { mutableStateOf(currentHome.contains("Mjolnir", ignoreCase = true)) }
    
    val SPECIAL_HOME_APPS = remember { setOf("com.android.launcher3", "com.odin.odinlauncher") }
    val hasInvalidSpecialApp = state.topAppPackage in SPECIAL_HOME_APPS

    LaunchedEffect(Unit) {
        currentHome = getCurrentDefaultHome(context)
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
                                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                prefs.edit() 
                                    .putString(KEY_TOP_APP, state.topAppPackage)
                                    .putString(KEY_BOTTOM_APP, state.bottomAppPackage)
                                    .putBoolean(KEY_HOME_INTERCEPTION_ACTIVE, false)
                                    .putInt(KEY_LAUNCH_FAILURE_COUNT, 0)
                                    .apply()
                                val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                                context.startActivity(intent)
                                onFinish()
                            },
                            modifier = Modifier.fillMaxWidth(0.7f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            )
                        ) { Text("Set Default Home") }
                    }
                }
            }
            
            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { Text("Back") }
            
            OutlinedButton(
                onClick = onFinish, 
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
                enabled = !hasInvalidSpecialApp && isMjolnirDefault // Disable if invalid OR if Mjolnir is not default
            ) { 
                Text(if (isMjolnirDefault) "Finish" else "Skip & Finish") 
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
        modifier = modifier.then(borderModifier).clip(shape).clickable { onClick() },
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