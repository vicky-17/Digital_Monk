<div align="center">

# 🛡️ CurbMe

### Parental or Self Control App for Android

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%20%2B%20Java-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Oreo)-orange)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-blue)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Open%20Source-red)](LICENSE)

> A multi-layered, tamper-resistant parental or self-control system. Blocks short-form videos and exploit content, enforces screen time, protects itself from uninstallation, and survives aggressive OEM battery killers — all without root access.

[Features](#-features) • [Architecture](#-architecture) • [Tech Stack](#-tech-stack) • [Security Design](#-security-design) • [Setup](#-setup) • [Screenshots](#-screenshots)

</div>

---

## 📖 Overview


**CurbMe** is a fully self-contained self-control application for Android, designed to give self-control to everyone who are addicted to their device usage without requiring root access, MDM enrollment, or cloud accounts. Every enforcement mechanism runs locally on the device.

### Why This Project Stands Out

Most parental control apps are either cloud-dependent or trivially bypassable. CurbMe layers **7 independent protection mechanisms** that must all be simultaneously defeated to disable the app — without root, that's practically impossible.

---

## ✨ Features

### 🛡️ Content Filtering
- **DNS-Level Web Filter** — Custom `VpnService` intercepts all DNS queries; blocks adult domains via a hardcoded seed list + remotely fetched blocklist (Steven Black / HaGeZi format)
- **SafeSearch Enforcement** — Rewrites DNS A-records for Google, Bing, YouTube, and DuckDuckGo to point at their respective SafeSearch IPs (`216.239.38.120` for Google, etc.)
- **Short-Form Video Blocking** — Accessibility-service-based detection of YouTube Shorts (`reel_recycler`), Instagram Reels, and TikTok — fires `GLOBAL_ACTION_BACK` to exit without closing the app

### 🔒 App Control
- **App Blocking** — Blocks any installed app by package name; kicks to home screen on `TYPE_WINDOW_STATE_CHANGED` events
- **Screen Time Limits** — `UsageStatsManager`-based session tracking with daily limit enforcement

### 🏰 Anti-Bypass & Tamper Resistance

| Protection Layer | Mechanism |
|---|---|
| Anti-Uninstall | Device Administrator API (`DevicePolicyManager`) |
| Settings Page Guard | `SettingsAppMonitor` (UsageStats) + Compose overlay blocks dangerous pages |
| Force-Stop Detection | `GuardianAccessibilityService` + WatchdogService loop |
| VPN Continuity | 4-layer resurrection: AlarmManager → JobScheduler → WorkManager → VpnMonitorService |
| Clock Tamper Detection | NTP + `SystemClock.elapsedRealtime()` — detects backward clock jumps |
| OEM Kill Resistance | MIUI/Oppo/Vivo/Samsung/Huawei autostart whitelisting + battery exemption |
| Settings Lock | Parent-set duration lock using NTP-verified multi-anchor time system |

### 👁️ Monitoring & Oversight
- **WatchdogService** — Foreground service running 3 parallel loops (health check / settings detection / protection state) on dedicated `HandlerThread`s
- **ProtectionStateMonitor** — Continuously checks all permissions and VPN state; surfaces issues via `BlockedPageActivity` with step-by-step fix instructions
- **Usage Logging** — Room DB persistence of session data for future dashboard reporting

---

## 🏗️ Architecture

Digital Monk follows **MVVM + Clean Architecture** with a strict layer separation:

```
┌─────────────────────────────────────────────┐
│                  UI Layer                    │
│   Jetpack Compose · ViewModels · Kotlin      │
│   MainActivity · DashboardScreen             │
│   BlockedPageActivity · PermissionsScreen    │
└────────────────────┬────────────────────────┘
                     │ observes StateFlow
┌────────────────────▼────────────────────────┐
│              Domain / Data Layer             │
│   PrefsManager · Room DB · Repositories     │
│   AppRule · UsageLog · ParentProfile         │
└────────────────────┬────────────────────────┘
                     │ reads / writes
┌────────────────────▼────────────────────────┐
│              Service Layer                   │
│   WatchdogService · DnsVpnService            │
│   GuardianAccessibilityService               │
│   SettingsBlockOverlayService                │
└─────────────────────────────────────────────┘
```

### Package Structure

```
com.example.digitalmonk/
├── core/
│   ├── base/          # BaseActivity, BaseFragment, BaseViewModel
│   ├── di/            # Dependency injection stubs (Hilt-ready)
│   ├── extensions/    # Kotlin extension functions
│   ├── navigation/    # AppNavigator, Screen sealed class
│   └── utils/         # Constants, Logger, NtpFetcher, PermissionHelper,
│                      #   AlarmScheduler, PersistenceManager
├── data/
│   ├── local/
│   │   ├── db/        # Room: AppDatabase, UsageLogDao, VpnHeartBeatDao
│   │   └── prefs/     # PrefsManager (SharedPreferences), SecurePrefs stub
│   ├── model/         # AppRule, UsageLog, ParentProfile, ScreenTimeLimit
│   └── repository/    # AppRuleRepository, UsageRepository, ProfileRepository
├── service/
│   ├── accessibility/ # GuardianAccessibilityService, GuardianRedirectActivity
│   │   ├── handlers/  # ShortsBlockHandler, AppBlockHandler, ScreenTimeHandler
│   │   └── detectors/ # ShortsDetector, AppOpenDetector, KeywordDetector
│   ├── vpn/           # DnsVpnService, DnsFilterEngine, DnsPacketParser
│   │   ├── blocklist/ # BlocklistManager, PornDomainBlocklist
│   │   └── heartbeat/ # VpnHeartBeatEntity, VpnHeartbeatMonitorWorker
│   ├── overlay/       # OverlayService, SettingsBlockOverlayService
│   └── monitor/       # WatchdogService, ProtectionStateMonitor,
│                      #   SettingsAppMonitor, SettingsPageReader
├── receiver/          # BootReceiver, AlarmRestartReceiver, MonkDeviceAdminReceiver
└── ui/
    ├── theme/         # Color, Type, Theme (Inknut Antiqua + Inclusive Sans)
    ├── components/    # Reusable cards, dialogs, common composables
    ├── auth/          # PinGateScreen, PinSetupScreen, AuthViewModel
    ├── dashboard/     # DashboardScreen, DashboardViewModel
    ├── block/         # BlockedPageActivity, ProtectionGateScreen
    ├── permissions/   # PermissionsScreen, PermissionsViewModel
    ├── security/      # SecurityScreen (VPN keep-alive controls)
    ├── settings/      # SettingsScreen
    └── overlay/       # SettingsBlockOverlay, OverlayBridge, OverlayLifecycleOwner
```

---

## 🔧 Tech Stack

| Category | Technology |
|---|---|
| **Language** | Kotlin (UI + Services) + Java (Core utilities, Services, Data layer) |
| **UI Framework** | Jetpack Compose with Material 3 |
| **Navigation** | `AnimatedContent` with `sealed class AppDestination` (crash-safe on Realme) |
| **Concurrency** | Kotlin Coroutines + `HandlerThread` (service loops) + Java `ExecutorService` |
| **Persistence** | Room 2.8 (usage logs, VPN heartbeat) + SharedPreferences (settings) |
| **Background Work** | `WorkManager` (periodic sync) + `JobScheduler` (resilient backup) + `AlarmManager` |
| **Networking (DNS)** | Raw UDP sockets via `VpnService` — no OkHttp, no libraries |
| **NTP** | Custom `NtpFetcher` via `DatagramSocket` — 3 fallback servers |
| **Animation** | `AnimatedContent`, `AnimatedVisibility`, `animateFloatAsState` |
| **Build System** | Gradle 9.4.1 with Kotlin DSL + Version Catalog (`libs.versions.toml`) |
| **Min SDK** | API 26 (Android 8.0 Oreo) |
| **Target SDK** | API 37 |

---

## 🔐 Security Design

### DNS Filtering Pipeline

```
Child opens Browser
        │
        ▼
  UDP Packet → VPN tun interface
        │
        ▼
  DnsPacketParser.parse()
  ┌─────────────────────────────┐
  │  Parse IPv4 + UDP headers   │
  │  Extract domain from DNS    │
  │  wire format (label-length) │
  └──────────────┬──────────────┘
                 │
                 ▼
  DnsFilterEngine.decide(domain, queryType)
  ┌──────────────────────────────────────┐
  │  1. SafeSearch redirect? → A-record  │
  │  2. Porn domain?         → NXDOMAIN  │
  │  3. Custom block?        → NXDOMAIN  │
  │  4. Default             → forward    │
  └──────────────────────────────────────┘
                 │
                 ▼
  Response written back to tun fd
```

### Settings Lock: Multi-Anchor Time System

The parent can lock settings for a duration. To prevent the child from bypassing by changing the system clock:

1. **Anchor A** — `SystemClock.elapsedRealtime()` (boot-relative, cannot be changed by the user)
2. **Anchor B** — NTP-verified Unix time with cached offset (`NtpFetcher` → `time.google.com` / `time.cloudflare.com` / `pool.ntp.org`)
3. **Anchor C** — Backward-clock-jump detection (if `System.currentTimeMillis()` moves back > 60s, the lock is extended)

All three checks run in `PrefsManager.isSettingsLocked()`. The lock survives reboots because anchors are persisted to SharedPreferences.

### VPN Resurrection — 4-Layer System

```
Layer 1: AlarmManager (fires every 3 min, exact, wakelock)
Layer 2: JobScheduler  (persisted job, every 15 min, survives reboot)
Layer 3: WorkManager   (VpnHeartbeatMonitorWorker, every 15 min)
Layer 4: VpnMonitorService (bound companion — detects process death)
```

If the VPN's last heartbeat was `ALIVE` but `DnsVpnService.isServiceRunning == false`, any of these layers will restart it.

---

## 📦 Key Components Deep Dive

### `WatchdogService`
A foreground service (`specialUse` type) running **3 parallel `HandlerThread` loops**:

- **Health Loop** (30s) — VPN liveness check + NTP offset refresh
- **Settings Poll Loop** (300ms) — `SettingsAppMonitor` detects when Settings is in foreground via `UsageEvents`; drives the overlay state machine
- **Protection Check Loop** (10s) — `ProtectionStateMonitor` evaluates all 8 `ProtectionIssue` types; shows `BlockedPageActivity` when issues are detected, with a 15s re-show cooldown

### `GuardianAccessibilityService`
Handles two jobs:

1. **Shorts blocking** — `ShortsDetector` checks `viewIdResourceName` for `reel_recycler`, `root_clips_layout`, etc. via BFS on accessibility tree
2. **App blocking** — `AppBlockHandler` fires `GLOBAL_ACTION_HOME` on `TYPE_WINDOW_STATE_CHANGED` for blocked packages

Also performs **dangerous-page detection** directly in `findAndPerformBack()` — fires `GLOBAL_ACTION_BACK` if the Digital Monk app info page is open (force-stop, uninstall, deactivate admin buttons visible).

### `SettingsBlockOverlayService`
A Compose-based overlay rendered via `WindowManager` **outside any Activity**, using a custom `OverlayLifecycleOwner` that implements both `LifecycleOwner` and `SavedStateRegistryOwner`. Operates in 4 stages: `HIDE → STRIP → HALF → FULL`.

### `DnsPacketParser`
Parses raw Layer 3 IP packets entirely from scratch — no libraries. Handles IPv4 header, UDP header, DNS wire format (including pointer compression for compressed labels), and builds NXDOMAIN / A-record responses with correct checksums.

---

## 🚀 Setup

### Prerequisites
- Android Studio Ladybug or newer
- JDK 21 (toolchain configured)
- Android device running API 26+

### Build & Run

```bash
git clone https://github.com/yourusername/digital-monk.git
cd digital-monk
./gradlew assembleDebug
```

### First-Time Setup (On Device)

1. Install the APK
2. Set a **Parent PIN** (minimum 4 digits)
3. Grant permissions in order:
    - Accessibility Service (GuardianAccessibilityService)
    - Display Over Other Apps
    - Usage Access Statistics
    - Battery Optimization Exemption
    - Device Administrator (anti-uninstall)
4. Enable SafeSearch toggle → grant VPN permission
5. (Optional) Enable Always-On VPN in system settings for maximum protection

> **MIUI Users:** Also enable Autostart and "Display pop-up windows while running in background" from MIUI Security Center.

---

## 🧪 Testing Approach

| Test Type | Coverage |
|---|---|
| Unit | `PrefsManager` lock logic, `DnsFilterEngine.decide()`, `ShortsDetector` |
| Integration | `AppDatabase` DAO queries, `BootReceiver` service restart flow |
| Manual | OEM kill scenarios (MIUI, ColorOS), clock tampering, VPN override attempts |

---

## 📱 Supported OEMs

Tested and includes specific workarounds for:

| OEM | Special Handling |
|---|---|
| **Xiaomi / MIUI** | AutoStart, Background Pop-up, PowerKeeper whitelist |
| **Oppo / ColorOS** | SafeCenter startup manager |
| **Vivo / VivoUI** | Background app manager |
| **Huawei / EMUI** | App launch management |
| **Samsung / OneUI** | Battery > Never sleeping apps |
| **OnePlus / OxygenOS** | Battery optimization |
| **ASUS** | Mobile Manager autostart |

---

## 🔮 Roadmap

- [ ] Screen time per-app scheduling (allowed time windows)
- [ ] Usage reports screen with charts
- [ ] Custom domain blocklist (parent-added via Room DB)
- [ ] Remote parent dashboard (Vercel + MongoDB backend)
- [ ] Google Play Billing subscription (premium tier)
- [ ] Geofencing — different rule sets by location
- [ ] Hilt dependency injection (modules scaffolded, pending wiring)

---

## 📄 License

This project is proprietary. All rights reserved. Not open for redistribution or commercial use without explicit written permission.

---

## 👤 Author

**Debashis** — Android Developer  
B.Sc. Computer Science, Utkal University (2026)

> Built Digital Monk as a production Android application demonstrating advanced system-level API usage, security hardening, and scalable Clean Architecture — entirely without third-party SDKs for the core enforcement stack.

---

<div align="center">

**Digital Monk** — *Discipline through technology.*

⭐ If you found this project interesting, consider starring it.

</div>