package com.valenzine.whisperdroid.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valenzine.whisperdroid.MainActivity
import com.valenzine.whisperdroid.WhisperDroidApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromptDraftRestorationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun preservesUnsavedCustomPromptDraftAcrossActivityRecreation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WhisperDroidApplication
        val originalPromptId = runBlocking { app.settings.settingsFlow.first().activePromptId }
        val promptId = runBlocking { app.settings.createPrompt("Fixture prompt", "Fixture content") }
        runBlocking { app.settings.selectPrompt(promptId) }

        try {
            composeRule.onNodeWithContentDescription("Settings").performClick()
            composeRule.onNodeWithText("Fixture prompt").performClick()
            composeRule.onNode(hasSetTextAction() and hasText("Fixture prompt"))
                .performTextReplacement("Unsaved name")
            composeRule.onNode(hasSetTextAction() and hasText("Fixture content"))
                .performTextReplacement("Unsaved instruction")

            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()

            composeRule.onNode(hasSetTextAction() and hasText("Unsaved name")).assertIsDisplayed()
            composeRule.onNode(hasSetTextAction() and hasText("Unsaved instruction")).assertIsDisplayed()
            composeRule.onNodeWithText("Fixture prompt").assertIsSelected()
            assertEquals(promptId, runBlocking { app.settings.settingsFlow.first().activePromptId })
        } finally {
            runBlocking {
                app.settings.deletePrompt(promptId)
                app.settings.selectPrompt(originalPromptId)
            }
        }
    }
}
