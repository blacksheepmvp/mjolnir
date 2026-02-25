package xyz.blacksheep.mjolnir

import android.os.Bundle
import android.util.Log
import android.app.ActivityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class SafetyNetActivity : ComponentActivity() {
    companion object {
        private const val TAG = "SafetyNetActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        excludeTaskFromRecents()
        Log.d(TAG, "onCreate displayId=${display?.displayId} taskId=$taskId data=${intent?.data}")
        setContent { SafetyNetScreen() }
    }

    private fun excludeTaskFromRecents() {
        val activityManager = getSystemService(ActivityManager::class.java)
        val taskId = this.taskId
        activityManager.appTasks.firstOrNull { it.taskInfo.taskId == taskId }
            ?.setExcludeFromRecents(true)
    }
}

@Composable
private fun SafetyNetScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("You should not be here.", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
