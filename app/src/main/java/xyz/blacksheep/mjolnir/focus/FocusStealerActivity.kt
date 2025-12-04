package xyz.blacksheep.mjolnir.focus

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import xyz.blacksheep.mjolnir.utils.DiagnosticsLogger
import xyz.blacksheep.mjolnir.utils.FocusHackHelper

class FocusStealerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0) // Suppress entry animation
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        DiagnosticsLogger.logEvent("FocusStealer", "ACTIVITY_CREATED", "displayId=${intent.getIntExtra(EXTRA_DISPLAY_ID, -1)}", this)
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // Consume and run the action passed from the helper
                val action = FocusHackHelper.consumePendingAction()
                if (action != null) {
                    action()
                    DiagnosticsLogger.logEvent("FocusStealer", "ACTION_CALLBACK_EXECUTED", context = this)
                } else {
                    DiagnosticsLogger.logEvent("FocusStealer", "ACTION_NOT_FOUND", "No pending action to execute.", this)
                }
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent("FocusStealer", "ACTION_FAILED", "msg=${e.message}", this)
            } finally {
                // Ensure the activity is always closed.
                finish()
            }
        }, 50L) // 50ms is a brief but generally safe delay.
    }
    
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0) // Suppress exit animation
    }

    companion object {
        private const val EXTRA_DISPLAY_ID = "mjolnir_display_id"

        fun launchForDisplay(context: Context, displayId: Int) {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = dm.getDisplay(displayId)
            
            if (display == null) {
                DiagnosticsLogger.logEvent("FocusStealer", "LAUNCH_FAILED_INVALID_DISPLAY", "displayId=$displayId", context)
                return
            }

            val displayContext = context.createDisplayContext(display)
            val intent = Intent(displayContext, FocusStealerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_DISPLAY_ID, displayId)
            }

            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = displayId
            }
            
            try {
                DiagnosticsLogger.logEvent("FocusStealer", "LAUNCHING", "displayId=$displayId", context)
                displayContext.startActivity(intent, options.toBundle())
            } catch (e: Exception) {
                DiagnosticsLogger.logEvent("FocusStealer", "LAUNCH_FAILED", "msg=${e.message}", context)
            }
        }
    }
}
