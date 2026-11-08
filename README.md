# MIO OS v1.0 — a real Android launcher

MIO OS is a **working Android home-screen launcher** written in Kotlin + Jetpack Compose.
Install the APK, choose MIO OS as your Home app, and pressing the Home key/gesture opens it —
including after a reboot. It is not a mockup, a website or a Figma file.

![Home](ci/shots/02-home.png)

---

## 1. Install & activate (2 minutes)

1. **Download the APK**
   - GitHub Release (recommended): <https://github.com/devkazzu/SEGawaOS/releases/latest>
   - Direct from this branch: [`dist/MIO-OS-debug.apk`](dist/MIO-OS-debug.apk) ·
     [`dist/MIO-OS-release.apk`](dist/MIO-OS-release.apk)
2. On the phone, allow installs from your browser/file manager when Android asks
   (*Install unknown apps*).
3. Open **MIO OS** → the welcome flow starts:
   - **Get started** → **Set as default**
   - Android's own *RoleManager* dialog appears ("Use MIO OS as Home app?") or the
     *Default apps ▸ Home app* screen on Android 9 and older.
   - Pick **MIO OS**, then **Always / Set as default**.
4. Press the Home button — MIO OS is now your home screen.
   You can re-check any time in **Settings ▸ Home screen** or from the welcome flow.

> Minimum Android 8.0 (API 26). Built and tested against Android 13 / API 33.
> `applicationId` is `com.mio.os` (release) and `com.mio.os.debug` (debug build) — install only one.

---

## 2. What actually works

### Home screen (`ui/home/HomeScreen.kt`)
- Big gradient digital clock + date + city, live weather pill (Open-Meteo, no API key)
- Always-present music widget (artwork, title, prev/play/next)
- Multi-page app grid, swipe up for the drawer, swipe down for the control centre
- Dock with auto-detected Phone/Messages/Browser/Camera (or your own picks)
- Search pill, per-app notification badges, page indicator
- **App folders** — long-press any app → *Move to folder* → create/rename/delete
- Long-press the wallpaper for the home customisation sheet
- App quick sheet: open, app info, hide, uninstall, move to folder

### App drawer (`ui/drawer/AppDrawer.kt`)
- Every installed launchable activity, real icons and names (PackageManager)
- Live search, category chips (Games/Music/Video/Photos/Social/Work…)
- Sorting: alphabetical / most-recent / category
- Widgets tab with MIO widget visibility toggles
- Hidden-apps management, per-app context sheet

### Control centre (`ui/control/ControlCenter.kt`)
| Tile | Behaviour |
|---|---|
| Wi-Fi / Bluetooth / Internet | Opens the Android system panel (`Settings.Panel`) |
| Torch | **Real** `CameraManager.setTorchMode` |
| Do Not Disturb | **Real** `NotificationManager.setInterruptionFilter` (needs DND access) |
| Night | **Real** `UiModeManager.setApplicationNightMode` (API 31+) |
| Brightness | Changes MIO's own window instantly; the system brightness is written when *Modify system settings* is granted |
| Volume | **Real** `AudioManager.setStreamVolume` with the system volume UI |
| Airplane / Rotate / Cast / Hotspot / Battery | Open the matching Android settings screen — a normal app cannot flip these |
| Lock | `DevicePolicyManager.lockNow` when the optional device admin is enabled, otherwise the MIO lock screen |

### MIO AI (`ui/apps/AiScreen.kt`, `core/AiEngine.kt`)
- Offline assistant that **acts on the device**: “open camera”, “torch on”, “battery”,
  “note buy milk”, “play music”, “am I the default launcher?”
- Chat history persisted locally, voice input through the system speech recogniser
- Plug any OpenAI-compatible endpoint + key + model in the AI settings sheet
  (stored only on the device) to switch to full conversation

### Music (`ui/apps/MusicScreen.kt`, `core/MioPlayer.kt`, `service/MusicService.kt`)
- Local library from MediaStore, album art, seek bar, queue
- Foreground playback service with a media notification, platform `MediaSession`
  metadata and **lock-screen controls**
- Real equalizer through `android.media.audiofx.Equalizer`
- Ships a synthesised ambient demo track so playback works on a device with no music

### Other apps
Files (storage + MediaStore categories), Gallery (grid + preview + set-as-wallpaper),
Themes (bundled mountain/anime/aurora/violet artwork, custom image, accent colours),
Notes, Calendar (real `CalendarContract` events), Clock (stopwatch/timer/world clocks +
`AlarmClock` hand-off), Calculator (own expression parser), Weather (live + 5-day forecast),
Gaming mode (DND + brightness + game launcher), About/Device info, Hidden apps,
MIO lock screen (in-app).

