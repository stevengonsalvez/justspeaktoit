# Android Parity Checklist

This checklist maps the iOS feature inventory from `research/2026-05-16_14-28-31_android_ios_clone_goal.md` to the Android implementation under `android/`.

| iOS feature | Android implementation | Validation evidence |
| --- | --- | --- |
| Root tabs: Transcribe and OpenClaw | `MainActivity.kt` uses Material 3 bottom navigation with Transcribe/OpenClaw tabs | `android/scripts/validate-emulator.sh` installs, launches, focus-checks, and screenshots the app |
| Live transcription screen | `TranscribeScreen` with recording state, live transcript text, copy, polish, history, settings | Gradle build plus adb launch/screenshot validation |
| Provider selection and fallback | `SettingsScreen` exposes Android Speech, Deepgram, ElevenLabs, OpenAI; `SpeakViewModel.resolvedModel` falls back to Android Speech when keys are missing; `AndroidSpeechTranscriber` uses Android `SpeechRecognizer`; `RemoteStreamingTranscriber` streams Android `AudioRecord` PCM to Deepgram, ElevenLabs Scribe, and OpenAI Realtime when keys and microphone permission are present | `ParityLogicTest.parityChecklist_coversMajorGoalSurfaces`; Gradle unit tests; adb transcription flow |
| Secure API keys | `SpeakRepository` stores keys with Android Keystore-backed `EncryptedSharedPreferences` and falls back to private prefs if device crypto setup is unavailable | `android/validation/flow-bubble-settings-ui.xml` verifies Settings exposes "Manage Keys" |
| Transcript history | `HistoryScreen`, `HistoryEntry`, and Android DataStore-backed JSON persistence in `SpeakRepository` | Gradle build plus adb launch/screenshot validation |
| Post-processing/polish | `PostProcessingScreen`, OpenRouter `OpenRouterPostProcessor` via OkHttp when an OpenRouter key is saved, and deterministic `TranscriptFormatter.polish` fallback | `ParityLogicTest.transcriptFormatter_polishesSpacingCasingAndPunctuation`; Gradle unit tests |
| OpenClaw conversations | `OpenClawListScreen`, `OpenClawChatScreen`, `Conversation`, persisted messages, and configured-gateway send path through `OpenClawGatewayClient`/OkHttp WebSocket | `ParityLogicTest.chatPayload_matchesOpenClawStreamingShape`; Gradle build validation |
| OpenClaw gateway protocol helpers | `OpenClawProtocol.normaliseGatewayUrl`, keyword acknowledgement, cleanup helpers, streaming payload, completion detection, and assistant message extraction | `ParityLogicTest.normaliseGatewayUrl_*`, `ParityLogicTest.keywordAcknowledge_*`, `ParityLogicTest.extractAssistantContent_readsGatewayMessageShapes` |
| Conversation mode and acknowledgement controls | `OpenClawChatScreen` and `OpenClawSettingsScreen` support conversation mode, auto-resume, headset acknowledgement, keyword acknowledgement | `ParityLogicTest` keyword coverage |
| TTS controls | `OpenClawSettingsScreen` exposes speak responses, summarise responses, low-latency speech, voice/model/speed state; `AndroidSpeechSpeaker` speaks assistant responses through Android TextToSpeech | Gradle build and Compose settings coverage |
| iOS Live Activities | Android-native foreground service notification in `RecordingForegroundService` using a foreground `dataSync` service for emulator-safe validation | Manifest plus adb install validation |
| iOS AppIntents/quick action | Android launcher shortcut and `TOGGLE_RECORDING` intent in `shortcuts.xml` and `MainActivity` | Manifest plus adb launch/install validation |
| Recordings, Send to Mac, Privacy, Debugging, About | Android settings surfaces with platform-specific parity copy and debug toggle | Gradle build and screen navigation coverage |
| iCloud sync | Platform-specific Apple feature; Android implementation is local-first with import/export parity noted in Settings | Documented platform-specific exception |
| Apple Speech | Android Speech API is the platform-native equivalent; deterministic validation provider is used in emulator tests | Documented platform-specific equivalent |

## Wispr Flow Android Features Worth Copying

Research refreshed on 2026-05-17:

