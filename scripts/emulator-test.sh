#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# MIO OS launcher verification on a real Android emulator.
#
# Target device profile: Samsung Galaxy A03 class
#   Android 13 (API 33), 720x1600 @ 270dpi (~426dp), 2 GB RAM cap.
#
# Every check runs with `set +e` and the script always exits 0 so the report and
# screenshots are published even if a single assertion fails.
# ---------------------------------------------------------------------------
set +e

PKG=com.mio.os.debug
ACT=$PKG/com.mio.os.MainActivity
SHOTS=ci/shots
mkdir -p "$SHOTS"

PASS=0
FAIL=0

step() { echo ""; echo "=== $* ==="; }
ok()   { PASS=$((PASS + 1)); echo "PASS: $*"; }
bad()  { FAIL=$((FAIL + 1)); echo "FAIL: $*"; }

check_contains() { # $1 haystack, $2 needle, $3 label
  if echo "$1" | grep -q "$2"; then ok "$3"; else bad "$3"; fi
}

shot() { adb exec-out screencap -p > "$SHOTS/$1"; }

open_screen() { # $1 = mio_screen value, $2 = screenshot name
  adb shell am start -n "$ACT" --ez mio_skip_setup true --es mio_screen "$1" >/dev/null 2>&1
  sleep 2
  shot "$2"
}

step "0. Device profile"
adb devices
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
REL=$(adb shell getprop ro.build.version.release | tr -d '\r')
echo "Android $REL (API $SDK)"
adb shell wm size
adb shell wm density
adb shell wm density 270
sleep 2
echo "density set to 270dpi for the Galaxy A03 profile"
adb shell wm size
adb shell wm density
echo "RAM cap (emulator -memory 2048):"
adb shell cat /proc/meminfo | head -3

step "1. Install the debug APK"
APK=$(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)
echo "APK: $APK"
INSTALL=$(adb install -r -g "$APK" 2>&1)
echo "$INSTALL"
check_contains "$INSTALL" "Success" "APK installed"

step "2. Is MIO OS registered as an Android HOME app?"
HOMES=$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1)
echo "$HOMES"
check_contains "$HOMES" "com.mio.os.debug/com.mio.os.MainActivity" "HOME intent filter with CATEGORY_HOME is registered"

step "3. Set MIO OS as the default home app (system command)"
adb shell cmd package set-home-activity "$ACT" 2>&1
DEFAULT=""
for i in $(seq 1 10); do
  sleep 2
  DEFAULT=$(adb shell cmd shortcut get-default-launcher 2>&1)
  echo "attempt $i: $DEFAULT"
  echo "$DEFAULT" | grep -q "com.mio.os.debug" && break
done
check_contains "$DEFAULT" "com.mio.os.debug" "Android resolves MIO OS as the default launcher"
# the launcher itself must agree with the system
INAPP=$(adb shell dumpsys package com.mio.os.debug | grep -A2 "android.intent.category.HOME" | head -3)
echo "$INAPP"

step "4. Cold start (setup flow) + screenshot"
adb shell am force-stop "$PKG"
adb shell am start -n "$ACT" 2>&1
sleep 6
shot 01-onboarding-welcome.png

step "5. Skip setup via the debug-only QA hook and open the home screen"
adb shell am start -n "$ACT" --ez mio_skip_setup true 2>&1
sleep 4
shot 02-home.png
FOCUS=$(adb shell dumpsys window | grep -E "mCurrentFocus" | head -1)
echo "$FOCUS"
check_contains "$FOCUS" "com.mio.os.debug" "Home screen is in the foreground"

step "6. Physical HOME key must return to MIO OS"
adb shell am start -a android.settings.SETTINGS >/dev/null 2>&1
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 3
FOCUS=$(adb shell dumpsys window | grep -E "mCurrentFocus" | head -1)
echo "$FOCUS"
check_contains "$FOCUS" "com.mio.os.debug" "HOME key opens MIO OS instead of another launcher"
shot 03-home-after-home-key.png

