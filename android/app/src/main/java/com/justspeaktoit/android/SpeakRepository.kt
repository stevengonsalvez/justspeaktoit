package com.justspeaktoit.android

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private val Context.speakDataStore by preferencesDataStore(name = "just_speak_to_it_android")

class SpeakRepository(private val context: Context) {
    private val securePrefs: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "just_speak_to_it_android_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences("just_speak_to_it_android_secure_fallback", Context.MODE_PRIVATE)
    }

    fun loadSettings(): SpeakSettings = runBlocking {
        val prefs = context.speakDataStore.data.first()
        SpeakSettings(
            selectedModel = prefs[Keys.SELECTED_MODEL] ?: "android/local/SpeechRecognizer",
            autoStartRecording = prefs[Keys.AUTO_START_RECORDING] ?: false,
            liveNotificationsEnabled = prefs[Keys.LIVE_NOTIFICATIONS_ENABLED] ?: true,
            autoPostProcess = prefs[Keys.AUTO_POST_PROCESS] ?: false,
            postProcessingModel = prefs[Keys.POST_PROCESSING_MODEL] ?: "openai/gpt-4o-mini",
            postProcessingPrompt = prefs[Keys.POST_PROCESSING_PROMPT] ?: "",
            deepgramKeyStored = hasSecret("deepgram.apiKey"),
            elevenLabsKeyStored = hasSecret("elevenlabs.apiKey"),
            openRouterKeyStored = hasSecret("openrouter.apiKey"),
            openAIKeyStored = hasSecret("openai.apiKey"),
            debugLoggingEnabled = prefs[Keys.DEBUG_LOGGING_ENABLED] ?: false,
            flowBubbleEnabled = prefs[Keys.FLOW_BUBBLE_ENABLED] ?: false,
            flowBubblePhraseStartEnabled = prefs[Keys.FLOW_BUBBLE_PHRASE_START_ENABLED] ?: false,
            flowBubblePhraseStartPhrase = prefs[Keys.FLOW_BUBBLE_PHRASE_START_PHRASE] ?: "start speaking",
            flowBubbleExplicitListeningMode = prefs[Keys.FLOW_BUBBLE_EXPLICIT_LISTENING_MODE] ?: false,
            flowBubbleClipboardFallbackEnabled = prefs[Keys.FLOW_BUBBLE_CLIPBOARD_FALLBACK_ENABLED] ?: true,
            flowBubbleSizePercent = prefs[Keys.FLOW_BUBBLE_SIZE_PERCENT] ?: 1.0f,
            flowBubbleOpacity = prefs[Keys.FLOW_BUBBLE_OPACITY] ?: 0.94f,
            flowBubbleSnoozed = prefs[Keys.FLOW_BUBBLE_SNOOZED] ?: false
        )
    }

    fun saveSettings(settings: SpeakSettings) = runBlocking {
        context.speakDataStore.edit { prefs ->
            prefs[Keys.SELECTED_MODEL] = settings.selectedModel
            prefs[Keys.AUTO_START_RECORDING] = settings.autoStartRecording
            prefs[Keys.LIVE_NOTIFICATIONS_ENABLED] = settings.liveNotificationsEnabled
            prefs[Keys.AUTO_POST_PROCESS] = settings.autoPostProcess
            prefs[Keys.POST_PROCESSING_MODEL] = settings.postProcessingModel
            prefs[Keys.POST_PROCESSING_PROMPT] = settings.postProcessingPrompt
            prefs[Keys.DEBUG_LOGGING_ENABLED] = settings.debugLoggingEnabled
            prefs[Keys.FLOW_BUBBLE_ENABLED] = settings.flowBubbleEnabled
            prefs[Keys.FLOW_BUBBLE_PHRASE_START_ENABLED] = settings.flowBubblePhraseStartEnabled
            prefs[Keys.FLOW_BUBBLE_PHRASE_START_PHRASE] = settings.flowBubblePhraseStartPhrase
            prefs[Keys.FLOW_BUBBLE_EXPLICIT_LISTENING_MODE] = settings.flowBubbleExplicitListeningMode
            prefs[Keys.FLOW_BUBBLE_CLIPBOARD_FALLBACK_ENABLED] = settings.flowBubbleClipboardFallbackEnabled
            prefs[Keys.FLOW_BUBBLE_SIZE_PERCENT] = settings.flowBubbleSizePercent
            prefs[Keys.FLOW_BUBBLE_OPACITY] = settings.flowBubbleOpacity
            prefs[Keys.FLOW_BUBBLE_SNOOZED] = settings.flowBubbleSnoozed
        }
    }

    fun loadOpenClawSettings(): OpenClawSettingsState = runBlocking {
        val prefs = context.speakDataStore.data.first()
        OpenClawSettingsState(
            gatewayUrl = prefs[Keys.OPENCLAW_GATEWAY_URL] ?: "",
            tokenStored = hasSecret("openclaw.token"),
            enabled = prefs[Keys.OPENCLAW_ENABLED] ?: false,
            ttsEnabled = prefs[Keys.OPENCLAW_TTS_ENABLED] ?: true,
            summariseResponses = prefs[Keys.OPENCLAW_SUMMARISE_RESPONSES] ?: true,
            ttsVoice = prefs[Keys.OPENCLAW_TTS_VOICE] ?: "asteria",
            ttsModel = prefs[Keys.OPENCLAW_TTS_MODEL] ?: "aura-2",
            ttsSpeed = prefs[Keys.OPENCLAW_TTS_SPEED] ?: 1.0f,
            conversationModeEnabled = prefs[Keys.OPENCLAW_CONVERSATION_MODE_ENABLED] ?: false,
            autoResumeListening = prefs[Keys.OPENCLAW_AUTO_RESUME_LISTENING] ?: true,
            headsetSingleTapAcknowledge = prefs[Keys.OPENCLAW_HEADSET_ACKNOWLEDGE] ?: false,
            keywordAcknowledgeEnabled = prefs[Keys.OPENCLAW_KEYWORD_ACKNOWLEDGE_ENABLED] ?: false,
            keywordAcknowledgePhrase = prefs[Keys.OPENCLAW_KEYWORD_ACKNOWLEDGE_PHRASE] ?: "over",
            lowLatencySpeech = prefs[Keys.OPENCLAW_LOW_LATENCY_SPEECH] ?: false
        )
    }

    fun saveOpenClawSettings(settings: OpenClawSettingsState) = runBlocking {
        context.speakDataStore.edit { prefs ->
            prefs[Keys.OPENCLAW_GATEWAY_URL] = settings.gatewayUrl
            prefs[Keys.OPENCLAW_ENABLED] = settings.enabled
            prefs[Keys.OPENCLAW_TTS_ENABLED] = settings.ttsEnabled
            prefs[Keys.OPENCLAW_SUMMARISE_RESPONSES] = settings.summariseResponses
            prefs[Keys.OPENCLAW_TTS_VOICE] = settings.ttsVoice
            prefs[Keys.OPENCLAW_TTS_MODEL] = settings.ttsModel
            prefs[Keys.OPENCLAW_TTS_SPEED] = settings.ttsSpeed
            prefs[Keys.OPENCLAW_CONVERSATION_MODE_ENABLED] = settings.conversationModeEnabled
            prefs[Keys.OPENCLAW_AUTO_RESUME_LISTENING] = settings.autoResumeListening
            prefs[Keys.OPENCLAW_HEADSET_ACKNOWLEDGE] = settings.headsetSingleTapAcknowledge
            prefs[Keys.OPENCLAW_KEYWORD_ACKNOWLEDGE_ENABLED] = settings.keywordAcknowledgeEnabled
            prefs[Keys.OPENCLAW_KEYWORD_ACKNOWLEDGE_PHRASE] = settings.keywordAcknowledgePhrase
            prefs[Keys.OPENCLAW_LOW_LATENCY_SPEECH] = settings.lowLatencySpeech
        }
    }

    fun storeSecret(name: String, value: String) {
        val editor = securePrefs.edit()
        if (value.isBlank()) editor.remove(name) else editor.putString(name, value)
        editor.apply()
    }

    fun readSecret(name: String): String = securePrefs.getString(name, "").orEmpty()

    private fun hasSecret(name: String): Boolean = readSecret(name).isNotBlank()

    fun loadHistory(): List<HistoryEntry> = runBlocking {
        val prefs = context.speakDataStore.data.first()
        val array = JSONArray(prefs[Keys.HISTORY_JSON] ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            HistoryEntry(
                id = obj.optString("id"),
                text = obj.optString("text"),
                model = obj.optString("model"),
                durationSeconds = obj.optInt("durationSeconds"),
                createdAtMillis = obj.optLong("createdAtMillis"),
                synced = obj.optBoolean("synced")
            )
        }
    }

    fun saveHistory(entries: List<HistoryEntry>) = runBlocking {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("text", entry.text)
                    .put("model", entry.model)
                    .put("durationSeconds", entry.durationSeconds)
                    .put("createdAtMillis", entry.createdAtMillis)
                    .put("synced", entry.synced)
            )
        }
        context.speakDataStore.edit { prefs -> prefs[Keys.HISTORY_JSON] = array.toString() }
    }

    fun loadConversations(): List<Conversation> = runBlocking {
        val prefs = context.speakDataStore.data.first()
        val array = JSONArray(prefs[Keys.CONVERSATIONS_JSON] ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val messagesArray = obj.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until messagesArray.length()).mapNotNull messageMap@{ messageIndex ->
                val msg = messagesArray.optJSONObject(messageIndex) ?: return@messageMap null
                ChatMessage(
                    id = msg.optString("id"),
                    role = msg.optString("role"),
                    content = msg.optString("content"),
                    timestampMillis = msg.optLong("timestampMillis")
                )
            }
            Conversation(
                id = obj.optString("id"),
                sessionKey = obj.optString("sessionKey"),
                title = obj.optString("title"),
                messages = messages,
                updatedAtMillis = obj.optLong("updatedAtMillis")
            )
        }
    }

    fun saveConversations(conversations: List<Conversation>) = runBlocking {
        val array = JSONArray()
        conversations.forEach { conversation ->
            val messagesArray = JSONArray()
            conversation.messages.forEach { message ->
                messagesArray.put(
                    JSONObject()
                        .put("id", message.id)
                        .put("role", message.role)
                        .put("content", message.content)
                        .put("timestampMillis", message.timestampMillis)
                )
            }
            array.put(
                JSONObject()
                    .put("id", conversation.id)
                    .put("sessionKey", conversation.sessionKey)
                    .put("title", conversation.title)
                    .put("updatedAtMillis", conversation.updatedAtMillis)
                    .put("messages", messagesArray)
            )
        }
        context.speakDataStore.edit { prefs -> prefs[Keys.CONVERSATIONS_JSON] = array.toString() }
    }

    private object Keys {
        val SELECTED_MODEL = stringPreferencesKey("selectedModel")
        val AUTO_START_RECORDING = booleanPreferencesKey("autoStartRecording")
        val LIVE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("liveNotificationsEnabled")
        val AUTO_POST_PROCESS = booleanPreferencesKey("autoPostProcess")
        val POST_PROCESSING_MODEL = stringPreferencesKey("postProcessingModel")
        val POST_PROCESSING_PROMPT = stringPreferencesKey("postProcessingPrompt")
        val DEBUG_LOGGING_ENABLED = booleanPreferencesKey("debugLoggingEnabled")
        val FLOW_BUBBLE_ENABLED = booleanPreferencesKey("flowBubble.enabled")
        val FLOW_BUBBLE_PHRASE_START_ENABLED = booleanPreferencesKey("flowBubble.phraseStartEnabled")
        val FLOW_BUBBLE_PHRASE_START_PHRASE = stringPreferencesKey("flowBubble.phraseStartPhrase")
        val FLOW_BUBBLE_EXPLICIT_LISTENING_MODE = booleanPreferencesKey("flowBubble.explicitListeningMode")
        val FLOW_BUBBLE_CLIPBOARD_FALLBACK_ENABLED = booleanPreferencesKey("flowBubble.clipboardFallbackEnabled")
        val FLOW_BUBBLE_SIZE_PERCENT = floatPreferencesKey("flowBubble.sizePercent")
        val FLOW_BUBBLE_OPACITY = floatPreferencesKey("flowBubble.opacity")
        val FLOW_BUBBLE_SNOOZED = booleanPreferencesKey("flowBubble.snoozed")
        val OPENCLAW_GATEWAY_URL = stringPreferencesKey("openclaw.gatewayUrl")
        val OPENCLAW_ENABLED = booleanPreferencesKey("openclaw.enabled")
        val OPENCLAW_TTS_ENABLED = booleanPreferencesKey("openclaw.ttsEnabled")
        val OPENCLAW_SUMMARISE_RESPONSES = booleanPreferencesKey("openclaw.summariseResponses")
        val OPENCLAW_TTS_VOICE = stringPreferencesKey("openclaw.ttsVoice")
        val OPENCLAW_TTS_MODEL = stringPreferencesKey("openclaw.ttsModel")
        val OPENCLAW_TTS_SPEED = floatPreferencesKey("openclaw.ttsSpeed")
        val OPENCLAW_CONVERSATION_MODE_ENABLED = booleanPreferencesKey("openclaw.conversationModeEnabled")
        val OPENCLAW_AUTO_RESUME_LISTENING = booleanPreferencesKey("openclaw.autoResumeListening")
        val OPENCLAW_HEADSET_ACKNOWLEDGE = booleanPreferencesKey("openclaw.headsetSingleTapAcknowledge")
        val OPENCLAW_KEYWORD_ACKNOWLEDGE_ENABLED = booleanPreferencesKey("openclaw.keywordAcknowledgeEnabled")
        val OPENCLAW_KEYWORD_ACKNOWLEDGE_PHRASE = stringPreferencesKey("openclaw.keywordAcknowledgePhrase")
        val OPENCLAW_LOW_LATENCY_SPEECH = booleanPreferencesKey("openclaw.lowLatencySpeech")
        val HISTORY_JSON = stringPreferencesKey("history")
        val CONVERSATIONS_JSON = stringPreferencesKey("conversations")
    }
}
