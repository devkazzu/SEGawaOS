# SEGawa OS — Beyond Ordinary

A native Android companion/launcher-style dashboard built with Kotlin and Jetpack Compose.

## Included
- Premium dark Japanese-minimal inspired UI
- Home dashboard and quick navigation
- Installed launcher-app discovery, search, and app launching
- Device information and battery status
- Focus timer with presets, pause/resume/reset, and local session history
- Persistent accent and appearance preferences using DataStore
- Personalization screen and settings
- Wi-Fi, Bluetooth, sound and display settings shortcuts
- Local-only operation; no account or backend

## Build
1. Open this folder in Android Studio (JDK 17).
2. Allow Gradle sync.
3. Run on an Android 8.0+ device/emulator.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).

The APK is produced by Android Studio at:
`app/build/outputs/apk/debug/app-debug.apk`

## Notes
- This is a companion app, not a replacement Android OS.
- Android restricts changing protected system settings. Control Center shortcuts open the relevant Android settings pages.
- App discovery uses the launcher intent query declared in the manifest; Android package visibility rules still apply.
- Focus history and preferences are stored locally.
