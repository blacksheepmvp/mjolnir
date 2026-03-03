package xyz.blacksheep.mjolnir.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.blacksheep.mjolnir.KEY_THEME
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.model.AppTheme
import xyz.blacksheep.mjolnir.settings.BlacklistSettingsScreen
import xyz.blacksheep.mjolnir.settings.GestureConfigStore
import xyz.blacksheep.mjolnir.settings.GesturePresetEditorScreen
import xyz.blacksheep.mjolnir.utils.DiagnosticsConfig
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import android.graphics.Color as AndroidColor
import xyz.blacksheep.mjolnir.settings.settingsPrefs

class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.settingsPrefs() }

            val themeName = remember { prefs.getString(KEY_THEME, AppTheme.SYSTEM.name) }
            val appTheme = remember(themeName) {
                try {
                    AppTheme.valueOf(themeName!!)
                } catch (e: Exception) {
                    AppTheme.SYSTEM
                }
            }

            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = remember(appTheme, systemDark) {
                when (appTheme) {
                    AppTheme.LIGHT -> false
                    AppTheme.DARK -> true
                    AppTheme.SYSTEM -> systemDark
                }
            }

            val view = LocalView.current
            SideEffect {
                window.statusBarColor = AndroidColor.TRANSPARENT
                window.navigationBarColor = AndroidColor.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDarkTheme
            }

            val gradientColors = if (useDarkTheme) {
                listOf(Color(0xFF0D47A1), Color.Black)
            } else {
                listOf(Color(0xFFE3F2FD), Color.White)
            }

            OnboardingTheme(darkTheme = useDarkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = Brush.verticalGradient(colors = gradientColors))
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        OnboardingNavHost(viewModel = viewModel)
                    }
                }
            }
        }
    }

@Composable
private fun OnboardingTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        androidx.compose.material3.darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Transparent,
            surface = Color.Transparent,
            surfaceContainerHigh = Color(0xFF012A60),
            onBackground = Color.White,
            onSurface = Color(0xFF9E9E9E),
            onSurfaceVariant = Color(0xFFBDBDBD),
            surfaceVariant = Color(0x4D000000),
            outline = Color(0x80FFFFFF)
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            background = Color.Transparent,
            surface = Color.Transparent,
            surfaceContainerHigh = Color(0xFFE8E8EB),
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = Color(0xFF424242),
            surfaceVariant = Color(0x40000000),
            outline = Color(0x40000000)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography, 
        content = content
    )
}

