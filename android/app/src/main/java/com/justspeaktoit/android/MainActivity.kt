package com.justspeaktoit.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: SpeakViewModel by viewModels()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JustSpeakToItApp(viewModel)
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE_RECORDING || intent?.action == "com.justspeaktoit.android.action.TOGGLE_RECORDING") {
            viewModel.handleExternalToggleRecording()
        }
    }

    companion object {
        const val ACTION_TOGGLE_RECORDING = "com.justspeaktoit.android.action.TOGGLE_RECORDING"
    }
}

@Composable
private fun JustSpeakToItApp(viewModel: SpeakViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0F6B73),
            secondary = Color(0xFFB36B00),
            tertiary = Color(0xFF6E4B8B)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = state.activeTab == MainTab.Transcribe,
                            onClick = { viewModel.selectTab(MainTab.Transcribe) },
                            label = { Text("Transcribe") },
                            icon = { Text("Mic") },
                            modifier = Modifier.testTag("tabTranscribe")
                        )
                        NavigationBarItem(
                            selected = state.activeTab == MainTab.OpenClaw,
                            onClick = { viewModel.selectTab(MainTab.OpenClaw) },
                            label = { Text("OpenClaw") },
                            icon = { Text("Bolt") },
                            modifier = Modifier.testTag("tabOpenClaw")
                        )
                    }
                }
            ) { padding ->
                AppContent(state, viewModel, padding)
            }
        }
    }
}

@Composable
private fun AppContent(state: SpeakUiState, viewModel: SpeakViewModel, padding: PaddingValues) {
    when (state.screen) {
        AppScreen.Transcribe -> TranscribeScreen(state, viewModel, padding)
        AppScreen.History -> HistoryScreen(state, viewModel, padding)
        AppScreen.Settings -> SettingsScreen(state, viewModel, padding)
        AppScreen.ApiKeys -> ApiKeysScreen(state, viewModel, padding)
        AppScreen.PostProcessing -> PostProcessingScreen(state, viewModel, padding)
        AppScreen.OpenClawList -> OpenClawListScreen(state, viewModel, padding)
        AppScreen.OpenClawChat -> OpenClawChatScreen(state, viewModel, padding)
        AppScreen.OpenClawSettings -> OpenClawSettingsScreen(state, viewModel, padding)
        AppScreen.Privacy -> InfoScreen("Privacy Information", "Microphone audio is captured only while recording. Local Android Speech stays on device when available. Cloud providers process audio only when selected and configured.", padding) { viewModel.showSettings() }
        AppScreen.Recordings -> InfoScreen("Saved Recordings", "Android keeps transcription records locally and can retain recording metadata for replay or re-transcription flows.", padding) { viewModel.showSettings() }
        AppScreen.SendToMac -> InfoScreen("Send to Mac", "Android parity uses local connection settings and QR/import-export affordances as the counterpart to the iOS Send to Mac screen.", padding) { viewModel.showSettings() }
        AppScreen.About -> InfoScreen("About", "Just Speak to It Android 0.1.0 debug\nSpeakCore parity implemented natively for Android.", padding) { viewModel.showSettings() }
    }
}

@Composable
private fun TranscribeScreen(state: SpeakUiState, viewModel: SpeakViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("transcribeScreen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Header(
                title = "Just Speak to It",
                subtitle = if (state.isRecording) "Recording with live transcript" else "Tap the microphone to start transcription"
            )
        }
        item {
            TranscriptCard(state)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.toggleRecording() },
                    shape = CircleShape,
                    modifier = Modifier
                        .size(96.dp)
                        .testTag("recordButton"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRecording) Color(0xFFB3261E) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (state.isRecording) "Stop" else "Mic")
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.showPostProcessing() },
                        enabled = state.transcriptText.isNotBlank(),
                        modifier = Modifier.testTag("polishButton")
                    ) {
                        Text("Polish")
                    }
                    OutlinedButton(
                        onClick = { viewModel.copyTranscript() },
                        enabled = state.transcriptText.isNotBlank(),
                        modifier = Modifier.testTag("copyButton")
                    ) {
                        Text(if (state.copied) "Copied" else "Copy")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.showHistory() }, modifier = Modifier.testTag("historyButton")) {
                    Text("History")
                }
                OutlinedButton(onClick = { viewModel.showSettings() }, modifier = Modifier.testTag("settingsButton")) {
                    Text("Settings")
                }
            }
        }
        item { StatusText(state.statusMessage) }
    }
}