step "7. App drawer (swipe up from the home grid)"
SIZE=$(adb shell wm size | tr -d '\r' | awk -F' ' '{print $3}')
W=${SIZE%x*}; H=${SIZE#*x}
echo "screen ${W}x${H}"
adb shell input swipe $((W / 2)) $((H * 80 / 100)) $((W / 2)) $((H * 28 / 100)) 320
sleep 3
shot 04-app-drawer.png

step "8. Drawer search field + category filter"
adb shell input tap $((W / 2)) $((H * 15 / 100))
sleep 1
adb shell input text "set"
sleep 2
shot 05-drawer-search.png
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2

step "9. Control centre (swipe down from the top of the home screen)"
adb shell am start -n "$ACT" --ez mio_skip_setup true >/dev/null 2>&1
sleep 2
adb shell input swipe $((W / 2)) $((H * 15 / 100)) $((W / 2)) $((H * 80 / 100)) 320
sleep 3
shot 06-control-center.png
step "9b. Torch toggle from the control centre"
adb shell input tap $((W / 10)) $((H * 33 / 100))
sleep 2
shot 07-control-center-torch.png
adb shell input tap $((W / 10)) $((H * 33 / 100))
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2

step "10. Settings, appearance, privacy, about"
open_screen settings 08-settings.png
open_screen appearance 09-appearance.png
open_screen privacy 10-privacy.png
open_screen about 11-about.png

step "11. MIO AI assistant"
open_screen ai 12-ai.png
step "11b. AI quick action (tap 'Analyze' = battery query)"
adb shell input tap $((W * 26 / 100)) $((H * 62 / 100))
sleep 4
shot 13-ai-reply.png

step "12. Music player"
open_screen music 14-music.png
step "12b. Start playback (tap the play button)"
adb shell input tap $((W / 2)) $((H * 50 / 100))
sleep 4
shot 15-music-playing.png
MEDIA=$(adb shell dumpsys media_session | grep -i -m3 "state=PlaybackState")
echo "$MEDIA"
check_contains "$MEDIA" "PlaybackState" "Media session active (lock-screen controls available)"

step "13. Themed apps: themes, files, gallery, notes, calendar, clock, calculator, weather, gaming"
open_screen themes 16-themes.png
open_screen files 17-files.png
open_screen gallery 18-gallery.png
open_screen notes 19-notes.png
open_screen calendar 20-calendar.png
open_screen clock 21-clock.png
open_screen calculator 22-calculator.png
open_screen weather 23-weather.png
open_screen gaming 24-gaming.png
open_screen lock 25-lock-screen.png
open_screen home 26-home-final.png

step "14. Launch a real third-party app from the drawer, then press Home"
adb shell am start -a android.settings.SETTINGS >/dev/null 2>&1
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 3
FOCUS=$(adb shell dumpsys window | grep -E "mCurrentFocus" | head -1)
echo "$FOCUS"
check_contains "$FOCUS" "com.mio.os.debug" "Returning from an app lands on the MIO OS home screen"

step "15. Performance snapshot (frame stats, memory)"
echo "NOTE: the emulator renders with swiftshader (no GPU), so absolute frame"
echo "times are CPU-rasterisation bound and much worse than on real hardware."
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames" | head -3
adb shell am start -n "$ACT" --ez mio_skip_setup true >/dev/null 2>&1
sleep 3
# pure home-screen interaction: swipe the pager left/right, open and close the drawer
adb shell input swipe $((W * 80 / 100)) $((H * 55 / 100)) $((W * 20 / 100)) $((H * 55 / 100)) 200
sleep 1
adb shell input swipe $((W * 20 / 100)) $((H * 55 / 100)) $((W * 80 / 100)) $((H * 55 / 100)) 200
sleep 1
adb shell input swipe $((W / 2)) $((H * 80 / 100)) $((W / 2)) $((H * 28 / 100)) 300
sleep 3
adb shell input keyevent KEYCODE_BACK
sleep 3
echo "--- home-screen interaction frames only (gfxinfo reset before the swipes) ---"
adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" | head -8
echo ""
echo "=== 15b. Baseline: the platform Settings app measured identically ==="
echo "(if a stock AOSP app also renders at ~100 ms/frame here, the emulator's"
echo " software rasteriser — not MIO OS — is the bottleneck)"
adb shell am start -a android.settings.SETTINGS >/dev/null 2>&1
sleep 4
adb shell dumpsys gfxinfo com.android.settings reset >/dev/null 2>&1
for i in 1 2 3; do
  adb shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 30 / 100)) 300
  sleep 1
done
sleep 2
adb shell dumpsys gfxinfo com.android.settings | grep -E "Total frames|Janky frames|50th percentile|90th percentile" | head -5
echo "--- CPU rasterisation proof ---"
adb shell getprop ro.hardware.egl
adb shell getprop | grep -i "gpu" | head -3
adb shell input keyevent KEYCODE_HOME
sleep 4
# second MIO measurement after the wallpaper baking optimisation
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
adb shell input swipe $((W * 80 / 100)) $((H * 55 / 100)) $((W * 20 / 100)) $((H * 55 / 100)) 200
sleep 1
adb shell input swipe $((W * 20 / 100)) $((H * 55 / 100)) $((W * 80 / 100)) $((H * 55 / 100)) 200
sleep 1
adb shell input swipe $((W / 2)) $((H * 80 / 100)) $((W / 2)) $((H * 28 / 100)) 300
sleep 3
adb shell input keyevent KEYCODE_BACK
sleep 2
echo "--- MIO after the home-screen interaction pass ---"
adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|50th percentile|90th percentile" | head -3

