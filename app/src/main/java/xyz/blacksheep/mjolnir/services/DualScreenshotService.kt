package xyz.blacksheep.mjolnir.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import xyz.blacksheep.mjolnir.EXTRA_SOURCE_URI
import xyz.blacksheep.mjolnir.HomeKeyInterceptorService
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
        
        // Handle Update Notification Action
        if (intent?.action == ACTION_UPDATE_NOTIFICATION) {
            val notificationId = intent.getIntExtra(KeepAliveService.EXTRA_NOTIFICATION_ID, -1)
            val resultUri = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_RESULT_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_URI)
            }
            
            if (notificationId != -1 && resultUri != null) {
                serviceScope.launch {
                    performUpdateNotification(resultUri, notificationId)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }
        
        val sourceUri = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_SOURCE_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_SOURCE_URI)
        }

        serviceScope.launch {
            try {
                if (sourceUri != null) {
                    performRootlessDualCapture(sourceUri)
                } else {
                    // Only run legacy capture if NOT an update action
                    if (intent?.action != ACTION_UPDATE_NOTIFICATION) {
                        performDualCapture()
                    }
                }
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

    private suspend fun performUpdateNotification(resultUri: Uri, notificationId: Int) {
        // Reload the bitmap to rebuild the notification
        val bitmap = withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(resultUri)?.use { 
                    BitmapFactory.decodeStream(it) 
                }
            } catch (e: Exception) {
                null
            }
        }

        if (bitmap != null) {
            withContext(Dispatchers.Main) {
                // Repost without sourceUri, effectively removing the "Delete Original" button
                postResultNotification(resultUri, bitmap, null, notificationId)
            }
        } else {
            // If file is gone, cancel notification
            withContext(Dispatchers.Main) {
                getSystemService(NotificationManager::class.java).cancel(notificationId)
            }
        }
    }

    private suspend fun performRootlessDualCapture(sourceUri: Uri) = coroutineScope {
        DiagnosticsLogger.logEvent("DualScreenshot", "ROOTLESS_CAPTURE_START", "source=$sourceUri", this@DualScreenshotService)
        
        // 1. Load Bottom Bitmap (Source)
        val bottomBitmap = withContext(Dispatchers.IO) {
             contentResolver.openInputStream(sourceUri)?.use { 
                 BitmapFactory.decodeStream(it) 
             }
        } ?: throw IllegalStateException("Failed to load bottom bitmap from $sourceUri")
        
        // 2. Bouncer: Dismiss PIP (via Global Back) + Delay
        DiagnosticsLogger.logEvent("DualScreenshot", "PERFORM_BOUNCER_START", context=this@DualScreenshotService)
        val bouncerSuccess = HomeKeyInterceptorService.instance?.performBack() == true
        if (!bouncerSuccess) {
             DiagnosticsLogger.logEvent("DualScreenshot", "BOUNCER_FAILED", "reason=service_null_or_fail", this@DualScreenshotService)
        }
        
        // Wait for back animation / PIP fade out (200ms is usually enough for system anims)
        delay(200)

        // 3. Capture Top (Accessibility)
        // Assuming Top is Display 0
        val topBitmap = ScreenshotUtil.captureDisplay(this@DualScreenshotService, 0)
        
        val finalTop = topBitmap ?: createDebugBitmap(0, true)
        
        // 4. Stitch
        val stitched = BitmapStitcher.stitch(finalTop, bottomBitmap)
        
        // 5. Save
        val resultUri = saveBitmapToMediaStore(stitched)
        
        // 6. Notify
        withContext(Dispatchers.Main) {
            postResultNotification(resultUri, stitched, sourceUri)
            Toast.makeText(this@DualScreenshotService, "Dual screenshot captured", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun performDualCapture() = coroutineScope {
        // Existing Shell-based logic
        // 1. Discover IDs
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
            postResultNotification(uri, stitched, null)
            Toast.makeText(this@DualScreenshotService, "Dual screenshot captured", Toast.LENGTH_SHORT).show()
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

    private fun postResultNotification(uri: Uri, bitmap: Bitmap, sourceUri: Uri?, existingId: Int? = null) {
        val channelId = "mjolnir_dualshot"
        val notificationId = existingId ?: Random().nextInt()
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("DualShot", uri)
        }
        val sharePendingIntent = PendingIntent.getActivity(
            this, 1, Intent.createChooser(shareIntent, "Share DualShot"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Update: "Delete Dual" now routes to ScreenshotActionActivity with a specific action
        val deleteDualIntent = Intent(this, xyz.blacksheep.mjolnir.ScreenshotActionActivity::class.java).apply {
            action = ACTION_DELETE_DUAL
            data = uri
            putExtra(KeepAliveService.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val deleteDualPendingIntent = PendingIntent.getActivity(
            this, notificationId, deleteDualIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Dual Screenshot Saved")
            .setSmallIcon(R.drawable.ic_home)
            .setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .bigLargeIcon(null as Bitmap?))
            .setContentIntent(viewPendingIntent)
            .setAutoCancel(false) // Persistent until swiped or deleted explicitly

        // Action 1: Share
        builder.addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)
        
        // Action 2: Delete Dual
        builder.addAction(android.R.drawable.ic_menu_delete, "Delete Dual", deleteDualPendingIntent)

        // Action 3: Delete Original (if available)
        if (sourceUri != null) {
             val deleteSourceIntent = Intent(this, xyz.blacksheep.mjolnir.ScreenshotActionActivity::class.java).apply {
                 action = xyz.blacksheep.mjolnir.ACTION_DELETE_SOURCE
                 data = sourceUri
                 putExtra(KeepAliveService.EXTRA_NOTIFICATION_ID, notificationId)
                 // Pass the Result URI so we can update the notification later
                 putExtra(EXTRA_RESULT_URI, uri)
             }
             val deleteSourcePendingIntent = PendingIntent.getActivity(
                 this, notificationId + 1, deleteSourceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
             )
             builder.addAction(android.R.drawable.ic_delete, "Delete Original", deleteSourcePendingIntent)
        }

        manager.notify(notificationId, builder.build())
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
        const val ACTION_UPDATE_NOTIFICATION = "xyz.blacksheep.mjolnir.ACTION_UPDATE_NOTIFICATION"
        const val EXTRA_RESULT_URI = "xyz.blacksheep.mjolnir.EXTRA_RESULT_URI"
        const val ACTION_DELETE_DUAL = "xyz.blacksheep.mjolnir.ACTION_DELETE_DUAL"
    }
}
