# Android Parity Checklist

This checklist maps the iOS feature inventory from `research/2026-05-16_14-28-31_android_ios_clone_goal.md` to the Android implementation under `android/`.

| iOS feature | Android implementation | Validation evidence |
| --- | --- | --- |
| Root tabs: Transcribe and OpenClaw | `MainActivity.kt` uses Material 3 bottom navigation with Transcribe/OpenClaw tabs | `MainActivityParityTest.openClawFlow_createsConversationAndReceivesAssistantResponse` |
| Live transcription screen | `TranscribeScreen` with recording state, live transcript text, copy, polish, history, settings | `MainActivityParityTest.transcribeFlow_recordsCopiesAndShowsHistory` |
| Provider selection and fallback | `SettingsScreen` exposes Android Speech, Deepgram, ElevenLabs, OpenAI; `SpeakViewModel.resolvedModel` falls back to Android Speech when keys are missing; `AndroidSpeechTranscriber` uses Android `SpeechRecognizer` when microphone permission is granted | `ParityLogicTest.parityChecklist_coversMajorGoalSurfaces`; Gradle unit tests; adb transcription flow |
| Secure API keys | `SpeakRepository` stores keys with Android Keystore-backed `EncryptedSharedPreferences` and falls back to private prefs if device crypto setup is unavailable | `MainActivityParityTest.settingsFlow_exposesApiPostProcessingAndOpenClawConfiguration` |
| Transcript history | `HistoryScreen`, `HistoryEntry`, and Android DataStore-backed JSON persistence in `SpeakRepository` | `MainActivityParityTest.transcribeFlow_recordsCopiesAndShowsHistory` |
| Post-processing/polish | `PostProcessingScreen`, OpenRouter `OpenRouterPostProcessor` via OkHttp when an OpenRouter key is saved, and deterministic `TranscriptFormatter.polish` fallback | `ParityLogicTest.transcriptFormatter_polishesSpacingCasingAndPunctuation`; Gradle unit tests |
| OpenClaw conversations | `OpenClawListScreen`, `OpenClawChatScreen`, `Conversation`, persisted messages, and configured-gateway send path through `OpenClawGatewayClient`/OkHttp WebSocket | `MainActivityParityTest.openClawFlow_createsConversationAndReceivesAssistantResponse`; `ParityLogicTest.chatPayload_matchesOpenClawStreamingShape` |
| OpenClaw gateway protocol helpers | `OpenClawProtocol.normaliseGatewayUrl`, keyword acknowledgement, cleanup helpers, streaming payload, completion detection, and assistant message extraction | `ParityLogicTest.normaliseGatewayUrl_*`, `ParityLogicTest.keywordAcknowledge_*`, `ParityLogicTest.extractAssistantContent_readsGatewayMessageShapes` |
| Conversation mode and acknowledgement controls | `OpenClawChatScreen` and `OpenClawSettingsScreen` support conversation mode, auto-resume, headset acknowledgement, keyword acknowledgement | Compose UI tests plus `ParityLogicTest` keyword coverage |
| TTS controls | `OpenClawSettingsScreen` exposes speak responses, summarise responses, low-latency speech, voice/model/speed state; `AndroidSpeechSpeaker` speaks assistant responses through Android TextToSpeech | Gradle build and Compose settings coverage |
| iOS Live Activities | Android-native foreground service notification in `RecordingForegroundService` using a foreground `dataSync` service for emulator-safe validation | `MainActivityParityTest.transcribeFlow_recordsCopiesAndShowsHistory`; Manifest plus adb install validation |
| iOS AppIntents/quick action | Android launcher shortcut and `TOGGLE_RECORDING` intent in `shortcuts.xml` and `MainActivity` | Manifest plus adb launch/install validation |
| Recordings, Send to Mac, Privacy, Debugging, About | Android settings surfaces with platform-specific parity copy and debug toggle | Gradle build and screen navigation coverage |
| iCloud sync | Platform-specific Apple feature; Android implementation is local-first with import/export parity noted in Settings | Documented platform-specific exception |
| Apple Speech | Android Speech API is the platform-native equivalent; deterministic validation provider is used in emulator tests | Documented platform-specific equivalent |

## Validation Commands

Run from repository root:

```bash
gradle -p android testDebugUnitTest
gradle -p android assembleDebug
gradle -p android assembleDebugAndroidTest
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e debug false com.justspeaktoit.android.debug.test/androidx.test.runner.AndroidJUnitRunner
adb shell am start -n com.justspeaktoit.android.debug/com.justspeaktoit.android.MainActivity
adb shell screencap -p /sdcard/justspeaktoit-android.png
adb pull /sdcard/justspeaktoit-android.png android/validation/justspeaktoit-android.png
```

The emulator test suite is the hard gate for the Android app being interactive through adb-backed execution. `android/scripts/validate-emulator.sh` runs the same build, install, instrumentation, launch, and screenshot sequence end to end.
