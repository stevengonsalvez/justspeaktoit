# Plan Spec: Android Exact Clone Of iOS App

## Project

Build a native Android application in this repository that mirrors the existing iOS app feature-for-feature. The Android app must provide the same user-facing product: live voice transcription, provider selection and fallback, transcript history, post-processing/polish, settings and secure API-key management, OpenClaw voice chat, conversation mode, TTS, shortcuts/notification controls, recordings, privacy/debugging views, and Android equivalents for iOS quick actions and Live Activities.

## Stack

Use the existing repository as the source of truth. Add a new native Android project under `android/` using Kotlin, Gradle, Jetpack Compose, Material 3, Kotlin coroutines/Flow, AndroidX Navigation, AndroidX lifecycle, Room or DataStore for persistence, Android Keystore-backed encrypted storage for secrets, OkHttp/WebSocket for streaming APIs and OpenClaw, Android speech APIs for the local/default transcription provider, and Android notification/foreground-service/shortcut APIs for iOS Live Activity and AppIntent equivalents. Keep the existing SwiftPM/Tuist macOS and iOS targets intact.

## Current State

The repo currently has SwiftPM/Tuist targets for macOS and iOS, with no Android app project. iOS source lives mainly in `Sources/SpeakiOS/`, `SpeakiOSApp/`, `JustSpeakToItWidgetExtension/`, `Sources/SpeakCore/`, and `Sources/SpeakSync/`. Research captured the iOS feature surface in `research/2026-05-16_14-28-31_android_ios_clone_goal.md`; treat that document plus the referenced Swift files as the parity checklist.

## Working Dir

`/Users/stevengonsalvez/.agents-in-a-box/worktrees/stevengonsalvez_justspeaktoit_feat_android`

## Constraints

Do not ask Stevie for clarifications unless truly blocked. Research the iOS implementation and make conservative Android-native decisions. Do not break macOS or iOS builds. Do not commit secrets, signing assets, or personal credentials. Use fake/injectable providers where needed so emulator validation does not depend on personal API keys or physical microphone input. Preserve the existing repo rules: no `git add -A`, use specific file staging if committing, and never use destructive tmux/process commands.

## Audience

End users of Just Speak to It who should get an Android experience equivalent to the iOS app, plus maintainers who need a buildable, testable Android project in the same repository.

## Success Criteria

1. The repository contains a native Android app that builds from a clean checkout, installs on an Android emulator, and launches successfully through adb.
2. Automated Android tests and adb-backed emulator validation prove the core iOS parity flows work: Transcribe tab, Settings/API key management, History, Post-Processing, OpenClaw conversation list/chat, and Android notification/shortcut equivalents for recording controls.
3. A checked-in Android parity checklist maps every major iOS feature found in the research document to an implemented Android feature, an Android-native equivalent, or a documented platform-specific exception with validation evidence.
