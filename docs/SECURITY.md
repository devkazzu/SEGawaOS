# SEGAWA OS security & backend notes

## There is no SEGAWA backend — by design

SEGAWA OS v2.0 is an **offline-first launcher with no first-party backend**:
no accounts, no analytics, no telemetry, no ad SDK, no push provider. Every
feature works in airplane mode except the ones that inherently need the
network (browser, weather data, user-configured AI endpoints). This document
records what touches the network, what is stored where, and what a hosted
service would still need if one is ever built.

## Network use (all user-initiated, all HTTPS)

| Feature | Destination | Notes |
|---|---|---|
| Weather | `api.open-meteo.com` | No API key; city-level request only |
| AI cloud replies | User-supplied OpenAI-compatible endpoint | Only when the user pastes an endpoint + key; history window is the last 10 messages |
| Browser | Wherever the user navigates | System WebView; cleartext HTTP is blocked (targetSdk 35 default) |

No pre-seeded endpoints, no license checks, no crash reporters.

## Data at rest

- Everything lives in one private `SharedPreferences` file, `mio_prefs_v1`
  (plus MediaStore content the apps read, never copy).
- The AI API key is stored in that same private file and sent only to the
  user's own endpoint as an `Authorization: Bearer` header. It is never
  logged, and the repository contains no API keys — only the user-entered
  value on their own device.
- `android:allowBackup="true"` means the system backup agent includes the
  prefs file (and therefore the AI key) in the user's own device backup.
  Treat device backups as sensitive if you configured a cloud endpoint.
- Backup files: plain JSON unless the user sets a password, in which case
  the whole document is AES-256-GCM encrypted (`SHA-256(password ‖ salt)`
  key derivation, random 16-byte salt + 12-byte IV per export).
  Decryption with a wrong password fails closed (returns null).

## Exported components

| Component | Exported | Protection |
|---|---|---|
| `MainActivity` (HOME + LAUNCHER) | Yes | Required to be a launcher; debug extras (`mio_screen`, `mio_skip_setup`) are ignored in release builds; `segawa_open` accepts a fixed 6-screen allowlist and requires onboarding |
| `MusicService` | No | Same-app only |
| `MioNotificationListener` | Yes | `BIND_NOTIFICATION_LISTENER_SERVICE`, user opt-in |
| `MioDeviceAdmin` | Yes | `BIND_DEVICE_ADMIN`, user opt-in |
| `SegaWallpaperService` | Yes | `BIND_WALLPAPER` |
| 4 × widget providers | Yes | AppWidget framework only (`APPWIDGET_UPDATE`) |
| `SegaAlarmReceiver` | No | In-app alarms + `BOOT_COMPLETED` re-arm |

## Permissions

Install-time permissions are limited to launcher essentials (`QUERY_ALL_PACKAGES`,
`SET_WALLPAPER`, `READ_MEDIA_*`, `POST_NOTIFICATIONS`, `INTERNET`, …). Sensitive
capabilities (notification access, DND, modify-settings, device admin, Usage
Access) are all runtime opt-ins that hand off to official Android settings
screens. Voice input/output go through the system speech services, so no
microphone permission is declared.

## What hosted services would still need (not in 2.0)

An online theme store, sync, or hosted AI would require, at minimum:

1. An account system (auth, password reset, deletion) and hosted storage.
2. A moderation pipeline for community uploads (themes, wallpapers).
3. A push/update channel and a signing authority for OTA.
4. A privacy policy, data-retention rules and an abuse contact.
5. Funding for hosting, review tooling and incident response.

None of these exist today, which is why the store is an honest on-device
collection and AI cloud access is bring-your-own-endpoint. Any future backend
work starts from this list — no credentials or infrastructure are required to
build, test or run v2.0.
