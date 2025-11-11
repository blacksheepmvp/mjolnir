package xyz.blacksheep.mjolnir

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val TAG = "MjolnirExperimental"

data class LauncherApp(
    val label: String,
    val packageName: String,
    val launchIntent: Intent
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalUiScreen(onClose: () -> Unit) {
    val context = LocalContext.current

    var showAllApps by remember { mutableStateOf(false) }

    // recompute when toggle changes
    val launcherApps = remember(showAllApps) { getLaunchableApps(context, showAllApps) }

    var topExpanded by remember { mutableStateOf(false) }
    var bottomExpanded by remember { mutableStateOf(false) }

    var selectedTopApp by remember { mutableStateOf<LauncherApp?>(null) }
    var selectedBottomApp by remember { mutableStateOf<LauncherApp?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = showAllApps,
                onCheckedChange = { showAllApps = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show all installed apps")
        }

        // --- Top Screen Dropdown ---
        ExposedDropdownMenuBox(
            expanded = topExpanded,
            onExpandedChange = { topExpanded = !topExpanded }
        ) {
            TextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = selectedTopApp?.label ?: "Select Top Screen App",
                onValueChange = {},
                label = { Text("Top Screen") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = topExpanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors()
            )
            ExposedDropdownMenu(
                expanded = topExpanded,
                onDismissRequest = { topExpanded = false }
            ) {
                launcherApps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.label) },
                        onClick = {
                            selectedTopApp = app
                            topExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Bottom Screen Dropdown ---
        ExposedDropdownMenuBox(
            expanded = bottomExpanded,
            onExpandedChange = { bottomExpanded = !bottomExpanded }
        ) {
            TextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = selectedBottomApp?.label ?: "Select Bottom Screen App",
                onValueChange = {},
                label = { Text("Bottom Screen") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bottomExpanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors()
            )
            ExposedDropdownMenu(
                expanded = bottomExpanded,
                onDismissRequest = { bottomExpanded = false }
            ) {
                launcherApps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.label) },
                        onClick = {
                            selectedBottomApp = app
                            bottomExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (selectedTopApp != null && selectedBottomApp != null) {
                    launchOnDualScreens(
                        context,
                        selectedTopApp!!.launchIntent,
                        selectedBottomApp!!.launchIntent
                    )
                }
            },
            enabled = selectedTopApp != null && selectedBottomApp != null
        ) {
            Text("Launch UI")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onClose) {
            Text("Back")
        }
    }
}

// ----------------------------------------------------------------------
// Core logic for listing launchable apps
// ----------------------------------------------------------------------
fun getLaunchableApps(context: Context, showAll: Boolean): List<LauncherApp> {
    val pm = context.packageManager
    val apps = mutableListOf<LauncherApp>()

    // --- Query all launchable (CATEGORY_LAUNCHER) apps ---
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val launcherActivities = pm.queryIntentActivities(launcherIntent, 0)

    // --- Query home (CATEGORY_HOME) apps, e.g. Quickstep ---
    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    val homeActivities = pm.queryIntentActivities(homeIntent, 0)

    // Combine both
    val allActivities = (launcherActivities + homeActivities).distinctBy {
        it.activityInfo.packageName
    }

    for (ri in allActivities) {
        val label = ri.loadLabel(pm).toString()
        val pkg = ri.activityInfo.packageName
        val activityName = ri.activityInfo.name

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(pkg, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        apps.add(LauncherApp(label, pkg, intent))
    }

    // --- Apply optional filter ---
    val filtered = if (!showAll) {
        val homePkgs = homeActivities.map { it.activityInfo.packageName }
        apps.filter { app ->
            val name = app.packageName.lowercase()
            homePkgs.contains(app.packageName) ||
                    listOf("launcher", "home", "quickstep", "daijisho", "beacon", "es-de", "nova", "odin")
                        .any { name.contains(it) }
        }
    } else apps

    val distinct = filtered.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

    Log.d(TAG, "Found ${distinct.size} apps (showAll=$showAll)")
    return distinct
}


// ----------------------------------------------------------------------
// Dual-screen launching logic
// ----------------------------------------------------------------------
fun launchOnDualScreens(context: Context, topIntent: Intent, bottomIntent: Intent) {
    topIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    bottomIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        val topDisplay = displays.getOrNull(0)
        val bottomDisplay = displays.getOrNull(1)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            topDisplay?.let {
                context.startActivity(
                    topIntent,
                    android.app.ActivityOptions.makeBasic().setLaunchDisplayId(it.displayId).toBundle()
                )
            }
            bottomDisplay?.let {
                context.startActivity(
                    bottomIntent,
                    android.app.ActivityOptions.makeBasic().setLaunchDisplayId(it.displayId).toBundle()
                )
            }
        } else {
            context.startActivity(topIntent)
            context.startActivity(bottomIntent)
        }
    } catch (e: Exception) {
        Log.e(TAG, "launchOnDualScreens: error launching intents: ${e.message}", e)
    }
}
