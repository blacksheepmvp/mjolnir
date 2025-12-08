# Mjolnir Debugging Session - Status & Plan

## 1. Current Status
We are investigating a critical bug in the Settings menu overlay where the menu becomes "non-responsive" after specific navigation sequences. Previous attempts to fix this via layout modifiers (`Box`, `fillMaxSize`) have failed, indicating the root cause is not rendering size or z-ordering, but rather a **state/composition desynchronization** tied to back navigation.

Additionally, a regression has been observed where closing the app now crashes the Accessibility Service (`HomeKeyInterceptorService`).

## 2. The Problem Detail

### A. The "Stuck" Settings Menu
*   **Symptoms:** After opening Settings, navigating to a submenu (e.g., Appearance), and returning to the Dashboard, the Settings menu often fails to open again.
*   **Log Evidence:**
    *   `MENU_CLICKED` events show `currentShowSettings=true`, proving the state variable is toggling correctly.
    *   However, clicks are falling through to the Dashboard (underneath), proving the Settings overlay is **not present** in the UI tree or is effectively invisible/detached.
    *   **Smoking Gun:** The logs contain `OnBackInvokedCallback is not enabled for the application`. This suggests that the `BackHandler` logic inside `SettingsScreen` (which relies on newer Android back handling APIs) is not receiving events correctly, leading to a desync where the navigation stack or overlay state isn't cleaned up.

### B. The Service Crash
*   **Symptoms:** Swiping away Mjolnir from Recents causes the Accessibility Service to crash/close.
*   **Likely Cause:** This is likely a side effect of the process being killed aggressively or logic in `onTaskRemoved`/`onDestroy` that attempts to stop the `KeepAliveService` incorrectly.

## 3. What We Have Tried (And Why It Failed)

1.  **Layout Layering (`Box` wrapper in `MainActivity`)**:
    *   *Hypothesis:* The overlay was rendering behind the dashboard.
    *   *Result:* Failed. Logs show clicks passing through, meaning the overlay isn't just behind; it's likely not rendering.
2.  **Layout Sizing (`fillMaxSize` modifiers)**:
    *   *Hypothesis:* The `NavHost` was collapsing to 0x0 size.
    *   *Result:* Failed. Since `NavHost` inside `SettingsScreen` wasn't modified directly in `SharedUI.kt`, the wrapper in `MainActivity` wasn't enough to force the internal graph to fill space if it was detached or in a bad state.
3.  **Theme Safety (`SideEffect` try-catch)**:
    *   *Hypothesis:* Theme changes were crashing the Window composition on secondary screens.
    *   *Result:* Good for stability, but didn't fix the menu bug.

## 4. The Plan (Next Steps)

We will pivot from layout fixes to **lifecycle and navigation fixes**.

### Step 1: Enable Predictive Back
The logs explicitly warn that `OnBackInvokedCallback` is not enabled. We need to enable this in the manifest to ensure the `BackHandler` inside Compose works reliably with the system back gesture, ensuring the `onClose` cleanup logic actually runs.

**Instruction:**
Open `app/src/main/AndroidManifest.xml`.
Add `android:enableOnBackInvokedCallback="true"` to the `<application>` tag.

```xml
<application
    android:name=".MjolnirApp"
    android:enableOnBackInvokedCallback="true" 
    ... >
```

### Step 2: Composition Logging (Verification)
We need to prove if `SettingsScreen` is even entering the composition tree when `showSettings=true`.

**Instruction:**
Open `app/src/main/java/xyz/blacksheep/mjolnir/settings/SharedUI.kt`.
Add a logging effect at the very top of the `SettingsScreen` composable.

```kotlin
@Composable
fun SettingsScreen(...) {
    // Add this at the top
    LaunchedEffect(Unit) {
        DiagnosticsLogger.logEvent("SettingsScreen", "COMPOSED", "Entered Composition", LocalContext.current)
    }
    
    val navController = rememberNavController()
    // ...
}
```

### Step 3: Navigation Stack Cleanup (If Step 1 fails)
If enabling the callback doesn't fix it, the internal `NavController` inside `SettingsScreen` might be retaining a stale state. We may need to ensure the `NavHost` starts fresh or explicitly handles the back stack pop when the overlay closes.

*Action:* Review `BackHandler` logic in `SharedUI.kt` to ensure it calls `onClose` *only* when the stack is empty, and verify `NavHost` isn't trapping the back event.

### Step 4: Address Service Crash
Once the UI is stable, we will review `HomeKeyInterceptorService.kt`'s `onDestroy` logic to ensure it handles `KeepAliveService` stopping gracefully without crashing the accessibility process.
