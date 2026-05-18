#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="com.justspeaktoit.android.debug"
ACTIVITY="com.justspeaktoit.android.MainActivity"
OUT_DIR="$ROOT_DIR/validation"

mkdir -p "$OUT_DIR"

adb wait-for-device
gradle -p "$ROOT_DIR" testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb install -r "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
adb shell pm clear "$PKG" >/dev/null || true
adb shell am instrument -w -r -e debug false "$PKG.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee "$OUT_DIR/adb-instrumentation.txt"
grep -Eq "OK \\([0-9]+ tests?\\)" "$OUT_DIR/adb-instrumentation.txt"
adb shell pm clear "$PKG" >/dev/null || true
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
adb shell am start -n "$PKG/$ACTIVITY"
for _ in $(seq 1 15); do
  adb shell dumpsys window | sed -n '/mCurrentFocus/p;/mFocusedApp/p' > "$OUT_DIR/window-focus.txt"
  if grep -Eq "m(CurrentFocus|FocusedApp)=.*$PKG/$ACTIVITY" "$OUT_DIR/window-focus.txt"; then
    break
  fi
  sleep 1
done
grep -Eq "m(CurrentFocus|FocusedApp)=.*$PKG/$ACTIVITY" "$OUT_DIR/window-focus.txt"
sleep 5
adb shell screencap -p /sdcard/justspeaktoit-android.png
adb pull /sdcard/justspeaktoit-android.png "$OUT_DIR/justspeaktoit-android.png"

SIZE="$(adb shell wm size | tr -d '\r' | sed -n 's/Physical size: //p' | tail -1)"
WIDTH="${SIZE%x*}"
HEIGHT="${SIZE#*x}"
adb shell input tap "$((WIDTH * 73 / 100))" "$((HEIGHT * 72 / 100))"
sleep 2
adb shell uiautomator dump /sdcard/flow-bubble-settings.xml >/dev/null
adb pull /sdcard/flow-bubble-settings.xml "$OUT_DIR/flow-bubble-settings-ui.xml" >/dev/null
grep -q "Flow Bubble" "$OUT_DIR/flow-bubble-settings-ui.xml"
grep -q "Manage Keys" "$OUT_DIR/flow-bubble-settings-ui.xml"
grep -q "Start phrase" "$OUT_DIR/flow-bubble-settings-ui.xml"
grep -q "Insertion preview: Hello Flow Android" "$OUT_DIR/flow-bubble-settings-ui.xml"
grep -q "Overlay" "$OUT_DIR/flow-bubble-settings-ui.xml"
grep -q "Accessibility" "$OUT_DIR/flow-bubble-settings-ui.xml"
adb shell screencap -p /sdcard/flow-bubble-settings.png
adb pull /sdcard/flow-bubble-settings.png "$OUT_DIR/flow-bubble-settings.png"

echo "Android emulator validation complete: $OUT_DIR/justspeaktoit-android.png"
