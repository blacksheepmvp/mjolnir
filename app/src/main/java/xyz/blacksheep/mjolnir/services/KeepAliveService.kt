package xyz.blacksheep.mjolnir.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import xyz.blacksheep.mjolnir.MainActivity
import xyz.blacksheep.mjolnir.MjolnirApp
import xyz.blacksheep.mjolnir.R

class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service does no work; it only exists to keep the process alive.
        // START_STICKY ensures it restarts if the system kills it.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Not a bound service.
        return null
    }

    private fun startForegroundInternal() {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        val notification: Notification = NotificationCompat.Builder(this, MjolnirApp.PERSISTENT_CHANNEL_ID)
            .setContentTitle("Mjolnir is active")
            .setContentText("Interception can be toggled in settings or via quick tile.")
            .setSmallIcon(R.drawable.ic_home)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW) // legacy devices
            .setSilent(true)
            .build()

        // Use ServiceCompat for backwards compatibility.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            0 // No special foreground type; we don't use location/camera/etc.
        )
    }

    private fun stopForegroundInternal() {
        // Remove notification and stop service.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
