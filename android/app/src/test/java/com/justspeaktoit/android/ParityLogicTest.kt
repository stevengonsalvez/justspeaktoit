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
    fun transcriptFormatter_polishesSpacingCasingAndPunctuation() {
        assertEquals("Hello android.", TranscriptFormatter.polish("  hello   android "))
    }

    @Test
    fun parityChecklist_coversMajorGoalSurfaces() {
        val text = AndroidParityChecklist.features.joinToString("\n")
        listOf("Transcribe", "OpenClaw", "API key", "History", "Post-processing", "Foreground", "Launcher").forEach {
            assertTrue("Expected checklist to include $it", text.contains(it, ignoreCase = true))
        }
    }
}
