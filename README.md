<picture><img width="512" height="512" alt="mjolnir_icon" src="https://raw.githubusercontent.com/blacksheepmvp/mjolnir/main/app/src/main/res/drawable/ic_launcher_foreground.png" /></picture>

## 🔨 Mjolnir

---

## 🏠 Dual-Screen Home Launcher

Mjolnir allows you to transform your Home button into a powerful dual-screen controller.

### How it Works
*   **Accessibility Service:** Mjolnir uses an accessibility service to capture Home button inputs (and nothing else).
*   **Custom Configuration:** You configure exactly what happens when you press Home: launch an app on the top screen, the bottom screen, or both.
*   **Preserve App Switcher (AYN Thor):** For the AYN Thor, this service allows you to keep **Quickstep** as your default home app. This ensures you don't lose your Recents/App Switcher functionality while still enjoying a custom dual-screen home experience.
*   **Device Support:** While designed for the Thor, it should work on other dual-screen devices (confirmation pending).

### Setup Guide

1.  **Install:** Download and install the latest release from [this repository](https://github.com/blacksheepmvp/mjolnir).
    *   *(Optional) Add to [Obtanium](https://github.com/ImranR98/Obtainium) for automatic updates.*
2.  **Initialize:** Open Mjolnir and select **Initialize Mjolnir Home**.
3.  **Permissions:**
    *   Grant **Notification Permissions** so the background service can stay active.
    *   Enable the **Mjolnir Accessibility Service** when prompted to allow home button capture.
4.  **Quick Tile (Optional):** Add the **Mjolnir Home** quick tile to your notification shade to easily toggle the Home key override on/off.

### Configuration

1.  **Select Apps:** Go to **Mjolnir Home Settings** in the menu.
    *   Select the apps you want for your Top and Bottom screens.
    *   *Note:* By default, the list shows only launchers/frontends. You can remove the filter to pick any app installed on your device.
2.  **Customize Gestures:** Scroll down to configure actions for:
    *   Single Press
    *   Double Press
    *   Triple Press
    *   Long Press
    *   *Actions include: Top Screen Home, Bottom Screen Home, Both Screens, Open Recents, or Do Nothing.*
3.  **Double-Tap Delay:** Adjust the double-tap detection speed or use the system default.
4.  **Set Default Home:**
    *   **AYN THOR USERS:** Ensure **Quickstep** is set as your system default home app. This keeps your App Switcher working correctly. Mjolnir handles the rest.
5.  **Enable:** Use the toggle in the main menu or the Quick Tile to enable Mjolnir Home. Press Home and enjoy!

---

## 🛠️ Additional Tools: Steam File Generator

Mjolnir also includes a tool to streamline adding PC games to Android frontends like ES-DE and Beacon.

*   **Automated File Creation:** Quickly generates `.steam` files with the correct Steam AppID.
*   **Multiple Input Methods:** Create files by sharing a `steamdb.info` URL to Mjolnir or by manually entering the AppID.
*   **File Management:** Lists existing files, provides overwrite protection, and supports multi-select deletion.

Note: this tool is going to be separated from Mjolnir and released as a standalone app in future releases.

---

## 📜 Changelog
A complete history of all changes made to the project can be found in the [Changelog](CHANGELOG.md).

---

## 📝 Building the Project

This project requires **Android Studio** and the necessary SDKs for building. Clone the repository and sync Gradle to begin development.

---

## 🤝 Contributing

If you have suggestions, bug reports, or feature requests, please open an issue or leave a comment on the release video. Thanks for checking out Mjolnir!
