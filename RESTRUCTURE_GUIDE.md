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

## 🔐 Security Notes for Future Features

- **PIN storage** → Move from plain SharedPrefs to `EncryptedSharedPreferences`
- **Payment data** → Never store locally; use Google Play Billing's server validation
- **VPN keys** → Use Android Keystore system
- **Add `network_security_config.xml`** before any network feature

---
## Open source Projects
The following are open source projects used in order to deliver our products and

services:

○ https://github.com/InflationX/Calligraphy

○ https://github.com/square/okhttp

○ https://github.com/greenrobot/EventBus

○ https://github.com/javiersantos/MaterialStyledDialogs

○ https://github.com/square/leakcanary

○ https://github.com/rrice/java-string-similarity

○ https://github.com/akexorcist/RoundCornerProgressBar

○ https://github.com/duckduckgo/Android

○ https://github.com/duckduckgo/iOS

○ https://github.com/Jigsaw-Code/Intra

○ https://github.com/DNSCrypt/dnscrypt-proxy

○ https://github.com/tjfontaine/node-dns

○ https://github.com/PowerDNS/pdns


---