@Composable
private fun TranscriptCard(state: SpeakUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Transcript", fontWeight = FontWeight.SemiBold)
            Text(
                text = state.processedText.ifBlank { state.transcriptText.ifBlank { "Tap the microphone to start transcription" } },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transcriptText")
            )
            if (state.isRecording) {
                Text("Live", color = Color(0xFFB3261E), modifier = Modifier.testTag("recordingIndicator"))
            }
        }
    }
}

@Composable
private fun HistoryScreen(state: SpeakUiState, viewModel: SpeakViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("historyScreen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BackHeader("History", "Your transcriptions will appear here") { viewModel.showTranscribe() }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("${state.history.size}", "Transcriptions")
                Stat("${state.history.sumOf { it.durationSeconds }}s", "Total Time")
                Stat("${state.history.sumOf { it.wordCount }}", "Words")
            }
        }
        if (state.history.isEmpty()) {
            item { Text("No History", modifier = Modifier.testTag("emptyHistory")) }
        } else {
            items(state.history, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Text("${item.model} · ${item.durationSeconds}s · ${item.wordCount} words", color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.copyTranscript() }) { Text("Copy") }
                            OutlinedButton(onClick = { viewModel.removeHistory(item.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: SpeakUiState, viewModel: SpeakViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("settingsScreen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { BackHeader("Settings", "Transcription, keys, sync, and app behavior") { viewModel.showTranscribe() } }
        item {
            SettingsSection("Transcription") {
                ModelOption("Android Speech (On-device)", "android/local/SpeechRecognizer", state.settings.selectedModel, viewModel::updateSelectedModel)
                ModelOption("Deepgram Nova-3", "deepgram/nova-3", state.settings.selectedModel, viewModel::updateSelectedModel)
                ModelOption("ElevenLabs Scribe", "elevenlabs/scribe_v1", state.settings.selectedModel, viewModel::updateSelectedModel)
                ModelOption("OpenAI gpt-realtime-whisper", "openai/gpt-realtime-whisper-streaming", state.settings.selectedModel, viewModel::updateSelectedModel)
            }
        }
        item {
            SettingsSection("Behavior") {
                SwitchRow("Auto-Start Recording", state.settings.autoStartRecording, viewModel::setAutoStart)
                SwitchRow("Live Notifications", state.settings.liveNotificationsEnabled, viewModel::setLiveNotifications)
                SwitchRow("Auto-Polish After Recording", state.settings.autoPostProcess, viewModel::setAutoPostProcess)
            }
        }
        item {
            SettingsSection("API Keys") {
                KeyStatus("Deepgram", state.settings.deepgramKeyStored)
                KeyStatus("ElevenLabs", state.settings.elevenLabsKeyStored)
                KeyStatus("OpenRouter", state.settings.openRouterKeyStored)
                KeyStatus("OpenAI", state.settings.openAIKeyStored)
                Button(onClick = { viewModel.showApiKeys() }, modifier = Modifier.testTag("manageKeysButton")) { Text("Manage Keys") }
            }
        }
        item {
            SettingsSection("Post-Processing") {
                Text("Model: ${state.settings.postProcessingModel}")
                Button(onClick = { viewModel.showPostProcessing() }) { Text("Model & Prompt") }
            }
        }
        item {
            SettingsSection("Sync") {
                Text("Android local-first history sync is available through import/export parity.")
                Text("iCloud Keychain: Platform-specific Apple feature")
            }
        }
        item {
            SettingsSection("OpenClaw") {
                Text(if (state.openClawSettings.isConfigured) "Connected" else "Not configured")
                Button(onClick = { viewModel.showOpenClawSettings() }, modifier = Modifier.testTag("openClawSettingsButton")) { Text("Configure OpenClaw") }
            }
        }
        item {
            SettingsSection("More") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.showRecordings() }) { Text("Recordings") }
                    OutlinedButton(onClick = { viewModel.showSendToMac() }) { Text("Send to Mac") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.showPrivacy() }) { Text("Privacy") }
                    OutlinedButton(onClick = { viewModel.showAbout() }) { Text("About") }
                }
                SwitchRow("Debug Logging", state.settings.debugLoggingEnabled, viewModel::setDebugLogging)
            }
        }
    }
}