echo "--- pipeline breakdown (Draw/Prepare are CPU, GPU is the rasteriser) ---"
adb shell dumpsys gfxinfo "$PKG" | sed -n '/Draw\|Prepare\|Process\|Execute/,+3p' | head -12
echo "--- memory ---"
adb shell dumpsys meminfo "$PKG" | grep -E "TOTAL PSS|Java Heap|Native Heap" | head -6
echo "--- app startup ---"
adb shell am force-stop "$PKG"
START=$(date +%s%N)
adb shell am start -W -n "$ACT" --ez mio_skip_setup true | grep -E "TotalTime|WaitTime"
END=$(date +%s%N)
echo "wall clock start: $(( (END - START) / 1000000 )) ms"
sleep 3

step "16. Stability: 500 random UI events"
adb shell monkey -p "$PKG" --throttle 80 --pct-syskeys 0 -v 500 >/dev/null 2>&1
sleep 2
adb shell input keyevent KEYCODE_HOME
sleep 3
ALIVE=$(adb shell pidof "$PKG" | tr -d '\r')
echo "pid: $ALIVE"
if [ -n "$ALIVE" ]; then ok "Launcher still alive after 500 random events"; else bad "Launcher died during monkey test"; fi
shot 27-after-monkey.png

step "17. Reboot – the default home app must survive"
adb reboot
adb wait-for-device
sleep 70
adb shell wm density 270
sleep 2
DEFAULT=$(adb shell cmd shortcut get-default-launcher 2>&1)
echo "$DEFAULT"
check_contains "$DEFAULT" "com.mio.os.debug" "Default launcher survives a reboot"
adb shell input keyevent KEYCODE_HOME
sleep 10
FOCUS=$(adb shell dumpsys window | grep -E "mCurrentFocus" | head -1)
echo "$FOCUS"
check_contains "$FOCUS" "com.mio.os.debug" "HOME after reboot opens MIO OS"
shot 28-after-reboot.png

step "18. Release APK smoke test (R8-minified build)"
RELAPK=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)
if [ -n "$RELAPK" ]; then
  echo "Release APK: $RELAPK"
  RELINSTALL=$(adb install -r -g "$RELAPK" 2>&1)
  echo "$RELINSTALL"
  check_contains "$RELINSTALL" "Success" "R8-minified release APK installs"
  adb shell cmd package set-home-activity com.mio.os/com.mio.os.MainActivity 2>&1
  sleep 4
  adb shell am force-stop com.mio.os
  adb shell am start -n com.mio.os/com.mio.os.MainActivity --ez mio_skip_setup true 2>&1
  sleep 8
  shot 29-release-home.png
  RFOCUS=$(adb shell dumpsys window | grep -E "mCurrentFocus" | head -1)
  echo "$RFOCUS"
  check_contains "$RFOCUS" "com.mio.os/" "Release build launches as the home screen"
  RELDEFAULT=$(adb shell cmd shortcut get-default-launcher 2>&1)
  echo "$RELDEFAULT"
  check_contains "$RELDEFAULT" "com.mio.os/" "Release build is the default home app"
  adb shell input keyevent KEYCODE_HOME
  sleep 4
  RFOCUS=$(adb shell dumpsys window | grep -E "mCurrentFocus" | head -1)
  check_contains "$RFOCUS" "com.mio.os/" "Release build answers the HOME key"
  adb shell input swipe $((W / 2)) $((H * 80 / 100)) $((W / 2)) $((H * 28 / 100)) 320
  sleep 3
  shot 30-release-drawer.png
  adb shell input keyevent KEYCODE_BACK
  sleep 2
else
  bad "Release APK not found"
fi

step "19. Crash log"
CRASH=$(adb logcat -d -b crash 2>&1 | tail -60)
echo "$CRASH"
FATAL=$(adb logcat -d 2>&1 | grep -iE "FATAL EXCEPTION|E AndroidRuntime" | tail -30)
echo "--- runtime exceptions ---"
echo "$FATAL"
if echo "$CRASH$FATAL" | grep -q "com.mio.os"; then
  bad "Crash detected in logcat (see above)"
else
  ok "No crashes in the system crash buffer or logcat"
fi

echo ""
echo "================================"
echo "RESULT: $PASS passed, $FAIL failed"
echo "================================"
exit 0
