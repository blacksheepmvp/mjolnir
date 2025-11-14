package xyz.blacksheep.mjolnir

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MjolnirApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createPersistentNotificationChannel()
    }

    private fun createPersistentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PERSISTENT_CHANNEL_ID,
                "Mjolnir Persistent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Mjolnir active so home key interception remains available."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val PERSISTENT_CHANNEL_ID = "mjolnir_persistent_channel"
    }
}