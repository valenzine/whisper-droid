package com.valenzine.whisperdroid

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valenzine.whisperdroid.repository.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenControlsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun promptPillsStayOnMainScreen() {
        composeRule.onNodeWithText("Tidy up").assertIsDisplayed()
        composeRule.onNodeWithText("Custom").assertIsDisplayed()
        composeRule.onNodeWithText("Edit new transcripts automatically").assertDoesNotExist()
    }

    @Test
    fun customPromptDialogRequiresTranscriptAndCancelLeavesSettingsUntouched() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WhisperDroidApplication
        val before: AppSettings = runBlocking { app.settings.settingsFlow.first() }

        composeRule.onNodeWithText("Custom").performClick()
        composeRule.onNodeWithText("Custom prompt").assertIsDisplayed()
        val run = composeRule.onNodeWithText("Run")
        run.assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction() and hasText("Processing instruction"))
            .performTextReplacement("Return a haiku.")
        run.assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Custom prompt").assertDoesNotExist()

        assertTrue(before == runBlocking { app.settings.settingsFlow.first() })
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
    fun settingsOpensPromptManagementWithAnEmptyNewDraft() {
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Editing prompts").assertIsDisplayed()
        composeRule.onNodeWithText("Edit new transcripts automatically").assertIsDisplayed()
        composeRule.onNodeWithText("New").performClick()
        val instruction = composeRule.onNode(hasSetTextAction() and hasText("Processing instruction"))
        instruction.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))

        composeRule.onNode(hasSetTextAction() and hasText("Prompt name")).performTextReplacement("New prompt")
        val save = composeRule.onNodeWithText("Save prompt")
        save.assertIsNotEnabled()
        instruction.performTextReplacement("Summarize in one sentence.")
        save.assertIsEnabled()
    }
}
