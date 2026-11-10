package com.valenzine.whisperdroid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenControlsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun processingControlsExposePromptPillsAndAutoFormatStatus() {
        composeRule.onNodeWithText("Processing").assertIsDisplayed()
        composeRule.onNodeWithText("Format manually when ready").assertIsDisplayed()
        composeRule.onNodeWithText("Default prompt").assertIsDisplayed()
    }

    @Test
    fun settingsOpensPromptManagement() {
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Processing prompts").assertIsDisplayed()
        composeRule.onNodeWithText("Save prompt").assertIsDisplayed()
    }
}
