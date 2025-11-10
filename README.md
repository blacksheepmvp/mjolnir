<picture><img width="512" height="512" alt="mjolnir_icon" src="https://raw.githubusercontent.com/blacksheepmvp/mjolnir/main/app/src/main/res/drawable/ic_launcher_foreground.png" /></picture>

## 🔨 Mjolnir

Mjolnir is a utility application designed specifically for the **AYN Thor** dual-screen Android gaming handheld - but should work with all Android devices running Android 7.0 or later.

This initial release, **v0.1.2**, delivers the **Steam File Generator** tool, streamlining the process of adding your emulated Steam library to frontends (currently ES-DE and Beacon Game Launcher are supported).

---

## ✨ Features in v0.1.2: Steam File Generator

This version focuses on providing a robust and seamless way to create the necessary files to launch PC games through your Android frontend of choice.

* **Automated File Creation:** Quickly generates `.steam` files containing the necessary Steam AppID to launch games.
* **Steam API Integration:** Fetches official **game names** and **header images** from the public Steam Store API.
* **Dual Input Methods:**
    * **Share Intent:** Accepts shared URLs from SteamDB.info and automatically extracts the AppID.
    * **Manual Entry:** Allows direct input of a Steam AppID.
* **Robust File Management:**
    * Lists all existing `.steam` files in the chosen directory.
    * Includes a safe **overwrite confirmation dialog** that shows both the old and new AppIDs before proceeding with file creation.
* **First-Run Setup:** Prompts the user to select and grant persistent read/write access to their **ROMs directory** on first launch.
* **Theming and Preferences:**
    * The Settings menu allows the user to **change the ROMs directory** at any time and select a Light, Dark, or System default theme.
    * The settings screen is now fully scrollable and features a modern back button in the top app bar.

---

## 🚀 How to Use: Generating .steam Files

Mjolnir is designed for simplicity, allowing you to add PC games to your Android frontend's library via two easy entry points.

### Step 1: Input the Game Data (Choose A or B)

| Path A: Via Share 🔗                                                                          | Path B: Manual AppID Entry ⌨️                                             |
|:----------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------|
| **1.** Navigate to the game on **`steamdb.info`** in your browser.                            | **1.** Open Mjolnir and locate the **AppID input field**.                 |
| **2.** Use your browser's **"Share..."** function to send the game's URL directly to Mjolnir. | **2.** Find the game's **AppID** (a unique number) on **`steamdb.info`**. |
| **3.** Mjolnir will automatically grab the AppID and game title.                              | **3.** Type the AppID into the field and tap **"Search"**.                |

### Step 2: Generate and Complete the File

1.  **Generate the File:** Tap **"Generate .steam file"**.
    * **Directory Setup:** If you haven't set one, Mjolnir will prompt you to specify your **ROMs directory**. This is where your frontend looks for Steam games, and you will need to grant file access permissions.
2.  **Refresh Frontend:** Once the file is created, refresh your frontend's game list (e.g., in ES-DE or Beacon), and your Steam game will now appear.

---

## 📜 Changelog
A complete history of all changes made to the project can be found in the [Changelog](CHANGELOG.md).

---

## 🛠 Project Status & Development Roadmap

Mjolnir is built to be a collection of sparsely-related tools for dual screen Android handhelds, inspired by the AYN Thor. This initial release is the first tool in that kit.

### Next Major Milestone (v0.2.0)

The next version will introduce the app's core function: transforming Mjolnir into a **"meta-launcher"** utility.

* **Dual-Screen Home Launcher:** Mjolnir will be configurable as a Home Launcher, allowing the user to select and launch **two separate apps** (e.g., two different launchers) when the Home button is pressed—one for the top screen and one for the bottom screen.
* **Refactoring:** The Steam File Generator will be moved into its own dedicated Activity/Tool and accessible via a hamburger menu.
* **Deep Linking:** Sharing a URL to Mjolnir will continue to open the Steam File Generator tool directly.

---

## 📝 Building the Project

This project requires **Android Studio** and the necessary SDKs for building. Clone the repository and sync Gradle to begin development.

*(Further instructions on cloning, building, and installation would go here.)*

---

## 🤝 Contributing

If you have suggestions or bug reports, please open an issue.