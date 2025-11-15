# Mjolnir Project – High‑Level Architectural & Behavioral Summary

This file serves as a **context pack** for future development, debugging, refactoring, or onboarding of an AI assistant or human collaborator. It intentionally avoids raw code and instead captures:

* What each module *is*, *does*, and *depends on*
* How the components interact
* What design decisions have been made and why
* What pitfalls we've encountered and must avoid in the future
* What behaviors are expected at runtime
* What assumptions about the AYN Thor dual‑screen environment shaped the design
* Future‑proofing notes

This is **not** implementation documentation. It is a cognitive map of the entire codebase.

---

# 📌 Core Architectural Overview

Mjolnir is an Android app designed specifically for **dual‑screen gaming handhelds** (especially the AYN Thor). It provides:

* A **dual-screen home launcher** (top and bottom screens launch separate activities)
* **Home button interception** using an AccessibilityService
* **Foreground service + persistent notification** to keep the app process alive
* User‑defined **gesture mapping** for single/double/triple/long Home presses
* UI for configuring the home apps and gesture actions
* An **in‑memory volatile icon cache** that is prewarmed on app startup
* Querying, filtering, and dynamically displaying installed apps with icons

The architecture heavily relies on Android’s multi‑display API (`setLaunchDisplayId`) and must carefully avoid behaviors that break multi‑screen compatibility.

---

# 📁 File‑by‑File System Summary

Below is a structured overview of each major file, what it contains, and its role.

---

# 1. `MjolnirApp.kt` – Application Root

### **Responsibility:**

* Runs when the Mjolnir process starts.
* Creates notification channels.
* Starts background prewarm of all app icons.

### **Key Behaviors:**

* Defines `onCreate()` which is executed once per app process lifetime.
* Launches a coroutine on `Dispatchers.IO` to execute `AppQueryHelper.prewarmAllApps(...)`.
* Because the app has a persistent foreground service, **the process stays alive even if the UI closes**, so the cache persists.

### **Why This Matters:**

* Speeds up UI app lists significantly.
* Reduces recomposition lag.
* Ensures Home launcher config UI loads instantly.

### **Pitfalls Avoided:**

* Don’t put UI logic here.
* Don’t block the main thread.
* Never remove the persistent notification setup.

---

# 2. `AndroidManifest.xml` – Process Anchoring & Activities

### **Responsibility:**

* Defines application components.
* Requests permissions.
* Marks activities as multi‑display compatible.
* Defines AccessibilityService.

### **Important Configurations:**

* `HomeKeyInterceptorService` declared with `BIND_ACCESSIBILITY_SERVICE`.
* `KeepAliveService` declared as a foreground service.
* `MainActivity` uses:

  * `launchMode="standard"` (required for dual displays)
  * `screenOrientation="fullSensor"`
  * `resizeableActivity="true"`
* Intent filters for HOME + LAUNCHER both point to `MainActivity` (after consolidation).

### **Pitfalls Avoided:**

* A dedicated `HomeActivity` caused the app to appear twice in the launcher.
* Must not use singleTask/singleInstance launch modes.

---

# 3. `HomeKeyInterceptorService.kt` – Accessibility‑Based Home Button Listener

### **Responsibility:**

* Listens for all Home button presses.
* Detects single/double/triple/long Home gestures.
* Dispatches appropriate actions (TOP_HOME, BOTTOM_HOME, BOTH_HOME).

### **Key Behaviors:**

* Uses system events from Accessibility.
* Uses timestamps to detect gesture types.
* Saves gesture actions in SharedPreferences using constants.
* Delegates actual launching to `HomeActionLauncher`.
* Provides optional haptic feedback.
* Auto‑launches BOTH_HOME on boot if user enabled that option.

### **Pitfalls Encountered:**

* Hardcoded preference keys caused major bugs; now uses constants.
* Haptics may not work on AYN Thor.
* Runs inside accessibility sandbox — must not crash.

---

# 4. `KeepAliveService.kt` – Foreground Process Anchor

### **Responsibility:**

* Keeps the process alive.
* Presents persistent notification.
* Ensures icon cache + logic survives UI swipes.

### **Why This Matters:**

* Android kills background processes frequently.
* Foreground service promotes app to “important” process state.
* Without this, icon cache and gesture detection would reset often.

---

# 5. `HomeActionLauncher.kt` – Multi‑Display App Launcher

### **Responsibility:**

* Launches apps on a specific display.
* Selects display 0 (top) or display 1 (bottom).
* Launches both in user‑defined order if doing dual launch.

### **Key Behaviors:**

* Reads `KEY_TOP_APP`, `KEY_BOTTOM_APP`.
* Reads `MainScreen` enum for which display is primary.
* Uses `ActivityOptions.makeBasic().setLaunchDisplayId(...)`.

### **Pitfalls Avoided:**

* Must use display IDs 0 and 1.
* Must use `ActivityOptions` not startActivity alone.
* Must avoid crashing when display 1 is unavailable.

---

# 6. `SharedUI.kt` – Large Composable UI & State Management

### **Responsibility:**

* Entire settings interface.
* App list pickers.
* Home button behavior settings.
* Double‑tap delay controls.
* Filtering (launcher apps vs all apps).

### **Key Components:**

* **HomeLauncherSettingsMenu()**: The massive central composable.
* **AppSlotCard()**: Visual representation of a chosen app.
* **Dropdown for picking apps** (ExposedDropdownMenuBox).
* **Three‑column grid** built with ConstraintLayout.
* **Gesture mapping controls** with `RadioButton` and enum selectors.

