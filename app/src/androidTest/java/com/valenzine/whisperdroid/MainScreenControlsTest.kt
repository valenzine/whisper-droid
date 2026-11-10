package com.valenzine.whisperdroid

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
class MainScreenControlsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun processingControlsExposePromptPillsAndAutoFormatStatus() {
        composeRule.onNodeWithText("Processing").assertIsDisplayed()
        composeRule.onNodeWithText("Format manually when ready").assertIsDisplayed()
        composeRule.onNodeWithText("Tidy up").assertIsDisplayed()
    }

    @Test
    fun languageAndAudioPickerShareOneRow() {
        val language = composeRule.onNode(hasText("Language") and hasClickAction())
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val audioPicker = composeRule.onNodeWithContentDescription("Select audio file")
            .assertIsDisplayed()
            .assertHasClickAction()
            .getUnclippedBoundsInRoot()

        val audioCenter = (audioPicker.top + audioPicker.bottom) / 2f
        assertTrue(audioCenter > language.top && audioCenter < language.bottom)
        assertTrue(language.right <= audioPicker.left || audioPicker.right <= language.left)
        assertTrue(audioPicker.right - audioPicker.left >= 48.dp)
        assertTrue(audioPicker.bottom - audioPicker.top >= 48.dp)
    }

    @Test
    fun settingsOpensPromptManagement() {
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Processing prompts").assertIsDisplayed()
        composeRule.onNodeWithText("Save prompt").assertIsDisplayed()
    }
}
