package xyz.blacksheep.mjolnir.utils

import android.content.Context
import xyz.blacksheep.mjolnir.focus.FocusStealerActivity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A utility to reliably execute an action with the focus pinned to a specific display.
 *
 * **Mechanism:**
 * Delegates to [FocusStealerActivity], which launches on the target display,
 * gains focus, executes the provided `onComplete` action, and immediately closes.
 *
 * **Use Cases:**
 * 1. Fixing the "Focus Lock" bug on AYN Thor by running an empty action on the top display.
 * 2. Ensuring GLOBAL_ACTION_HOME targets the correct display by passing the global action as the callback.
 */
object FocusHackHelper {

    private data class PendingRequest(
        val onAction: (() -> Unit)?,
        val onComplete: (() -> Unit)?
    )

    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()

    /**
     * Executes the onComplete lambda after forcing focus to the specified display.
     *
     * @param context Context to launch the new activity.
     * @param displayId The ID of the display to target.
     * @param onComplete The action to execute once focus has been acquired.
     */
    fun requestFocus(context: Context, displayId: Int, onComplete: (() -> Unit)?) {
        requestFocus(context, displayId, onComplete, null)
    }

    fun requestFocus(
        context: Context,
        displayId: Int,
        onAction: (() -> Unit)?,
        onComplete: (() -> Unit)?
    ) {
        val requestId = UUID.randomUUID().toString()
        DiagnosticsLogger.logEvent(
            "FocusHackHelper",
            "REQUEST_FOCUS_WITH_ACTION",
            "displayId=$displayId requestId=$requestId",
            context
        )

        pendingRequests[requestId] = PendingRequest(
            onAction = onAction,
            onComplete = onComplete
        )

        val launched = FocusStealerActivity.launchForDisplay(context, displayId, requestId)
        if (!launched) {
            val pending = pendingRequests.remove(requestId) ?: return
            try {
                pending.onAction?.invoke()
            } finally {
                pending.onComplete?.invoke()
            }
        }
    }
    
    /**
     * Internal method for FocusStealerActivity to retrieve and clear the pending action.
     * This ensures the action is only executed once.
     */
    internal fun consumePendingRequest(requestId: String?): Pair<(() -> Unit)?, (() -> Unit)?> {
        val pending = requestId?.let { pendingRequests.remove(it) }
        return (pending?.onAction) to (pending?.onComplete)
    }
}
