# Mjolnir Project – Architecture & Master Context

> **Status:** Active Development
> **Target Device:** AYN Thor (Dual-Screen Android Handheld)
> **Core Function:** Dual-Screen Home Launcher & Utility Suite

This document is the **single source of truth** for Mjolnir's architecture, behavior, and constraints. It consolidates previous specifications and architectural notes.

---

# 1. Core Constraints & Principles

These rules apply to all development on this project.

1.  **Target Hardware Assumptions:**
    *   **Display 0 (Top):** Primary display.
    *   **Display 1 (Bottom):** Secondary display.
    *   **Hardware Mirroring:** Using `MediaProjection` on Display 1 results in a mirrored Display 0 image. **`MediaProjection` is BANNED for DSS.**

2.  **Performance & Threading:**
    *   **No Blocking UI:** App list queries and icon loading must be prewarmed and cached.
    *   **Icon Cache:** Icons are cached in-memory (volatile) to ensure instant UI rendering.

3.  **Code Hygiene:**
    *   **Explicit Imports:** Do not rely on IDE auto-imports for new types.
    *   **No BOM Churn:** Do not change Compose/Kotlin versions without explicit authorization.
    *   **SharedPreferences:** Must use keys defined in `Constants.kt`. Never hardcode strings.

4.  **Process Model:**
    *   **Unified Process:** The app runs in a single process. `KeepAliveService` must **NOT** run in a separate process (e.g., `:keepalive`) to ensure `SharedPreferences` listeners fire instantly across UI and Service components.

---

# 2. Architecture Overview

Mjolnir consists of a launcher UI, a background persistence service, and an accessibility service for gesture interception.

## 2.1 Core Components

### `MjolnirApp.kt` (Application Root)
*   **Role:** App lifecycle root.
*   **Behavior:** Triggers background icon prewarming (`AppQueryHelper.prewarmAllApps`) on creation.

### `HomeActivity` (Previously MainActivity)
*   **Role:** The actual Launcher Activity that Android launches when Home is pressed.
*   **Behavior:**
    *   If `HOME_INTERCEPTION_ACTIVE` is true: Acts as a dummy/pass-through for the Accessibility Service to handle logic.
    *   If `HOME_INTERCEPTION_ACTIVE` is false (Basic Mode): Executes fallback logic (Both/Top/Bottom launch) directly.
*   **Launch Mode:** `standard` (required for dual displays), `fullSensor`, `resizeableActivity="true"`.

### `KeepAliveService.kt` (Foreground Anchor)
*   **Role:** Keeps the app process alive via a persistent foreground notification.
*   **New Role:** Acts as the **Screenshot Observer**.
*   **Behavior:**
    *   Monitors `MediaStore` for new images.
    *   Triggers "Auto-Stitch" DSS when a bottom-screen screenshot is detected.
    *   Observes `SharedPreferences` to update notification actions dynamically.

### `HomeKeyInterceptorService.kt` (Accessibility Service)
*   **Role:** Intercepts hardware Home button events.
*   **Behavior:**
    *   Detects Single/Double/Triple/Long presses.
    *   Dispatches actions based on user preferences.
    *   Provides `AccessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)` for the DSS "Bouncer" logic.

### `HomeActionLauncher.kt` (Multi-Display Logic)
*   **Role:** Launches activities on specific displays.
*   **Logic:** Uses `ActivityOptions.setLaunchDisplayId(0 or 1)`.
*   **Constraint:** Must handle cases where Display 1 is momentarily unavailable.

### `SharedUI.kt` (The Monolith)
*   **Role:** Contains almost all UI/Settings code.
*   **Status:** Scheduled for refactor/breakup in **v0.3.0**.
*   **Caution:** High risk of breakage. Modify with extreme care.

---

# 3. Feature Implementation Details

## 3.1 Dual-Screen Screenshot (DSS) – "Auto-Stitch"
**Strategy:** Rootless, Reactive, "Work with the OS."

*   **Concept:** Instead of fighting the hardware mirroring bug, Mjolnir reacts to the user taking a standard system screenshot (which captures the focused Bottom screen).
*   **Workflow:**
    1.  **User:** Toggles "DualShot: Auto" in Quick Settings.
    2.  **User:** Takes a standard screenshot (VolDown+Power).
    3.  **KeepAliveService:** Detects new image in `MediaStore`. Verifies it matches Bottom Display dimensions and isn't a Mjolnir output.
    4.  **Bouncer:** Triggers `GLOBAL_ACTION_BACK` (via Accessibility) + 150ms delay to clear the screenshot UI.
    5.  **Capture:** Captures Top Display via `AccessibilityService.takeScreenshot`.
    6.  **Stitch:** Combines Top (Accessibility) + Bottom (MediaStore Source) into one PNG.
    7.  **Result:** Notification offers to "Delete Dual" or "Delete Original (Source)".

