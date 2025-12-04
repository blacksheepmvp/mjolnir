package xyz.blacksheep.mjolnir.utils

import android.content.Context
import xyz.blacksheep.mjolnir.focus.FocusStealerActivity

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

    @Volatile
    private var pendingAction: (() -> Unit)? = null

    /**
     * Executes the onComplete lambda after forcing focus to the specified display.
     *
     * @param context Context to launch the new activity.
     * @param displayId The ID of the display to target.
     * @param onComplete The action to execute once focus has been acquired.
     */
    fun requestFocus(context: Context, displayId: Int, onComplete: (() -> Unit)?) {
        DiagnosticsLogger.logEvent(
            "FocusHackHelper",
            "REQUEST_FOCUS_WITH_ACTION",
            "displayId=$displayId",
            context
        )

        // Store the action and launch the activity. The activity will consume the action.
        pendingAction = onComplete
        FocusStealerActivity.launchForDisplay(context, displayId)
    }
    
    /**
     * Internal method for FocusStealerActivity to retrieve and clear the pending action.
     * This ensures the action is only executed once.
     */
    internal fun consumePendingAction(): (() -> Unit)? {
        return pendingAction.also { pendingAction = null }
    }
}
