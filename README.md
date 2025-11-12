<picture><img width="512" height="512" alt="mjolnir_icon" src="https://raw.githubusercontent.com/blacksheepmvp/mjolnir/main/app/src/main/res/drawable/ic_launcher_foreground.png" /></picture>

## 🔨 Mjolnir

Mjolnir is a utility application designed specifically for the **AYN Thor** dual-screen Android gaming handheld, but it should work with any Android device running Android 7.0 or later.

This release, **v0.2.1**, introduces the core feature of Mjolnir: the **Dual-Screen Home Launcher**. It also includes the previously released **Steam File Generator** tool.

---

## ✨ Core Feature: Dual-Screen Home Launcher

Mjolnir can be set as your default home screen, allowing you to launch two different apps simultaneously—one on the top screen and one on the bottom—every time you press the Home button.

### Workaround for AYN Thor App Switcher Bug

Currently, a firmware bug on the AYN Thor breaks the Recents/App Switcher screen when a third-party launcher is set as default. Mjolnir provides a workaround to restore this functionality:

1.  **Add Quick Tile:** Add the "Mjolnir Home" tile to your Quick Settings panel.
2.  **Enable Service:** Tap the tile to open Accessibility settings and enable the "Mjolnir Home Button Interceptor" service. This allows Mjolnir to detect Home button presses.
3.  **Toggle Functionality:**
    *   **Tile ON:** Mjolnir's dual-launch function is active. QuickStep MUST be set as the default launcher. This tile will override it properly.
    *   **Tile OFF:** Your default launcher (e.g., QuickStep) will handle the Home button press.

This setup ensures you can use the dual-launcher feature while retaining full access to the App Switcher. This workaround will not be needed once AYN fixes the App Switcher bug in an OTA update.

---

## 🛠️ Additional Tools: Steam File Generator

Mjolnir also includes a tool to streamline adding PC games to Android frontends like ES-DE and Beacon.

*   **Automated File Creation:** Quickly generates `.steam` files with the correct Steam AppID.
*   **Multiple Input Methods:** Create files by sharing a `steamdb.info` URL to Mjolnir or by manually entering the AppID.
*   **File Management:** Lists existing files, provides overwrite protection, and supports multi-select deletion.

---

## 📜 Changelog
A complete history of all changes made to the project can be found in the [Changelog](CHANGELOG.md).

---

## 📝 Building the Project

This project requires **Android Studio** and the necessary SDKs for building. Clone the repository and sync Gradle to begin development.

---

## 🤝 Contributing

If you have suggestions or bug reports, please open an issue.