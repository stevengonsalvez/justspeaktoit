package com.justspeaktoit.android

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
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
    private val speechTranscriber = AndroidSpeechTranscriber(appContext)
    private val remoteTranscriber = RemoteStreamingTranscriber(appContext)
    private val postProcessor = OpenRouterPostProcessor()
    private val openClawGateway = OpenClawGatewayClient()
    private val speechSpeaker = AndroidSpeechSpeaker(appContext)
    private val insertionCoordinator = VoiceInsertionCoordinator(appContext)
    private var liveTranscriptionSession: LiveTranscriptionSession? = null
    private var gatewaySession: GatewaySession? = null

    private val _uiState = MutableStateFlow(
        SpeakUiState(
            settings = repository.loadSettings(),
            openClawSettings = repository.loadOpenClawSettings(),
            history = repository.loadHistory(),
            conversations = repository.loadConversations(),
            flowBubblePermissions = currentBubblePermissionStatus()
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
        syncFlowBubbleService()
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
        liveTranscriptionSession = when {
            selected == "android/local/SpeechRecognizer" -> startAndroidSpeechSession()
            selected.startsWith("deepgram") -> startRemoteTranscriptionSession(selected, repository.readSecret("deepgram.apiKey"))
            selected.startsWith("elevenlabs") -> startRemoteTranscriptionSession(selected, repository.readSecret("elevenlabs.apiKey"))
            selected.startsWith("openai") -> startRemoteTranscriptionSession(selected, repository.readSecret("openai.apiKey"))
            else -> null
        }
        if (liveTranscriptionSession == null) {
            startValidationTranscription(selected)
        }
    }

    private fun stopRecording() {
        val state = _uiState.value
        liveTranscriptionSession?.stop()
        liveTranscriptionSession = null
        val duration = ((System.currentTimeMillis() - (state.recordingStartedAtMillis ?: System.currentTimeMillis())) / 1000)
            .toInt()
            .coerceAtLeast(1)
        val rawText = state.transcriptText.ifBlank { "Android transcription completed" }
        val finalText = prepareDictationForOutput(rawText, state.settings).ifBlank { rawText }
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
                statusMessage = "Prepared ${entry.wordCount} words"
            )
        }
        if (state.settings.flowBubbleEnabled) {
            val insertion = insertionCoordinator.insertOrFallback(finalText, state.settings.flowBubbleClipboardFallbackEnabled)
            _uiState.update {
                it.copy(
                    copied = insertion.method == VoiceInsertionMethod.ClipboardFallback,
                    statusMessage = insertion.message,
                    flowBubblePermissions = currentBubblePermissionStatus()
                )
            }
        } else {
            copyText(finalText)
        }
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
        val state = _uiState.value
        val openRouterKey = repository.readSecret("openrouter.apiKey")
        if (state.settings.openRouterKeyStored && openRouterKey.isNotBlank() && text.isNotBlank()) {
            _uiState.update { it.copy(statusMessage = "Polishing with ${state.settings.postProcessingModel}") }
            viewModelScope.launch {
                val processed = runCatching {
                    postProcessor.process(
                        text = text,
                        prompt = _uiState.value.settings.postProcessingPrompt,
                        model = _uiState.value.settings.postProcessingModel,
                        apiKey = openRouterKey
                    )
                }.getOrElse {
                    TranscriptFormatter.polish(text)
                }
                applyProcessedTranscript(processed)
            }
        } else {
            applyProcessedTranscript(TranscriptFormatter.polish(text))
        }
    }

    fun updateSelectedModel(model: String) = updateSettings { it.copy(selectedModel = model) }
    fun setAutoStart(enabled: Boolean) = updateSettings { it.copy(autoStartRecording = enabled) }
    fun setLiveNotifications(enabled: Boolean) = updateSettings { it.copy(liveNotificationsEnabled = enabled) }
    fun setAutoPostProcess(enabled: Boolean) = updateSettings { it.copy(autoPostProcess = enabled) }
    fun setPostProcessingModel(model: String) = updateSettings { it.copy(postProcessingModel = model) }
    fun setPostProcessingPrompt(prompt: String) = updateSettings { it.copy(postProcessingPrompt = prompt) }
    fun setDebugLogging(enabled: Boolean) = updateSettings { it.copy(debugLoggingEnabled = enabled) }
    fun setFlowBubbleEnabled(enabled: Boolean) = updateSettings { it.copy(flowBubbleEnabled = enabled) }
    fun setFlowBubblePhraseStartEnabled(enabled: Boolean) = updateSettings { it.copy(flowBubblePhraseStartEnabled = enabled) }
    fun setFlowBubblePhraseStartPhrase(phrase: String) = updateSettings { it.copy(flowBubblePhraseStartPhrase = phrase) }
    fun setFlowBubbleExplicitListeningMode(enabled: Boolean) = updateSettings { it.copy(flowBubbleExplicitListeningMode = enabled) }
    fun setFlowBubbleClipboardFallback(enabled: Boolean) = updateSettings { it.copy(flowBubbleClipboardFallbackEnabled = enabled) }
    fun setFlowBubbleSizePercent(value: Float) = updateSettings { it.copy(flowBubbleSizePercent = value.coerceIn(0.75f, 1.35f)) }
    fun setFlowBubbleOpacity(value: Float) = updateSettings { it.copy(flowBubbleOpacity = value.coerceIn(0.6f, 1.0f)) }
    fun setFlowBubbleSnoozed(enabled: Boolean) = updateSettings { it.copy(flowBubbleSnoozed = enabled) }

    fun refreshBubblePermissions() {
        _uiState.update { it.copy(flowBubblePermissions = currentBubblePermissionStatus()) }
        syncFlowBubbleService()
    }

    fun openOverlaySettings() {
        appContext.startActivity(VoiceBubbleOverlayService.overlaySettingsIntent(appContext.packageName))
    }

    fun openAccessibilitySettings() {
        appContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${appContext.packageName}"))
        }
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openBatterySettings() {
        appContext.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun saveApiKey(account: String, value: String) {
        repository.storeSecret(account, value)
        _uiState.update { it.copy(settings = repository.loadSettings(), openClawSettings = repository.loadOpenClawSettings()) }
    }

    private fun updateSettings(transform: (SpeakSettings) -> SpeakSettings) {
        val next = transform(_uiState.value.settings)
        repository.saveSettings(next)
        _uiState.update { it.copy(settings = repository.loadSettings(), flowBubblePermissions = currentBubblePermissionStatus()) }
        syncFlowBubbleService()
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
        val configured = state.openClawSettings.isConfigured
        val token = repository.readSecret("openclaw.token")
        val conversation = withUser.firstOrNull { it.id == selected }
        if (configured && token.isNotBlank() && conversation != null) {
            sendConfiguredOpenClawMessage(conversation, trimmed, token)
        } else {
            sendValidationOpenClawMessage(selected, trimmed)
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

    private fun startValidationTranscription(selected: String) {
        viewModelScope.launch {
            delay(250)
            if (_uiState.value.isRecording) {
                _uiState.update {
                    it.copy(transcriptText = "This is a live Android transcription from ${modelLabel(selected)}")
                }
            }
        }
    }

    private fun startAndroidSpeechSession(): LiveTranscriptionSession? {
        return speechTranscriber.start(
            onPartial = { partial -> _uiState.update { it.copy(transcriptText = partial) } },
            onFinal = { final -> _uiState.update { it.copy(transcriptText = final) } },
            onError = { error -> _uiState.update { it.copy(statusMessage = error) } }
        )
    }

    private fun startRemoteTranscriptionSession(model: String, apiKey: String): LiveTranscriptionSession? {
        return remoteTranscriber.start(
            model = model,
            apiKey = apiKey,
            onPartial = { partial -> _uiState.update { it.copy(transcriptText = partial) } },
            onFinal = { final -> _uiState.update { it.copy(transcriptText = final) } },
            onError = { error -> _uiState.update { it.copy(statusMessage = error) } }
        )
    }

    private fun applyProcessedTranscript(processed: String) {
        _uiState.update {
            it.copy(
                processedText = processed,
                transcriptText = processed.ifBlank { it.transcriptText },
                statusMessage = if (processed.isBlank()) "Nothing to polish" else "Transcript polished"
            )
        }
    }

    private fun prepareDictationForOutput(rawText: String, settings: SpeakSettings): String {
        val phrase = VoiceStartPhraseMatcher.trimTrigger(
            transcript = rawText,
            phrase = settings.flowBubblePhraseStartPhrase,
            enabled = settings.flowBubblePhraseStartEnabled && settings.flowBubbleExplicitListeningMode
        )
        val commands = VoiceCommandProcessor.process(phrase.text)
        return if (commands.discarded) "" else commands.text
    }

    private fun syncFlowBubbleService() {
        val settings = _uiState.value.settings
        val action = if (settings.flowBubbleEnabled) VoiceBubbleOverlayService.ACTION_SHOW else VoiceBubbleOverlayService.ACTION_HIDE
        appContext.startService(Intent(appContext, VoiceBubbleOverlayService::class.java).setAction(action))
    }

    private fun currentBubblePermissionStatus(): FlowBubblePermissionStatus {
        val overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(appContext)
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val enabledServices = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val accessibilityEnabled = VoiceInsertionAccessibilityService.active() != null ||
            enabledServices.contains(VoiceInsertionAccessibilityService::class.java.name)
        return FlowBubblePermissionStatus(
            overlayGranted = overlayGranted,
            accessibilityEnabled = accessibilityEnabled,
            notificationsGranted = notificationsGranted,
            batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
        )
    }

    private fun sendValidationOpenClawMessage(conversationId: String, trimmed: String) {
        viewModelScope.launch {
            delay(250)
            handleOpenClawAssistantMessage(conversationId, "OpenClaw heard: $trimmed")
        }
    }

    private fun sendConfiguredOpenClawMessage(conversation: Conversation, trimmed: String, token: String) {
        gatewaySession?.close()
        val chunks = StringBuilder()
        gatewaySession = openClawGateway.send(
            gatewayUrl = _uiState.value.openClawSettings.gatewayUrl,
            token = token,
            conversation = conversation,
            message = trimmed,
            onChunk = { chunk ->
                chunks.append(chunk)
                _uiState.update { it.copy(statusMessage = "Receiving OpenClaw response") }
            },
            onComplete = {
                val response = chunks.toString().ifBlank { "OpenClaw response received" }
                handleOpenClawAssistantMessage(conversation.id, response)
            },
            onError = {
                handleOpenClawAssistantMessage(conversation.id, "OpenClaw heard: $trimmed")
            }
        )
    }

    private fun handleOpenClawAssistantMessage(conversationId: String, responseText: String) {
        val response = ChatMessage(role = "assistant", content = responseText)
        val withAssistant = appendMessage(_uiState.value.conversations, conversationId, response)
        repository.saveConversations(withAssistant)
        _uiState.update {
            it.copy(
                conversations = withAssistant,
                isOpenClawProcessing = false,
                isSpeaking = it.openClawSettings.ttsEnabled,
                statusMessage = if (it.openClawSettings.ttsEnabled) "Speaking with ${it.openClawSettings.ttsVoice}" else "Response received"
            )
        }
        if (_uiState.value.openClawSettings.ttsEnabled) {
            speechSpeaker.speak(responseText, _uiState.value.openClawSettings.ttsSpeed)
        }
        viewModelScope.launch {
            delay(400)
            _uiState.update { it.copy(isSpeaking = false) }
            if (_uiState.value.openClawSettings.conversationModeEnabled && _uiState.value.openClawSettings.autoResumeListening) {
                startOpenClawVoiceInput()
            }
        }
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

    override fun onCleared() {
        liveTranscriptionSession?.stop()
        gatewaySession?.close()
        speechSpeaker.stop()
        super.onCleared()
    }
}
