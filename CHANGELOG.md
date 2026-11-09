# SEGAWA OS changelog

## v2.0.0 (Sakura)

Full upgrade from v1.1, implemented in phases C–I. `applicationId` stays
`com.mio.os`, data in `mio_prefs_v1` upgrades in place, and the v1.0.0/v1.1.0
releases are untouched.

**Home & personalization (Phase C)**
- Arrange mode: drag-to-reorder grid with auto-scroll, persisted order
- Icon Studio: 5 profiles (Neon, Glass, AMOLED, Minimal, Bold Gradient) plus
  per-app custom images picked through the system picker
- Wallpaper studio: favorites, recency history, timed rotation (15+ minutes),
  live gradient/petal engines and video wallpapers via `SegaWallpaperService`
- Themes: favorites, history, scheduled rotation, live/video wallpaper, export
  and import themes as shareable text
- Universal search across apps, notes, music, wallpapers and settings
- Double-tap gesture with 5 configurable actions; favorites + dock from every
  context sheet; drawer favorites filter

**Apps (Phase D)**
- 4 home-screen widgets (Clock, Search, Music, Battery), pinnable from the
  drawer; release-safe `segawa_open` deep links
- Notes 2.0: titles, categories, tags, favorites, search, on-device summaries
- Music 2.0: playlists, `.lrc` synced lyrics, sleep timer, favorites
- SEGAWA Browser: smart address bar, bookmarks, history, 4 search engines
- Automation: time-based routines (reminder, focus, music, wallpaper, app)
  with day picker, run-now test, reboot re-arming
- Backup & restore: full settings export/import through the system picker,
  optional AES-256-GCM password encryption
- Theme store: on-device collection, instant apply, honest no-downloads note
- Control Center: editable round tiles, recent-apps row
- About: screen-time dashboard over UsageStats (opt-in)

**Segawa AI 2.0 (Phase E)**
- Chat threads with persistence and v1-history migration
- On-device memory: facts and "my X is Y" pairs with recall/forget
- 20+ local device commands (apps, screens, media, volume, DND, themes,
  wallpaper, brightness, notes, memory)
- Voice replies via system TTS plus existing voice input; per-message
  on-device/cloud source badges

**Quality, security, release (Phases F–I)**
- 45 JVM unit tests over 9 pure core modules, gating every CI build
- Security review: no hardcoded secrets, no cleartext traffic, permission-gated
  exported components; unused `RECORD_AUDIO` permission removed
- `docs/SECURITY.md` documents the no-backend architecture and what hosted
  services would still need
- Version 2.0.0 (versionCode 3), release notes, emulator regression evidence

**Known limitations (honest)**
- Routines use inexact alarms and may drift a few minutes in Doze
- No third-party icon-pack APK support (private formats); Icon Studio instead
- No online store/accounts/sync — no backend by design
- Widgets need a launcher with an AppWidget host (system launcher qualifies)

## v1.1.0 (Hiro)

Previous stable release. Preserved as-is; see the v1.1.0 GitHub release.

## v1.0.0

Initial launcher release. Preserved as-is; see the v1.0.0 GitHub release.
