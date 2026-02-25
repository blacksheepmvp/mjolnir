package xyz.blacksheep.mjolnir.settings

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import xyz.blacksheep.mjolnir.KEY_APP_BLACKLIST
import xyz.blacksheep.mjolnir.PREFS_NAME
import xyz.blacksheep.mjolnir.launchers.rememberDrawablePainter
import xyz.blacksheep.mjolnir.utils.AppInfo
import xyz.blacksheep.mjolnir.utils.AppQueryHelper
import xyz.blacksheep.mjolnir.settings.settingsPrefs

/**
 * App blacklist editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistSettingsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.settingsPrefs() }
    val forcedBlacklist = remember { setOf(context.packageName) }

    var blacklistedPackages by rememberSaveable {
        mutableStateOf(prefs.getStringSet(KEY_APP_BLACKLIST, emptySet()) ?: emptySet())
    }

    fun updateBlacklist(newBlacklist: Set<String>) {
        val filtered = newBlacklist.filterNot { it in forcedBlacklist }.toSet()
        blacklistedPackages = filtered
        prefs.edit().putStringSet(KEY_APP_BLACKLIST, filtered).apply()
    }

    var showAddDialog by remember { mutableStateOf(false) }

    val allApps = remember {
        AppQueryHelper(context).queryCanonicalApps().sortedBy { it.label.lowercase() }
    }

    val displayBlacklist = remember(blacklistedPackages, forcedBlacklist) {
        blacklistedPackages + forcedBlacklist
    }

    val blacklistedAppInfo = remember(displayBlacklist, allApps) {
        allApps.filter { it.packageName in displayBlacklist }
    }

    Scaffold(
        containerColor = settingsSurfaceColor(),
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("App Blacklist") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add to blacklist")
            }
        }
    ) { innerPadding ->
        if (showAddDialog) {
            val nonBlacklistedApps = remember(allApps, blacklistedPackages) {
                allApps.filter { it.packageName !in displayBlacklist }
            }
            AddAppToBlacklistDialog(
                allApps = nonBlacklistedApps,
                onDismiss = { showAddDialog = false },
                onAppSelected = { appPackage ->
                    updateBlacklist(blacklistedPackages + appPackage)
                }
            )
        }

        if (blacklistedAppInfo.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Blacklist is empty")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(blacklistedAppInfo, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberDrawablePainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp))
                        Text(
                            text = app.label,
                            modifier = Modifier.weight(1f)
                        )
                        val isForced = app.packageName in forcedBlacklist
                        TextButton(
                            onClick = {
                                updateBlacklist(blacklistedPackages - app.packageName)
                            },
                            enabled = !isForced
                        ) {
                            Text(if (isForced) "Locked" else "Remove")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog listing all apps not currently blacklisted and allowing the user to
 * add any one of them to the blacklist.
 */
@Composable
fun AddAppToBlacklistDialog(
    allApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add App to Blacklist") },
        text = {
            LazyColumn {
                items(allApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(app.packageName) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberDrawablePainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp))
                        Text(app.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
