package xyz.blacksheep.mjolnir

import android.app.Activity
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import xyz.blacksheep.mjolnir.services.DualScreenshotService
import xyz.blacksheep.mjolnir.services.KeepAliveService
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger

/**
 * A headless activity that handles the deletion of the source (bottom) screenshot.
 * 
 * **Why is this needed?**
 * Deleting a file created by another app (System UI) throws a `RecoverableSecurityException` on Android 10+.
 * This exception contains an `IntentSender` that must be launched by an Activity to show the system dialog.
 * A Service or BroadcastReceiver cannot launch this IntentSender directly.
 */
class ScreenshotActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        if (action == ACTION_DELETE_SOURCE) {
            handleDeleteSource(intent)
        } else if (action == DualScreenshotService.ACTION_DELETE_DUAL) {
            handleDeleteDual(intent)
        } else {
            finish()
        }
    }

    private fun handleDeleteDual(intent: Intent) {
        val uri = intent.data
        val notificationId = intent.getIntExtra(KeepAliveService.EXTRA_NOTIFICATION_ID, -1)

        if (uri == null) {
            finish()
            return
        }

        // Create a simple ImageView for the preview
        val imageView = ImageView(this)
        imageView.setImageURI(uri)
        imageView.adjustViewBounds = true
        imageView.setPadding(32, 32, 32, 32)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        // Custom Dialog mimicking System Dialog with Preview
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Allow Mjolnir to delete this photo?")
            .setView(imageView)
            .setPositiveButton("Allow") { _, _ ->
                // Delete silently since we own it
                try {
                    contentResolver.delete(uri, null, null)
                    Toast.makeText(this, "Dual screenshot deleted", Toast.LENGTH_SHORT).show()
                    
                    // Cancel notification since the main subject is gone
                    if (notificationId != -1) {
                        val manager = getSystemService(android.app.NotificationManager::class.java)
                        manager.cancel(notificationId)
                    }
                } catch (e: Exception) {
                    DiagnosticsLogger.logException("ScreenshotAction", e, this)
                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            .setNegativeButton("Deny") { _, _ ->
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    private fun handleDeleteSource(intent: Intent) {
        val uri = intent.data
        val notificationId = intent.getIntExtra(KeepAliveService.EXTRA_NOTIFICATION_ID, -1)

        if (uri == null) {
            DiagnosticsLogger.logEvent("ScreenshotAction", "DELETE_FAILED", "reason=null_uri", this)
            finish()
            return
        }

        DiagnosticsLogger.logEvent("ScreenshotAction", "DELETE_ATTEMPT", "uri=$uri", this)

        try {
            contentResolver.delete(uri, null, null)
            // If delete succeeds immediately (e.g., we have permission or are on older Android), finish.
            onDeleteSuccess(notificationId, intent)
        } catch (e: SecurityException) {
            // Check if we can recover (Android 10+ / API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException = e as? RecoverableSecurityException
                if (recoverableSecurityException != null) {
                    try {
                        // Launch the system prompt
                        // userAction requires API 29, actionIntent requires API 26. Both covered by check above.
                        startIntentSenderForResult(
                            recoverableSecurityException.userAction.actionIntent.intentSender,
                            REQUEST_CODE_DELETE,
                            null,
                            0,
                            0,
                            0
                        )
                        // Do NOT finish() here; wait for onActivityResult
                        return
                    } catch (senderEx: IntentSender.SendIntentException) {
                        DiagnosticsLogger.logException("ScreenshotAction", senderEx, this)
                        Toast.makeText(this, "Failed to launch delete prompt", Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }
                }
            }
            
            // Non-recoverable or API < 29
            DiagnosticsLogger.logException("ScreenshotAction", e, this)
            Toast.makeText(this, "Cannot delete: Permission denied", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            DiagnosticsLogger.logException("ScreenshotAction", e, this)
            Toast.makeText(this, "Error deleting file", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_DELETE) {
            if (resultCode == RESULT_OK) {
                // User allowed the deletion.
                val uri = intent?.data
                val notificationId = intent?.getIntExtra(KeepAliveService.EXTRA_NOTIFICATION_ID, -1) ?: -1
                
                if (uri != null) {
                    try {
                        contentResolver.delete(uri, null, null)
                        onDeleteSuccess(notificationId, intent)
                    } catch (e: Exception) {
                        DiagnosticsLogger.logEvent("ScreenshotAction", "RETRY_DELETE_FAILED", "uri=$uri", this)
                    }
                }
            } else {
                DiagnosticsLogger.logEvent("ScreenshotAction", "DELETE_DENIED", "resultCode=$resultCode", this)
            }
            finish()
        }
    }

    private fun onDeleteSuccess(notificationId: Int, originalIntent: Intent?) {
        Toast.makeText(this, "Source screenshot deleted", Toast.LENGTH_SHORT).show()
        
        if (notificationId != -1) {
            // Trigger notification update to remove the "Delete Original" button
            val resultUri = if (Build.VERSION.SDK_INT >= 33) {
                originalIntent?.getParcelableExtra(DualScreenshotService.EXTRA_RESULT_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                originalIntent?.getParcelableExtra(DualScreenshotService.EXTRA_RESULT_URI)
            }

            if (resultUri != null) {
                val updateIntent = Intent(this, DualScreenshotService::class.java).apply {
                    action = DualScreenshotService.ACTION_UPDATE_NOTIFICATION
                    putExtra(KeepAliveService.EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(DualScreenshotService.EXTRA_RESULT_URI, resultUri)
                }
                startService(updateIntent)
            }
        }
        
        finish()
    }

    companion object {
        private const val REQUEST_CODE_DELETE = 1001
    }
}
