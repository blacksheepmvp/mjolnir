package xyz.blacksheep.mjolnir.launchers

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

/**
 * Converts an Android [Drawable] into a Compose [Painter] instance suitable
 * for displaying inside Image composables.
 *
 * Behavior:
 * - If the drawable is non-null, it is converted to a Bitmap and wrapped in
 *   a [BitmapPainter].
 * - If null, returns a transparent [ColorPainter].
 *
 * This helper ensures consistent image rendering within Compose without
 * leaking Android-specific types.
 *
 * @param drawable The drawable to convert into a painter.
 * @return A compose Painter that can be passed directly to Image().
 */
@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter {
    return remember(drawable) {
        drawable?.toBitmap()?.let { BitmapPainter(it.asImageBitmap()) } ?: ColorPainter(Color.Transparent)
    }
}

/**
 * Extension function that retrieves the application icon for the package
 * referenced by this intent.
 *
 * Behavior:
 * - Extracts the package name from the intent.
 * - Calls PackageManager.getApplicationIcon(packageName).
 * - Returns null if the package cannot be resolved or the icon fails to load.
 *
 * NOTE:
 * This method is used primarily in app selection UIs to render launcher icons.
 *
 * @receiver Intent whose package should be inspected.
 * @param context Optional context (defaults to LocalContext).
 * @return Drawable for the app's icon, or null on failure.
 */
@Composable
fun Intent.getPackageIcon(context: Context = LocalContext.current): Drawable? {
    val pm = context.packageManager
    val pkg = this.getPackage() ?: return null
    return try {
        pm.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }
}
