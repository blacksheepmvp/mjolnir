# Changelog

All notable changes to this project will be documented in this file.

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
