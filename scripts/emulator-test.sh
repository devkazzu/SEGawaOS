#!/usr/bin/env bash
# MIO OS launcher verification on a real Android emulator.
# Runs every check with `set +e` and always exits 0 so the report is published.
set +e

PKG=com.mio.os.debug
ACT=$PKG/com.mio.os.MainActivity
SHOTS=ci/shots
mkdir -p "$SHOTS"

step() { echo ""; echo "=== $* ==="; }

step "Environment"
adb devices
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell wm density

APK=$(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)
step "Install $APK"
adb install -r -g "$APK"

step "1. Is MIO OS registered as a HOME app?"
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME

step "2. Make MIO OS the default home app"
adb shell cmd package set-home-activity "$ACT"
sleep 3
adb shell cmd shortcut get-default-launcher

step "3. Cold start"
adb shell am force-stop "$PKG"
adb shell am start -n "$ACT"
sleep 7
adb exec-out screencap -p > "$SHOTS/01-welcome.png"

step "4. Skip onboarding through the debug test hook"
SIZE=$(adb shell wm size | tr -d '\r' | awk -F' ' '{print $3}')
W=${SIZE%x*}
H=${SIZE#*x}
echo "screen ${W}x${H}"
adb shell am start -n "$ACT" --ez mio_skip_setup true
sleep 4
adb exec-out screencap -p > "$SHOTS/02-home.png"

step "5. HOME key must return to MIO OS"
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | head -4
adb exec-out screencap -p > "$SHOTS/03-after-home-key.png"

step "6. Swipe up -> app drawer"
adb shell input swipe $((W / 2)) $((H * 85 / 100)) $((W / 2)) $((H * 30 / 100)) 300
sleep 3
adb exec-out screencap -p > "$SHOTS/04-drawer.png"

step "7. Back, then swipe down -> control centre"
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input swipe $((W / 2)) $((H * 20 / 100)) $((W / 2)) $((H * 80 / 100)) 300
sleep 3
adb exec-out screencap -p > "$SHOTS/05-control-center.png"

step "8. Control centre: tap flashlight + brightness area"
adb shell input tap $((W / 8)) $((H * 33 / 100))
sleep 2
adb exec-out screencap -p > "$SHOTS/06-control-center-torch.png"
adb shell input keyevent KEYCODE_BACK
sleep 2

step "9. Open the drawer and launch a real third-party app (system Settings)"
adb shell am start -n "$ACT"
sleep 3
adb shell input swipe $((W / 2)) $((H * 85 / 100)) $((W / 2)) $((H * 30 / 100)) 300
sleep 3
adb shell input tap $((W / 2)) $((H * 30 / 100))
sleep 4
adb exec-out screencap -p > "$SHOTS/07-drawer-launch.png"
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell dumpsys window | grep -E "mCurrentFocus" | head -2
adb exec-out screencap -p > "$SHOTS/08-home-return.png"

step "10. Settings screen"
adb shell input tap $((W * 94 / 100)) $((H * 5 / 100))
sleep 3
adb exec-out screencap -p > "$SHOTS/09-settings.png"
adb shell input keyevent KEYCODE_BACK
sleep 2

step "11. MIO AI assistant"
adb shell am start -n "$ACT"
sleep 2
adb shell input swipe $((W / 2)) $((H * 85 / 100)) $((W / 2)) $((H * 30 / 100)) 300
sleep 2
adb shell input tap $((W * 12 / 100)) $((H * 93 / 100))
sleep 4
adb exec-out screencap -p > "$SHOTS/10-ai.png"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1

step "12. Stability: 400 random UI events"
adb shell monkey -p "$PKG" --throttle 90 --pct-syskeys 0 -v 400 >/dev/null 2>&1
sleep 2
adb shell input keyevent KEYCODE_HOME
sleep 3
adb exec-out screencap -p > "$SHOTS/11-after-monkey.png"

step "13. Reboot: default home app must survive"
adb reboot
adb wait-for-device
sleep 60
adb shell cmd shortcut get-default-launcher
adb shell input keyevent KEYCODE_HOME
sleep 10
adb shell dumpsys window | grep -E "mCurrentFocus" | head -2
adb exec-out screencap -p > "$SHOTS/12-after-reboot.png"

step "14. Crash log"
adb logcat -d -b crash | tail -60
echo "--- java crashes ---"
adb logcat -d | grep -iE "FATAL EXCEPTION|E AndroidRuntime" | tail -40
echo "--- app alive? ---"
adb shell pidof "$PKG"

echo ""
echo "=== MIO OS emulator verification finished ==="
exit 0
