# Changelog

All notable changes to this project will be documented in this file.

## [0.1.3] - 2024-07-28

### Features
*   Added a "Manual Entry" option in developer mode to allow for the creation of custom `.steam` files.
*   Implemented "Fire-and-forget" sharing: When sharing a URL from another app, Mjolnir now processes the request in the background without launching the UI. A toast notification confirms the result.

### Improvements
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
