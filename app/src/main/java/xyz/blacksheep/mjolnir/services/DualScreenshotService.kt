package xyz.blacksheep.mjolnir.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.blacksheep.mjolnir.R
import xyz.blacksheep.mjolnir.utils.BitmapStitcher
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.ScreenshotUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

class DualScreenshotService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 29) {
             ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createForegroundNotification(),
                if (Build.VERSION.SDK_INT >= 34) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                }
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                createForegroundNotification()
            )
        }
        
        serviceScope.launch {
            try {
                performDualCapture()
            } catch (e: Exception) {
                handleFailure(e)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun createForegroundNotification(): Notification {
        val channelId = "mjolnir_dss_capturing"
        val manager = getSystemService(NotificationManager::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Dual Screenshot Capturing",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
        }
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Capturing Dual Screenshot...")
            .setSmallIcon(R.drawable.ic_home)
            .setProgress(0, 0, true)
            .build()
    }

    private suspend fun performDualCapture() = coroutineScope {
        // 1. Discover IDs using Root/Shell (since standard discovery is insufficient for capture)
        val discoveredIds = ScreenshotUtil.discoverHardwareDisplayIds(this@DualScreenshotService)
        
        // We expect at least 2 IDs for a dual screen device. 
        // If fewer found, fall back to standard assumptions (0 and 1 or 0 and 4)
        val topId = if (discoveredIds.isNotEmpty()) discoveredIds[0] else 0L
        val bottomId = if (discoveredIds.size > 1) discoveredIds[1] else 4L
        
        DiagnosticsLogger.logEvent("DualScreenshot", "CAPTURE_START", "topId=$topId bottomId=$bottomId", this@DualScreenshotService)

        // 2. Capture Top (Main) - Using Root Shell
        val topBitmap = ScreenshotUtil.captureViaShell(this@DualScreenshotService, topId)
        
        // 3. Capture Bottom (Secondary) - Using Root Shell
        val bottomBitmap = ScreenshotUtil.captureViaShell(this@DualScreenshotService, bottomId)
        
        // 4. Fallback / Debug (Red/Blue)
        val finalTop = topBitmap ?: createDebugBitmap(topId.toInt(), isTop = true)
        val finalBottom = bottomBitmap ?: createDebugBitmap(bottomId.toInt(), isTop = false)

        // 5. Stitch
        val stitched = BitmapStitcher.stitch(finalTop, finalBottom)
        
        // 6. Save & Share
        val uri = saveBitmapToMediaStore(stitched)
        
        withContext(Dispatchers.Main) {
            postResultNotification(uri, stitched)
        }
    }
    
    private fun createDebugBitmap(displayId: Int, isTop: Boolean): Bitmap {
        val width = 1920
        val height = 1080 // Default assumption if capture fails
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (isTop) Color.RED else Color.BLUE)
        return bitmap
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "DualShot_$timestamp.png"
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        resolver.openOutputStream(uri).use { stream ->
            if (stream == null) throw IllegalStateException("Failed to open output stream")
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw IllegalStateException("Failed to compress bitmap")
            }
        }

        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return uri
    }

    private fun postResultNotification(uri: Uri, bitmap: Bitmap) {
        val channelId = "mjolnir_dualshot"
        val notificationId = Random().nextInt()
        val manager = getSystemService(NotificationManager::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Dual Screenshots",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                manager.createNotificationChannel(channel)
            }
        }

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val viewPendingIntent = PendingIntent.getActivity(
            this, 0, viewIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val sharePendingIntent = PendingIntent.getActivity(
            this, 1, Intent.createChooser(shareIntent, "Share DualShot"), PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(this, KeepAliveService::class.java).apply {
            action = KeepAliveService.ACTION_DELETE_SCREENSHOT
            data = uri
            putExtra(KeepAliveService.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val deletePendingIntent = PendingIntent.getService(
            this, notificationId, deleteIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Dual Screenshot Saved")
            .setSmallIcon(R.drawable.ic_home)
            .setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .bigLargeIcon(null as Bitmap?))
            .setContentIntent(viewPendingIntent)
            .addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)
            .addAction(android.R.drawable.ic_menu_delete, "Delete", deletePendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun handleFailure(e: Exception) {
        DiagnosticsLogger.logException("DualScreenshot", e, this)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "Dual Screenshot Failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2938
        // REMOVED: projectionResultCode/Data
    }
}
