package com.justspeaktoit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParityLogicTest {
    @Test
    fun normaliseGatewayUrl_addsWebSocketSchemeForBareHost() {
        assertEquals("ws://gateway.example.com:18789", OpenClawProtocol.normaliseGatewayUrl("gateway.example.com:18789"))
    }

    @Test
    fun normaliseGatewayUrl_rewritesHttpsToSecureWebSocket() {
        assertEquals("wss://gateway.example.com/socket", OpenClawProtocol.normaliseGatewayUrl("https://gateway.example.com/socket"))
    }

    @Test
    fun keywordAcknowledge_matchesTrailingWholePhrase() {
        assertTrue(
            OpenClawProtocol.shouldTriggerKeywordAcknowledge(
                transcript = "send this now, over.",
                keyword = "over",
                enabled = true,
                conversationMode = true
            )
        )
    }

    @Test
    fun keywordAcknowledge_doesNotMatchPartialWord() {
        assertFalse(
            OpenClawProtocol.shouldTriggerKeywordAcknowledge(
                transcript = "send this leftover",
                keyword = "over",
                enabled = true,
                conversationMode = true
            )
        )
    }

    @Test
    fun removeAcknowledgementKeyword_stripsTrailingControlPhrase() {
        assertEquals("send this now", OpenClawProtocol.removeAcknowledgementKeyword("send this now, over.", "over"))
    }

    @Test
    fun chatPayload_matchesOpenClawStreamingShape() {
        val payload = OpenClawProtocol.chatPayload("speak-android:voice:test", "hello")
        assertEquals("chat", payload.getString("type"))
        assertEquals("speak-android:voice:test", payload.getString("session_key"))
        assertTrue(payload.getBoolean("stream"))
        assertEquals("hello", payload.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test
    fun extractAssistantContent_readsGatewayMessageShapes() {
        assertEquals("hello", OpenClawProtocol.extractAssistantContent("""{"message":{"content":"hello"}}"""))
        assertEquals("chunk", OpenClawProtocol.extractAssistantContent("""{"delta":"chunk"}"""))
        assertTrue(OpenClawProtocol.isCompletionMessage("""{"type":"complete"}"""))
    }

    @Test
    fun transcriptFormatter_polishesSpacingCasingAndPunctuation() {
        assertEquals("Hello android.", TranscriptFormatter.polish("  hello   android "))
    }

    @Test
    fun phraseStart_trimsConfiguredTriggerOnlyWhenExplicitlyEnabled() {
        assertEquals(
            PhraseStartResult(true, "send the update"),
            VoiceStartPhraseMatcher.trimTrigger("Start speaking, send the update", "start speaking", enabled = true)
        )
        assertEquals(
            PhraseStartResult(false, "Start speaking, send the update"),
            VoiceStartPhraseMatcher.trimTrigger("Start speaking, send the update", "start speaking", enabled = false)
        )
    }

    @Test
    fun voiceCommandProcessor_handlesFillerCommandsAndSnippets() {
        val processed = VoiceCommandProcessor.process("um insert my email new paragraph thanks")
        assertEquals("Hello@justspeaktoit.app\n\nthanks.", processed.text)
        assertTrue(processed.appliedCommands.contains("insert my email"))
        assertTrue(processed.appliedCommands.contains("new paragraph"))
        assertTrue(VoiceCommandProcessor.process("scratch that").discarded)
    }

    @Test
    fun textInsertionPlanner_preservesExistingTextAtCursorAndSelection() {
        assertEquals(
            PlannedInsertion("Hello Flow Android", 10),
            TextInsertionPlanner.insertAtSelection("Hello Android", 5, 5, "Flow")
        )
        assertEquals(
            PlannedInsertion("Hello Flow", 10),
            TextInsertionPlanner.insertAtSelection("Hello Android", 6, 13, "Flow")
        )
    }

    @Test
    fun parityChecklist_coversMajorGoalSurfaces() {
        val text = AndroidParityChecklist.features.joinToString("\n")
        listOf("Transcribe", "OpenClaw", "API key", "History", "Post-processing", "Foreground", "Launcher", "Flow Bubble", "accessibility").forEach {
            assertTrue("Expected checklist to include $it", text.contains(it, ignoreCase = true))
        }
    }
}
