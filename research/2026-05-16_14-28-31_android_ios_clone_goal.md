# Research: Android Clone Of The iOS App

**Date**: 2026-05-16 14:28:31 BST
**Repository**: justspeaktoit
**Branch**: feat/android
**Commit**: 2a41236
**Research Type**: Codebase

## Research Question

Create a goal prompt for building an Android app that is an exact clone of the iOS app in this repository, with success based on validation that the Android app works on an Android emulator through adb or equivalent tooling.

## Executive Summary

The current repository has macOS and iOS Swift targets but no Android project yet. The iOS app surface is broad: it has a two-tab SwiftUI app with transcription and OpenClaw voice chat, multiple live transcription providers, secure API-key storage, post-processing through OpenRouter, history and sync views, Live Activities, quick actions, Shortcuts/AppIntents, and local conversation persistence. The Android goal should therefore create a native Android app under a new Android project, reproduce iOS behavior with Android-native equivalents, and prove it by building, installing, launching, and exercising flows on an emulator with adb and automated Android tests.

## Key Findings

- No Android project exists in the checkout; only `landing-page/appcast.xml` matched Android/Gradle/Kotlin file searches.
- The iOS app is a SwiftPM/Tuist project with `SpeakCore`, `SpeakSync`, `SpeakiOSLib`, and a Tuist iOS app target.
- iOS top-level navigation has two tabs: `Transcribe` and `OpenClaw`.
- The transcription tab records live audio, shows partial text, supports copy and post-processing actions, and auto-starts when configured.
- iOS live transcription provider selection currently includes Apple Speech, Deepgram, ElevenLabs Scribe, and OpenAI Realtime Whisper, with fallback to Apple Speech when keys are missing.
- OpenClaw includes persistent conversations, WebSocket gateway connection, streaming responses, TTS playback, conversation mode, auto-resume, tap/headset/keyword acknowledgement, and Live Activity state.
- Settings include API keys, transcription provider, behavior toggles, post-processing model/prompt, iCloud/QR sync, OpenClaw configuration, recordings, Send to Mac, privacy/debugging, and about/build metadata.

## Prior Learnings

QMD learnings had relevant historical references for JustSpeakToIt feature inventory and UX patterns, plus an Android emulator testing pattern. The useful point for this goal is to make Android emulator validation explicit and scriptable, because emulator-first validation is less fragile than iOS-style device automation.

## Detailed Findings

### Project And Current State

- `README.md:40` documents the source layout: `SpeakCore`, `SpeakApp`, `SpeakiOS`, and `SpeakiOSApp`.
- `README.md:73` documents current iOS build flow: `swift build --target SpeakiOSLib`, `tuist generate`, then Xcode workspace build.
- `Package.swift:1` uses Swift 5.9 and supports macOS 14 plus iOS 17.
- `Package.swift:31` declares `SpeakiOSLib` as a Swift package target depending on `SpeakCore` and `SpeakSync`.
- `Project.swift:72` defines the iOS `SpeakiOS` app target with bundle id `com.justspeaktoit.ios`, iOS 17 deployment, app groups, URL scheme, quick action, background audio, and Live Activities support.

### iOS Navigation And Main Screens

- `SpeakiOSApp/SpeakiOSApp.swift:50` is the iOS app entry point.
- `SpeakiOSApp/SpeakiOSApp.swift:67` defines the root tab view with `Transcribe` and `OpenClaw`.
- `SpeakiOSApp/SpeakiOSApp.swift:91` wraps the OpenClaw tab in its own navigation stack and supports deep-link navigation into conversations.
- `Sources/SpeakiOS/Views/ContentView.swift:252` defines the transcription screen.
- `Sources/SpeakiOS/Views/OpenClawChatView.swift:8` defines the main OpenClaw chat view.

### Transcription Feature Surface

