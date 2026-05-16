# Just Speak to It Android

Native Android parity app for the iOS `SpeakiOS` target.

## Build

```bash
gradle -p android assembleDebug
```

## Unit Tests

```bash
gradle -p android testDebugUnitTest
```

## Emulator Validation

Start an Android emulator, then run:

```bash
android/scripts/validate-emulator.sh
```

The script builds the app and test APK, installs both through adb, runs the
instrumentation suite with `adb shell am instrument`, verifies the app is the
focused emulator window, and captures `android/validation/justspeaktoit-android.png`.

The validation suite covers the Transcribe, Settings/API keys, History,
Post-Processing, OpenClaw chat, foreground notification, and
shortcut-equivalent surfaces described in `Docs/Android/ANDROID_PARITY_CHECKLIST.md`.
