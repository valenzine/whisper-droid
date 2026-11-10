package com.valenzine.whisperdroid.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import com.valenzine.whisperdroid.repository.DEFAULT_PROMPT_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HomePromptSwitchingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchesBetweenSavedEditsWithoutStartingAnEdit() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WhisperDroidApplication
        val originalPromptId = runBlocking { app.settings.settingsFlow.first().activePromptId }
        val entry = runBlocking {
            app.history.saveRaw(UUID.randomUUID().toString(), "test.wav", "Saved transcript.", "Test", System.currentTimeMillis())
        }
        val gist = runBlocking { app.settings.createPrompt("Test gist", "Summarize this.") }
        val unused = runBlocking { app.settings.createPrompt("Test unused", "Rephrase this.") }
        try {
            val updated = runBlocking {
                app.history.savePromptResult(entry.id, DEFAULT_PROMPT_ID, "Tidy up", "# Cleaned text", "")
                app.history.savePromptResult(entry.id, gist, "Test gist", "# Short summary", "")
                app.settings.selectPrompt(DEFAULT_PROMPT_ID)
                requireNotNull(app.history.entry(entry.id).first())
            }
            val savedResults = runBlocking { app.history.results(entry.id).first() }
            composeRule.runOnIdle { app.transcription.showHistoryEntry(updated) }

            composeRule.onNodeWithText("Transcript").performClick()
            composeRule.onNodeWithText("Saved transcript.").assertIsDisplayed()
            composeRule.onNodeWithText("Tidy up").performClick()
            onView(withText("Cleaned text")).check(matches(isDisplayed()))
            composeRule.onNodeWithContentDescription("Open full screen").performClick()
            onView(withText("Cleaned text")).check(matches(isDisplayed()))
            composeRule.onNodeWithContentDescription("Back").performClick()

            composeRule.onNodeWithText("Test gist").performClick()
            onView(withText("Short summary")).check(matches(isDisplayed()))
            composeRule.onNodeWithText("Test unused").performClick()
            composeRule.onNodeWithText("Edited").assertIsSelected()
            composeRule.onNodeWithText("No saved edit for this prompt.").assertIsDisplayed()
            composeRule.onNodeWithText("Edit transcript").assertIsDisplayed()
            composeRule.onNodeWithText("Tidy up").performClick()
            onView(withText("Cleaned text")).check(matches(isDisplayed()))

            assertEquals(savedResults, runBlocking { app.history.results(entry.id).first() })
        } finally {
            runBlocking {
                app.history.delete(entry.id)
                app.settings.deletePrompt(gist)
                app.settings.deletePrompt(unused)
                app.settings.selectPrompt(originalPromptId)
            }
        }
    }
}