@Composable
fun OnboardingNavHost(viewModel: OnboardingViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isNavigating by remember { mutableStateOf(false) }

    fun onNavigate(block: () -> Unit) {
        if (!isNavigating) {
            isNavigating = true
            block()
            scope.launch {
                delay(300)
                isNavigating = false
            }
        }
    }

    fun finishOnboarding() {
        DiagnosticsLogger.logEvent("Onboarding", "ON_FINISH_CALLED", "Finishing and returning Home", context)
        
        // Go to Home screen (Launcher)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
        
        // Finish and remove from recents so it can't be swiped away to kill the service
        context.findActivity()?.finishAndRemoveTask()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "entry") {
            composable("entry") { EntryScreen(navController = navController, onFinish = ::finishOnboarding, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("basic_home_selection") { BasicHomeSelectionScreen(navController, viewModel, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("basic_set_default") { BasicSetDefaultHomeScreen(navController, viewModel, onFinish = ::finishOnboarding, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("advanced_permissions") { AdvancedPermissionScreen(navController, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("advanced_accessibility") { AdvancedAccessibilityScreen(navController, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("advanced_home_selection") { AdvancedHomeSelectionScreen(navController, viewModel, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("advanced_gestures") { AdvancedGestureScreen(navController, viewModel, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("advanced_start_on_boot") { AdvancedStartOnBootScreen(navController, viewModel, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("advanced_gesture_edit") {
                val context = LocalContext.current
                val state by viewModel.uiState
                val config = GestureConfigStore.peekDraft() ?: GestureConfigStore.getActiveConfig(context, forceRefresh = true)
                val isDraft = remember { GestureConfigStore.peekDraft() != null }
                var nameValue by remember { mutableStateOf(config.name) }
                var workingConfig by remember { mutableStateOf(config) }
                GesturePresetEditorScreen(
                    title = "Edit Gesture Preset",
                    presetName = nameValue,
                    topAppPackage = state.topAppPackage,
                    bottomAppPackage = state.bottomAppPackage,
                    config = workingConfig,
                    onConfigChange = { updated ->
                        workingConfig = updated
                    },
                    onNameChange = { nameValue = it },
                    onSave = {
                        val saved = if (isDraft) {
                            GestureConfigStore.saveDraft(context, workingConfig, nameValue)
                        } else {
                            val renamed = GestureConfigStore.renamePreset(context, workingConfig, nameValue)
                            GestureConfigStore.saveConfig(context, renamed)
                            GestureConfigStore.setActiveConfig(context, renamed.fileName)
                            renamed
                        }
                        viewModel.setGesturePreset(saved.fileName)
                        viewModel.setGestureAction(Gesture.SINGLE, saved.single)
                        viewModel.setGestureAction(Gesture.DOUBLE, saved.double)
                        viewModel.setGestureAction(Gesture.TRIPLE, saved.triple)
                        viewModel.setGestureAction(Gesture.LONG, saved.long)
                        viewModel.setLongPressDelay(saved.longPressDelayMs)
                        navController.popBackStack()
                    },
                    onCancel = {
                        if (isDraft) GestureConfigStore.clearDraft()
                        navController.popBackStack()
                    }
                )
            }
            composable("advanced_dss") { AdvancedDssScreen(navController, viewModel, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("advanced_set_default") { AdvancedSetDefaultHomeScreen(navController, viewModel, onFinish = ::finishOnboarding, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("no_home") { NoHomeSetupScreen(navController, onFinish = ::finishOnboarding, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("app_blacklist") { BlacklistSettingsScreen(navController) }
        }
        
        if (isNavigating) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                color = Color.Transparent
            ) {}
        }
    }
}

@Composable
fun EntryScreen(navController: NavController, onFinish: () -> Unit, isNavigating: Boolean, onNavigate: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val diagnosticsEnabled = remember { mutableStateOf(DiagnosticsConfig.isEnabled(context)) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val basicFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = true) {
        onNavigate(onFinish)
    }

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onBackground
    )
    
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            title = { Text("Setup Modes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Basic Home Setup", style = MaterialTheme.typography.titleMedium)
                        Text("• Top & Bottom Home apps.\n• No special permissions.\n• No Gestures or DualShot.")
                    }
                    Column {
                        Text("Advanced Home Setup", style = MaterialTheme.typography.titleMedium)
                        Text("• Full Feature Set.\n• Home Gestures & DualShot.\n• Requires Notification & Accessibility permissions.")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } }
        )
    }

    LaunchedEffect(Unit) {
        basicFocusRequester.requestFocus()
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Mjolnir Home Setup", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Mjolnir Home Basic lets you choose the home app for your top and bottom screens individually. Mjolnir Home Advanced lets you customize home button behavior and access special features", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(48.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val basicShape = MaterialTheme.shapes.large
                    Button(
                        onClick = { onNavigate { navController.navigate("basic_home_selection") } },
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                            .focusRequester(basicFocusRequester)
                            .focusable(),
                        colors = buttonColors,
                        shape = basicShape,
                        enabled = !isNavigating
                    ) { Text("Basic\nHome Setup", textAlign = TextAlign.Center) }
                    
                    val advancedShape = MaterialTheme.shapes.large
                    Button(
                        onClick = { onNavigate { navController.navigate("advanced_permissions") } },
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                            .focusable(),
                        colors = buttonColors,
                        shape = advancedShape,
                        enabled = !isNavigating
                    ) { Text("Advanced\nHome Setup", textAlign = TextAlign.Center) }
        }
    }

            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(checked = diagnosticsEnabled.value, onCheckedChange = { isChecked ->
                    diagnosticsEnabled.value = isChecked
                    DiagnosticsConfig.setEnabled(context, isChecked)
                }, enabled = !isNavigating)
                Spacer(Modifier.width(8.dp))
                Text("Capture Diagnostics", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enabled = !isNavigating) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

                TextButton(
                    onClick = { onNavigate { navController.navigate("no_home") } }, 
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 2.dp),
                    enabled = !isNavigating
                ) {
                    Text("Skip Home Setup", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun getAppName(context: Context, packageName: String?): String? {
    if (packageName == null) return null
    return try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        packageName
    }
}
