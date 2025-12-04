# 📄 **Mjolnir v0.2.5c Specification – Rootless DSS (Auto-Stitch)**

**Version:** 0.2.5c
**Scope:** Implement a rootless "Auto-Stitch" screenshot system that reacts to system screenshots.
**Supersedes:** Replaces the capture strategy of 0.2.5b for non-root users.
**Core Philosophy:** "Work with the OS, not against it." Mjolnir reacts to the user taking a standard screenshot (Bottom screen) by instantly capturing the Top screen and stitching them.

---

# 1. **Overview**

The **Auto-Stitch** system allows Mjolnir to produce dual-screen screenshots without Root or Shizuku.

**The Flow:**
1.  User toggles **"DualShot: Auto"** via the Quick Settings Tile.
2.  User takes a standard system screenshot (Volume Down + Power, or standard gesture).
3.  The system captures the **Bottom Display** (focused screen) and saves it to MediaStore.
4.  Mjolnir's `KeepAliveService` detects the new image.
5.  Mjolnir instantly captures the **Top Display** using `AccessibilityService`.
6.  Mjolnir stitches both images and posts a notification.
7.  The notification offers options to delete the *Stitched* image or the *Original Source* (Bottom) image.

---

# 2. **Quick Settings Tile**

Update `MjolnirHomeTileService` to support a simple toggle.

*   **Label:** "DualShot"
*   **States:**
    1.  **Inactive (Off):** Icon dimmed. Label "DualShot: Off".
    2.  **Active (Auto):** Icon active. Label "DualShot: Auto".
*   **Storage:**
    *   Key: `KEY_DSS_AUTO_STITCH` (Boolean)
    *   Default: `false`
*   **Behavior:**
    *   Tap toggles the boolean preference and updates the tile state immediately.
    *   No dialogs, no complex modes.

---

# 3. **The Watcher: `KeepAliveService`**

The foreground service (`KeepAliveService`) is responsible for monitoring the MediaStore.

### 3.1 `ScreenshotObserver`
Implement a `ContentObserver` registered on `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`.

**Logic on Change:**
1.  **Check Preference:** If `KEY_DSS_AUTO_STITCH` is `false`, return immediately.
2.  **Fetch Latest Image:** Query MediaStore for the most recent image (sort by `DATE_ADDED` desc, limit 1).
3.  **Debounce:** Ensure we don't process the same ID twice. Maintain a `lastProcessedId` variable.
4.  **Filter (Crucial):**
    *   **Path Check:** Ensure the image is **NOT** inside Mjolnir's own output directory (`Pictures/Mjolnir/Screenshots`) to prevent infinite loops.
    *   **Dimension Check:** Verify the image width/height matches the **Bottom Display** (Display ID 1/4) metrics. If the user took a screenshot of the *Top* screen manually, we should ignore it (or handle it, but primarily we target the Bottom).
    *   **Timing:** Ensure `DATE_ADDED` is within the last 5 seconds.
5.  **Trigger:** If valid, start `DualScreenshotService`.

---

# 4. **The Worker: `DualScreenshotService`**

Update the service to accept a "Source URI" as input.

### 4.1 Input
*   **Intent Extra:** `EXTRA_SOURCE_URI` (The Uri of the bottom screenshot detected by the observer).

### 4.2 Execution Flow (`performDualCapture`)
1.  **Load Bottom Bitmap:**
    *   Decode bitmap from `EXTRA_SOURCE_URI`.
2.  **Capture Top Bitmap:**
    *   Call `ScreenshotUtil.captureViaAccessibility(context, topDisplayId)`.
    *   *Note:* This uses the existing rootless implementation found in `ScreenshotUtil.kt`.
3.  **Stitch:**
    *   `BitmapStitcher.stitch(topBitmap, bottomBitmap)`.
4.  **Save:**
    *   Save result to `Pictures/Mjolnir/Screenshots/`.
    *   Store the `resultUri`.
5.  **Notify:**
    *   Post the result notification, passing *both* `resultUri` and `sourceUri`.

---

# 5. **The Result Notification**

The notification must allow the user to manage both the new creation and the original source.

### 5.1 Actions
1.  **Share:**
    *   `ACTION_SEND` with `resultUri`.
2.  **Delete Dual (Stitched):**
    *   **Action:** `ACTION_DELETE_DUAL`
    *   **Behavior:** Silently deletes `resultUri` (Mjolnir owns this file).
    *   **UI:** Cancels notification.
3.  **Delete Bottom (Source):**
    *   **Action:** `ACTION_DELETE_SOURCE`
    *   **Behavior:** Attempts to delete `sourceUri`.
    *   **Mechanism:**
        *   Since Mjolnir did not create this file, `contentResolver.delete(sourceUri)` will throw `RecoverableSecurityException`.
        *   **Catch the exception:** Extract the `intentSender` from the exception.
        *   **Launch Intent:** `startIntentSenderForResult(...)` (requires a transparent activity or PendingIntent wrapper).
        *   **User Experience:** User sees system dialog: *"Allow Mjolnir to move this item to Trash?"* -> User taps Allow.
    *   **UI:** Updates notification (optionally) or cancels.