### **Important State Variables:**

* topApp / bottomApp
* showAllApps
* mainScreen
* gesture preferences (single/double/triple/long)
* customDoubleTapDelay + useSystemDelay

### **Pitfalls Encountered:**

* Compose imports broke after version changes.
* ConstraintLayout changed APIs (createGuidelineFromStart vs createVerticalGuideline).
* Dropdowns were invisible due to `.size(1.dp)` mistakenly applied.
* Image icons showed as white silhouettes due to wrong Drawable source.
* SharedUI has become extremely large; should be broken into feature modules.

---

# 7. `AppQueryHelper.kt` – App Metadata & Icon Cache

### **Responsibility:**

* Retrieves installed apps.
* Extracts labels, icons, package names.
* Provides filtered (launcher‑only) and unfiltered lists.

### **Key Behaviors:**

* Maintains a **global in‑memory icon cache** via `companion object`.
* queryLauncherApps(): returns only apps with LAUNCHER category.
* queryAllApps(): returns all launchable apps.
* prewarmAllApps(): loads all icons on background thread at startup.

### **Pitfalls Encountered:**

* Getting icons on demand caused 4‑second hangs.
* Must use `pm.getApplicationIcon(pkg)` instead of masking activity icons.
* LaunchIntent icons were white masks.

### **Result After Fixes:**

* App list loads instantly after prewarm.
* Icons are full color, not silhouettes.

---

# 8. SharedPreferences Keys (`Constants.kt`)

### **Responsibility:**

* All preference keys live here.
* Must be used **everywhere**, never hardcoded.

### **Common Keys:**

* KEY_TOP_APP
* KEY_BOTTOM_APP
* KEY_MAIN_SCREEN
* KEY_SHOW_ALL_APPS
* KEY_SINGLE_HOME_ACTION
* KEY_DOUBLE_HOME_ACTION
* KEY_TRIPLE_HOME_ACTION
* KEY_LONG_HOME_ACTION
* KEY_CUSTOM_DOUBLE_TAP_DELAY
* KEY_USE_SYSTEM_DOUBLE_TAP_DELAY
* KEY_AUTO_BOOT_BOTH_HOME

### **Pitfalls Avoided:**

* Hardcoded keys broke gesture selection entirely.
* Now all access must use constants.

---

# 🛑 Major Pitfalls to Avoid (Critical Observations)

### ❌ 1. Never modify SharedUI.kt blindly

It is large, fragile, and contains stateful Compose logic. Poor changes break everything.

### ❌ 2. Never downgrade Compose BOM unless absolutely necessary

Doing so destroyed many imports and APIs.

### ❌ 3. Always back up `.idea/` and `gradle/` before nuking workspace

We learned this the hard way.

### ❌ 4. Never rely on activity-specific icons

Use `pm.getApplicationIcon(pkg)` to avoid white mask icons.

### ❌ 5. Never put blocking operations inside Composable functions

App list queries must be cached and prewarmed.

---

# 🧠 Behavioral Summary of Gesture System

### Single Home → user‑mapped action

### Double Home → user‑mapped action

### Triple Home → user‑mapped action

### Long Home → user‑mapped action

Mapping stored in SharedPreferences.

Gesture detection in AccessibilityService uses timestamps.

Launch logic delegated to HomeActionLauncher.

---

# 📱 Dual Screen Launching Summary

* Uses `DisplayManager` to detect displays 0 and 1.
* Launching app on display 1 triggers dual‑screen behavior.
* Some apps may cause screen swapping if launched in opposite display (AYN Thor behavior).
* Dual launch order depends on `mainScreen` preference.

---

# 💾 Volatile Icon Cache Summary

### Cache type:

In‑memory, process‑bound, via `companion object`.

### Lifespan:

* survives UI swipes
* survives backgrounding
* survives user switching apps
* dies only when process dies

### Prewarm:

Runs on app startup in background thread.

---

# 🧱 Refactor Suggestions (Future Safe)

### 1. Break SharedUI.kt into feature modules:

* HomeLauncher UI
* Gesture UI
* Delay slider UI
* App dropdown UI
* App card UI

### 2. Extract state machines from UI files

Compose should not manage gesture business logic.

### 3. Add ViewModel for Home Launcher settings

Would simplify state and reduce recomposition.

### 4. Move AppQueryHelper icon cache into dedicated IconRepository

For clean architecture.

### 5. Create a performance test harness

Test prewarm timing on cold/warm starts.

---

# 🧭 Closing Summary (What an AI should know when helping)

Mjolnir is a dual‑screen launcher that:

* intercepts the Home button
* maps gestures to actions
* launches separate apps on top/bottom screens
* uses a KeepAliveService to keep process alive
* builds and prewarms an icon cache
* renders UI with Compose + ConstraintLayout

The largest technical risks are:

* incorrect Compose imports
* unstable alpha ConstraintLayout versions
* SharedUI over‑complexity
* android manifest misconfiguration

Any future AI working with this project should:

* ask before modifying SharedUI
* never change BOM version without reviewing entire dependency tree
* always check Constants.kt before modifying preference use
* respect dual‑display launch constraints
* treat prewarm behavior as essential

---

This summary captures the system as accurately as possible without including raw code. It should be enough for an AI or human to re‑engage this project without relearning the entire system from scratch.
