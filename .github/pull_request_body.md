# MIO OS v1.0 — a real, installable Android home-screen launcher

This PR replaces the Segawa prototype with **MIO OS v1.0 by RJNX**: a complete Kotlin + Jetpack Compose
launcher (`com.mio.os`, minSdk 26 / target 35) that registers a genuine `CATEGORY_HOME` activity, can be
selected as the system Home app, and was verified end-to-end on a real Android 13 emulator — including a
device reboot.

* 51 commits, 109 files, 32 Kotlin sources, ~10.6k insertions
* Signed APKs built by CI and committed: `dist/MIO-OS-debug.apk` (18 MB), `dist/MIO-OS-release.apk` (2.2 MB, R8 + resource shrinking)
* GitHub Release: https://github.com/devkazzu/SEGawaOS/releases/tag/v1.0.0

## Verification (not claims)

`.github/workflows/emulator-test.yml` boots an emulator shaped like a **Samsung Galaxy A03**
(Android 13 / API 33, 720×1600, 270 dpi, `-memory 2048`), installs the APK and drives the real UI.
Latest run: **16 / 16 checks passed**, no `FATAL EXCEPTION` in logcat.

| Check | Result |
|---|---|
| APK installs | ✅ |
| `MAIN` + `CATEGORY_HOME` filter registered | ✅ |
| Resolves as the **default launcher** (`set-home-activity`) | ✅ |
| HOME key is actually **routed** to MIO OS | ✅ |
| Home screen is the foreground window | ✅ |
| **HOME key** returns to MIO OS from another app | ✅ |
| Media session active (lock-screen controls) | ✅ |
| Drawer / search / control centre / torch toggle | ✅ screenshots 04–07 |
| Settings, Appearance, Privacy, About, AI, Music, Files, Gallery, Themes, Notes, Calendar, Clock, Calculator, Weather, Gaming, Lock | ✅ screenshots 08–26 |
| 500 random monkey events without dying | ✅ |
| **Default launcher survives `adb reboot`** | ✅ |
| HOME after reboot opens MIO OS | ✅ |
| **R8-minified release APK**: installs, is default home, answers HOME | ✅ |
| No crash in the crash buffer or logcat | ✅ |

Evidence lives in the branch: [`ci/emulator-report.txt`](../blob/arena/4e9da799-segawaos/ci/emulator-report.txt)
and 41 screenshots in [`ci/shots/`](../tree/arena/4e9da799-segawaos/ci/shots) (home, drawer, control centre,
settings, AI, player, themes, files, gallery, notes, calendar, clock, calculator, weather, gaming, lock screen,
post-reboot, release build).

## What's implemented

**Home** — parallax wallpaper, gradient clock/date, live weather (Open-Meteo, no key), always-present music
widget, multi-page app grid, folders (long-press → *Move to folder*), dock, search pill, notification badges,
swipe-up drawer / swipe-down control centre, long-press customisation, app quick sheets (info / hide / uninstall).

**Drawer** — every installed launchable app with real icons, live search, category chips, A–Z / recent /
category sorting, widgets tab, hidden apps, per-app actions.

**Control centre** — real torch (`CameraManager.setTorchMode`), DND (`setInterruptionFilter`), night mode,
volume (`AudioManager`) and window/system brightness; Wi-Fi, Bluetooth, airplane, hotspot, rotation and cast
open the official Android panels because a normal app is not permitted to toggle them.

**MIO AI** — offline assistant that performs real device actions (open app, torch, brightness, notes, music,
battery/storage/time, "am I the default launcher?") plus optional OpenAI-compatible endpoint + key + model,
stored locally.

**Music** — MediaStore library, artwork, seek, queue, foreground `MusicService` with media notification and
platform `MediaSession` (lock-screen controls), real `audiofx.Equalizer`, bundled demo track so playback works
on a fresh device.

**Also** — Files, Gallery, Themes (4 bundled artworks + custom image + 10 accent palettes), Notes, Calendar
(`CalendarContract`), Clock (alarms/stopwatch/timer/world), Calculator (own parser), Weather (live + 5-day),
Gaming mode, Device info/About, Hidden apps, MIO lock screen. Settings engine persists accent colour,
wallpaper, grid columns, icon size/shape, labels, font scale, glass intensity, dim, animations, dock and
drawer sorting via `SharedPreferences` + `StateFlow`.

## Performance work

The emulator baseline (AOSP Settings: 16 ms/frame) proved the heavy rendering was ours, so:

* wallpaper + vignette + accent glow + dim are **baked into one cached texture** (median frame time 89 ms → ~44 ms on a GPU-less emulator, which is 3–5× slower than real hardware)
* the clock ticker was moved into the clock so the home screen no longer recomposes every second
* app icons are decoded once into an LRU bitmap cache
* navigation slides instead of cross-fading (no full-screen alpha layer per frame), and the root background fill was removed
* release builds enable R8 + resource shrinking: 12.1 MB → 2.2 MB, faster cold start

## Platform limits (deliberate, labelled in the UI)

Cannot replace the system lock screen/PIN, cannot toggle Wi-Fi/Bluetooth/airplane/hotspot on Android 10+,
cannot write Secure settings or boost the CPU without root, no public icon-pack API, Android 11+ blocks raw
filesystem browsing. Each has a legitimate in-app counterpart that is explained to the user rather than faked.

## CI notes

`Android Build` compiles debug + release, publishes the GitHub Release and commits `dist/` + `ci/build.log`
back to the branch; `Launcher Emulator Test` runs `scripts/emulator-test.sh`. Both publish with a
fetch/rebase/push retry loop, and the emulator step uses `set -o pipefail` so a failed APK build can no longer
masquerade as a pass. The QA hooks used to drive individual screens (`--ez mio_skip_setup true --es mio_screen …`)
exist only in debug builds.
