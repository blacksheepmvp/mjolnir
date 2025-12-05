Here’s the full v0.2.5 onboarding spec, end-to-end, with the “commit before home picker” rule baked in and all the constraints we discussed.

---

# Mjolnir v0.2.5 – Onboarding Flow Specification

## 0. Goals & Scope

**Goals**

* Give new users a clear, guided setup for:

    * Dual-screen “home” behavior (top/bottom apps).
    * Optional advanced features (home-button gestures + DualShot).
* Make it easy to *re-run* setup later (“Initialize Mjolnir Home”).
* Avoid spooking users with permissions; keep explanations simple and specific.
* Keep the flow robust against invalid configs and Android’s “boot to new home” behavior.

**Out of Scope for v0.2.5**

* Any special-case onboarding treatment for Quickstep/Odin.

    * They remain **blacklisted** and will not appear in the onboarding app picker.
* Any refactor of existing `SharedUI` beyond reusing existing picker / gesture components.
* Any complex “temporary invalid config” infra for special launchers (that’s a later version).

Onboarding is implemented as **new UI**, in a separate package (e.g. `onboarding`), but it may **reuse existing logic** (app picker, gesture config, tile helpers) where appropriate.

---

## 1. When Onboarding Runs

Onboarding should appear in these cases:

1. **Fresh install / first launch**

    * Example: no stored “home config completed” flag, or other obvious “no config yet” signal.

2. **Invalid / broken home configuration**, such as:

    * `HOME_INTERCEPTION_ACTIVE == true` but:

        * Accessibility service not running, or
        * Home tile disabled, or
        * Both top and bottom slots empty.
    * Any other state you already consider “invalid home config”.

3. **Manual re-entry**

    * User taps **“Initialize Mjolnir Home”** from settings.
    * Always starts onboarding from the very first screen (Entry Screen).

If onboarding is **cancelled or backed out**, the **previous valid configuration stays in effect**. Only a completed flow (Basic/Advanced/No Home) modifies the live configuration.

---

## 2. State Handling & Commit Rules

### 2.1 Buffered State

During onboarding, configuration changes must be held in **buffered state**, not written straight to the live prefs:

* Store user choices in an in-memory object (e.g. `OnboardingState` in a `ViewModel`).
* Examples of buffered fields:

    * `buffer.topAppPackage`
    * `buffer.bottomAppPackage`
    * `buffer.homeInterceptionActive`
    * `buffer.gestureConfig` (single/double/long/triple actions)
    * `buffer.dssAutoStitch`
    * Any “mode” state (Basic/Advanced/No Home).

**Do NOT** write these to `SharedPreferences` as the user clicks through the steps.

### 2.2 Exceptions (System-Level Things)

These are allowed to “take effect immediately” because they are OS-level, not internal prefs:

* Notification permission request.
* Accessibility service enabling (user toggles it in system UI).
* The DualShot tile being turned on/off by the user.

We still mirror any needed state into `OnboardingState` for consistency, but we accept that the OS is “live” here.

### 2.3 Commit Timing (Critical Rule)

For **Basic** and **Advanced** modes:

> **All buffered preferences must be written to disk *before* we launch the default home picker (`Settings.ACTION_HOME_SETTINGS`).**

Rationale:

* The system home picker usually does **not** return to Mjolnir.
* If we commit *after* launching it, we risk never applying the configuration.

So for Basic and Advanced flows:

1. Validate buffered config.
2. Final safety fixups (e.g., last-minute HOME_INTERCEPTION check).
3. Write buffered prefs → `SharedPreferences`.
4. Launch `Settings.ACTION_HOME_SETTINGS`.
5. Finish onboarding (e.g. `finish()`).

For **No Home** mode:

* We just write the small set of flags that mark onboarding as completed (if needed), then finish.
* No home picker is launched.

---

## 3. High-Level Flow Structure

The onboarding engine is a single wizard with three modes:

1. **Basic Home Setup**
2. **Advanced Home Setup**
3. **No Home Setup**

There is:

* **One Entry Screen** where the user chooses the mode and toggles diagnostics.
* Shared sub-screens where possible (e.g. the Top/Bottom app picker UI is reused in both Basic and Advanced).
* Consistent **Next / Back** navigation throughout the flow.

---

## 4. Entry Screen

### 4.1 Trigger

Shown whenever onboarding is invoked (fresh, invalid, or re-entry).

### 4.2 Content

* Title: **“Welcome to Mjolnir”**

* Body (paraphrased):
  “Mjolnir lets you choose what opens on your top and bottom screens, and optionally adds Home-button gestures and dual-screen screenshots.”

* **Mode selection buttons**

    * **Basic Home Setup**
    * **Advanced Home Setup**
    * **No Home Setup**

