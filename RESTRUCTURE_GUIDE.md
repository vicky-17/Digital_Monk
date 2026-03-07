# Digital Monk — Project Restructure Guide
## From Basic → Production-Grade Scalable Architecture

---

## 📁 New Package Structure

```
app/src/main/
├── java/com/example/digitalmonk/
│
│   ── core/                          ← App-wide foundation (never feature-specific)
│   │   ├── di/                       ← Dependency injection (Hilt modules)
│   │   │   ├── AppModule.kt
│   │   │   ├── ServiceModule.kt
│   │   │   └── DatabaseModule.kt
│   │   ├── base/                     ← Base classes
│   │   │   ├── BaseActivity.kt
│   │   │   ├── BaseFragment.kt
│   │   │   └── BaseViewModel.kt
│   │   ├── extensions/               ← Kotlin extension functions
│   │   │   ├── ContextExtensions.kt
│   │   │   ├── StringExtensions.kt
│   │   │   └── FlowExtensions.kt
│   │   ├── utils/                    ← Pure utility/helper classes
│   │   │   ├── Constants.kt          ← (replaces Constants.java)
│   │   │   ├── Logger.kt
│   │   │   └── PermissionHelper.kt
│   │   └── navigation/
│   │       ├── AppNavigator.kt
│   │       └── Screen.kt
│
│   ── data/                          ← All data sources (single source of truth)
│   │   ├── local/
│   │   │   ├── db/                   ← Room database (usage stats, logs, rules)
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── dao/
│   │   │   │   │   ├── AppRuleDao.kt
│   │   │   │   │   ├── UsageLogDao.kt
│   │   │   │   │   └── ScreenTimeDao.kt
│   │   │   │   └── entity/
│   │   │   │       ├── AppRuleEntity.kt
│   │   │   │       └── UsageLogEntity.kt
│   │   │   └── prefs/
│   │   │       ├── PrefsManager.kt   ← (moved from data/)
│   │   │       └── SecurePrefs.kt    ← EncryptedSharedPreferences for PIN/payment
│   │   ├── model/                    ← Domain models (pure Kotlin, no Android deps)
│   │   │   ├── AppRule.kt
│   │   │   ├── ScreenTimeLimit.kt
│   │   │   ├── UsageLog.kt
│   │   │   └── ParentProfile.kt
│   │   └── repository/               ← Abstracts data sources from features
│   │       ├── AppRuleRepository.kt
│   │       ├── UsageRepository.kt
│   │       └── ProfileRepository.kt
│
│   ── service/                       ← All Android Services
│   │   ├── accessibility/
│   │   │   ├── GuardianAccessibilityService.kt   ← (moved)
│   │   │   ├── handlers/             ← One handler per feature
│   │   │   │   ├── ShortsBlockHandler.kt
│   │   │   │   ├── AppBlockHandler.kt
│   │   │   │   └── ScreenTimeHandler.kt
│   │   │   └── detectors/            ← Detection logic (was utils/ShortsDetector)
│   │   │       ├── ShortsDetector.kt
│   │   │       ├── AppOpenDetector.kt
│   │   │       └── KeywordDetector.kt
│   │   ├── vpn/                      ← VPN/DNS filter service (Phase 2)
│   │   │   ├── DnsVpnService.kt
│   │   │   ├── DnsFilterEngine.kt
│   │   │   └── blocklist/
│   │   │       ├── BlocklistManager.kt
│   │   │       └── PornDomainBlocklist.kt
│   │   ├── overlay/                  ← Display-over-other-apps
│   │   │   ├── OverlayService.kt
│   │   │   ├── BlockOverlayView.kt
│   │   │   └── PinPromptOverlay.kt
│   │   ├── monitor/                  ← Background monitoring
│   │   │   ├── ScreenTimeTracker.kt
│   │   │   ├── UsageStatsCollector.kt
│   │   │   └── AppUsageWorker.kt     ← WorkManager worker
│   │   └── notification/
│   │       ├── NotificationHelper.kt
│   │       └── NotificationChannels.kt
│
│   ── receiver/                      ← BroadcastReceivers
│   │   ├── BootReceiver.kt           ← (moved from receivers/)
│   │   ├── ScreenStateReceiver.kt    ← Screen on/off for time tracking
│   │   └── PackageChangeReceiver.kt  ← App install/uninstall detection
│
│   ── ui/                            ← All UI — feature-based modules
│   │   ├── theme/                    ← (stays here)
│   │   │   ├── Color.kt
│   │   │   ├── Theme.kt
│   │   │   ├── Type.kt
│   │   │   └── Shape.kt
│   │   ├── components/               ← Shared reusable Composables
│   │   │   ├── cards/
│   │   │   │   ├── StatusCard.kt
│   │   │   │   ├── ToggleCard.kt
│   │   │   │   └── ActionCard.kt
│   │   │   ├── dialogs/
│   │   │   │   ├── PinDialog.kt
│   │   │   │   └── ConfirmDialog.kt
│   │   │   └── common/
│   │   │       ├── SectionLabel.kt
│   │   │       ├── LoadingScreen.kt
│   │   │       └── ErrorScreen.kt
│   │   ├── auth/                     ← PIN gate / parent authentication
│   │   │   ├── PinGateScreen.kt
│   │   │   ├── PinSetupScreen.kt
│   │   │   ├── PinSetupActivity.kt
│   │   │   └── AuthViewModel.kt
│   │   ├── dashboard/                ← Main parent dashboard
│   │   │   ├── DashboardScreen.kt
│   │   │   ├── DashboardViewModel.kt
│   │   │   └── MainActivity.kt
│   │   ├── appblock/                 ← App blocking feature UI
│   │   │   ├── AppBlockScreen.kt
│   │   │   ├── AppBlockViewModel.kt
│   │   │   └── AppListItem.kt
│   │   ├── screentime/               ← Screen time limits UI
│   │   │   ├── ScreenTimeScreen.kt
│   │   │   ├── ScreenTimeViewModel.kt
│   │   │   └── UsageChart.kt
│   │   ├── contentfilter/            ← Shorts/porn/content filter UI
│   │   │   ├── ContentFilterScreen.kt
│   │   │   └── ContentFilterViewModel.kt
│   │   ├── reports/                  ← Usage reports & activity logs
│   │   │   ├── ReportsScreen.kt
│   │   │   └── ReportsViewModel.kt
│   │   ├── settings/                 ← App settings
│   │   │   ├── SettingsScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   └── onboarding/               ← First-launch onboarding flow
│   │       ├── OnboardingActivity.kt
│   │       └── OnboardingScreen.kt
│
│   └── DigitalMonkApp.kt             ← Application class (Hilt entry point)
│
└── res/
    ├── drawable/
    ├── layout/                       ← Only if non-Compose views needed (overlays)
    ├── mipmap-*/
    ├── values/
    │   ├── colors.xml
    │   ├── strings.xml
    │   └── themes.xml
    └── xml/
        ├── accessibility_config.xml
        ├── network_security_config.xml  ← Add for VPN/DNS feature
        ├── backup_rules.xml
        └── device_admin.xml
```

