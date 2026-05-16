package com.justspeaktoit.android

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MainActivityParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun transcribeFlow_recordsCopiesAndShowsHistory() {
        composeRule.onNodeWithTag("transcribeScreen").assertExists()
        composeRule.onNodeWithTag("recordButton").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodes(hasText("This is a live Android transcription", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("recordButton").performClick()
        composeRule.onNodeWithTag("historyButton").performClick()
        composeRule.onNodeWithTag("historyScreen").assertExists()
        composeRule.onNodeWithText("Transcriptions").assertExists()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodes(hasText("This is a live Android transcription", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun settingsFlow_exposesApiPostProcessingAndOpenClawConfiguration() {
        composeRule.onNodeWithTag("settingsButton").performClick()
        composeRule.onNodeWithTag("settingsScreen").assertExists()
        composeRule.onNodeWithText("API Keys").assertExists()
        composeRule.onNodeWithTag("manageKeysButton").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodes(hasText("Deepgram API Key"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("apiKeysScreen").assertExists()
        composeRule.onNodeWithText("Deepgram API Key").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
    }

    @Test
    fun openClawFlow_createsConversationAndReceivesAssistantResponse() {
        composeRule.onNodeWithTag("tabOpenClaw").performClick()
        composeRule.onNodeWithTag("openClawListScreen").assertExists()
        composeRule.onNodeWithTag("newConversationButton").performClick()
        composeRule.onNodeWithTag("openClawChatScreen").assertExists()
        composeRule.onNodeWithTag("openClawInput").performTextInput("hello android")
        composeRule.onNodeWithTag("openClawSendButton").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodes(hasText("OpenClaw heard: hello android"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("assistantMessage").assertExists()
    }
}