## 3.2 Focus Management – `FocusStealerActivity`
**Problem:** Android does not treat overlay windows (`TYPE_APPLICATION_OVERLAY`) as focus owners, breaking `GLOBAL_ACTION_HOME` routing and game controller input on specific screens.

*   **Solution:** A transient, invisible `Activity` (`FocusStealerActivity`).
*   **Mechanism:**
    1.  `FocusHackHelper` stores a pending action (lambda).
    2.  Launches `FocusStealerActivity` on the target display (0 or 1).
    3.  Activity becomes top-resumed -> gains true focus.
    4.  Activity executes lambda -> finishes immediately.
*   **Use Case:** Routing Home commands to the correct screen; fixing "Focus Lock" bugs.

## 3.3 Gesture System
*   **Input:** Hardware Home button events timestamped in `HomeKeyInterceptorService`.
*   **Mapping:** User configurable (Single, Double, Triple, Long).
*   **Actions:** Launch App (Top/Bottom), Open Notification Shade, Quick Settings, Dual Screenshot, etc.

---

# 4. Roadmap & Onboarding Spec (v0.2.5)

## 4.1 Improved Onboarding (Spec)
**Goal:** Replace the static "HomeSetup" screen with a guided, state-aware flow.

### Core Rules
1.  **Trigger:** Fresh install, Invalid config, or User taps “Initialize Mjolnir Home”.
2.  **State Buffering:** **Do not write** to `SharedPreferences` until the flow commits. Use in-memory state (`OnboardingViewModel`).
3.  **Re-entry:** Always starts at Entry Screen. Does not wipe valid config unless committed.

### Flow
**1. Entry Screen:**
*   Welcome text + Options: **Basic**, **Advanced**, **No Home Setup**.
*   Diagnostics Toggle (with info bubble).

**2. BASIC Mode (No Permissions/Services):**
*   **Step B1:** Top/Bottom Home Selection (Reuse App Picker).
*   **Step B2 (Commit):** Write prefs. Set `HOME_INTERCEPTION_ACTIVE = false`.
    *   **Crucial:** `HomeActivity` handles logic. If Interception is OFF, it must fallback to: Both (if both slots set) / Top (if only top) / Bottom (if only bottom).
*   **Step B3:** Set Default Home -> `Settings.ACTION_HOME_SETTINGS`.

**3. ADVANCED Mode (Full Suite):**
*   **Step A1:** Notification Permission.
*   **Step A2:** Accessibility Service (Link to Settings).
    *   *Explanation:* Required for Gestures, DSS (Back/Bouncer), and reacting to screenshots.
*   **Step A3:** Top/Bottom Home Selection.
*   **Step A4:** Gesture Setup (Reuse existing UI).
    *   *Commit:* If valid, set `HOME_INTERCEPTION_ACTIVE = true` (buffered).
*   **Step A5:** DualShot Setup.
    *   Toggle "Enable DualShot now?". If Yes -> `dss_auto_stitch = true`.
    *   *Note:* Storage permission will be requested by system when tile activates.
*   **Step A6:** Set Default Home.

**4. NO HOME Mode:**
*   Exit flow immediately.

## 4.2 SharedUI Refactor (Target: v0.3.0)
*   **Goal:** Break `SharedUI.kt` into modular `settings/` package components.
*   **Concept:** Introduce `SettingsMenuScaffold`.
*   **Status:** Postponed to v0.3.0 to prioritize stability.

---

# 5. Pitfalls & Lessons Learned

1.  **MediaProjection on Thor:** Banned. It mirrors the main screen when targeting the secondary screen.
2.  **Separate Processes:** Do not run Services in `:keepalive` process. It breaks SharedPreferences synchronization.
3.  **Activity Icons:** Use `pm.getApplicationIcon(pkg)` instead of ActivityInfo icons to avoid white masks.
4.  **Blind Refactoring:** Never modify `SharedUI.kt` without understanding the full state tree.
5.  **Focus:** Overlays cannot steal focus reliable. Use `FocusStealerActivity`.

---

*End of Specification*
