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
