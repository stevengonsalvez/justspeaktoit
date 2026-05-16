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
grep -q "OK (3 tests)" "$OUT_DIR/adb-instrumentation.txt"
adb shell pm clear "$PKG" >/dev/null || true
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
adb shell am start -n "$PKG/$ACTIVITY"
for _ in 1 2 3 4 5; do
  adb shell dumpsys window | sed -n '/mCurrentFocus/p;/mFocusedApp/p' > "$OUT_DIR/window-focus.txt"
  if grep -q "mCurrentFocus=.*$PKG/$ACTIVITY" "$OUT_DIR/window-focus.txt"; then
    break
  fi
  sleep 1
done
grep -q "mCurrentFocus=.*$PKG/$ACTIVITY" "$OUT_DIR/window-focus.txt"
sleep 5
adb shell screencap -p /sdcard/justspeaktoit-android.png
adb pull /sdcard/justspeaktoit-android.png "$OUT_DIR/justspeaktoit-android.png"

echo "Android emulator validation complete: $OUT_DIR/justspeaktoit-android.png"
