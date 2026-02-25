# SWAP concept (active app to opposite display)

Goal: Detect the active screen + active app, then attempt to launch the same app on the opposite display to simulate a top/bottom swap. This should be best-effort and heavily logged; success depends on app capabilities.

## Proposed detection flow
1. Use AccessibilityService windows to identify the active/focused window:
   - Prefer windows where `isFocused == true`, else `isActive == true`.
   - Capture `displayId` and `packageName`.
   - Log: displayId, packageName, windowId, isFocused, isActive, windowType.

2. Determine opposite display:
   - If `displayId == 0`, choose the first non-0 display from `DisplayManager.displays`.
   - If `displayId != 0`, opposite is display 0.
   - If no opposite exists, abort with a log entry.

3. Launch same app on opposite display:
   - `packageManager.getLaunchIntentForPackage(pkg)`
   - Add `FLAG_ACTIVITY_NEW_TASK`.
   - Use `ActivityOptions.makeBasic()` and `setLaunchDisplayId(oppositeId)`.
   - Start activity with options bundle.

## Expected limitations / failure modes
- Apps with `singleTask` / `singleInstance` or multi-display restrictions will refuse to spawn on another display; launch will be ignored or bring the existing task to foreground.
- Some apps lack launcher intents; cannot be re-launched.
- Without system-level privileges, moving an existing task to another display isn’t possible; only new launches can be targeted.
- Result is “best effort,” not guaranteed to swap.

## Logging recommendations
- Log focus resolution events (active/focused window) similar to FOCUS: Auto.
- Log launch attempt with: pkg, sourceDisplayId, targetDisplayId, launchIntentFound.
- Log failure cases with explicit reason.

## Future improvement (needs elevated privileges)
- If system APIs or shell commands become available, use `ActivityTaskManager.moveTaskToDisplay` or equivalent to move existing tasks rather than relaunching.
