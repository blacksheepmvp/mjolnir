package xyz.blacksheep.mjolnir.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import xyz.blacksheep.mjolnir.utils.DiagnosticsActions
import xyz.blacksheep.mjolnir.utils.DiagnosticsSummary
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSummaryScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0L) }

    val isEnabled = remember(refreshTrigger) { DiagnosticsSummary.isEnabled(context) }
    val fileExists = remember(refreshTrigger) { DiagnosticsSummary.getLogFileExists(context) }
    val fileSize = remember(refreshTrigger) { DiagnosticsSummary.getLogFileSize(context) }
    val maxBytes = remember(refreshTrigger) { DiagnosticsSummary.getMaxBytes(context) }
    val lastMod = remember(refreshTrigger) { DiagnosticsSummary.getLastModified(context) }
    val entryCount = remember(refreshTrigger) { DiagnosticsSummary.getApproxEntryCount(context) }
    val filePath = remember(refreshTrigger) { DiagnosticsSummary.getLogFile(context).absolutePath }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.2f MB".format(mb)
    }

    fun formatDate(millis: Long): String {
        if (millis == 0L) return "N/A"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(Date(millis))
    }

    Scaffold(
        containerColor = settingsSurfaceColor(),
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Diagnostics Summary") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

                    SummaryRow("Diagnostics Enabled", isEnabled.toString())
                    SummaryRow("Log File Exists", fileExists.toString())
                    SummaryRow("File Size", "${formatSize(fileSize)} / ${formatSize(maxBytes)}")
                    SummaryRow("Approx. Entries", if (entryCount >= 0) entryCount.toString() else "N/A")
                    SummaryRow("Last Modified", formatDate(lastMod))

                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

                    Text("Path:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(filePath, style = MaterialTheme.typography.bodySmall)
                }
            }

            Divider()

            Text("Actions", style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = {
                    try {
                        val file = DiagnosticsActions.getLogFileForViewing(context)
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "text/plain")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "View Log"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = fileExists,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Raw Log File")
            }

            Button(
                onClick = {
                    try {
                        val exportFile = DiagnosticsActions.createExportWithSummary(context)
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", exportFile)

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export with Summary"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = fileExists,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export with Summary")
            }

            OutlinedButton(
                onClick = {
                    DiagnosticsActions.deleteLog(context)
                    refreshTrigger = System.currentTimeMillis()
                    Toast.makeText(context, "Log deleted", Toast.LENGTH_SHORT).show()
                },
                enabled = fileExists,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Active Log")
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