* **Diagnostics toggle** (on this same screen)

    * When toggled on:

        * Immediately enable diagnostics (write to prefs, initialize logger).
        * This is an acceptable exception to buffered state.
    * Info bubble text (intent, you can tweak wording later):

      > “Diagnostics writes a plain-text log file on your device to help debug issues.
      > Logs stay on your device and focus on app behavior, not personal content.”

Once the user chooses a mode (Basic/Advanced/No Home), the wizard transitions to that path.

---

## 5. BASIC Mode

**Goal:**
Dual-screen home behavior with **no permissions**, **no gestures**, **no DSS**. “Zero-permission mode” for cautious users.

### B1. Top / Bottom Home Selection

* **UI**: Reuse the existing app picker UI (the same one used today for top/bottom selection).

* **Logic**:

    * Respect the existing `APP_BLACKLIST` (Quickstep, Odin, Settings, etc. are excluded).
    * Allow:

        * Top-only
        * Bottom-only
        * Both set

* **Info bubble (intent)**:

    * Explain what “Top Home” and “Bottom Home” mean:

        * Top Home = app that opens on top screen when Mjolnir home is triggered.
        * Bottom Home = app that opens on bottom screen when Mjolnir home is triggered.

### B2. Commit Basic Config

At this step we:

1. **Derive buffered config**

    * `buffer.topAppPackage` / `buffer.bottomAppPackage` from the picker.
    * `buffer.homeInterceptionActive = false` (Basic mode does **not** use interception or gestures).

2. **Enforce runtime behavior for “no service” case**

   The app logic (outside onboarding, in MainActivity/HomeActivity or whatever the home entrypoint is) must ensure:

    * If `HOME_INTERCEPTION_ACTIVE == false` and the user launches Mjolnir via home:

        * If **both** top and bottom set → launch **both**.
        * If **only top** set → launch **top**.
        * If **only bottom** set → launch **bottom**.

   This ensures Mjolnir as default home is usable even without the accessibility service.

3. We **do not** commit prefs yet; that happens in B3 just before launching the home picker.

### B3. Set Default Home (Basic)

* **Text (intent)**:

  > “On the next screen, set Mjolnir as your default home.”

* **Button**: `Set default home`

**Implementation order:**

1. Validate buffered Basic config (at least one of top/bottom is non-null).

2. Run any final safety checks if desired.

3. **Commit buffered prefs to disk**:

    * Write `TOP_APP`, `BOTTOM_APP`, `HOME_INTERCEPTION_ACTIVE = false`, etc.

4. Launch the system home settings:

   ```kotlin
   startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
   ```

5. End onboarding (`finish()`).

---

## 6. ADVANCED Mode

**Goal:**
Full experience — notifications, accessibility-driven gestures, and DualShot.

### A1. Notification Permission

* **Purpose**: Needed for:

    * Persistent status notification (“Home button capture ENABLED / DISABLED”).
    * DualShot feedback (success/error messages).
* **Action**:

    * Request Android’s standard notification permission (for versions where this is required).
* **Info bubble (intent)**:

  > “Mjolnir uses notifications to show the current Home-capture status and to display feedback for DualShot.”

No logs about “not reading notifications”, no defensive wording — just what it’s used for.

### A2. Accessibility Service

* **Action**:

    * Show a screen with a “Enable Mjolnir Accessibility Service” button.
    * Button opens Accessibility settings to Mjolnir’s service entry.
    * After returning, detect whether the service is actually enabled.

* **Info bubble (intent)**:

  > “This service lets Mjolnir:
  > • Detect Home button presses so your gesture actions can run.
  > • Perform a quick ‘back’ action to clear the screen before capturing the top display.
  > • Help DualShot combine your top and bottom screens into a single image.
  >
  > Without this service, gestures and DualShot won’t work.”

Internally, this corresponds to the combination of AccessibilityService and KeepAliveService, but to the user it’s just “Mjolnir’s service”.

### A3. Top / Bottom Home Selection

Same UI and logic as **B1**, but in Advanced mode.

* **UI**: Reuse the existing app picker.
* **Blacklist**: Same `APP_BLACKLIST` applies (Quickstep / Odin / Settings hidden in v0.2.5).
* **Info bubble**: Same explanation as in Basic: what Top/Bottom Home are conceptually.

### A4. Gesture Setup

* **UI**: Reuse existing gesture configuration UI.

    * Single, double, long, triple actions.
    * Available actions include:

        * `DEFAULT_HOME`
        * `TOP_HOME`
        * `BOTTOM_HOME`
        * `BOTH_HOME`
        * `APP_SWITCH` (recents)

* **Info bubble (intent)**:

  > “Map Home button presses to actions like:
  > • Default Home – Let Android run its usual Home behavior.
  > • Top Home – Open your top-screen app.
  > • Bottom Home – Open your bottom-screen app.
  > • Both Home – Open both apps at once.
  > • App Switcher – Open the recent apps screen.”

* **Buffered result**:

    * The gesture selections are stored in `OnboardingState.gestureConfig`.

