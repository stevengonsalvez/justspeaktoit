package com.justspeaktoit.android

import java.util.UUID

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val model: String,
    val durationSeconds: Int,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val synced: Boolean = false
) {
    val wordCount: Int get() = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val sessionKey: String = "speak-android:voice:${UUID.randomUUID().toString().take(8).lowercase()}",
    val title: String = "New Conversation",
    val messages: List<ChatMessage> = emptyList(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class SpeakSettings(
    val selectedModel: String = "android/local/SpeechRecognizer",
    val autoStartRecording: Boolean = false,
    val liveNotificationsEnabled: Boolean = true,
    val autoPostProcess: Boolean = false,
    val postProcessingModel: String = "openai/gpt-4o-mini",
    val postProcessingPrompt: String = "",
    val deepgramKeyStored: Boolean = false,
    val elevenLabsKeyStored: Boolean = false,
    val openRouterKeyStored: Boolean = false,
    val openAIKeyStored: Boolean = false,
    val debugLoggingEnabled: Boolean = false,
    val flowBubbleEnabled: Boolean = false,
    val flowBubblePhraseStartEnabled: Boolean = false,
    val flowBubblePhraseStartPhrase: String = "start speaking",
    val flowBubbleExplicitListeningMode: Boolean = false,
    val flowBubbleClipboardFallbackEnabled: Boolean = true,
    val flowBubbleSizePercent: Float = 1.0f,
    val flowBubbleOpacity: Float = 0.94f,
    val flowBubbleSnoozed: Boolean = false
)

data class FlowBubblePermissionStatus(
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationsGranted: Boolean = true,
    val batteryUnrestricted: Boolean = false
) {
    val ready: Boolean get() = overlayGranted && accessibilityEnabled && notificationsGranted
    val summary: String get() = if (ready) "Ready in other apps" else "Needs setup"
}

data class OpenClawSettingsState(
    val gatewayUrl: String = "",
    val tokenStored: Boolean = false,
    val enabled: Boolean = false,
    val ttsEnabled: Boolean = true,
    val summariseResponses: Boolean = true,
    val ttsVoice: String = "asteria",
    val ttsModel: String = "aura-2",
    val ttsSpeed: Float = 1.0f,
    val conversationModeEnabled: Boolean = false,
    val autoResumeListening: Boolean = true,
    val headsetSingleTapAcknowledge: Boolean = false,
    val keywordAcknowledgeEnabled: Boolean = false,
    val keywordAcknowledgePhrase: String = "over",
    val lowLatencySpeech: Boolean = false
) {
    val isConfigured: Boolean get() = gatewayUrl.isNotBlank() && tokenStored && enabled
}

enum class MainTab { Transcribe, OpenClaw }
enum class AppScreen { Transcribe, History, Settings, ApiKeys, PostProcessing, OpenClawList, OpenClawChat, OpenClawSettings, Privacy, Recordings, SendToMac, About }

data class SpeakUiState(
    val activeTab: MainTab = MainTab.Transcribe,
    val screen: AppScreen = AppScreen.Transcribe,
    val settings: SpeakSettings = SpeakSettings(),
    val openClawSettings: OpenClawSettingsState = OpenClawSettingsState(),
    val isRecording: Boolean = false,
    val transcriptText: String = "",
    val processedText: String = "",
    val copied: Boolean = false,
    val recordingStartedAtMillis: Long? = null,
    val history: List<HistoryEntry> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val selectedConversationId: String? = null,
    val openClawConnectionState: String = "Disconnected",
    val isOpenClawProcessing: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialVoiceInput: String = "",
    val flowBubblePermissions: FlowBubblePermissionStatus = FlowBubblePermissionStatus(),
    val statusMessage: String? = null
)