---

## 🔁 File Migration Map

| **Old Path** | **New Path** | **Notes** |
|---|---|---|
| `utils/Constants.java` | `core/utils/Constants.kt` | Convert to Kotlin object |
| `utils/ShortsDetector.kt` | `service/accessibility/detectors/ShortsDetector.kt` | Detector belongs with the service |
| `data/model/AppRule.kt` | `data/model/AppRule.kt` | Same location, already correct |
| `data/PrefsManager.kt` | `data/local/prefs/PrefsManager.kt` | Move deeper into local data |
| `services/GuardianAccessibilityService.kt` | `service/accessibility/GuardianAccessibilityService.kt` | Rename folder: services → service |
| `receivers/BootReceiver.kt` | `receiver/BootReceiver.kt` | Rename folder: receivers → receiver |
| `ui/MainActivity.kt` | `ui/dashboard/MainActivity.kt` | Feature-based grouping |
| `ui/PinSetupActivity.kt` | `ui/auth/PinSetupActivity.kt` | Feature-based grouping |
| `ui/theme/*` | `ui/theme/*` | No change |

---

## 🏗️ Architectural Pattern: MVVM + Clean Architecture

```
UI Layer  (ui/)          →  ViewModels + Composables
    ↓ observes
Domain Layer (data/model + repository/)  →  Business logic
    ↓ reads/writes
Data Layer (data/local/)  →  Room DB + SharedPrefs
    ↑
Service Layer (service/)  →  Accessibility, VPN, Overlay
    ↓ writes events
```

