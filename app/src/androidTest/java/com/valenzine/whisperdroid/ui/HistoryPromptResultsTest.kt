package com.valenzine.whisperdroid.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valenzine.whisperdroid.MainActivity
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.history.PREVIOUS_RESULT_PROMPT_ID
import com.valenzine.whisperdroid.repository.DEFAULT_PROMPT_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryPromptResultsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensAndDeletesOnePromptResultWithoutChangingTheRawTranscript() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WhisperDroidApplication
        val history = app.history
        val entry = runBlocking {
            history.saveRaw(
                UUID.randomUUID().toString(),
                "test.wav",
                "Saved raw transcript.",
                "Test fixture",
                System.currentTimeMillis()
            )
        }
        try {
            runBlocking {
                history.savePromptResult(entry.id, PREVIOUS_RESULT_PROMPT_ID, "Previous result", "Older formatted output.", "")
                history.savePromptResult(entry.id, DEFAULT_PROMPT_ID, "Tidy up", "Output from Tidy up.", "A test summary.")
            }

            composeRule.onNodeWithText("History").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("A test summary.").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText("Edits").onFirst().performClick()
            composeRule.onNodeWithText("Earlier edit").performClick()
            composeRule.onNodeWithText("Prompt unknown").assertIsDisplayed()
            onView(withText("Older formatted output.")).check(matches(isDisplayed()))
            composeRule.onNodeWithText("Tidy up").performClick()
            onView(withText("Output from Tidy up.")).check(matches(isDisplayed()))
            composeRule.onNodeWithContentDescription("Delete selected edit").performClick()
            composeRule.onNodeWithText("Delete").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("No saved edit for this prompt.").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Earlier edit").assertIsDisplayed()

            assertEquals("Saved raw transcript.", runBlocking { history.entry(entry.id).first()?.rawText })
            assertEquals("Older formatted output.", runBlocking { history.entry(entry.id).first()?.formattedText })
        } finally {
            runBlocking { history.delete(entry.id) }
        }
    }
}
