# **Mjolnir**

## ⚔️ Mjolnir Dual-Screen Update

Mjolnir is a Home button router built for multi-display Android devices.  
With **BOTH: Home**, dual-screen frontends (iisu, cocoon, console launcher, etc.) can launch both screens with a single button press.  
With **FOCUS: Home**, you can do the same but only for the selected screen.  
Prefer mixing single-screen activities? **BOTH: Auto** and **FOCUS: Auto** are here.  
**TOP/BOTTOM** options remain for full customization.

[FAQ](./FAQ.md) | [Releases](https://github.com/blacksheepmvp/mjolnir/releases) | [Add to Obtanium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22xyz.blacksheep.mjolnir%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fblacksheepmvp%2Fmjolnir%22%2C%22author%22%3A%22blacksheepmvp%22%2C%22name%22%3A%22Mjolnir%22%2C%22apkUrls%22%3Anull%2C%22otherAssetUrls%22%3Anull%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%7D%22%2C%22overrideSource%22%3A%22GitHub%22%2C%22allowIdChange%22%3Afalse%7D) | [Discord](https://discord.gg/SByZdew8Kw)

---

## ⚡ A Home Button Router for Multi-Display Android Devices

Mjolnir gives you **precise control** over what happens when you press the Home button — routing frontends or launchers to the top display, bottom display, or both.  
Originally designed for the **AYN Thor**, it also works on single-screen devices as a powerful Home-button automation tool.

**Device support note (0.2.7):** AYN Thor is the primary supported target. RG-DS support is planned for 0.2.8 and is not officially supported in 0.2.7.

---

## 🧠 How Mjolnir Works (Current Behavior)

Mjolnir supports two onboarding modes:

### **Basic Mode (Safe, Simple)**

- Android’s default launcher remains unchanged
- Mjolnir launches your selected top/bottom apps without Home interception
- Requires valid apps in both slots

### **Advanced Mode (Full Functionality)**

- Requires Notification + Accessibility permissions
- May require setting Mjolnir or Quickstep as default home depending on configuration
- Supports gestures, presets, and full routing control

Advanced Mode is ideal for:

- Heavy customization
- Multi-frontend setups
- Using gestures to control both displays
- Start-on-boot behavior (Advanced only)

---

## ✅ Safety Net Protection

Mjolnir includes a Safety Net activity that prevents soft-lock when a screen would otherwise be left with an empty activity stack.  
Protection status is visible in the persistent notification and in Settings/Onboarding.

Configs live at: `/Android/data/xyz.blacksheep.mjolnir/`

---

## 📦 Installation & Setup

### 1. **Download**

Get the latest release from the [Releases page](https://github.com/blacksheepmvp/mjolnir/releases).  
Use [Add to Obtanium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22xyz.blacksheep.mjolnir%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fblacksheepmvp%2Fmjolnir%22%2C%22author%22%3A%22blacksheepmvp%22%2C%22name%22%3A%22Mjolnir%22%2C%22apkUrls%22%3Anull%2C%22otherAssetUrls%22%3Anull%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%7D%22%2C%22overrideSource%22%3A%22GitHub%22%2C%22allowIdChange%22%3Afalse%7D) for auto-updates.

### 2. **Initialize**

Open Mjolnir → select **Initialize Mjolnir Home**.

### 3. **Choose a Mode**

- **Basic Mode** → simpler, safer, does not replace your launcher
- **Advanced Mode** → full Home routing control

### 4. **Grant Permissions (Advanced Mode)**

You’ll be prompted for:

- **Notification** (keeps the service alive)
- **Accessibility** (required to capture Home events)

### 5. **Select Top & Bottom Apps**

Basic Mode requires valid apps in both slots.  
Advanced Mode allows more flexible setups.

### 6. **Customize Gestures (Advanced Mode)**

Configure actions for:

- **Single Tap**
- **Double Tap**
- **Triple Tap**
- **Long Press**

Gestures support **FOCUS**, **TOP**, **BOTTOM**, and **BOTH** routing options.

### 7. **(Optional) Quick Tiles**

Add the **Mjolnir Home** tile to toggle behavior on/off instantly.  
Add the **SafetyNet Debug** tile to surface SafetyNet activities if needed.

---

## ⚙️ Features

- Route Home presses to:
  - Top screen
  - Bottom screen
  - Both screens
  - Recents menu
  - Custom bindings via gestures
- Gesture presets (Type-A / Type-B / Type-C + custom)
- Start on boot (Advanced only): BOTH: Auto or BOTH: Home
- Separate Basic / Advanced onboarding flows
- Safety Net protection for empty-activity soft-lock prevention
- Multi-screen routing powered by Android 10+ display APIs
- Optional Quick Tiles
- Included utilities for gaming frontends

---

## 🧭 Main Screen Setting (Advanced)

**Main screen** is intended to decide which display should receive focus after a dual‑app launch when focus is ambiguous.  
Right now, it does **not** change behavior because of ongoing issues with simultaneous app launching.  
In the future, it will be the user’s way of saying “this is where I expect focus to default to.”

---

## 🔧 Steam File Generator (Included Utility)

A helper tool for frontends like ES-DE and Beacon:

- Generates `.steam` metadata files
- Accepts SteamDB links via Android’s Share menu
- Prevents duplicates
- Supports bulk deletion

This tool will be spun off into its own app in a future release.

---

## 🛠 Building

Open in Android Studio → Sync Gradle → Run.

---

## 🤝 Contributing

Open an issue for bugs, requests, or feedback.

Buy me a coffee: **[https://ko-fi.com/xyzblacksheep](https://ko-fi.com/xyzblacksheep)**