- Wispr's Android setup describes a floating bubble above the existing keyboard, with tap and long-press dictation controls rather than keyboard replacement: https://docs.wisprflow.ai/articles/8858845757-setup-wispr-flow-on-android-android-settings
- Wispr's Android accessibility guidance says accessibility permission is used to detect typing context, insert dictated text, and recover disconnected services: https://docs.wisprflow.ai/articles/7669452251-accessibility-permission-on-android
- Wispr markets Android app-wide dictation, 100+ languages, continued dictation while switching apps, and polished text: https://wisprflow.ai/android
- TechCrunch reported Android Flow uses a floating bubble, tap/hold controls, filler cleanup, context formatting, translation, and multilingual/Hinglish support: https://techcrunch.com/2026/02/23/wispr-flow-launches-an-android-app-for-ai-powered-dictation/
- Android foreground microphone and background-start restrictions require visible/user-initiated listening for ordinary apps: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android audio input priority reserves reliable hotword behavior for assistant/privileged paths, so this app does not claim a private always-on "Hey Siri" equivalent: https://developer.android.com/media/platform/sharing-audio-input
- Android overlay permission is user-granted through `SYSTEM_ALERT_WINDOW`: https://developer.android.com/reference/android/Manifest.permission.html#SYSTEM_ALERT_WINDOW

| Wispr-style feature | Android implementation | Validation evidence |
| --- | --- | --- |
| Floating dictation bubble | `VoiceBubbleOverlayService` owns a `WindowManager` overlay with tap-to-toggle, long-press push-to-talk affordance, drag/dock movement, opacity/size settings, and snooze support | Manifest declares `SYSTEM_ALERT_WINDOW`; `android/scripts/validate-emulator.sh` captures `android/validation/flow-bubble-settings.png` and verifies `android/validation/flow-bubble-settings-ui.xml` |
| Accessibility insertion | `VoiceInsertionAccessibilityService` tracks focused editable fields; `VoiceInsertionCoordinator` inserts via accessibility and reports failure states | Accessibility service XML at `android/app/src/main/res/xml/voice_insertion_accessibility.xml`; unit-tested insertion planner preserves text and cursor/selection behavior |
| Clipboard recovery | `VoiceInsertionCoordinator` copies dictated text to clipboard and shows recovery copy when accessibility insertion is unavailable or disabled | `VoiceInsertionResult` models `ClipboardFallback`; Flow Bubble settings expose the Clipboard Recovery toggle |
| Phrase-start | `VoiceStartPhraseMatcher` trims configured start phrases only when Phrase Start and Explicit Listening Mode are enabled | `ParityLogicTest.phraseStart_trimsConfiguredTriggerOnlyWhenExplicitlyEnabled`; Compose settings test edits `flowBubblePhraseInput` |
| Spoken commands/snippets | `VoiceCommandProcessor` handles filler cleanup, "scratch that", "new paragraph", "new line", and snippet phrases | `ParityLogicTest.voiceCommandProcessor_handlesFillerCommandsAndSnippets` |
| Permission recovery UI | Settings contains overlay, accessibility, notification, and battery status rows with direct Android Settings launch buttons and refresh | `android/validation/flow-bubble-settings-ui.xml` contains Flow Bubble, Start phrase, Overlay, Accessibility, and insertion preview text |

Known limitations and follow-ups:

- True global always-on wake word is intentionally out of scope for a normal third-party app unless the app is configured through an assistant/VoiceInteractionService path or runs an explicit foreground microphone service with user-visible notification.
- Emulator automation cannot silently enable accessibility services or overlay permission on all images; validation proves the app surfaces, pure insertion logic, and recovery path, while manual permission enablement proves real cross-app insertion.
- Per-app formatting profiles, offline foreground wake-word models, and work-profile/OEM autostart guides remain follow-up work.

## Validation Commands

Run from repository root:

```bash
gradle -p android testDebugUnitTest
gradle -p android assembleDebug
gradle -p android assembleDebugAndroidTest
android/scripts/validate-emulator.sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e debug false com.justspeaktoit.android.debug.test/androidx.test.runner.AndroidJUnitRunner
adb shell am start -n com.justspeaktoit.android.debug/com.justspeaktoit.android.MainActivity
adb shell screencap -p /sdcard/justspeaktoit-android.png
adb pull /sdcard/justspeaktoit-android.png android/validation/justspeaktoit-android.png
```

The emulator test suite is the hard gate for the Android app being interactive through adb-backed execution. `android/scripts/validate-emulator.sh` runs the same build, install, instrumentation, launch, and screenshot sequence end to end.
