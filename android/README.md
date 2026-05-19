# Just Speak to It Android

Native Android parity app for the iOS `SpeakiOS` target.

## Runtime Coverage

- UI: Kotlin, Jetpack Compose, Material 3, AndroidX Navigation/lifecycle
- Persistence: Android DataStore for settings/history/conversations
- Secrets: Android Keystore-backed `EncryptedSharedPreferences`
- Transcription: Android `SpeechRecognizer` locally, plus AudioRecord/OkHttp WebSocket streaming for Deepgram, ElevenLabs Scribe, and OpenAI Realtime when keys and microphone permission are available
- Post-processing: OpenRouter HTTP via OkHttp with deterministic polish fallback
- OpenClaw: OkHttp WebSocket gateway client with deterministic emulator fallback
- Speech output: Android TextToSpeech for assistant responses
- Recording controls: foreground service notification and launcher shortcut intent
- Flow Bubble: Android overlay bubble with AccessibilityService insertion, phrase-start trimming in explicit listening mode, spoken cleanup commands, and clipboard recovery

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

The validation script runs the Gradle unit/build gates, installs the app and
test APK, runs an instrumentation launch smoke test, verifies the app is focused,
and captures the main app screenshot. It also opens Settings through adb and
verifies API-key access plus the Flow Bubble UI with UIAutomator, saving
`android/validation/flow-bubble-settings-ui.xml` and
`android/validation/flow-bubble-settings.png`.

The same script also drives the interactive validation matrix: it taps through
Transcribe mic start/stop, verifies a saved History entry, opens OpenClaw, uses
the OpenClaw voice button, and saves proof XML/screenshots for each step. The
full matrix lives at `Docs/Android/ANDROID_VALIDATION_MATRIX.md`.

Flow Bubble validation is included in the instrumentation suite. The emulator can
show the settings, permission status cards, phrase-start configuration, and
insertion preview automatically. Real cross-app insertion requires the user to
enable the Just Speak to It accessibility service and overlay permission in
Android Settings; when either path is unavailable, dictated text falls back to
clipboard recovery with a visible status message.
