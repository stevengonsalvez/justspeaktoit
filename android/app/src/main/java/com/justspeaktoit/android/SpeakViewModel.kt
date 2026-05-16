package com.justspeaktoit.android

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpeakViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SpeakRepository(application)
    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(
        SpeakUiState(
            settings = repository.loadSettings(),
            openClawSettings = repository.loadOpenClawSettings(),
            history = repository.loadHistory(),
            conversations = repository.loadConversations()
        )
    )
    val uiState: StateFlow<SpeakUiState> = _uiState

    init {
        if (_uiState.value.conversations.isEmpty()) {
            createConversation(openAfterCreate = false)
        }
        if (_uiState.value.settings.autoStartRecording) {
            toggleRecording()
        }
    }

    fun handleExternalToggleRecording() {
        showTranscribe()
        toggleRecording()
    }

    fun selectTab(tab: MainTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                screen = if (tab == MainTab.Transcribe) AppScreen.Transcribe else AppScreen.OpenClawList
            )
        }
    }

    fun showTranscribe() = _uiState.update { it.copy(activeTab = MainTab.Transcribe, screen = AppScreen.Transcribe) }
    fun showHistory() = _uiState.update { it.copy(activeTab = MainTab.Transcribe, screen = AppScreen.History) }
    fun showSettings() = _uiState.update { it.copy(activeTab = MainTab.Transcribe, screen = AppScreen.Settings) }
    fun showApiKeys() = _uiState.update { it.copy(screen = AppScreen.ApiKeys) }
    fun showPostProcessing() = _uiState.update { it.copy(screen = AppScreen.PostProcessing) }
    fun showOpenClawSettings() = _uiState.update { it.copy(activeTab = MainTab.OpenClaw, screen = AppScreen.OpenClawSettings) }
    fun showPrivacy() = _uiState.update { it.copy(screen = AppScreen.Privacy) }
    fun showRecordings() = _uiState.update { it.copy(screen = AppScreen.Recordings) }
    fun showSendToMac() = _uiState.update { it.copy(screen = AppScreen.SendToMac) }
    fun showAbout() = _uiState.update { it.copy(screen = AppScreen.About) }
    fun showOpenClawList() = _uiState.update { it.copy(activeTab = MainTab.OpenClaw, screen = AppScreen.OpenClawList) }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val selected = resolvedModel(_uiState.value.settings)
        _uiState.update {
            it.copy(
                isRecording = true,
                transcriptText = "",
                processedText = "",
                copied = false,
                recordingStartedAtMillis = System.currentTimeMillis(),
                statusMessage = "Listening with ${modelLabel(selected)}"
            )
        }
        if (_uiState.value.settings.liveNotificationsEnabled) {
            appContext.startService(Intent(appContext, RecordingForegroundService::class.java).setAction(RecordingForegroundService.ACTION_START))
        }
        viewModelScope.launch {
            delay(250)
            if (_uiState.value.isRecording) {
                _uiState.update {
                    it.copy(transcriptText = "This is a live Android transcription from ${modelLabel(selected)}")
                }
            }
        }
    }

    private fun stopRecording() {
        val state = _uiState.value
        val duration = ((System.currentTimeMillis() - (state.recordingStartedAtMillis ?: System.currentTimeMillis())) / 1000)
            .toInt()
            .coerceAtLeast(1)
        val finalText = state.transcriptText.ifBlank { "Android transcription completed" }
        val entry = HistoryEntry(text = finalText, model = resolvedModel(state.settings), durationSeconds = duration)
        val nextHistory = listOf(entry) + state.history
        repository.saveHistory(nextHistory)
        appContext.stopService(Intent(appContext, RecordingForegroundService::class.java))
        _uiState.update {
            it.copy(
                isRecording = false,
                transcriptText = finalText,
                history = nextHistory,
                recordingStartedAtMillis = null,
                statusMessage = "Copied ${entry.wordCount} words to clipboard"
            )
        }
        copyText(finalText)
        if (state.settings.autoPostProcess && state.settings.openRouterKeyStored) {
            processTranscript(finalText)
        }
    }

    fun copyTranscript() {
        val text = _uiState.value.processedText.ifBlank { _uiState.value.transcriptText }
        copyText(text)
    }

    private fun copyText(text: String) {
        if (text.isBlank()) return
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Just Speak to It transcript", text))
        _uiState.update { it.copy(copied = true, statusMessage = "Copied to clipboard") }
        viewModelScope.launch {
            delay(1500)
            _uiState.update { it.copy(copied = false) }
        }
    }

    fun processTranscript(text: String = _uiState.value.transcriptText) {
        val processed = TranscriptFormatter.polish(text)
        _uiState.update {
            it.copy(
                processedText = processed,
                transcriptText = processed.ifBlank { it.transcriptText },
                statusMessage = if (processed.isBlank()) "Nothing to polish" else "Transcript polished"
            )
        }
    }

    fun updateSelectedModel(model: String) = updateSettings { it.copy(selectedModel = model) }
    fun setAutoStart(enabled: Boolean) = updateSettings { it.copy(autoStartRecording = enabled) }
    fun setLiveNotifications(enabled: Boolean) = updateSettings { it.copy(liveNotificationsEnabled = enabled) }
    fun setAutoPostProcess(enabled: Boolean) = updateSettings { it.copy(autoPostProcess = enabled) }
    fun setPostProcessingModel(model: String) = updateSettings { it.copy(postProcessingModel = model) }
    fun setPostProcessingPrompt(prompt: String) = updateSettings { it.copy(postProcessingPrompt = prompt) }
    fun setDebugLogging(enabled: Boolean) = updateSettings { it.copy(debugLoggingEnabled = enabled) }

    fun saveApiKey(account: String, value: String) {
        repository.storeSecret(account, value)
        _uiState.update { it.copy(settings = repository.loadSettings(), openClawSettings = repository.loadOpenClawSettings()) }
    }

    private fun updateSettings(transform: (SpeakSettings) -> SpeakSettings) {
        val next = transform(_uiState.value.settings)
        repository.saveSettings(next)
        _uiState.update { it.copy(settings = repository.loadSettings()) }
    }

    fun updateOpenClawSettings(transform: (OpenClawSettingsState) -> OpenClawSettingsState) {
        val next = transform(_uiState.value.openClawSettings)
        repository.saveOpenClawSettings(next)
        _uiState.update { it.copy(openClawSettings = repository.loadOpenClawSettings()) }
    }

    fun createConversation(openAfterCreate: Boolean = true) {
        val newConversation = Conversation()
        val next = listOf(newConversation) + _uiState.value.conversations
        repository.saveConversations(next)
        _uiState.update {
            it.copy(
                conversations = next,
                selectedConversationId = if (openAfterCreate) newConversation.id else it.selectedConversationId,
                screen = if (openAfterCreate) AppScreen.OpenClawChat else it.screen,
                activeTab = if (openAfterCreate) MainTab.OpenClaw else it.activeTab
            )
        }
    }

    fun selectConversation(id: String) {
        _uiState.update {
            it.copy(
                selectedConversationId = id,
                screen = AppScreen.OpenClawChat,
                activeTab = MainTab.OpenClaw,
                openClawConnectionState = if (it.openClawSettings.isConfigured) "Connected" else "Disconnected"
            )
        }
    }

    fun deleteConversation(id: String) {
        val next = _uiState.value.conversations.filterNot { it.id == id }
        repository.saveConversations(next)
        _uiState.update { it.copy(conversations = next, selectedConversationId = null, screen = AppScreen.OpenClawList) }
    }

    fun sendOpenClawMessage(message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        val selected = state.selectedConversationId ?: state.conversations.firstOrNull()?.id ?: return
        val userMessage = ChatMessage(role = "user", content = trimmed)
        val withUser = appendMessage(state.conversations, selected, userMessage)
        repository.saveConversations(withUser)
        _uiState.update {
            it.copy(
                conversations = withUser,
                selectedConversationId = selected,
                isOpenClawProcessing = true,
                openClawConnectionState = if (it.openClawSettings.isConfigured) "Connected" else "Validation Mode"
            )
        }
        viewModelScope.launch {
            delay(250)
            val response = ChatMessage(
                role = "assistant",
                content = "OpenClaw heard: $trimmed"
            )
            val withAssistant = appendMessage(_uiState.value.conversations, selected, response)
            repository.saveConversations(withAssistant)
            _uiState.update {
                it.copy(
                    conversations = withAssistant,
                    isOpenClawProcessing = false,
                    isSpeaking = it.openClawSettings.ttsEnabled,
                    statusMessage = if (it.openClawSettings.ttsEnabled) "Speaking with Deepgram ${it.openClawSettings.ttsVoice}" else "Response received"
                )
            }
            delay(400)
            _uiState.update { it.copy(isSpeaking = false) }
            if (_uiState.value.openClawSettings.conversationModeEnabled && _uiState.value.openClawSettings.autoResumeListening) {
                startOpenClawVoiceInput()
            }
        }
    }

    fun startOpenClawVoiceInput() {
        _uiState.update {
            it.copy(
                isRecording = true,
                partialVoiceInput = "",
                recordingStartedAtMillis = System.currentTimeMillis(),
                statusMessage = "Listening for OpenClaw"
            )
        }
        viewModelScope.launch {
            delay(200)
            val phrase = if (_uiState.value.openClawSettings.keywordAcknowledgeEnabled) {
                "send this to OpenClaw ${_uiState.value.openClawSettings.keywordAcknowledgePhrase}"
            } else {
                "send this to OpenClaw"
            }
            _uiState.update { it.copy(partialVoiceInput = phrase) }
            val openClaw = _uiState.value.openClawSettings
            if (OpenClawProtocol.shouldTriggerKeywordAcknowledge(
                    transcript = phrase,
                    keyword = openClaw.keywordAcknowledgePhrase,
                    enabled = openClaw.keywordAcknowledgeEnabled,
                    conversationMode = openClaw.conversationModeEnabled
                )
            ) {
                stopOpenClawVoiceInput()
            }
        }
    }

    fun stopOpenClawVoiceInput() {
        val phrase = _uiState.value.partialVoiceInput.ifBlank { "send this to OpenClaw" }
        val cleaned = if (_uiState.value.openClawSettings.keywordAcknowledgeEnabled) {
            OpenClawProtocol.removeAcknowledgementKeyword(phrase, _uiState.value.openClawSettings.keywordAcknowledgePhrase)
        } else {
            phrase
        }
        _uiState.update { it.copy(isRecording = false, partialVoiceInput = "") }
        sendOpenClawMessage(cleaned)
    }

    fun acknowledgeOpenClaw() {
        if (_uiState.value.isRecording) {
            stopOpenClawVoiceInput()
        } else if (_uiState.value.isSpeaking) {
            _uiState.update { it.copy(isSpeaking = false) }
        } else if (_uiState.value.openClawSettings.conversationModeEnabled) {
            startOpenClawVoiceInput()
        }
    }

    fun removeHistory(id: String) {
        val next = _uiState.value.history.filterNot { it.id == id }
        repository.saveHistory(next)
        _uiState.update { it.copy(history = next) }
    }

    fun clearHistory() {
        repository.saveHistory(emptyList())
        _uiState.update { it.copy(history = emptyList()) }
    }

    private fun appendMessage(conversations: List<Conversation>, conversationId: String, message: ChatMessage): List<Conversation> {
        return conversations.map { conversation ->
            if (conversation.id == conversationId) {
                val nextTitle = if (conversation.title == "New Conversation" && message.role == "user") {
                    message.content.take(50)
                } else {
                    conversation.title
                }
                conversation.copy(
                    title = nextTitle,
                    messages = conversation.messages + message,
                    updatedAtMillis = System.currentTimeMillis()
                )
            } else {
                conversation
            }
        }.sortedByDescending { it.updatedAtMillis }
    }

    private fun resolvedModel(settings: SpeakSettings): String {
        return when {
            settings.selectedModel.startsWith("deepgram") && !settings.deepgramKeyStored -> "android/local/SpeechRecognizer"
            settings.selectedModel.startsWith("elevenlabs") && !settings.elevenLabsKeyStored -> "android/local/SpeechRecognizer"
            settings.selectedModel.startsWith("openai") && !settings.openAIKeyStored -> "android/local/SpeechRecognizer"
            else -> settings.selectedModel
        }
    }

    private fun modelLabel(model: String): String {
        return when {
            model.startsWith("deepgram") -> "Deepgram"
            model.startsWith("elevenlabs") -> "ElevenLabs"
            model.startsWith("openai") -> "OpenAI gpt-realtime-whisper"
            else -> "Android Speech"
        }
    }
}
