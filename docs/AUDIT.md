# SEGAWA OS — Phase A technical audit

**Audited commit:** `32ef3b7` (branch `arena/4e9da799-segawaos`, PR #1)
**Product as found:** MIO OS v1.0.0 (`versionCode 1`), package `com.mio.os` (debug suffix `.debug`)
**Audit date:** 2026-11-08 · **Audited by:** engineering agent, via direct repository inspection
**Method:** every statement below was read from the checked-out source or from CI evidence in `ci/`.
Nothing here is inferred from documentation alone.

---

## 1. What actually exists (verified inventory)

### 1.1 Source tree — 32 Kotlin files, ~10.6k lines

| Area | File(s) | State |
|---|---|---|
| Entry | `MainActivity.kt`, `MioApp.kt` | Working. HOME activity (`MAIN`+`HOME`+`DEFAULT`), edge-to-edge, debug-only QA intent hook (`mio_skip_setup`, `mio_screen`) |
| Navigation | `core/MioNav.kt` | Hand-rolled `object` with `ArrayDeque` back stack + `MioScreen` enum (24 destinations). No Navigation-Compose |
| State | `core/MioSettings.kt` | Single `SharedPreferences` file `mio_prefs_v1`, 37 fields, exposed as `StateFlow`, no migration framework |
| Apps | `core/AppsRepository.kt`, `core/Folders.kt` | PackageManager discovery, hidden apps, folders serialised as pipe-delimited string |
| Icons | `ui/components/MioComponents.kt` (`AppIconView`), `LauncherState.kt` | Bitmap LRU cache, shape/size/tint restyling |
| Home | `ui/home/HomeScreen.kt` | Clock, weather, music widget, pager, folders row, dock, search pill, quick sheets |
| Drawer | `ui/drawer/AppDrawer.kt` | Search, category chips, sort modes, widgets tab, hidden apps |
| Control centre | `ui/control/ControlCenter.kt` | Real torch/DND/night/volume/brightness; panels for Wi-Fi/BT/airplane/hotspot/cast |
| AI | `core/AiEngine.kt`, `ui/apps/AiScreen.kt` | Offline intent engine that performs real device actions; optional OpenAI-compatible endpoint via `HttpURLConnection` (non-streaming), single-thread history string in prefs |
| Music | `core/MediaRepository.kt`, `core/MioPlayer.kt`, `service/MusicService.kt` | MediaStore library, foreground service, `MediaSession`, notification actions, equalizer, synthesised demo track |
| Other apps | `ui/apps/{UtilityScreens,ProductivityScreens,ExperienceScreens}.kt` | Files, Gallery, Themes, Notes, Calendar, Clock, Calculator, Weather, Gaming, Lock, About, Hidden |
| Settings | `ui/settings/{SettingsScreen,AppearanceScreen}.kt`, `ui/setup/WelcomeScreen.kt` | Role request, permission grants, appearance controls, onboarding |
| Notifications | `service/MioNotificationListener.kt` | Badge counts per package |
| Device admin | `service/MioDeviceAdmin.kt` | Optional real `lockNow()` |
| Theme | `ui/theme/MioTheme.kt` | Hard-coded dark palette + accent from prefs + `MioType` type scale |

### 1.2 Resources
`drawable-nodpi` holds three 1080×1920 JPEG wallpapers (mountain, aurora, anime ≈ 300 KB each),
four stat icons, a vector launcher foreground/background, `values/{strings,colors,styles}.xml`, `xml/device_admin.xml`.
No `values-night`, no `values-v31`, no splash config, no AppWidget resources, no test resources.

### 1.3 Build
AGP application module, `compileSdk 35`, `minSdk 26`, `targetSdk 35`, Compose BOM `2024.12.01`,
material-icons-extended, Coil 2.7. Java 17. Release: R8 + resource shrinking, signed with the debug keystore.
**No** Room, DataStore, WorkManager, Hilt, Retrofit/Ktor, Media3, Navigation, navigation-testing,
JUnit, Robolectric, Espresso, or Compose UI-test dependency.

### 1.4 CI
- `android.yml` — builds debug + release, publishes a GitHub Release, commits `dist/` and `ci/build.log`.
- `emulator-test.yml` — API 33 AVD, `-skin 720x1600 -memory 2048`, runs `scripts/emulator-test.sh`
  (19 steps, 16 assertions), publishes `ci/emulator-report.txt` + 41 screenshots.

### 1.5 Verified behaviour (CI evidence, run `42448889809` → 16/16 PASS)
Install, HOME intent filter, default-launcher resolution, HOME routing, foreground home,
HOME key from another app, drawer/search/torch, 15 screens screenshotted, `MediaSession` active,
500 monkey events survived, default launcher survives `adb reboot`, R8 release APK installs and is
default, no `FATAL EXCEPTION`.

### 1.6 Performance baseline (same emulator, GPU-less)
| Measurement | Value |
|---|---|
| MIO home, median frame | 34–65 ms |
| AOSP Settings (baseline) | 16 ms |
| Cold start | ~2.5 s |
| PSS | ~120 MB |
| Release APK | 2.2 MB |

---

## 2. Risks and technical debt

| # | Finding | Severity | Consequence if unaddressed |
|---|---|---|---|
| T1 | Branding is MIO everywhere (74 string literals, `app_name`, notification channel, docs) | Blocker for v1.1 | Product name wrong for release |
| T2 | `MioSettings` = 37 flat `SharedPreferences` fields, no versioning | High | Every new feature widens a fragile parse; a bad write can corrupt all settings |
| T3 | No tests of any kind | High | Refactors for v1.1 are unguarded; regressions are only caught by a 11-minute emulator run |
| T4 | Navigation is a global singleton `object` (`MioNav`) + hand-rolled stack | Medium | Works, but untestable in isolation and hostile to deep links / widgets |
| T5 | AI history stored as one delimited string in prefs; no threads, no streaming | Medium | Cannot host conversation search or regenerate reliably |
| T6 | Icons are only restyled (shape/size); no per-app custom icon, no icon pack import | Medium | Icon Studio requirement unmet |
| T7 | No `WallpaperService`; only static bitmap application | Medium | Live/video wallpapers unmet |
| T8 | No AppWidgetProvider; "widgets" are in-launcher composables only | Medium | Widget Engine requirement unmet |
| T9 | No scheduler (no WorkManager/AlarmManager usage) | Medium | Automation/routines unmet |
| T10 | No backup/export path for settings, notes, layout | Medium | Cloud/backup requirement unmet |
| T11 | `AsyncImage`-era Coil usage replaced by manual bitmap decode for wallpaper — good, but gallery loads full-size bitmaps | Low–Medium | OOM risk on 2 GB devices when browsing large photos |
| T12 | `GamingScreen`, `FilesScreen` label themselves honestly but have no deep functionality | Low | Acceptable if documented |
| T13 | Release signed with the debug keystore | Low (deliberate) | Play Store upload impossible; local install fine. Documented |
| T14 | Weather fetch has no cache invalidation/backoff | Low | Repeated failed requests offline |

**No security defects found:** no hard-coded secrets, no exported components without permission,
`AI key` only in private prefs, no WebView (yet), no cleartext HTTP beyond the user-supplied endpoint
(kept as an explicit user configuration).

---

## 3. Proposed architecture for v1.1 (incremental, no rewrite)

```
core/
  SegaBrand.kt            constants: product, tagline, author, version
  SegaThemeSpec.kt        8-theme design engine + JSON import/export
  PrefsStore.kt           versioned preference layer (keeps mio_prefs_v1 intact)
  AiClient.kt             streaming SSE client + thread store + TTS/STT glue
  WallpaperRepo.kt        collections, favorites, history, rotation
  AutomationRepo.kt       routines persisted as JSON, executed via AlarmManager/WorkManager
  BackupRepo.kt           export/import (JSON, optional AES-GCM)
  SearchIndex.kt          universal search across apps/notes/music/wallpapers/settings
service/
  SegaWallpaperService.kt WallpaperService (animated + video, battery aware)
widget/
  ClockWidget, WeatherWidget, MusicWidget, NotesWidget, SearchWidget, BatteryWidget
ui/
  theme/SegaTheme.kt      tokens → CompositionLocal (keeps MioColors as an alias)
  studio/IconStudioScreen.kt, WallpaperStudioScreen.kt
  apps/BrowserScreen.kt, SearchScreen.kt, AutomationScreen.kt, DeviceIntelScreen.kt,
       PrivacyScreen.kt (extended), BackupScreen.kt, ThemeStoreScreen.kt
```

**Compatibility rules honoured:** package `com.mio.os`, prefs file `mio_prefs_v1`, existing screens,
existing navigation `object`, existing music/AI/offline behaviour, existing resources. New fields are
added with defaults so a v1.0 install upgrades in place; nothing is renamed at the storage layer.

---

## 4. Upgrade roadmap (phases, each independently buildable)

| Phase | Scope | Verification gate |
|---|---|---|
| **A** | This audit + roadmap | — |
| **B** | Brand → SEGAWA, design engine (8 themes), tokens, launcher icon, splash, version 1.1.0/2 | `assembleDebug` green |
| **C** | Home 2.0, drawer pro, icon studio, wallpaper studio + `WallpaperService` | build green + screenshots |
| **D** | Control-centre tiles, universal search, widget engine (AppWidget), notes 2.0, device intelligence, privacy | build green |
| **E** | AI 2.0: streaming client, threads, TTS, attachments + secure backend (`server/`) | build green + backend unit run |
| **F** | Music 2.0 (playlists, lyrics, visualiser, sleep timer), gallery 2.0, browser | build green |
| **G** | Gaming 2.0, automation/routines, backup & restore, theme store, lock-screen studio | build green |
| **H** | Unit tests (theme JSON, backup round-trip, lyrics, search index, calculator), CI test job, emulator regression | `testDebugUnitTest` + 16/16 emulator |
| **I** | Docs (9 files), changelog, release notes, APK, release | release published |

Test strategy: pure-logic unit tests run on the JVM in CI (fast, deterministic); UI behaviour is verified
on the emulator script with screenshots as evidence. No result is reported that CI did not produce.
