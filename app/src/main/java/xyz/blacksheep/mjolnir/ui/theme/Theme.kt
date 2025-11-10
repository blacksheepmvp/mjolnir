@file:Suppress("DEPRECATION")

package xyz.blacksheep.mjolnir.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = White,
    background = Black,
    surface = Black,
    onPrimary = Black,
    onSecondary = Black,
    onTertiary = Black,
    onBackground = White,
    onSurface = White,
)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    background = White,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = Black,
    onSurface = Black,
)

@Composable
fun MjolnirTheme(
    darkTheme: Boolean = true, // Default to dark theme
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic color
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    //val view = LocalView.current
    //if (!view.isInEditMode) {
    //    val window = (view.context as Activity).window
    //    window.statusBarColor = colorScheme.primary.toArgb()
    //}
    val view = LocalView.current
    if (!view.isInEditMode) {
        // Use SideEffect to safely perform non-Compose operations
        SideEffect {
            val window = (view.context as Activity).window

            // 1. Set the system bar background color to match the theme's background
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()

            // 2. Control the appearance of the icons (CRITICAL for dark theme contrast)
            WindowCompat.getInsetsController(window, view).apply {
                // If NOT darkTheme (i.e., Light Mode), use dark icons for contrast.
                // If darkTheme, icons will remain light.
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}