* **Derived flag**:

    * If config is valid (at least one of top/bottom present, gestures set to something meaningful), we set:

      ```text
      buffer.homeInterceptionActive = true
      ```

    * If clearly invalid (e.g. no apps set at all), we leave `buffer.homeInterceptionActive = false`.

        * Final safety check will correct this again before commit.

### A5. DualShot (DSS) Setup

* **Text (intent)**:

  > “DualShot creates a combined top + bottom screenshot.
  > When you take a bottom screenshot, Mjolnir captures the top screen and stitches them together.”

* **Info bubble (intent)**:

  > “To use DualShot:
  > • Turn on the DualShot quick tile.
  > • Use the system screenshot button on the bottom screen.
  > Many people place the DualShot tile near their usual screenshot control.”

  (You can tweak the “many people” wording if you want, but that’s the intent.)

* **Prompt**:

    * “Enable DualShot now?”

        * Buttons: **Yes** / **Not now**

* **Behavior**:

    * If **Yes**:

        * `buffer.dssAutoStitch = true`
        * Attempt to mark the DSS tile as “enabled/active” if the existing infrastructure permits.
    * If **Not now**:

        * `buffer.dssAutoStitch = false` (or leave default false).

* **Storage Permissions**:

    * **Not requested here.**
    * Storage/Media permissions are requested later, when the **DualShot tile** is actually activated by the user (this is already how v0.2.5 works).
    * Onboarding only explains how to use the feature; it doesn’t front-load storage permissions.

### A6. Set Default Home (Advanced)

* **Text (intent)**:

  > “Finally, choose your default Home app.
  > Mjolnir’s gestures and DualShot will keep working as long as the Accessibility service and the Home tile stay enabled, no matter which Home app you pick.”

* **Button**: `Set default home`
  Launches the system home picker via `Settings.ACTION_HOME_SETTINGS`.

**Implementation order:**

1. **Validate Advanced config**

    * Ensure at least one of top/bottom set.
    * Gestures config is consistent.
    * Any other “valid home” criteria you already use.

2. **Final safety pass**

   Before commit, enforce:

    * If **both** top and bottom are empty/null:

        * Force `buffer.homeInterceptionActive = false`.
    * If anything else obviously invalid for interception, also force it to false.
    * This is just a “last line of defense”; the wizard itself should normally keep things valid.

3. **Commit buffered prefs to disk**

    * Write:

        * `TOP_APP`
        * `BOTTOM_APP`
        * Gesture prefs
        * `HOME_INTERCEPTION_ACTIVE` (as resolved above)
        * `dss_auto_stitch`
        * Any “onboarding complete” flag you choose to track.

4. **Launch home picker**:

   ```kotlin
   startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
   ```

5. **Finish onboarding** (`finish()`).

No preference writes occur after launching the picker.

---

## 7. NO HOME Mode

**Goal:**
Let users skip Home setup but still use Mjolnir’s non-home tools (e.g. Steam stuff).

### N1. Screen Content

* Text (intent):

  > “You can skip Home setup for now.
  > Mjolnir’s other tools will still be available.
  > If you want to set up Mjolnir Home later, open Mjolnir and tap ‘Initialize Mjolnir Home’ in settings.”

* Button: `Finish`

### N2. Behavior

* On Finish:

    * Optionally set a flag like `onboardingSeen = true` so we don’t auto-show onboarding on every launch.
    * Do **not** touch home-related prefs (`TOP_APP`, `BOTTOM_APP`, `HOME_INTERCEPTION_ACTIVE`, etc.).
* No calls to `Settings.ACTION_HOME_SETTINGS`.
* No live config is mutated beyond that minimal “seen” flag.

---

## 8. Final Validation & Integration Notes

### 8.1 Final Sanity Check (Global)

Even outside onboarding, you can keep a simple sanity check in the core logic (e.g. when starting the KeepAlive/Accessibility behavior):

* If `HOME_INTERCEPTION_ACTIVE == true` and both top and bottom apps are null:

    * Treat that as invalid and behave as if interception is off.
    * Optionally log a diagnostics event and/or nudge the user to re-run onboarding.

This should rarely happen if onboarding is the only mutation path, but it’s a safe guardrail.

### 8.2 Onboarding Integration Points

* Launch onboarding:

    * On first app open where config is clearly absent.
    * When user taps “Initialize Mjolnir Home” in settings.
    * When you detect an invalid config and want to offer repair.

* When onboarding finishes:

    * The live prefs now represent a fully valid config for the chosen mode.
    * Normal app behavior resumes:

        * Basic:

            * Mjolnir as default home, no interception, no gestures, no DSS.
        * Advanced:

            * Interception + gestures + potential DSS (subject to tile + permissions).
        * No Home:

            * No home behavior, but other tools are usable.

---

If you want, next step after this spec would be a file-level implementation plan (packages, new classes, and how to hook into existing `MainActivity`/`HomeActivity`), but this should be everything Gemini needs to implement the flow correctly and non-weirdly.
