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

data class PhraseStartResult(
    val triggered: Boolean,
    val text: String
)

object VoiceStartPhraseMatcher {
    fun trimTrigger(transcript: String, phrase: String, enabled: Boolean): PhraseStartResult {
        val cleanedTranscript = transcript.trim()
        val cleanedPhrase = phrase.trim()
        if (!enabled || cleanedTranscript.isEmpty() || cleanedPhrase.isEmpty()) {
            return PhraseStartResult(triggered = false, text = cleanedTranscript)
        }
        val pattern = Regex("^${Regex.escape(cleanedPhrase)}[\\s\\p{Punct}]*", RegexOption.IGNORE_CASE)
        val trimmed = pattern.replace(cleanedTranscript, "").trim()
        return PhraseStartResult(triggered = trimmed != cleanedTranscript, text = trimmed)
    }
}

data class VoiceCommandResult(
    val text: String,
    val discarded: Boolean = false,
    val appliedCommands: List<String> = emptyList()
)

object VoiceCommandProcessor {
    private val fillerPattern = Regex("\\b(um+|uh+|erm+|you know|like)\\b[\\s,]*", RegexOption.IGNORE_CASE)
    private val snippets = mapOf(
        "insert my email" to "hello@justspeaktoit.app",
        "signature block" to "Best,\nJust Speak to It",
        "today's date" to "today"
    )

    fun process(raw: String): VoiceCommandResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return VoiceCommandResult("")
        if (Regex("(^|\\b)scratch that[.!?\\s]*$", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
            return VoiceCommandResult(text = "", discarded = true, appliedCommands = listOf("scratch that"))
        }
        var working = fillerPattern.replace(trimmed, "")
        val commands = mutableListOf<String>()
        snippets.forEach { (spoken, replacement) ->
            val pattern = Regex("\\b${Regex.escape(spoken)}\\b", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(working)) {
                working = pattern.replace(working, replacement)
                commands.add(spoken)
            }
        }
        working = Regex("\\bnew paragraph\\b", RegexOption.IGNORE_CASE).replace(working, "\n\n")
        if (working.contains("\n\n")) commands.add("new paragraph")
        working = Regex("\\bnew line\\b", RegexOption.IGNORE_CASE).replace(working, "\n")
        if (working.contains("\n")) commands.add("new line")
        return VoiceCommandResult(text = polishCommandText(working), appliedCommands = commands.distinct())
    }

    private fun polishCommandText(raw: String): String {
        val trimmed = raw
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .trim()
        if (trimmed.isEmpty()) return ""
        val capitalised = trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (capitalised.last() in ".!?\n") capitalised else "$capitalised."
    }
}

data class PlannedInsertion(
    val text: String,
    val cursor: Int
)

object TextInsertionPlanner {
    fun insertAtSelection(existing: String, selectionStart: Int, selectionEnd: Int, insertion: String): PlannedInsertion {
        val safeStart = selectionStart.coerceIn(0, existing.length)
        val safeEnd = selectionEnd.coerceIn(0, existing.length)
        val start = minOf(safeStart, safeEnd)
        val end = maxOf(safeStart, safeEnd)
        val separator = if (needsSeparator(existing, start, insertion)) " " else ""
        val replacement = separator + insertion
        val next = existing.replaceRange(start, end, replacement)
        return PlannedInsertion(text = next, cursor = start + replacement.length)
    }

    private fun needsSeparator(existing: String, start: Int, insertion: String): Boolean {
        if (existing.isEmpty() || start == 0 || insertion.startsWith("\n") || insertion.startsWith(" ")) return false
        val previous = existing.getOrNull(start - 1) ?: return false
        return !previous.isWhitespace() && previous !in listOf('\n', '(', '[', '{')
    }
}

enum class VoiceInsertionMethod { Accessibility, ClipboardFallback, Failed }

data class VoiceInsertionResult(
    val method: VoiceInsertionMethod,
    val message: String,
    val insertedText: String
) {
    val success: Boolean get() = method != VoiceInsertionMethod.Failed
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
        "Recordings, Send to Mac, Privacy, Debugging, and About settings surfaces",
        "Wispr-style Flow Bubble with overlay permission, accessibility insertion, phrase-start, clipboard fallback, and spoken commands"
    )
}
