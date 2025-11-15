package xyz.blacksheep.mjolnir.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import xyz.blacksheep.mjolnir.MjolnirHomeTileService
import xyz.blacksheep.mjolnir.R
import xyz.blacksheep.mjolnir.isHomeInterceptionActive

const val CHANNEL_ID = "MjolnirHomeServiceChannel"
private var notificationJob: Job? = null

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Mjolnir Home Service"
        val descriptionText = "Channel for Mjolnir Home key interceptor service"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun showTestNotification(context: Context) {
    createNotificationChannel(context)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    notificationJob?.cancel() // Cancel any existing notification job
    notificationJob = CoroutineScope(Dispatchers.Default).launch {
        while (isActive) {
            val accessibilityServiceEnabled = MjolnirHomeTileService.isAccessibilityServiceEnabled(context)
            val tileState = if (isHomeInterceptionActive && accessibilityServiceEnabled) {
                "Active"
            } else {
                "Inactive"
            }

            val contentText = "isHomeInterceptionActive: $isHomeInterceptionActive\n"
                .plus("isAccessibilityServiceEnabled: $accessibilityServiceEnabled\n")
                .plus("Tile State: $tileState")

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_home)
                .setContentTitle("Mjolnir Test Notification")
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)

            notificationManager.notify(1, builder.build())

            delay(5000) // 5-second polling interval
        }
    }
}

fun cancelTestNotification(context: Context) {
    notificationJob?.cancel()
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(1)
}
