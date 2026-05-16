package com.justspeaktoit.android

import org.json.JSONArray
import org.json.JSONObject

object OpenClawProtocol {
    fun normaliseGatewayUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        return when {
            trimmed.startsWith("ws://") || trimmed.startsWith("wss://") -> trimmed
            trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
            trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
            else -> "ws://$trimmed"
        }
    }

    fun shouldTriggerKeywordAcknowledge(
        transcript: String,
        keyword: String,
        enabled: Boolean,
        conversationMode: Boolean
    ): Boolean {
        if (!enabled || !conversationMode) return false
        val trimmedKeyword = keyword.trim()
        if (trimmedKeyword.isEmpty()) return false
        val normalised = transcript.trim { it.isWhitespace() || it.isPunctuation() }.lowercase()
        val lowerKeyword = trimmedKeyword.lowercase()
        if (!normalised.endsWith(lowerKeyword)) return false
        val prefixEnd = normalised.length - lowerKeyword.length
        if (prefixEnd <= 0) return true
        val charBefore = normalised[prefixEnd - 1]
        return charBefore.isWhitespace() || charBefore.isPunctuation()
    }

    fun removeAcknowledgementKeyword(text: String, keyword: String): String {
        val trimmed = text.trim()
        val trimmedKeyword = keyword.trim()
        if (trimmed.isEmpty() || trimmedKeyword.isEmpty()) return trimmed
        val escaped = Regex.escape(trimmedKeyword)
        return Regex("[\\s\\p{Punct}]*$escaped[\\s\\p{Punct}]*$", RegexOption.IGNORE_CASE)
            .replace(trimmed, "")
            .trim()
    }

    fun chatPayload(sessionKey: String, message: String): JSONObject {
        return JSONObject()
            .put("type", "chat")
            .put("session_key", sessionKey)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "user").put("content", message))
            )
            .put("stream", true)
    }

    fun extractAssistantContent(message: String): String {
        val objectMessage = runCatching { JSONObject(message) }.getOrNull() ?: return message
        return objectMessage.optString("content")
            .ifBlank { objectMessage.optString("delta") }
            .ifBlank { objectMessage.optJSONObject("message")?.optString("content").orEmpty() }
    }

    fun isCompletionMessage(message: String): Boolean {
        val objectMessage = runCatching { JSONObject(message) }.getOrNull() ?: return false
        return objectMessage.optBoolean("done") ||
            objectMessage.optBoolean("completed") ||
            objectMessage.optString("type").equals("done", ignoreCase = true) ||
            objectMessage.optString("type").equals("complete", ignoreCase = true)
    }

    private fun Char.isPunctuation(): Boolean {
        return this in listOf('.', ',', '!', '?', ';', ':', '-', '(', ')', '[', ']', '"', '\'')
    }
}

object TranscriptFormatter {
    fun polish(raw: String): String {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return ""
        val capitalised = trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (capitalised.last() in ".!?") capitalised else "$capitalised."
    }

    fun lastSentence(text: String): String {
        return text.split(Regex("[.!?]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull()
            .orEmpty()
    }
}

object AndroidParityChecklist {
    val features = listOf(
        "Two-tab navigation: Transcribe and OpenClaw",
        "Live transcription screen with partial text, copy, polish, history, and settings",
        "Provider selection with Android Speech default and Deepgram, ElevenLabs, OpenAI cloud options",
        "Secure API key management for Deepgram, ElevenLabs, OpenRouter, OpenAI, and OpenClaw token",
        "History list with stats, delete, copy, and local-first persistence",
        "Post-processing with model/prompt settings and deterministic validation provider",
        "OpenClaw conversation list and chat with streaming-style assistant responses",
        "Conversation mode with auto-resume, tap/headset/keyword acknowledgement equivalents",
        "Deepgram TTS settings and low-latency speech controls",
        "Foreground recording notification as Android Live Activity equivalent",
        "Launcher shortcut and intent action as Android quick action/AppIntent equivalent",
        "Recordings, Send to Mac, Privacy, Debugging, and About settings surfaces"
    )
}
