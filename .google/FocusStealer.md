# Mjolnir Spec: `FocusStealerActivity` for Robust Focus Handling

**Version:** 2.0
**Scope:** Replace the unreliable `TYPE_APPLICATION_OVERLAY` focus hack with a transient, invisible `Activity` to reliably steal focus. This new implementation will be generic, allowing it to be used for both routing `GLOBAL_ACTION_HOME` and fixing the "Focus Lock" controller bug.

## 1. Problem Summary

The existing overlay-based `FocusHackHelper` is unreliable. Android does not treat overlay windows as true "focus owners" for routing global actions or system input. This causes `GLOBAL_ACTION_HOME` to fire on the wrong display and fails to consistently solve the controller focus lock issue.

## 2. Solution: The Generic "Focus Stealer" Activity

We will replace the overlay hack with a new, dedicated `FocusStealerActivity`. This activity will be launched on a specific display to steal focus, execute a designated action, and then immediately disappear.

**Mechanism:**
1. The `FocusHackHelper` will be given a lambda (`onComplete`) representing the action to execute.
2. The helper will store this lambda in a static `pendingAction` variable.
3. It will then launch `FocusStealerActivity` on the target display.
4. The activity, being a real `TYPE_APPLICATION` window, will become top-resumed and gain legitimate focus.
5. In `onResume`, the activity will retrieve and execute the `pendingAction` from `FocusHackHelper`.
6. It will immediately `finish()` after the action is dispatched.

This creates a generic, reliable, and non-visible way to execute any action in the context of a specific display's focus.

## 3. Implementation Details

### 3.1. New File: `FocusStealerActivity.kt`

Create a new file at `app/src/main/java/xyz/blacksheep/mjolnir/focus/FocusStealerActivity.kt`.

**Contents:**

```kotlin
package xyz.blacksheep.mjolnir.focus

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import xyz.blacksheep.mjolnir.HomeKeyInterceptorService
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
```

### 3.2. Theme & Style Updates in `themes.xml`

Append the following styles to `app/src/main/res/values/themes.xml`:

```xml
    <!-- Theme for the focus-stealing activity. It's fully translucent and has no animations. -->
    <style name="Theme.Mjolnir.FocusStealer" parent="@android:style/Theme.Translucent.NoTitleBar.Fullscreen">
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowIsFloating">false</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowAnimationStyle">@style/Animation.Mjolnir.NoAnimation</item>
    </style>

    <!-- A style that disables all activity transition animations. -->
    <style name="Animation.Mjolnir.NoAnimation" parent="@android:style/Animation">
        <item name="android:activityOpenEnterAnimation">@android:anim/none</item>
        <item name="android:activityOpenExitAnimation">@android:anim/none</item>
        <item name="android:activityCloseEnterAnimation">@android:anim/none</item>
        <item name="android:activityCloseExitAnimation">@android:anim/none</item>
    </style>
```

### 3.3. `AndroidManifest.xml` Registration

Add the following `<activity>` declaration inside the `<application>` tag in `app/src/main/AndroidManifest.xml`:

```xml
        <activity
            android:name=".focus.FocusStealerActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:taskAffinity=""
            android:theme="@style/Theme.Mjolnir.FocusStealer" />
```

### 3.4. Update `HomeKeyInterceptorService.kt`

Add a public method to allow the `onComplete` lambda (executed by `FocusStealerActivity`) to trigger `GLOBAL_ACTION_HOME`.

Add this method inside `HomeKeyInterceptorService` (located in `xyz.blacksheep.mjolnir`):

```kotlin
    /**
     * Public method to allow external components to trigger the GLOBAL_ACTION_HOME
     * in the context of this accessibility service.
     */
    fun performGlobalHomeAction() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
```

### 3.5. Update `FocusHackHelper.kt`

Replace the entire contents of `FocusHackHelper.kt` with the following generic implementation.

```kotlin
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
```
