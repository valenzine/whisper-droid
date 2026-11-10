package com.valenzine.whisperdroid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
    fun processingControlsExposeTheActivePromptChooser() {
        composeRule.onNodeWithText("Automatically process after transcription").assertIsDisplayed()
        composeRule.onNodeWithText("Active processing prompt").assertIsDisplayed()
        composeRule.onNodeWithText("Default prompt").assertIsDisplayed()
        composeRule.onNodeWithText("New prompt").assertIsDisplayed()
    }
}