### Customisation engine
Accent colour (10 palettes), wallpaper (4 artworks or any gallery image), grid columns 3–6,
icon size, icon shape, label visibility, font scale, glass intensity, wallpaper dim,
animations / reduce-motion, dock, weather widget, clock format, drawer sorting.
Everything persists in `SharedPreferences` and is exposed as a `StateFlow`.

---

## 3. Honest platform limitations

These are Android restrictions, not missing code. Each one is labelled in the UI.

| Feature | Why a third-party launcher cannot do it | What MIO OS does instead |
|---|---|---|
| Replace the **lock screen** | Only system-signed apps can host the keyguard | Full-screen in-app MIO lock screen + real media controls on the system lock screen through MediaSession |
| Toggle **Wi-Fi / Bluetooth / airplane / hotspot** | Requires `NETWORK_SETTINGS` / privileged permissions since Android 10 | Opens the official system panel (`Settings.Panel.ACTION_WIFI`) |
| System **rotation lock**, **dark mode**, **auto-brightness** | `WRITE_SETTINGS` is a special access and Secure settings are privileged | Writes brightness/auto-brightness when *Modify system settings* is granted; otherwise opens the settings page |
| **CPU/GPU boost**, true *game mode* | Needs root or a platform signature (`GameManager` is for games themselves) | DND + max brightness + distraction-free launcher + session timer |
| Third-party **icon pack** files | No public API; packs use their own private format | MIO restyles every icon (shape, size, tint) itself |
| **All-files** filesystem browsing | Android 11+ restricted permission | MediaStore categories + hand-off to the system document picker |
| **OTA updates** | Needs a distribution channel and signing authority | Rebuild/install from GitHub Actions artifacts |

---

## 4. Project layout

```
app/src/main/
├── AndroidManifest.xml            # HOME + LAUNCHER intent filters, services, permissions
└── java/com/mio/os/
    ├── MainActivity.kt            # the real home activity (+ debug-only QA router)
    ├── MioApp.kt                  # Application entry, service locator init
    ├── core/                      # prefs, apps, launcher helper, media, AI, weather, folders
    ├── service/                   # MusicService (media notification), notification listener, device admin
    └── ui/
        ├── MioRoot.kt             # navigation host, overlays, back handling
        ├── theme/                 # colours, typography, glass design system
        ├── components/            # glass cards, sliders, orbs, folder UI
        ├── home/ drawer/ control/ # the three primary surfaces
        ├── settings/ setup/       # settings screens + onboarding
        └── apps/                  # AI, music, files, gallery, themes, notes, calendar,
                                   # clock, calculator, weather, gaming, lock, about
```

The home intent filter that makes this a real launcher:

```xml
<intent-filter android:priority="1">
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

Default-home handling lives in `core/LauncherHelper.kt`:
`RoleManager.createRequestRoleIntent(ROLE_HOME)` on Android 10+, falling back to
`Settings.ACTION_HOME_SETTINGS` / the HOME chooser on older releases, with
`resolveActivity(... CATEGORY_HOME …)` used to detect the current default.

---

## 5. Build & test

**Android Studio:** open the folder, JDK 17, Gradle sync, Run.
The Gradle wrapper is generated by CI (`gradle/wrapper`), or just use your local Gradle 8.9+.

**Command line:**
```bash
gradle assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
gradle assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

**CI (GitHub Actions)**
- `Android Build` → builds debug + release APKs, publishes a GitHub Release and commits
  `dist/` + `ci/build.log` back to the branch.
- `Launcher Emulator Test` → boots an Android 13 emulator shaped like a **Samsung Galaxy A03**
  (720×1600 @ 270 dpi, 2 GB RAM cap), installs the APK, sets it as the default home app,
  presses the Home key, exercises every screen, reboots the device and checks for crashes.
  Results: [`ci/emulator-report.txt`](ci/emulator-report.txt) ·
  Screenshots: [`ci/shots/`](ci/shots)

Debug-only QA hooks (ignored by release builds):
```bash
adb shell am start -n com.mio.os.debug/com.mio.os.MainActivity \
  --ez mio_skip_setup true --es mio_screen settings
```

---

## 6. Permissions and why

`QUERY_ALL_PACKAGES` (a launcher must list apps) · `SET_WALLPAPER` · `READ_MEDIA_*`
(artwork, gallery, music) · `POST_NOTIFICATIONS` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (player) ·
`INTERNET` (weather, optional AI endpoint) · `RECORD_AUDIO` (voice input only) ·
optional special access: notification listener (badges), DND, modify-system-settings,
device admin (lock). Nothing is uploaded anywhere; there is no account, analytics or ad SDK.

---

## 7. Credits

Design language, copy and concept: **RJNX — MIO OS v1.0**. Implementation: this repository.
Built on Android AOSP APIs with Jetpack Compose. Wallpapers are AI-generated artwork
bundled with the app.
