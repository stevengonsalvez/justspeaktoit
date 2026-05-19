# Android Validation Matrix

This matrix defines what must be proven for the Android parity app to count as
working on an emulator or device. The automated gate is
`android/scripts/validate-emulator.sh`; device-only gaps are called out
explicitly instead of being hidden behind brittle emulator automation.

| Area | Feature | Verification | Automated evidence |
| --- | --- | --- | --- |
| App shell | Transcribe/OpenClaw tabs | adb launch, focus check, UIAutomator dump | `android/validation/window-focus.txt`, `justspeaktoit-android.png` |
| Settings | Setup and provider settings entry points | UIAutomator navigation and text assertions | `flow-bubble-settings-ui.xml`, `flow-bubble-settings.png` |
| Flow Bubble | Permission recovery UI | UIAutomator verifies Overlay, Accessibility, Notifications, Battery, Start phrase | `flow-bubble-settings-ui.xml` |
| Flow Bubble | Insertion planning preview | UIAutomator verifies cursor insertion preview | `flow-bubble-settings-ui.xml` |
| Flow Bubble | Phrase start and spoken commands | JVM unit tests | `gradle -p android testDebugUnitTest` |
| Flow Bubble | Accessibility insertion planning | JVM unit tests for cursor/selection behavior | `ParityLogicTest.textInsertionPlanner_preservesExistingTextAtCursorAndSelection` |
| Transcribe | Mic start/stop interaction | adb/UIAutomator taps Mic/Stop and asserts saved transcript state | `transcribe-recording-ui.xml`, `transcribe-stopped-ui.xml`, `history-ui.xml`, `transcribe-recording.png`, `history.png` |
| Transcribe | Live transcript/history persistence | Deterministic emulator validation path, then History assertion | `history-ui.xml` |
| Transcribe | Copy fallback after recording | UI state assertion after stop | `transcribe-stopped-ui.xml` |
| Providers | Android Speech, Deepgram, ElevenLabs, OpenAI selection/fallback | JVM unit coverage and Settings UI assertions | `ParityLogicTest.parityChecklist_coversMajorGoalSurfaces`, `flow-bubble-settings-ui.xml` |
| Secure keys | API key management surface | UIAutomator verifies Manage Keys path is rendered | `flow-bubble-settings-ui.xml` |
| Post-processing | Deterministic polish fallback | JVM unit test | `ParityLogicTest.transcriptFormatter_polishesSpacingCasingAndPunctuation` |
| OpenClaw | Gateway payload/completion parsing | JVM unit tests | `ParityLogicTest.chatPayload_matchesOpenClawStreamingShape`, `extractAssistantContent_readsGatewayMessageShapes` |
| OpenClaw | Conversation list and chat navigation | adb/UIAutomator taps OpenClaw and New Conversation | `openclaw-list-ui.xml`, `openclaw-chat-ui.xml` |
| OpenClaw voice | Voice button creates deterministic message and response | adb/UIAutomator taps Mic/Stop and asserts assistant response | `openclaw-voice-listening-ui.xml`, `openclaw-voice-response-ui.xml`, `openclaw-voice-response.png` |
| OpenClaw hands-free | Keyword acknowledgement | JVM unit tests | `ParityLogicTest.keywordAcknowledge_*` |
| Android equivalents | Instrumented debug package target | AndroidJUnitRunner smoke test | `adb-instrumentation.txt` |

## Device-Only / Manual Smoke Tests

- Real microphone recognition accuracy depends on the emulator image, Google
  speech services, host microphone routing, and runtime permissions. The
  automated gate verifies the recording controls and saved-output path; a real
  device smoke test should additionally grant microphone permission, tap Mic,
  speak a short phrase, stop, and confirm the spoken words appear in History.
- Overlay rendering and drag/long-press gestures can be automated only partially.
  The gate verifies the Flow Bubble settings, permission recovery UI, service
  wiring through build/install, and insertion logic. A real device smoke test
  should grant overlay and accessibility permissions, enable the bubble, drag it,
  long-press for push-to-talk, and insert into another app.
- Real Deepgram, ElevenLabs, OpenAI, OpenRouter, and OpenClaw gateway calls
  require keys/endpoints and should remain opt-in. Do not commit keys or print
  secret values during validation.

## Current Automated Gate

Run from the repository root with an emulator online:

```bash
android/scripts/validate-emulator.sh
```

The script builds unit/test APKs, installs with non-streaming adb install,
runs AndroidJUnitRunner, launches the app, captures screenshots, drives Settings,
Flow Bubble, Transcribe, History, and OpenClaw voice through adb/UIAutomator,
and saves XML/screenshot evidence under `android/validation/`.
