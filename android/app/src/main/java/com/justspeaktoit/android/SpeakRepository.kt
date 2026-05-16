package com.justspeaktoit.android

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class SpeakRepository(context: Context) {
    private val prefs = context.getSharedPreferences("just_speak_to_it_android", Context.MODE_PRIVATE)
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

    fun loadSettings(): SpeakSettings {
        return SpeakSettings(
            selectedModel = prefs.getString("selectedModel", "android/local/SpeechRecognizer") ?: "android/local/SpeechRecognizer",
            autoStartRecording = prefs.getBoolean("autoStartRecording", false),
            liveNotificationsEnabled = prefs.getBoolean("liveNotificationsEnabled", true),
            autoPostProcess = prefs.getBoolean("autoPostProcess", false),
            postProcessingModel = prefs.getString("postProcessingModel", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini",
            postProcessingPrompt = prefs.getString("postProcessingPrompt", "") ?: "",
            deepgramKeyStored = hasSecret("deepgram.apiKey"),
            elevenLabsKeyStored = hasSecret("elevenlabs.apiKey"),
            openRouterKeyStored = hasSecret("openrouter.apiKey"),
            openAIKeyStored = hasSecret("openai.apiKey"),
            debugLoggingEnabled = prefs.getBoolean("debugLoggingEnabled", false)
        )
    }

    fun saveSettings(settings: SpeakSettings) {
        prefs.edit()
            .putString("selectedModel", settings.selectedModel)
            .putBoolean("autoStartRecording", settings.autoStartRecording)
            .putBoolean("liveNotificationsEnabled", settings.liveNotificationsEnabled)
            .putBoolean("autoPostProcess", settings.autoPostProcess)
            .putString("postProcessingModel", settings.postProcessingModel)
            .putString("postProcessingPrompt", settings.postProcessingPrompt)
            .putBoolean("debugLoggingEnabled", settings.debugLoggingEnabled)
            .apply()
    }

    fun loadOpenClawSettings(): OpenClawSettingsState {
        return OpenClawSettingsState(
            gatewayUrl = prefs.getString("openclaw.gatewayUrl", "") ?: "",
            tokenStored = hasSecret("openclaw.token"),
            enabled = prefs.getBoolean("openclaw.enabled", false),
            ttsEnabled = prefs.getBoolean("openclaw.ttsEnabled", true),
            summariseResponses = prefs.getBoolean("openclaw.summariseResponses", true),
            ttsVoice = prefs.getString("openclaw.ttsVoice", "asteria") ?: "asteria",
            ttsModel = prefs.getString("openclaw.ttsModel", "aura-2") ?: "aura-2",
            ttsSpeed = prefs.getFloat("openclaw.ttsSpeed", 1.0f),
            conversationModeEnabled = prefs.getBoolean("openclaw.conversationModeEnabled", false),
            autoResumeListening = prefs.getBoolean("openclaw.autoResumeListening", true),
            headsetSingleTapAcknowledge = prefs.getBoolean("openclaw.headsetSingleTapAcknowledge", false),
            keywordAcknowledgeEnabled = prefs.getBoolean("openclaw.keywordAcknowledgeEnabled", false),
            keywordAcknowledgePhrase = prefs.getString("openclaw.keywordAcknowledgePhrase", "over") ?: "over",
            lowLatencySpeech = prefs.getBoolean("openclaw.lowLatencySpeech", false)
        )
    }

    fun saveOpenClawSettings(settings: OpenClawSettingsState) {
        prefs.edit()
            .putString("openclaw.gatewayUrl", settings.gatewayUrl)
            .putBoolean("openclaw.enabled", settings.enabled)
            .putBoolean("openclaw.ttsEnabled", settings.ttsEnabled)
            .putBoolean("openclaw.summariseResponses", settings.summariseResponses)
            .putString("openclaw.ttsVoice", settings.ttsVoice)
            .putString("openclaw.ttsModel", settings.ttsModel)
            .putFloat("openclaw.ttsSpeed", settings.ttsSpeed)
            .putBoolean("openclaw.conversationModeEnabled", settings.conversationModeEnabled)
            .putBoolean("openclaw.autoResumeListening", settings.autoResumeListening)
            .putBoolean("openclaw.headsetSingleTapAcknowledge", settings.headsetSingleTapAcknowledge)
            .putBoolean("openclaw.keywordAcknowledgeEnabled", settings.keywordAcknowledgeEnabled)
            .putString("openclaw.keywordAcknowledgePhrase", settings.keywordAcknowledgePhrase)
            .putBoolean("openclaw.lowLatencySpeech", settings.lowLatencySpeech)
            .apply()
    }

    fun storeSecret(name: String, value: String) {
        val editor = securePrefs.edit()
        if (value.isBlank()) editor.remove(name) else editor.putString(name, value)
        editor.apply()
    }

    private fun hasSecret(name: String): Boolean = !securePrefs.getString(name, "").isNullOrBlank()

    fun loadHistory(): List<HistoryEntry> {
        val array = JSONArray(prefs.getString("history", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { index ->
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

    fun saveHistory(entries: List<HistoryEntry>) {
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
        prefs.edit().putString("history", array.toString()).apply()
    }

    fun loadConversations(): List<Conversation> {
        val array = JSONArray(prefs.getString("conversations", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val messagesArray = obj.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until messagesArray.length()).mapNotNull { messageIndex ->
                val msg = messagesArray.optJSONObject(messageIndex) ?: return@mapNotNull null
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

    fun saveConversations(conversations: List<Conversation>) {
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
        prefs.edit().putString("conversations", array.toString()).apply()
    }
}
