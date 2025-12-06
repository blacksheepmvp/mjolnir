package xyz.blacksheep.mjolnir.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import xyz.blacksheep.mjolnir.settings.AppTheme
import xyz.blacksheep.mjolnir.utils.DiagnosticsConfig
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import android.graphics.Color as AndroidColor

class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

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
        DiagnosticsLogger.logEvent("Onboarding", "ON_FINISH_CALLED", "About to finish affinity", context)
        context.findActivity()?.finishAffinity()
        DiagnosticsLogger.logEvent("Onboarding", "FINISH_AFFINITY_CALLED", "(This might not be logged if process dies)", context)
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
            composable("advanced_dss") { AdvancedDssScreen(navController, viewModel, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("advanced_set_default") { AdvancedSetDefaultHomeScreen(navController, viewModel, onFinish = ::finishOnboarding, isNavigating = isNavigating, onNavigate = ::onNavigate) }
            composable("no_home") { NoHomeSetupScreen(navController, onFinish = ::finishOnboarding, isNavigating = isNavigating, onNavigate = ::onNavigate) }
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
                Text("Welcome to Mjolnir", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Mjolnir lets you choose what opens on your top and bottom screens, and optionally adds Home-button gestures and dual-screen screenshots.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(48.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { onNavigate { navController.navigate("basic_home_selection") } },
                        modifier = Modifier.weight(1f).height(90.dp),
                        colors = buttonColors,
                        shape = MaterialTheme.shapes.large,
                        enabled = !isNavigating
                    ) { Text("Basic\nHome Setup", textAlign = TextAlign.Center) }
                    
                    Button(
                        onClick = { onNavigate { navController.navigate("advanced_permissions") } },
                        modifier = Modifier.weight(1f).height(90.dp),
                        colors = buttonColors,
                        shape = MaterialTheme.shapes.large,
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
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}