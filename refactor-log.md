# Refactor Log

## 2026-02-20

### Completed
- Extracted core models into a neutral package:
  - `app/src/main/java/xyz/blacksheep/mjolnir/model/AppTheme.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/model/MainScreen.kt`
- Extracted launcher data + helpers:
  - `app/src/main/java/xyz/blacksheep/mjolnir/launchers/LauncherApp.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/launchers/LaunchableApps.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/launchers/LaunchableAppsUi.kt`
- Updated imports across the app to use the new packages.
- Split settings UI out of `SharedUI.kt` into focused files:
  - `app/src/main/java/xyz/blacksheep/mjolnir/settings/SettingsScreen.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/settings/SettingsNav.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/settings/MainSettingsScreen.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/settings/ToolSettingsScreen.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/settings/AppearanceSettingsScreen.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/settings/BlacklistSettingsScreen.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/settings/DeveloperSettingsScreen.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/settings/AboutDialog.kt`
  - `app/src/main/java/xyz/blacksheep/mjolnir/settings/DiagnosticsSummaryScreen.kt`
- Preserved the segmented theme selector and ROM directory UI behaviors.
- Kept `HomeLauncherSettingsMenu` in `SharedUI.kt` temporarily but made it public so `SettingsNavHost` can call it.

### Next
- Move `HomeLauncherSettingsMenu`, `AppSlotCard`, and `handleAppSelection` into a dedicated settings file.
- Extract Steam Generator UI to `steam/` and replace `UiStateSaver` with a `SavedStateHandle` ViewModel.
- Final cleanup of `SharedUI.kt` and imports.

### Completed (continued)
- Moved the Home Launcher settings UI into `app/src/main/java/xyz/blacksheep/mjolnir/settings/HomeLauncherSettingsMenu.kt`.
  - Includes `AppSlotCard`, `HomeLauncherSettingsMenu`, and `handleAppSelection`.
- Removed the corresponding blocks and orphaned comments from `app/src/main/java/xyz/blacksheep/mjolnir/settings/SharedUI.kt`.

### In Progress
- Began Steam Generator extraction to `steam/`:
  - Added `app/src/main/java/xyz/blacksheep/mjolnir/steam/SteamSearchState.kt`.
  - Added `app/src/main/java/xyz/blacksheep/mjolnir/steam/SteamGeneratorViewModel.kt` (SavedStateHandle-backed state).
  - Added `app/src/main/java/xyz/blacksheep/mjolnir/steam/SteamUi.kt` (Setup/Search/Manual/Overwrite UI).
- Updated `app/src/main/java/xyz/blacksheep/mjolnir/SteamFileGenActivity.kt` to use the new ViewModel and `SteamSearchState`.
- Simplified `app/src/main/java/xyz/blacksheep/mjolnir/settings/SharedUI.kt` to only `HamburgerMenu` and `HomeSetup`.
- Added lifecycle ViewModel Compose dependency and version:
  - `gradle/libs.versions.toml` (androidx-lifecycle-viewmodel-compose)
  - `app/build.gradle.kts` (implementation)

### Completed (continued)
- Finished Steam Generator UI move to `steam/` and removed steam-specific UI/state from `SharedUI.kt`.
- `SteamFileGenActivity` now uses `SteamGeneratorViewModel` with SavedStateHandle for search state.
- Cleaned up `HomeLauncherSettingsMenu` state (removed unused pending slot state).

### Compile Sanity
- Ran `./gradlew :app:compileDebugKotlin --no-daemon` successfully (warnings only).

### Safety Net + Debug Tile
- Added SafetyNet debug quick tile to bring safety-net activities to foreground and collapse the shade.
- SafetyNetActivity now uses a dedicated task affinity and remains excluded from recents.

### Display Listener
- Added a DisplayManager listener in `MjolnirApp` to mark pending SafetyNet re-seeding when displays are added (future profile switching hook).
- Centralized SafetyNet creation in `SafetyNetManager` and reused it from `HomeActivity`.