- `Sources/SpeakiOS/Views/ContentView.swift:9` defines `TranscriberCoordinator`, which switches among Apple Speech, Deepgram, ElevenLabs, and OpenAI transcribers.
- `Sources/SpeakiOS/Views/ContentView.swift:50` starts recording using selected settings and provider fallback.
- `Sources/SpeakiOS/Views/ContentView.swift:183` stops recording and records history.
- `Sources/SpeakiOS/Views/ContentView.swift:313` exposes navigation to history and settings.
- `Sources/SpeakiOS/Views/ContentView.swift:381` renders floating controls, with iOS 26 Liquid Glass when available and fallback controls otherwise.
- `Sources/SpeakiOS/Views/ContentView.swift:516` toggles recording and triggers post-processing when configured.
- `Sources/SpeakiOS/Services/TranscriptionRecordingService.swift:9` provides the headless recording service used by quick actions, intents, and background flows.
- `Sources/SpeakiOS/Services/TranscriptionRecordingService.swift:43` starts headless recording with Live Activity state.
- `Sources/SpeakiOS/Services/TranscriptionRecordingService.swift:171` stops recording, records history, copies text to the clipboard, updates shared state, and optionally post-processes.

### Providers And Secrets

- `README.md:119` documents iOS providers: Apple Speech, Deepgram, and ElevenLabs, although current code also includes OpenAI realtime.
- `Sources/SpeakiOS/Views/SettingsView.swift:21` defines iOS `AppSettings` using UserDefaults plus Keychain.
- `Sources/SpeakiOS/Views/SettingsView.swift:251` exposes the transcription model picker with Apple Speech, Deepgram, ElevenLabs, and OpenAI.
- `Sources/SpeakiOS/Views/SettingsView.swift:342` shows API key status for Deepgram, ElevenLabs, OpenRouter, and OpenAI.
- `Sources/SpeakiOS/Views/SettingsView.swift:571` implements secure API key entry and validation/save behavior.
- `Sources/SpeakCore/ModelCatalog.swift:111` is the canonical broader model catalog for live transcription options.

### Post-Processing

- `Sources/SpeakiOS/Views/SettingsView.swift:70` persists post-processing toggles, model, prompt, and auto-post-process state.
- `Sources/SpeakiOS/Views/SettingsView.swift:86` defines the default safe transcript cleanup prompt.
- `Sources/SpeakiOS/Views/SettingsView.swift:510` provides post-processing settings and prompt editing.
- `Sources/SpeakiOS/Views/PostProcessingView.swift:9` defines `iOSPostProcessingManager`.
- `Sources/SpeakiOS/Views/PostProcessingView.swift:21` processes text through OpenRouter with a selected model and prompt.
- `Sources/SpeakiOS/Views/PostProcessingView.swift:77` uses streaming OpenRouter chat completions.
- `Sources/SpeakiOS/Views/PostProcessingView.swift:174` provides the full-screen post-processing UI.

### History, Sync, And Persistence

- `Sources/SpeakiOS/Views/HistoryView.swift:7` renders transcription history with sync status, stats, delete, copy, and refresh actions.
- `Sources/SpeakiOS/Views/HistoryView.swift:107` includes history list, sync banner, stats, swipe delete, and swipe copy behavior.
- `Sources/SpeakSync/SyncModels.swift` and `Sources/SpeakSync/HistorySyncEngine.swift` provide iCloud/CloudKit history sync on Apple platforms; Android needs an equivalent or a documented Android-native parity strategy.
- `Sources/SpeakiOS/Services/OpenClawChatCoordinator.swift:13` persists OpenClaw conversations to documents as JSON.

### OpenClaw Feature Surface

- `Sources/SpeakiOS/Services/OpenClawSettings.swift:11` stores OpenClaw gateway URL, token, enablement, TTS settings, conversation mode, acknowledge behavior, and latency mode.
- `Sources/SpeakiOS/Services/OpenClawChatCoordinator.swift:97` coordinates voice-to-text, gateway send, streamed responses, summarisation, and TTS.
- `Sources/SpeakiOS/Services/OpenClawChatCoordinator.swift:154` connects to the gateway using settings.
- `Sources/SpeakiOS/Services/OpenClawChatCoordinator.swift:250` starts voice input using the app transcription provider.
- `Sources/SpeakiOS/Services/OpenClawChatCoordinator.swift:280` stops voice input and sends the transcript to OpenClaw.
- `Sources/SpeakiOS/Services/OpenClawChatCoordinator+HandsFree.swift:118` auto-resumes conversation listening when configured.
- `Sources/SpeakiOS/Services/OpenClawChatCoordinator+HandsFree.swift:158` detects keyword acknowledgement.
- `Sources/SpeakiOS/Services/OpenClawChatCoordinator+HandsFree.swift:251` speaks assistant responses using Deepgram TTS, optionally after summarisation.
- `Sources/SpeakCore/OpenClawClient.swift:6` defines the lightweight OpenClaw gateway WebSocket protocol client.
- `Sources/SpeakCore/OpenClawClient.swift:120` sends streaming chat messages.

