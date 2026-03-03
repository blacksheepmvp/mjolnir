# Changelog

All notable changes to this project will be documented in this file.

---

# Mjolnir v0.2.7 - Dual-Screen Update

## Highlights
- **Softlock safety net**: Per-display SafetyNet activities plus a debug tile to surface them when needed.
- **Protection indicators**: Persistent notification now reports coverage; settings/onboarding show protection dots on top/bottom cards.
- **Display change handling**: Display listener marks safety-net pending without surfacing on wake.
- **Gesture expansion**: New BOTH/FOCUS routing options, plus BOTH: Home and FOCUS: Home.
- **Gesture presets**: Presets (Type-A/Type-B/Type-C) with unlimited custom presets and sharing.
- **Start on boot (Advanced only)**: Choose BOTH: Auto or BOTH: Home.
- **Config files moved**: Settings live in `/Android/data/xyz.blacksheep.mjolnir/` (`settings.json`, `blacklist.json`, `config.ini`).
- **Major refactor**: Large internal cleanup touching all UI/UX surfaces.

---

# Mjolnir v0.2.6a - The "Safety" Hotfix

This release is focused entirely on fixing bugs, closing loopholes, and improving the safety and stability of the onboarding process.

## 1. Panic & State Handlers
- **Invalid Basic Mode Detection**: Mjolnir now detects "Bottomless Pit" states (e.g., Frontend Top, Nothing Bottom) in Basic Mode at startup. Invalid configs are automatically wiped, and the user is routed back to Onboarding.
- **Advanced Mode Save Fix**: Fixed a critical bug where settings for certain Advanced configurations (e.g., "Nothing" Bottom) were not saved on the final Onboarding screen. Configuration is now saved reliably when requirements are met.

## 2. Onboarding Flow Hardening
- **Strict Basic Mode**: The "Next" button on the app selection screen is now disabled until **both** Top and Bottom apps are selected. A toast message will guide the user if they try to proceed with an incomplete selection.
- **No More Skipping**: The "Skip" buttons on the Notification and Accessibility permission screens have been removed. Users are now required to grant these permissions to proceed with Advanced setup.
- **Duplicate App Prevention**: Re-implemented the "swap" logic in the Onboarding app picker. Selecting an app that is already in the other slot will now correctly swap the two apps instead of creating a duplicate configuration.
- **Correct Default Logic (Advanced)**: The final "Set Default Home" screen in the Advanced flow now has corrected logic:
  - It correctly allows **Any** default home for standard dual-app setups.
  - It correctly enforces **Quickstep** as the default when the bottom screen is set to `<Nothing>`.

## 3. UI Fixes
- **Formatted Changelogs**: The "What's New" dialog now correctly parses and displays Markdown formatting, making the changelogs readable.
- **Safer Gesture Defaults**: New installations or reset configurations will now default to safer gestures (Single: Both, Double: Top, Triple: Recents, Long: Bottom) instead of "None".

---

### **Features**

* New **Onboarding 2.0** with clearer setup flow and improved reliability.
* **DualShot**: When enabled, taking a bottom screenshot automatically captures + stitches the top screen.
* Added **Basic Home Mode** for users who prefer a zero-permissions setup.
* New **“Default Home”** action available for Home-button behavior.
* **Quickstep** and **OdinLauncher** supported when used in allowed configurations.

---

### **UI / UX**

* Updated onboarding screens with rewritten text and clearer guidance.
* Home-Button Behavior screen changed from a list to a **2×4 grid** layout.
* Action pickers now display **icons**, including app icons for Top/Bottom apps.
* Improved **DualShot** status line with clearer “Active / Inactive” indicators.
* Main menu updated:
  • “Initialize Mjolnir Home” → **“Home Setup”**
  • Tools reorganized for clarity
* **Create Steam Files** now consolidates all Steam-related tools into one entry with a selection dialog.
* Added **“What’s New”** info bubble at the bottom-center.
* Small visual polish across menus, defaults, and toggles.

---

### **Bug Fixes**

(Pre-existing issues resolved in this version)

* Fixed Home-button actions occasionally targeting the wrong screen.
* Quick tiles and persistent notification now stay in sync.
* Default app blacklist now appears correctly on first load.
* Fixed a case where Mjolnir required a restart after onboarding.
* DualShot permission prompt now reliably appears when needed.
* Fixed onboarding screens occasionally freezing or failing to reopen the menu.
* Corrected behavior when returning from the system “Default Home” picker on the Thor.

---

### **Misc**

* Diagnostics logging improved and made more consistent.
* Added version-tracking preference for future update-notices.
* Various stability, responsiveness, and performance improvements.