### Compiler Fixes (post-compile)
- Fixed DebugSafetyNetTileService to pass ActivityOptions via PendingIntent (API 26+), avoiding invalid startActivityAndCollapse signatures.
- Reworked SafetyNetManager task displayId checks to use reflection and safe fallbacks (avoids compile-time access to RecentTaskInfo.displayId).

### Compile Sanity (after fixes)
- Ran `./gradlew :app:compileDebugKotlin --no-daemon` successfully (warnings only).

### Debug Build
- Ran `./gradlew :app:assembleDebug --no-daemon` successfully (warnings only).

### SafetyNet Multi-Display Fix
- Made SafetyNetActivity intents unique per display (data URI) and added `FLAG_ACTIVITY_MULTIPLE_TASK` to allow per-display instances.
- Applied to both the debug quick tile and SafetyNetManager seeding.

### Compile + Build (SafetyNet multi-display)
- Ran `./gradlew :app:compileDebugKotlin --no-daemon` successfully (warnings only).
- Ran `./gradlew :app:assembleDebug --no-daemon` successfully (warnings only).

### SafetyNet Multi-Display Investigation
- Launch SafetyNetActivity using display-scoped context + NEW_DOCUMENT flag to reduce task reuse.
- Added logging for per-display launch requests and SafetyNetActivity onCreate (displayId/taskId/data).

### SafetyNet Multi-Display (tile launch)
- Routed DebugSafetyNetTileService through SafetyNetManager instead of PendingIntent per display.
- Removed display-scoped context + NEW_DOCUMENT for SafetyNet launches to align with DualScreenLauncher style.

### Debug Tile Shade Collapse
- Replaced CLOSE_SYSTEM_DIALOGS broadcast with startActivityAndCollapse + PendingIntent.
- Added try/catch fallback to avoid crashes if shade collapse fails.

### SafetyNet Display 0 Fallback
- Forced displayId=0 into SafetyNetManager launch list to validate multi-display instancing.

### SafetyNet Forced Dual Instance
- Added forced mode to SafetyNetManager (targetIds {0,4}) and display list logging.
- DebugSafetyNetTileService now calls SafetyNetManager with force=true to guarantee two launches.

### SafetyNet Dynamic Discovery Restored
- Removed forced displayId target set; back to DisplayManager-based discovery with logging.

### SafetyNet Task Detection Fix
- Adjusted task displayId detection: only skip when the task displayId matches exactly; no default-display fallback.

### Display Listener Reseed
- DisplayManager listener now re-seeds SafetyNet activities on display add/change with a short delay.

### Softlock Protection Indicators
- Added SafetyNetStatus helper for active display/running instance counts.
- Persistent notification appends "Softlock protection: enabled (x/y)" when Mjolnir is default home.
- Settings Home Launcher UI overlays a green/red dot on top/bottom screen cards.

### Compile + Build (Softlock Indicators)
- Ran `./gradlew :app:compileDebugKotlin --no-daemon` successfully (warnings only).
- Ran `./gradlew :app:assembleDebug --no-daemon` successfully (warnings only).

### Indicator Tweak
- SafetyNet indicator now only renders when active (green dot only).
- Rebuilt debug APK after indicator tweak.

### SafetyNet Detection Fix
- SafetyNet status now falls back to displayId parsed from SafetyNetActivity intent data.
- Settings overlay refreshes SafetyNet status on resume.
- Rebuilt debug APK.

### Indicator Update
- SafetyNet indicator shows green when active, black when inactive, only if Mjolnir is default home.
- Rebuilt debug APK.

### Onboarding Dots + Initial Refresh
- Home selection UI now refreshes SafetyNet status after a short delay and on resume.
- Added SafetyNet overlay dots to onboarding home selection cards.
- Rebuilt debug APK.