---

## 📦 Recommended Dependencies to Add (build.gradle.kts)

```kotlin
// Hilt - Dependency Injection
implementation("com.google.dagger:hilt-android:2.51")
kapt("com.google.dagger:hilt-android-compiler:2.51")

// Room - Local Database (for usage logs, app rules)
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// WorkManager - Background tasks that survive reboots
implementation("androidx.work:work-runtime-ktx:2.9.0")
implementation("androidx.hilt:hilt-work:1.2.0")

// EncryptedSharedPreferences - Secure PIN/payment data storage
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// Navigation Compose - For screen routing
implementation("androidx.navigation:navigation-compose:2.7.7")

// ViewModel Compose
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

// DataStore (modern replacement for SharedPreferences — use alongside Room)
implementation("androidx.datastore:datastore-preferences:1.1.1")
```

---

## 🗺️ Feature Roadmap → Package Mapping

| **Feature** | **Package** | **Dependencies Needed** |
|---|---|---|
| ✅ Shorts Blocking | `service/accessibility/` | Already done |
| 🔜 App Blocking | `service/accessibility/handlers/AppBlockHandler` + `ui/appblock/` | Room |
| 🔜 Screen Time Limits | `service/monitor/` + `ui/screentime/` | Room + WorkManager |
| 🔜 DNS/VPN Porn Filter | `service/vpn/` | Android VpnService |
| 🔜 Display Overlay | `service/overlay/` | SYSTEM_ALERT_WINDOW permission |
| 🔜 Usage Reports | `ui/reports/` | Room + Charts lib |
| 🔜 Autostart on Boot | `receiver/BootReceiver.kt` | Already wired (expand) |
| 🔜 Payment/Subscription | `ui/subscription/` + `data/repository/BillingRepository` | Google Play Billing |
| 🔜 Safe Search Enforcement | `service/vpn/DnsFilterEngine` | VpnService |
| 🔜 Geofencing | `service/monitor/GeofenceManager` | Google Location Services |

---

## ⚡ Immediate Action Steps

### Step 1 — Rename folders in Android Studio
- `services/` → `service/`
- `receivers/` → `receiver/`
- `utils/` → `core/utils/`

### Step 2 — Move files (use Android Studio Refactor → Move)
This auto-updates all import statements.

### Step 3 — Convert Constants.java → Constants.kt
```kotlin
// core/utils/Constants.kt
package com.example.digitalmonk.core.utils

object Constants {
    const val LOG_TAG = "DigitalMonk"
    const val NOTIFICATION_ID = 1
    const val NOTIFICATION_CHANNEL_ID = "guardian_channel"
    const val OVERLAY_SERVICE_NOTIFICATION_ID = 2
    const val VPN_SERVICE_NOTIFICATION_ID = 3
}
```

### Step 4 — Move Composables out of MainActivity
Extract `StatusCard`, `ToggleCard`, `ActionCard`, `SectionLabel` into `ui/components/`.

### Step 5 — Add Application class
```kotlin
// DigitalMonkApp.kt
@HiltAndroidApp
class DigitalMonkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Init logger, crash reporting, etc.
    }
}
```

---

## 🔐 Security Notes for Future Features

- **PIN storage** → Move from plain SharedPrefs to `EncryptedSharedPreferences`
- **Payment data** → Never store locally; use Google Play Billing's server validation
- **VPN keys** → Use Android Keystore system
- **Add `network_security_config.xml`** before any network feature

---

## 📝 AndroidManifest.xml — Permissions to Add Over Time

```xml
<!-- Already have -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Add for App Blocking + Screen Time -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Add for Overlay (block screen) -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Add for VPN/DNS Filter -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- Add for notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Add for screen time tracking -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```