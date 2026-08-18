# 🌐 WifiSwitcher · One-Tap Android Wi-Fi Network Configuration App

<div align="right">
  <strong>Language：</strong>
  <a href="README.md">🇨🇳 中文</a>
  <span>｜</span>
  🇬🇧 <b>English</b>
</div>

> A beginner-friendly single-page Android utility built with **Kotlin + Jetpack Compose**:  
> **Modify the currently-connected Wi-Fi network's IP / Gateway / Primary DNS / Secondary DNS on the fly**,  
> with one-tap switching between **static IP mode** and **automatic DHCP mode**,  
> plus **MIUI 14 (Xiaomi / Redmi) specific compatibility layer** to fix the two common pain points:  
> "the progress bar hangs forever" and "settings appear applied but are not actually effective".

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| 🧭 **Dual Mode Switch** | 🟢 **DHCP Auto (Recommended)**: revert to router-allocated settings, zero configuration required<br>🔵 **Static IP Manual Mode**: fill in IP / prefix / gateway / primary & secondary DNS yourself |
| 💾 **Persistent Storage** | Uses **Jetpack DataStore** (replacing the deprecated SharedPreferences) to remember your last configuration, auto-restored on next launch |
| 📁 **Up to 20 Saved Presets** | Vertical card list to manage common networks (Home / Office / Dorm / Lab etc.), one preset per row, click to load into the form |
| ⚡ **Root Shell Timeout Guard** | Every command runs in its own `su` session with 3~5 second soft/hard timeout; an additional 15 second global timeout **prevents the progress bar from getting stuck forever** |
| 🛠️ **MIUI 14 Compatibility Layer** | Automatically disables Private DNS, pauses NetworkAgent, installs custom routing tables + high-priority ip rules, and falls back to `iptables -t nat DNAT` to force DNS — preventing MIUI from silently overwriting root-level changes |
| 🧑‍🎨 **Beginner-Friendly UI** | Full UI redesign with semantic colors (Green = Apply / Blue = Save / Orange = Warning / Red = Error), grouped cards, realistic field placeholders, diagnostics log collapsed by default (so casual users don't get intimidated) |
| 💡 **Friendly Success Reminder** | After a successful apply, an extra Snackbar politely tells the user: "**It's normal that the System Settings → Wi-Fi page still shows old values. The low-level network stack has already been updated.** To sync the System Settings UI, simply toggle Wi-Fi off and back on." |

---

## 📱 Requirements

| Item | Requirement |
|------|-------------|
| Minimum Android version | **Android 12 (API Level 31)** |
| Permission | Device **must be rooted** (Magisk / KernelSU / APatch etc.). Grant root when prompted on first launch and select "Allow permanently". |
| Wi-Fi status | Must already be connected to a Wi-Fi network. This App only modifies the *currently connected* Wi-Fi's low-level network stack. |

---

## 🚀 How to Use

### 1️⃣ Install the APK

Option A — Build yourself (recommended): follow the steps under [🧰 Building a Debug APK](#-building-a-debug-apk) to generate `app-debug.apk`, then copy it to your phone and install.

Option B — If you downloaded a prebuilt APK from GitHub Releases or GitHub Actions, simply tap it. Enable "Install from unknown sources" if prompted by Android.

### 2️⃣ Step-by-step Flow (for beginners)

```
Launch the App
    ↓
Tap "Allow" in the root permission dialog (the top card will turn green with ✓)
    ↓
Choose a mode:
  🟢 Green "DHCP Auto Mode" → simply tap the big green button "✓ Switch Back to Auto Mode"
  🔵 Blue "Static IP Manual Mode" → fill in the 5 fields in sections ①②③ → tap "✓ Apply Settings Now"
    ↓
Wait a few seconds. Two Snackbars appear: one saying "Success", then a longer friendly reminder.
    ↓
🎉 Done! Open a browser and visit any website to verify connectivity.
```

---

## 🖼️ UI Overview

> (Screenshot placeholder — run the App yourself and capture screenshots to populate this section)

```
┌──────────────────────────────────────────────────────┐
│ Top Bar: "Wi-Fi Config" + subtitle                     │
├──────────────────────────────────────────────────────┤
│ 【Current Status Card】Root ✓ + Wi-Fi iface + IP/DNS     │
├──────────────────────────────────────────────────────┤
│ 【Two Large Mode Cards】🟢DHCP    🔵Static IP            │
├──────────────────────────────────────────────────────┤
│ Conditional expand area:                               │
│   - if DHCP → green hint "Auto mode selected"          │
│   - if Static → ①IP ②Gateway ③DNS grouped form + presets│
├──────────────────────────────────────────────────────┤
│ 【Bottom Big Green Button】Apply / Switch Back to Auto   │
└──────────────────────────────────────────────────────┘
```

- **Preset List**: vertical layout, one preset per row — icon + name on the left, summary in the middle, pencil (rename) / trash (delete) buttons on the right.
- **Diagnostics Log**: hidden by default. Only appears with a red border when one or more commands fail; tap the chevron to expand and inspect per-command stdout / stderr (useful for troubleshooting — take a screenshot and send it to the developer).

---

## 🧰 Building a Debug APK

This repo ships the Gradle Wrapper (`gradlew.bat`) — only **JDK 17** + **Android SDK** are required, no separate Gradle installation needed. Android Studio is optional.

### Build on Windows (PowerShell)

```powershell
# 1. Install JDK 17 (Eclipse Temurin 17 recommended, installable via winget)
winget install EclipseAdoptium.Temurin.17.JDK -e

# 2. Set JAVA_HOME (adjust path if you installed elsewhere)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"

# 3. Set Android SDK root (must contain platforms/android-34, build-tools/34, platform-tools)
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"

# 4. Enter the project root directory (the directory containing this README)
cd WifiSwitcher

# 5. Build the Debug APK
#    (on the very first run, Gradle 8.5 + Compose / Kotlin dependencies will be auto-downloaded — requires internet)
.\gradlew.bat --no-daemon assembleDebug

# 6. On success, the APK is located at:
ls .\app\build\outputs\apk\debug\app-debug.apk
```

A successful build prints `BUILD SUCCESSFUL`. Copy the APK onto your phone and tap to install.

---

## 🏗️ Project Structure

```
WifiSwitcher/
├── app/
│   ├── build.gradle.kts              # App module: dependencies, minSdk=31, targetSdk=34
│   ├── proguard-rules.pro            # ProGuard / R8 rules
│   └── src/main/
│       ├── AndroidManifest.xml       # App name, theme, permissions declaration
│       ├── java/com/wificonfig/app/
│       │   ├── WifiConfigApplication.kt
│       │   ├── MainActivity.kt       # Single Compose Activity entry point
│       │   ├── data/
│       │   │   ├── NetworkConfig.kt          # Data classes: StaticNetworkConfig, SavedPreset, etc.
│       │   │   └── PreferencesRepository.kt  # DataStore persistence (settings + preset list)
│       │   ├── ui/
│       │   │   ├── WifiConfigScreen.kt       # Main UI page (single Jetpack Compose screen)
│       │   │   ├── WifiConfigViewModel.kt    # ViewModel: state machine + business logic
│       │   │   └── theme/                    # Color / Theme / Typography
│       │   └── util/
│       │       ├── RootShell.kt              # Root executor (one su per command + timeout)
│       │       └── NetworkConfigManager.kt   # Actual network changes: static IP / DHCP / MIUI layer
│       └── res/                              # Resources: strings, colors, theme, launcher icons
├── build.gradle.kts                  # Project-level Gradle config (Kotlin / Compose plugin versions)
├── settings.gradle.kts               # Project name, module includes
├── gradle.properties                 # Global Gradle properties (non-transitive R classes, Jetifier etc.)
├── gradle/
│   └── wrapper/gradle-wrapper.properties / gradle-wrapper.jar
├── gradlew.bat                       # Gradle Wrapper launch script for Windows
└── .gitignore                        # Ignores build/, .gradle/, local.properties, *.apk, logs etc.
```

---

## 🧠 Tech Stack

| Category | Choice |
|----------|--------|
| **Language** | 100% **Kotlin** |
| **UI** | **Jetpack Compose BOM 2024.06.00** (Material 3) |
| **Architecture** | Single Activity + MVVM (`ViewModel` + `StateFlow`) |
| **Persistence** | **Jetpack DataStore Preferences** (replacing SharedPreferences) |
| **Async** | **Kotlin Coroutines + Flow**, `viewModelScope` + `withTimeout` dual-level timeout protection |
| **Root Execution** | `ProcessBuilder("su", "-c", ...)` one-command-per-session + `waitFor(timeout)` to avoid blocking |
| **Build Tool** | Gradle 8.5 (via Gradle Wrapper, no local Gradle install required) + Kotlin DSL |
| **JSON Serialization** | Hand-written with plain `org.json.JSONObject` — no extra deps, keeps APK size tiny |
| **Min SDK** | Android 12 API 31 |
| **Target SDK** | Android 14 API 34 |

---

## ⚠️ FAQ

<details>
<summary>1. After tapping "Apply", the progress bar keeps spinning forever?</summary>

> The new version implements dual-level timeout guard: 3~5 seconds per individual command, plus a 15/20 second global timeout. If it still hangs, the MIUI system is usually eating the root permission popup silently. Workaround: force-kill the App, reopen it, and manually grant root permission inside Magisk Manager.

</details>

<details>
<summary>2. "Applied successfully" but when I go to System Settings → Wi-Fi, the IP is still the old one?</summary>

> **Completely normal and expected!** This App modifies the kernel-level ip rules / routes / DNS directly. The System Settings UI caches those values and doesn't automatically refresh. How to verify it actually took effect:
> - Open any browser and visit `https://example.com` (or any website) — if the page loads, the settings are working.
> - If you also want the System Settings UI to reflect the new values: simply turn Wi-Fi off and back on (DHCP mode already does this automatically).

</details>

<details>
<summary>3. Why does the App need Root?</summary>

> Regular unprivileged Android apps (UID ≥ 10000) do not hold the permissions needed to call low-level network APIs such as `ip addr add`, `ip rule add`, `iptables -t nat`, or `ndc netd`. Only UID 0 (root) can modify the kernel networking stack.

</details>

<details>
<summary>4. Will it work on non-MIUI ROMs (AOSP / OxygenOS / ColorOS / One UI)?</summary>

> Yes, perfectly. The MIUI-specific compatibility code (disabling Private DNS, pausing NetworkAgent) degrades gracefully on other ROMs — if a MIUI-only command fails, it gets recorded in diagnostics but does NOT make the overall apply fail. The main IP/DNS changes still work.

</details>

<details>
<summary>5. Why are presets capped at 20?</summary>

> A typical casual user only switches between 2~5 networks (Home 2.4G / 5G / Office / Dorm / Café). 20 is far beyond daily needs and prevents the preset list from becoming unmanageable. The limit is defined as the constant `SavedPreset.MAX_PRESETS = 20` in [NetworkConfig.kt](app/src/main/java/com/wificonfig/app/data/NetworkConfig.kt) — developers can freely change it.

</details>

---

## 📜 License

```
MIT License

Copyright (c) 2026 Kanon1982

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
```