---

## [0.2.3] - 2024-11-17

### 🔧 Major Fixes & Stability Improvements
- **Resolved the critical “Clear All kills home capture” bug.**  
  Mjolnir’s Home interception and persistent notification now survive the Thor’s aggressive Recents → Clear All behavior.
- **KeepAliveService moved to a dedicated :keepalive process**, preventing OS task-kill events from shutting it down.
- **Launcher activity removed from Recents** using `excludeFromRecents`, preventing package-level kill triggers.

### 🎮 Major Feature: Gesture Engine Update
- Added new **APP SWITCH action**, allowing any home gesture (tap, double-tap, long press) to trigger the Android Recents / App Switcher menu.

### 🧪 Diagnostics System
A complete diagnostics framework has been implemented.

- New **Capture Diagnostics** toggle.
- **Export / View / Delete** diagnostic logs from inside the app.
- **Plaintext, human-readable** logs.
- **Zero** private data is collected.
- **Nothing** gets uploaded or transmitted.
- Includes:
  - Home gesture timing
  - Service lifecycle events
  - Notification events
  - Settings snapshots
  - Launcher events
  - Accessibility lifecycle
  - Exceptions

### 🧭 Blacklist & App Picker Improvements
- Fully functional **App Blacklist** allowing users to prevent problematic apps (e.g., Quickstep) from being selected.
- Fixed filtering logic — Quickstep and other system apps now appear correctly in the full app list.
- Automatic **slot-swap** behavior: assigning the same app to both screens now swaps them instead of duplicating.
- Quickstep is Blacklisted by default (it doesn't like being called from Mjolnir). User may remove it at their own risk.

### 🌐 Notification & UX Updates
- Notification text now accurately reflects:
  - Interception state  
  - Diagnostics state  
  - Quick access to settings
- Minor UI logic improvements.

### 🔭 Future Direction
Mjolnir’s launcher engine will be separated from the toolkit/UI in an upcoming release.  
This will ensure:
- Stable, persistent home interception  
- Easy access to settings & SteamFileGen  
- Clean foundation for future tools  

## [0.2.1] - 2024-07-30

### Bug Fixes
*   Fixed major bugs related to the AYN button and the TCC panel.

## [0.2.0] - 2024-07-29

### Features
*   **Dual-Screen Home Launcher:** Mjolnir can now be set as a default home launcher.
    *   Set two different applications to launch on the top and bottom screens when the home button is pressed.
*   **Home Button Interceptor:** Includes an Accessibility Service and a Quick Settings tile to provide a workaround for a firmware bug on the AYN Thor that breaks the app switcher.
    *   Toggle the Quick Tile to switch between Mjolnir's dual-launch functionality and the default QuickStep launcher while preserving app switcher access.
    *   Requires Quickstep to be set as the default launcher to preserve app switching.

### Known Issues
*   **AYN Button Conflict:** After using the AYN button to open the TCC panel, the button may become unresponsive for closing it. Pressing the Home button twice will close the panel and restore the AYN button's functionality. This is a temporary workaround.

## [0.1.3] - 2024-07-28

### Features
*   Added a "Manual Entry" option in developer mode to allow for the creation of custom `.steam` files.
*   Implemented "Fire-and-forget" sharing: When sharing a URL from another app, Mjolnir now processes the request in the background without launching the UI. A toast notification confirms the result.

### Improvements
*   The Steam File Generator has been refined and is now considered a stable tool within the Mjolnir suite.
*   You can now select multiple `.steam` files at once to delete them in a single action.
*   Added a hamburger menu for less-used actions like Settings, About, and Quit.
*   Added an "About" dialog to display the app's version.
*   General UI cleanup and refinement for a more polished experience.
*   Sharing from the in-app steamdb.info browser now keeps the app open instead of closing it after the operation.

### Bug Fixes
*   Fixed a bug that caused the app to launch in a small, non-resizable window, and prevented it from responding to device rotation.
*   Corrected two typos in the code (`FLAG_MUTABLE` and `empty_set`) that were causing build errors.

## [0.1.2] - 2024-07-27

### Changed
- The Settings screen now uses a top app bar with a back button, consistent with modern Android design.
- The Settings screen content is now scrollable to accommodate various screen sizes and orientations.

### Fixed
- The "Share to" shortcut now correctly passes the URL to the app.
- Minor UI fixes
- Minor bug fixes

## [0.1.1] - 2024-07-26

### Added
- A new setting to automatically create the `.steam` file when a search is successful.

### Fixed
- The "Share to" shortcut now correctly passes the URL to the app.
- Pressing the back button from the settings menu now returns to the main screen instead of exiting the app.
