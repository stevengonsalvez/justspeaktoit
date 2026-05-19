# /goal Create an Android app that is an exact clone of the iOS app in this repository and prove it works on an Android emulator through adb-backed validation.

— CONTEXT —
· Project: Build a native Android application in this repository that mirrors the existing iOS app feature-for-feature: live voice transcription, provider selection and fallback, transcript history, post-processing/polish, settings and secure API-key management, OpenClaw voice chat, conversation mode, TTS, shortcuts/notification controls, recordings, privacy/debugging views, and Android equivalents for iOS quick actions and Live Activities.
· Stack: Existing repo is SwiftPM/Tuist with Swift 5.9, macOS 14, iOS 17, `SpeakCore`, `SpeakSync`, `SpeakiOSLib`, and `SpeakApp`; add a new native Android project under `android/` using Kotlin, Gradle, Jetpack Compose, Material 3, Kotlin coroutines/Flow, AndroidX Navigation/lifecycle, Room or DataStore for persistence, Android Keystore-backed encrypted storage, OkHttp/WebSocket, Android speech APIs, and Android notification/foreground-service/shortcut APIs.
· Current state: There is no Android app project yet; iOS source lives in `Sources/SpeakiOS/`, `SpeakiOSApp/`, `JustSpeakToItWidgetExtension/`, `Sources/SpeakCore/`, and `Sources/SpeakSync/`; use `research/2026-05-16_14-28-31_android_ios_clone_goal.md` plus the referenced Swift files as the Android parity checklist and implementation source of truth.
· Working dir: `/Users/stevengonsalvez/.agents-in-a-box/worktrees/stevengonsalvez_justspeaktoit_feat_android`
· Constraints: Work autonomously and do not ask Stevie for clarifications unless truly blocked; research the iOS implementation and make conservative Android-native decisions; do not break existing macOS/iOS targets; do not commit secrets, signing assets, or personal credentials; use fake/injectable providers where needed so emulator validation is deterministic without personal API keys or physical microphone input; preserve repo rules, including specific-file staging only if committing and no destructive tmux/process commands.
· Audience: End users of Just Speak to It who should get an Android experience equivalent to the iOS app, plus maintainers who need a buildable, testable Android project in the same repository.

— SUCCESS CRITERIA (ALL MUST BE TRUE) —
1. The repository contains a native Android app that builds from a clean checkout, installs on an Android emulator, and launches successfully through adb.
2. Automated Android tests and adb-backed emulator validation prove the core iOS parity flows work: Transcribe tab, Settings/API key management, History, Post-Processing, OpenClaw conversation list/chat, and Android notification/shortcut equivalents for recording controls.
3. A checked-in Android parity checklist maps every major iOS feature found in `research/2026-05-16_14-28-31_android_ios_clone_goal.md` to an implemented Android feature, an Android-native equivalent, or a documented platform-specific exception with validation evidence.
4. Final deliverable runs without errors
5. You can show proof (screenshot · test output · URL)

— OPERATING RULES — NON-NEGOTIABLE —
1. PLAN FIRST. Output a numbered task list before writing any code.
2. WORK AUTONOMOUSLY. Don't ask clarifying Qs unless genuinely blocked.
3. SELF-VERIFY. After every step: run tests, inspect output, confirm it worked.
4. DEBUG YOURSELF. If it fails, diagnose + fix. Don't hand it back.
5. USE EVERY TOOL. MCPs · terminal · web · code exec · pull real data.
6. NO PLACEHOLDERS. No TODOs · no stubs · real components + real states.
7. PROGRESS LOG. Track completed · in-flight · decisions · blockers.
8. STAY ON GOAL. Discoveries off-spec? Note + keep moving.
9. IF BLOCKED. Log the wall · continue everything parallelizable.
10. CHECK SUCCESS BEFORE STOPPING. Re-read criteria · confirm each is met.

— QUALITY BAR —
· Code: clean, typed, follows project conventions
· Design: looks like a well-funded startup shipped it
· Output: survives a senior code review
· Docs: every new pattern / env var / decision logged

— FINAL DELIVERABLE —
✅ Confirmation each criterion is satisfied
📂 Every file created / modified
🚀 How to run / test / deploy
📊 Proof (screenshot · test output · URL)
📝 Decisions made + anything to know
⚠️ Known limitations + follow-ups

Begin by outputting your plan. Then execute end-to-end without checking
in until done or genuinely blocked.
