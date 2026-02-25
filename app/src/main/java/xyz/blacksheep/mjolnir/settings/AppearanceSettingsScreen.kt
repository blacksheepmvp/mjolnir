package xyz.blacksheep.mjolnir.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import xyz.blacksheep.mjolnir.model.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    navController: NavController,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val themeOptions = AppTheme.entries.map { it.name }

    Scaffold(
        containerColor = settingsSurfaceColor(),
        topBar = {
            Surface(tonalElevation = 2.dp) {
                TopAppBar(
                    title = { Text("Appearance") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Adjust themes and colors",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                                onClick = { onThemeChange(AppTheme.valueOf(label)) },
                                selected = currentTheme.name == label
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}