### Display Listener (Pending Only)
- Display listener now marks SafetyNet pending instead of launching, and skips reseed when keyguard is locked.

### SafetyNet Recents Flag
- Added FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS to SafetyNetActivity launches for OEM recents behavior.

### Debug Tile: Bring SafetyNet Forward
- Debug tile now explicitly brings SafetyNet activities to front on each display.
- Added SafetyNetManager.bringSafetyNetToFront for per-display foregrounding.

### Debug Tile: Force Foreground Instances
- Debug tile now forces new SafetyNet instances on each display to guarantee foregrounding.

### Recents Separation Prep
- Assigned recents task affinity to onboarding + file gen activities.
- Ensured Onboarding/Steam file gen launches use NEW_TASK to keep them out of Main task.

### Recents Exclusion Enforcement
- MainActivity and SafetyNetActivity now force their tasks excluded from recents at runtime.

### Cleanup + Changelog
- Removed display listener stub comment.
- Updated v0.2.7 changelog with SafetyNet, indicators, and display change handling notes.

### Cleanup Correction
- Restored display listener no-op comment.
- Updated v0.2.7 changelog highlights to include refactor work alongside SafetyNet changes.

### What's New Content
- Added `app/src/main/assets/changelogs/v0.2.7.txt` so the What's New dialog shows v0.2.7.

### Settings File Migration
- Added file-backed SettingsStore with one-time migration from SharedPreferences.
- All prefs now read/write via settings.json, config.ini, and blacklist.json in /Android/data/xyz.blacksheep.mjolnir/.
- Added Developer setting to regenerate settings files.

### Diagnostics Log Location
- Moved diagnostics logs to /Android/data/xyz.blacksheep.mjolnir/files/logs with external-files-path support.
- Kept internal files-path as fallback.

### Gesture Expansion (Phase 1)
- Added new gesture actions: FOCUS: Auto, FOCUS: Top App, and explicit TOP/BOTTOM/BOTH Home targets.
- Implemented focus-aware targeting using accessibility window inspection, with toast + diagnostics if focus cannot be resolved.
- Updated gesture labels across settings, onboarding, and toasts to use FOCUS/TOP/BOTTOM/BOTH prefixes and app names.
- Updated default gesture mappings to Type-A (Single: FOCUS Auto, Double: BOTH Auto, Triple: Recents, Long: FOCUS Home).
- Added diagnostics logging around focus resolution and new gesture dispatch paths.

### Gesture Focus Tracking
- Added accessibility-event focus tracker to cache last focused display ID.
- FOCUS: Auto/Top App now consult cached focus display before scanning windows.
- Added diagnostics logging for focus events, windowId mapping, and cache hits/misses.

### Gesture Presets (Phase 2)
- Added gesture preset files in /Android/data/xyz.blacksheep.mjolnir/gestures (type-a.cfg, type-b.cfg) with human-editable key=value format and inline comments.
- Added active gesture preset selection (ACTIVE_GESTURE_CONFIG) and migrated legacy gesture prefs into custom.cfg when present.
- Updated Home button handling to read actions and long-press delay from the active preset file.
- Updated onboarding and settings gesture UI to select and edit the active preset, plus long-press delay slider.

### Human-Editable File Comments
- Added inline comments to settings.json, config.ini, and blacklist.json outputs describing keys and legal values.
- Updated blacklist.json format to include comments while remaining backwards compatible with legacy array format.

### Gesture Presets UI/CRUD
- Added preset management controls (picker + edit/add/rename/delete) in Home Launcher settings.
- Renamed legacy custom.cfg to untitled.cfg and introduced Untitled naming for new presets.
- Added separate preset editor screen matching onboarding style, with long-press delay control.
- Updated onboarding gesture screen to be a preset viewer with separate edit screen, plus bottom fade overlay.
- Improved preset dropdown styling (constrained width, opaque menu background).