### Android Implementation Implications

- Build a native Android app in a new `android/` project using Kotlin, Gradle, Jetpack Compose, Material 3, Kotlin coroutines/Flow, AndroidX Navigation, Room or DataStore for local persistence, Android Keystore-backed encrypted storage for secrets, OkHttp WebSocket for OpenClaw and cloud STT, and Android `SpeechRecognizer`/`RecognizerIntent` or platform speech APIs for on-device speech.
- Use Android-native equivalents for iOS-only affordances: Live Activity becomes a foreground service plus ongoing notification with actions; home screen quick action becomes launcher shortcuts; AppIntents/Shortcuts become Android shortcuts/intents; iCloud sync needs an explicit Android parity decision, likely local-first history plus export/import unless a cross-platform backend is added.
- Keep feature labels, defaults, provider fallback semantics, conversation behavior, OpenClaw protocol behavior, post-processing prompt semantics, and visual hierarchy as close as possible to the iOS app.

### Validation Strategy

- Hard gates should include Gradle unit tests, Android instrumentation/Compose UI tests, app build, emulator boot, APK install via `adb install`, app launch via `adb shell monkey` or `am start`, and logcat/screenshot proof that the app is interactive.
- Because emulator microphone and cloud keys are variable, the Android implementation should include fake or injectable provider implementations for validation-only UI and service tests while preserving real provider implementations for runtime.
- The emulator validation should cover at least: app launches to Transcribe tab, settings screen opens, fake/on-device transcription updates the UI and can copy text, post-processing flow can run against a fake or configured provider, OpenClaw conversation list/chat screens load, and OpenClaw WebSocket protocol behavior is covered by tests or a local fake gateway.

## Code References

- `README.md:119` - iOS provider feature documentation.
- `SpeakiOSApp/SpeakiOSApp.swift:67` - root iOS tabs.
- `Sources/SpeakiOS/Views/ContentView.swift:9` - transcription coordinator.
- `Sources/SpeakiOS/Views/ContentView.swift:252` - transcription screen.
- `Sources/SpeakiOS/Views/SettingsView.swift:21` - iOS settings store.
- `Sources/SpeakiOS/Views/SettingsView.swift:244` - settings screen.
- `Sources/SpeakiOS/Views/PostProcessingView.swift:9` - iOS post-processing manager.
- `Sources/SpeakiOS/Views/HistoryView.swift:7` - history screen.
- `Sources/SpeakiOS/Services/TranscriptionRecordingService.swift:9` - headless recording service.
- `Sources/SpeakiOS/Services/OpenClawSettings.swift:11` - OpenClaw settings store.
- `Sources/SpeakiOS/Services/OpenClawChatCoordinator.swift:97` - OpenClaw coordinator.
- `Sources/SpeakCore/OpenClawClient.swift:6` - OpenClaw gateway client.
- `Tests/SpeakiOSTests/OpenClawConnectionTesterTests.swift:5` - existing iOS test shape for OpenClaw URL/connection behavior.
- `Tests/SpeakCoreTests/OpenClawTypesTests.swift:5` - core OpenClaw type tests.

## Recommendations

1. Create the Android implementation as a native app under `android/`, not as a side utility, because the requested deliverable is a real Android app.
2. Start from a shared feature inventory and parity checklist, then implement app shell, settings/secrets, transcription, history, post-processing, OpenClaw, notifications/shortcuts, and emulator validation.
3. Add fake/injected implementations for STT, post-processing, TTS, and gateway tests so emulator validation is deterministic without relying on personal API keys or microphone input.
4. Treat emulator proof as part of done: build, install, launch, run tests, capture screenshots/logs, and document commands.

## Open Questions

- Android cloud sync parity with iCloud is not directly possible without a cross-platform backend. The autonomous implementation should choose the closest Android-native parity path and document the tradeoff.
- Apple Speech has no exact Android equivalent; Android should use platform speech APIs for on-device/default transcription and preserve the same fallback behavior.