@Composable
private fun ApiKeysScreen(state: SpeakUiState, viewModel: SpeakViewModel, padding: PaddingValues) {
    var deepgram by remember { mutableStateOf("") }
    var elevenLabs by remember { mutableStateOf("") }
    var openRouter by remember { mutableStateOf("") }
    var openAI by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("apiKeysScreen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BackHeader("API Keys", "Keys are saved with Android encrypted storage when available") { viewModel.showSettings() } }
        item { SecretField("Deepgram API Key", deepgram) { deepgram = it } }
        item { SecretField("ElevenLabs API Key", elevenLabs) { elevenLabs = it } }
        item { SecretField("OpenRouter API Key", openRouter) { openRouter = it } }
        item { SecretField("OpenAI API Key", openAI) { openAI = it } }
        item {
            Button(
                onClick = {
                    viewModel.saveApiKey("deepgram.apiKey", deepgram)
                    viewModel.saveApiKey("elevenlabs.apiKey", elevenLabs)
                    viewModel.saveApiKey("openrouter.apiKey", openRouter)
                    viewModel.saveApiKey("openai.apiKey", openAI)
                    viewModel.showSettings()
                },
                modifier = Modifier.testTag("saveKeysButton")
            ) {
                Text("Save")
            }
        }
        item {
            Text("Stored now: Deepgram ${state.settings.deepgramKeyStored}, ElevenLabs ${state.settings.elevenLabsKeyStored}, OpenRouter ${state.settings.openRouterKeyStored}, OpenAI ${state.settings.openAIKeyStored}")
        }
    }
}

@Composable
private fun PostProcessingScreen(state: SpeakUiState, viewModel: SpeakViewModel, padding: PaddingValues) {
    var input by remember(state.transcriptText) { mutableStateOf(state.transcriptText) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("postProcessingScreen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BackHeader("Post-Process", "Clean up spelling, grammar, punctuation, and casing") { viewModel.showTranscribe() } }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Input") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("postInput")
            )
        }
        item {
            SettingsSection("Model") {
                listOf<String>(
                    "openai/gpt-4o-mini",
                    "google/gemini-2.0-flash-lite-001",
                    "anthropic/claude-3.5-haiku",
                    "anthropic/claude-sonnet-4"
                ).forEach { model: String ->
                    ModelOption(model, model, state.settings.postProcessingModel, viewModel::setPostProcessingModel)
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.settings.postProcessingPrompt,
                onValueChange = viewModel::setPostProcessingPrompt,
                label = { Text("Custom Prompt") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    viewModel.processTranscript(input)
                    viewModel.showTranscribe()
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.testTag("processButton")
            ) {
                Text("Process")
            }
        }
        item {
            Text(state.processedText, modifier = Modifier.testTag("postOutput"))
        }
    }
}

