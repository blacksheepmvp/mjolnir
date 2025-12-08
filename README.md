# **Mjolnir**

Latest Release: v0.2.4h [RELEASE]

Latest Pre-release: v0.2.6a [HOTFIX]

## ⚡ A Home Button Router for Multi-Display Android Devices

Mjolnir gives you **precise control** over what happens when you press the Home button — routing frontends or launchers to the top display, bottom display, or both.

Originally designed for the **AYN Thor**, it also works on single-screen devices as a powerful Home-button automation tool.

---

# 🚨 HOTFIX: v0.2.6a — Safe to Install

A rare onboarding edge case in older 0.2.5 builds allowed users to create an invalid Basic Mode configuration:

* One screen assigned to an app
* The other assigned to `<Nothing>`
* After reboot, Android launched Mjolnir into a voided display
* Users could not open Apps, Settings, Mjolnir, or uninstall

**v0.2.6a fixes this permanently.**

### Should you update?

| Your Version                    | Recommendation                                               |
| ------------------------------- | ------------------------------------------------------------ |
| **0.2.5 builds**                | **Upgrade immediately — these contain the soft-lock bug.**   |
| **0.2.4h or earlier**           | **0.2.6a is safe to upgrade to.**                            |
| Waiting for the Recovery Panel? | You may stay on **0.2.4h** until **v0.2.6b**, if you prefer. |

0.2.6a enforces strict onboarding validation and prevents invalid configurations from saving or launching.

---

# 🧠 How Mjolnir Works (Current Behavior)

Mjolnir supports two modes during onboarding:

---

## **Basic Mode (Safe, Simple)**

* Android’s default launcher remains unchanged
* Mjolnir intercepts Home only when toggled on
* Pressing Home routes apps to the top/bottom screens according to your configuration
* No elevated behavior and no launcher replacement

v0.2.6a enforces strict validation:

* `<Nothing>` is no longer allowed
* Both Top **and** Bottom must be valid apps
* Invalid Basic configs are detected at startup and rerouted to onboarding

---

## **Advanced Mode (Full Functionality)**

This mode gives you full control over screen routing, gestures, and Home override.

* Requires Notification + Accessibility permissions
* May require setting Mjolnir or Quickstep as default home depending on the chosen configuration
* Supports single, dual, or fallback frontends
* Supports multi-gesture Home actions

Advanced Mode is ideal for:

* Heavy customization
* Multi-frontend setups
* Using gestures to control both displays
* Users who want Mjolnir to orchestrate Home behavior more deeply

---

# 📦 Installation & Setup

### 1. **Download**

Get the latest release from the [Releases page](https://github.com/blacksheepmvp/mjolnir/releases).
*(Optional)* Add to [Obtanium](https://github.com/ImranR98/Obtainium) for auto-updates.

### 2. **Initialize**

Open Mjolnir → select **Initialize Mjolnir Home**.

### 3. **Choose a Mode**

* **Basic Mode** → simpler, safer, does not replace your launcher
* **Advanced Mode** → full Home routing control

### 4. **Grant Permissions**

You’ll be prompted for:

* **Notification** (keeps the service alive)
* **Accessibility** (required to capture Home events)

### 5. **Select Top & Bottom Frontends**

Basic Mode requires valid apps in both slots.
Advanced Mode allows more flexibility.

### 6. **Customize Gestures**

Defaults (safe for all users):

* **Single Tap → Both Home**
* **Double Tap → Top Home**
* **Triple Tap → Recents**
* **Long Press → Bottom Home**

### 7. **(Optional) Quick Tile**

Add the **Mjolnir Home** quick tile to toggle behavior on/off instantly.

---

# ⚙️ Features

* Route Home presses to:

    * Top screen
    * Bottom screen
    * Both screens
    * Recents menu
    * Custom bindings via gestures
* Separate Basic / Advanced onboarding flows
* Valid configuration enforcement (new in 0.2.6a)
* Top–Bottom app swapping logic
* Multi-screen routing powered by Android 10+ display APIs
* Optional Quick Tile entry point
* Included utilities for gaming frontends

---

# 🔧 Steam File Generator (Included Utility)

A helper tool for frontends like ES-DE and Beacon:

* Generates `.steam` metadata files
* Accepts SteamDB links via Android’s Share menu
* Prevents duplicates
* Supports bulk deletion

This tool will be spun off into its own app in a future release.

---

# 🧭 Coming in v0.2.6b — Emergency Recovery Panel

v0.2.6a **prevents** invalid states.
v0.2.6b will add a **universal escape hatch**:

* Activated via gesture (no UI required)
* Options to:

    * Re-run onboarding
    * Clear configuration
    * Open Settings (top or bottom)
    * Export logs
    * Trigger a fallback safe-mode window
    * Attempt uninstall
* Works even if:

    * Accessibility is off
    * Notifications are killed
    * Config is corrupted
    * UI is half-rendered
    * Mjolnir is not default home

This feature permanently eliminates soft-lock scenarios.

---

# 📜 Changelog

See **[CHANGELOG.md](CHANGELOG.md)** for a full version history.

---

# 🛠 Building

Open in Android Studio → Sync Gradle → Run.

---

# 🤝 Contributing

Open an issue for bugs, requests, or feedback.

Buy me a coffee: **[https://ko-fi.com/xyzblacksheep](https://ko-fi.com/xyzblacksheep)**