### 5.2 Implementation Detail regarding "Delete Bottom"
To handle the `RecoverableSecurityException` cleanly from a notification action:
*   The notification action should point to a broadcast receiver or a small headless Activity (`ScreenshotActionActivity`).
*   That component attempts the delete.
*   If the exception occurs, it immediately launches the system prompt.

---

# 6. **Permissions & Requirements**

*   **Accessibility:** Required for Top Screen capture (`HomeKeyInterceptorService` must be bound).
*   **Storage:**
    *   Android 13+: `READ_MEDIA_IMAGES`.
    *   Android 12-: `READ_EXTERNAL_STORAGE`.
    *   *Note:* Mjolnir likely already has these; ensure they are checked before enabling the Quick Tile.

---

# 7. **Summary of Changes**

1.  **`Constants.kt`**: Add `KEY_DSS_AUTO_STITCH`, `ACTION_DELETE_SOURCE`, `EXTRA_SOURCE_URI`.
2.  **`MjolnirHomeTileService.kt`**: Add toggle logic.
3.  **`KeepAliveService.kt`**: Add `ContentObserver` inner class and registration logic.
4.  **`DualScreenshotService.kt`**:
    *   Remove `captureViaShell` calls in the rootless path.
    *   Add logic to receive `Uri` and decode Bitmap.
    *   Call `ScreenshotUtil.captureViaAccessibility`.
    *   Update Notification builder to include new actions.
5.  **`ScreenshotActionActivity.kt`** (New/Optional): To handle the "Delete Bottom" security exception loop if a BroadcastReceiver context is insufficient.

This spec provides a complete, robust, rootless dual-screenshot experience.

***

# Implementation & Debugging Log (v2)

## Current Status (As of last test)
*   **Core Logic:** The rootless capture pipeline (Observer -> Bouncer -> Capture -> Stitch) is functionally **working after a manual app restart**.
*   **Capture Quality:** Excellent (Bouncer with 150ms delay clears artifacts successfully).
*   **Deletion UX:** Confirmed working as designed (custom dialog with preview for Dual, system dialog for Original).
*   **Critical Bugs Remaining (Pre-Force Close):**
    1.  **Zombie Observer:** The feature does not work on the first run after granting permissions. It requires a force-close and restart to function.
    2.  **Task Affinity/Redirection:** Helper activities (`DssPermissionActivity`, `ScreenshotActionActivity`) cause the UI to navigate back to Mjolnir's `MainActivity` instead of returning the user to their previous context (e.g., their game or launcher).

## Implemented Changes (Since Spec Inception)
1.  **Capture Strategy - The "Bouncer"**:
    *   The "delay-only" strategy was abandoned due to capturing UI artifacts (PIP).
    *   Implemented a "Bouncer" that uses `AccessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)` to dismiss the system screenshot UI, followed by a **150ms delay**.
    *   This provides a clean capture of the top screen.

2.  **Permission Flow**:
    *   `DualScreenshotTileService` now launches `DssPermissionActivity` if permissions are missing.
    *   `DssPermissionActivity` handles the `requestPermissions` flow and automatically enables the feature upon success.

3.  **Deletion UX**:
    *   `ScreenshotActionActivity` now handles all delete actions.
    *   A custom `AlertDialog` (styled to mimic the system) with an `ImageView` preview is now shown for the "Delete Dual" action.
    *   "Delete Original" action correctly triggers the system's `RecoverableSecurityException` dialog.

4.  **Notification Polish**:
    *   Notifications are now persistent (`setAutoCancel(false)`).
    *   After "Delete Original" is successful, the notification is updated to remove the button.
    *   A "Dual screenshot captured" Toast is shown on success.

## Current Debugging Focus
1.  **Zombie Observer:** The `ContentObserver` in `KeepAliveService` does not activate immediately after permissions are granted. An `ACTION_REFRESH_OBSERVER` intent has been implemented but may not be wired correctly or may be insufficient, leading to the "restart required" bug.
2.  **Task Affinity:** Helper activities are not correctly isolated from the main app task. The `launchMode` flags in `AndroidManifest.xml` may be incorrect or missing.

---

# Final Fixes (The "Silver Bullet")

**Issue Identified:**
The architecture relied on `KeepAliveService` running in a separate process (`:keepalive`) while the UI and Settings ran in the main process. `SharedPreferences` are process-local on Android, meaning the service never received updates when the UI changed settings (like `HOME_INTERCEPTION_ACTIVE` or `DSS_AUTO_STITCH`). It relied on its own stale cache until a restart forced a reload. This caused both the "Zombie Observer" and the "Notification lying about Home Config" bugs.

**Resolution:**
Removed `android:process=":keepalive"` from `AndroidManifest.xml`. This unified the process model, ensuring `SharedPreferences` listeners fire instantly across the entire app and eliminating race conditions.

**Status:**
**0.2.5c is now feature-complete and stable.** Home interception, persistent notification, and rootless DSS all function correctly without restarts.
