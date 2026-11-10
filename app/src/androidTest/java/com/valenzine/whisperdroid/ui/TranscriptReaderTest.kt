package com.valenzine.whisperdroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptReaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRawTranscription() {
        composeRule.setContent {
            MaterialTheme {
                TranscriptReader(text = "Raw dictation", formatted = false, onBack = {})
            }
        }

        composeRule.onNodeWithText("Raw transcription").assertIsDisplayed()
        composeRule.onNodeWithText("Raw dictation").assertIsDisplayed()
    }

    @Test
    fun rendersFormattedTextAndReturnsToCaller() {
        var returned = false
        composeRule.setContent {
            MaterialTheme {
                TranscriptReader(text = "Formatted paragraph.", formatted = true) { returned = true }
            }
        }

        composeRule.onNodeWithText("Formatted text").assertIsDisplayed()
        composeRule.onNodeWithText("Formatted paragraph.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.runOnIdle { assertTrue(returned) }
    }

    @Test
    fun rendersLoadingSeparatelyFromUnavailableText() {
        composeRule.setContent {
            MaterialTheme { TranscriptReaderLoading(formatted = false, onBack = {}) }
        }

        composeRule.onNodeWithText("Loading transcript…").assertIsDisplayed()
    }

    @Test
    fun transcriptPaneHasCopyAndExpandActions() {
        var expanded = false
        composeRule.setContent {
            MaterialTheme {
                TranscriptPane("Raw dictation", "Raw transcription", "No text", 150.dp) { expanded = true }
            }
        }
        composeRule.onNodeWithContentDescription("Copy transcript").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open full screen").performClick()
        composeRule.runOnIdle { assertTrue(expanded) }
    }

}
