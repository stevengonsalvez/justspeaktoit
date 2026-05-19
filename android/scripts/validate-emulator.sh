#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="com.justspeaktoit.android.debug"
ACTIVITY="com.justspeaktoit.android.MainActivity"
OUT_DIR="$ROOT_DIR/validation"
TIMEOUT_CMD="$(command -v timeout || command -v gtimeout || true)"

mkdir -p "$OUT_DIR"

timed() {
  local seconds="$1"
  shift
  if [ -n "$TIMEOUT_CMD" ]; then
    "$TIMEOUT_CMD" "$seconds" "$@"
  else
    "$@"
  fi
}

adb_timed() {
  local seconds="$1"
  shift
  timed "$seconds" adb "$@"
}

grant_runtime_permissions() {
  adb_timed 15 shell pm grant "$PKG" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
  adb_timed 15 shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
}

dump_ui() {
  local destination="$1"
  adb_timed 20 shell uiautomator dump /sdcard/justspeaktoit-ui.xml >/dev/null
  adb_timed 20 pull /sdcard/justspeaktoit-ui.xml "$destination" >/dev/null
}

ui_bounds_for_text() {
  local text="$1"
  local xml="$2"
  TEXT="$text" perl -0ne '
    my $text = $ENV{"TEXT"};
    while (/<node\b[^>]*>/g) {
      my $node = $&;
      if (($node =~ /\btext="\Q$text\E"/ || $node =~ /\bcontent-desc="\Q$text\E"/) &&
          $node =~ /bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/) {
        print int(($1 + $3) / 2) . " " . int(($2 + $4) / 2);
        exit;
      }
    }
  ' "$xml"
}

assert_ui_text() {
  local text="$1"
  local xml="$2"
  if ! grep -q "$text" "$xml"; then
    echo "Expected UI text not found: $text" >&2
    exit 1
  fi
}

tap_text() {
  local text="$1"
  local xml="${TMPDIR:-/tmp}/justspeaktoit-tap-target.xml"
  dump_ui "$xml"
  local bounds
  bounds="$(ui_bounds_for_text "$text" "$xml")"
  if [ -z "$bounds" ]; then
    echo "Could not find tappable UI text: $text" >&2
    exit 1
  fi
  adb_timed 8 shell input tap $bounds >/dev/null 2>&1 || true
  sleep 1
}

wait_for_ui_text() {
  local text="$1"
  local xml="$2"
  for _ in $(seq 1 12); do
    dump_ui "$xml"
    if grep -q "$text" "$xml"; then
      return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for UI text: $text" >&2
  exit 1
}

adb wait-for-device
gradle -p "$ROOT_DIR" testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb_timed 20 shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb_timed 30 uninstall "$PKG.test" >/dev/null 2>&1 || true
adb_timed 30 uninstall "$PKG" >/dev/null 2>&1 || true
adb_timed 90 install --no-streaming -r "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
adb_timed 90 install --no-streaming -r "$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
adb_timed 20 shell pm clear "$PKG" >/dev/null || true
grant_runtime_permissions
adb_timed 120 shell am instrument -w -r -e debug false "$PKG.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee "$OUT_DIR/adb-instrumentation.txt"
grep -Eq "OK \\([0-9]+ tests?\\)" "$OUT_DIR/adb-instrumentation.txt"
adb_timed 20 shell pm clear "$PKG" >/dev/null || true
grant_runtime_permissions
adb_timed 8 shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
adb_timed 20 shell am start -n "$PKG/$ACTIVITY"
for _ in $(seq 1 15); do
  adb_timed 20 shell dumpsys window | sed -n '/mCurrentFocus/p;/mFocusedApp/p' > "$OUT_DIR/window-focus.txt"
  if grep -Eq "m(CurrentFocus|FocusedApp)=.*$PKG/$ACTIVITY" "$OUT_DIR/window-focus.txt"; then
    break
  fi
  sleep 1
done
grep -Eq "m(CurrentFocus|FocusedApp)=.*$PKG/$ACTIVITY" "$OUT_DIR/window-focus.txt"
sleep 5
adb_timed 20 shell screencap -p /sdcard/justspeaktoit-android.png
adb_timed 20 pull /sdcard/justspeaktoit-android.png "$OUT_DIR/justspeaktoit-android.png"

tap_text "Settings"
sleep 2
dump_ui "$OUT_DIR/flow-bubble-settings-ui.xml"
assert_ui_text "Flow Bubble" "$OUT_DIR/flow-bubble-settings-ui.xml"
assert_ui_text "Manage Keys" "$OUT_DIR/flow-bubble-settings-ui.xml"
assert_ui_text "Start phrase" "$OUT_DIR/flow-bubble-settings-ui.xml"
assert_ui_text "Insertion preview: Hello Flow Android" "$OUT_DIR/flow-bubble-settings-ui.xml"
assert_ui_text "Overlay" "$OUT_DIR/flow-bubble-settings-ui.xml"
assert_ui_text "Accessibility" "$OUT_DIR/flow-bubble-settings-ui.xml"
adb_timed 20 shell screencap -p /sdcard/flow-bubble-settings.png
adb_timed 20 pull /sdcard/flow-bubble-settings.png "$OUT_DIR/flow-bubble-settings.png"

tap_text "Transcribe"
wait_for_ui_text "Just Speak to It" "$OUT_DIR/transcribe-return-ui.xml"
adb_timed 15 shell pm revoke "$PKG" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
adb_timed 20 shell am start -n "$PKG/$ACTIVITY" >/dev/null
wait_for_ui_text "Just Speak to It" "$OUT_DIR/transcribe-after-permission-revoke-ui.xml"
tap_text "Mic"
wait_for_ui_text "Listening now" "$OUT_DIR/transcribe-recording-ui.xml"
adb_timed 20 shell screencap -p /sdcard/transcribe-recording.png
adb_timed 20 pull /sdcard/transcribe-recording.png "$OUT_DIR/transcribe-recording.png" >/dev/null
tap_text "Stop"
wait_for_ui_text "1 saved" "$OUT_DIR/transcribe-stopped-ui.xml"
tap_text "History"
wait_for_ui_text "This is a live Android transcription" "$OUT_DIR/history-ui.xml"
adb_timed 20 shell screencap -p /sdcard/history.png
adb_timed 20 pull /sdcard/history.png "$OUT_DIR/history.png" >/dev/null

tap_text "OpenClaw"
wait_for_ui_text "New Conversation" "$OUT_DIR/openclaw-list-ui.xml"
tap_text "New Conversation"
wait_for_ui_text "Type a message" "$OUT_DIR/openclaw-chat-ui.xml"
tap_text "Mic"
wait_for_ui_text "Listening: send this to OpenClaw" "$OUT_DIR/openclaw-voice-listening-ui.xml"
tap_text "Stop"
wait_for_ui_text "OpenClaw heard: send this to OpenClaw" "$OUT_DIR/openclaw-voice-response-ui.xml"
adb_timed 20 shell screencap -p /sdcard/openclaw-voice-response.png
adb_timed 20 pull /sdcard/openclaw-voice-response.png "$OUT_DIR/openclaw-voice-response.png" >/dev/null

echo "Android emulator validation complete: $OUT_DIR/justspeaktoit-android.png"