@Composable
private fun OpenClawListScreen(state: SpeakUiState, viewModel: SpeakViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("openClawListScreen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Header("OpenClaw", "Voice chat with persistent gateway conversations")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.createConversation() }, modifier = Modifier.testTag("newConversationButton")) { Text("New Conversation") }
                OutlinedButton(onClick = { viewModel.showOpenClawSettings() }) { Text("Settings") }
            }
        }
        items(state.conversations, key = { it.id }) { conversation ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectConversation(conversation.id) }
                    .testTag("conversationRow")
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(conversation.title, fontWeight = FontWeight.SemiBold)
                    Text("${conversation.messages.size} messages · ${conversation.sessionKey}", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun OpenClawChatScreen(state: SpeakUiState, viewModel: SpeakViewModel, padding: PaddingValues) {
    val conversation = state.conversations.firstOrNull { it.id == state.selectedConversationId } ?: state.conversations.firstOrNull()
    var input by remember(conversation?.id) { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("openClawChatScreen")
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.showOpenClawList() }) { Text("Back") }
            Column(Modifier.weight(1f)) {
                Text(conversation?.title ?: "OpenClaw", fontWeight = FontWeight.Bold)
                Text(state.openClawConnectionState, color = Color.Gray)
            }
            OutlinedButton(onClick = { conversation?.let { viewModel.deleteConversation(it.id) } }) { Text("Delete") }
        }
        Divider()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(conversation?.messages.orEmpty(), key = { it.id }) { message ->
                MessageBubble(message)
            }
            if (state.partialVoiceInput.isNotBlank()) {
                item {
                    Text("Listening: ${state.partialVoiceInput}", modifier = Modifier.testTag("openClawPartial"))
                }
            }
            if (state.isOpenClawProcessing) {
                item { Text("Thinking...", modifier = Modifier.testTag("openClawProcessing")) }
            }
            if (state.isSpeaking) {
                item { Text("Speaking...", modifier = Modifier.testTag("openClawSpeaking")) }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwitchRow("Conversation Mode", state.openClawSettings.conversationModeEnabled) {
                viewModel.updateOpenClawSettings { settings -> settings.copy(conversationModeEnabled = it) }
            }
            if (state.openClawSettings.conversationModeEnabled) {
                Text("Tap the chat area to acknowledge")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Type a message...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("openClawInput")
                )
                Button(
                    onClick = {
                        if (state.isRecording) viewModel.stopOpenClawVoiceInput() else viewModel.startOpenClawVoiceInput()
                    },
                    modifier = Modifier.testTag("openClawVoiceButton")
                ) {
                    Text(if (state.isRecording) "Stop" else "Mic")
                }
                Button(
                    onClick = {
                        viewModel.sendOpenClawMessage(input)
                        input = ""
                    },
                    modifier = Modifier.testTag("openClawSendButton")
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun OpenClawSettingsScreen(state: SpeakUiState, viewModel: SpeakViewModel, padding: PaddingValues) {
    var gateway by remember(state.openClawSettings.gatewayUrl) { mutableStateOf(state.openClawSettings.gatewayUrl) }
    var token by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("openClawSettingsScreen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BackHeader("OpenClaw Settings", "Gateway, TTS, and hands-free controls") { viewModel.showSettings() } }
        item {
            OutlinedTextField(
                value = gateway,
                onValueChange = { gateway = it },
                label = { Text("Gateway URL") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { SecretField("OpenClaw Token", token) { token = it } }
        item {
            SwitchRow("Enable OpenClaw", state.openClawSettings.enabled) {
                viewModel.updateOpenClawSettings { settings -> settings.copy(enabled = it) }
            }
        }
        item {
            SettingsSection("TTS") {
                SwitchRow("Speak Responses", state.openClawSettings.ttsEnabled) {
                    viewModel.updateOpenClawSettings { settings -> settings.copy(ttsEnabled = it) }
                }
                SwitchRow("Summarise Before Speaking", state.openClawSettings.summariseResponses) {
                    viewModel.updateOpenClawSettings { settings -> settings.copy(summariseResponses = it) }
                }
                SwitchRow("Low-Latency Speech", state.openClawSettings.lowLatencySpeech) {
                    viewModel.updateOpenClawSettings { settings -> settings.copy(lowLatencySpeech = it) }
                }
                Text("Speed ${"%.1f".format(state.openClawSettings.ttsSpeed)}")
                Slider(
                    value = state.openClawSettings.ttsSpeed,
                    onValueChange = { speed: Float ->
                        viewModel.updateOpenClawSettings { settings -> settings.copy(ttsSpeed = speed) }
                    },
                    valueRange = 0.7f..1.3f
                )
            }
        }
        item {
            SettingsSection("Hands-Free") {
                SwitchRow("Conversation Mode", state.openClawSettings.conversationModeEnabled) {
                    viewModel.updateOpenClawSettings { settings -> settings.copy(conversationModeEnabled = it) }
                }
                SwitchRow("Auto-Resume Listening", state.openClawSettings.autoResumeListening) {
                    viewModel.updateOpenClawSettings { settings -> settings.copy(autoResumeListening = it) }
                }
                SwitchRow("Headset Single-Tap Acknowledge", state.openClawSettings.headsetSingleTapAcknowledge) {
                    viewModel.updateOpenClawSettings { settings -> settings.copy(headsetSingleTapAcknowledge = it) }
                }
                SwitchRow("Keyword Acknowledge", state.openClawSettings.keywordAcknowledgeEnabled) {
                    viewModel.updateOpenClawSettings { settings -> settings.copy(keywordAcknowledgeEnabled = it) }
                }
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.updateOpenClawSettings {
                        it.copy(gatewayUrl = OpenClawProtocol.normaliseGatewayUrl(gateway))
                    }
                    viewModel.saveApiKey("openclaw.token", token)
                    viewModel.showOpenClawList()
                },
                modifier = Modifier.testTag("saveOpenClawSettingsButton")
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun InfoScreen(title: String, body: String, padding: PaddingValues, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { BackHeader(title, body, onBack) }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.Gray)
    }
}

@Composable
private fun BackHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Header(title, subtitle)
    }
}

@Composable
private fun StatusText(text: String?) {
    if (!text.isNullOrBlank()) {
        Text(text, color = Color.Gray, modifier = Modifier.testTag("statusMessage"))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModelOption(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun KeyStatus(label: String, stored: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(if (stored) "Stored" else "Missing", color = if (stored) Color(0xFF0B7A3B) else Color.Gray)
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else Color(0xFFECEFF1),
            contentColor = if (isUser) Color.White else Color.Black,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .testTag(if (isUser) "userMessage" else "assistantMessage")
        ) {
            Text(message.content, Modifier.padding(14.dp))
        }
    }
}
