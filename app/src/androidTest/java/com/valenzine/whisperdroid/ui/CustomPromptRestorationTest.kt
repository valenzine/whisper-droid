package com.valenzine.whisperdroid.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valenzine.whisperdroid.MainActivity
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.repository.FormattedTranscript
import com.valenzine.whisperdroid.repository.SettingsGateway
import com.valenzine.whisperdroid.repository.StagedAudio
import com.valenzine.whisperdroid.repository.TranscriptionGateway
import com.valenzine.whisperdroid.ui.theme.WhisperDroidTheme
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CustomPromptRestorationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun customEditSurvivesRecreationAndSettingsWithoutAnotherRequestOrPersistence() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WhisperDroidApplication
        val settingsBefore = runBlocking { app.settings.settingsFlow.first() }
        val entry = runBlocking {
            app.history.saveRaw(UUID.randomUUID().toString(), "custom-lifecycle.wav", "Raw fixture.", "Test", 1L)
        }
        val gateway = Gateway()
        val settings = object : SettingsGateway {
            override val settingsFlow = MutableStateFlow(settingsBefore.copy(apiKey = "test-key"))
        }
        val model = TranscriptionViewModel(gateway, settings, app.history)
        fun installScreen() {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.setContent { TestNavigation(app, model) }
            }
            composeRule.waitForIdle()
        }
        try {
            composeRule.runOnIdle { model.showHistoryEntry(entry) }
            installScreen()
            composeRule.onNodeWithText("Custom").performClick()
            composeRule.onNode(hasSetTextAction() and hasText("Processing instruction"))
                .performTextReplacement("Return a temporary custom edit.")
            composeRule.onNodeWithText("Run").performClick()
            composeRule.waitUntil(5_000) { !model.screenState.value.isBusy }
            assertCustomResult()

            composeRule.activityRule.scenario.recreate()
            // Reattach the same process-owned model to the recreated screen, as the app does.
            installScreen()
            assertCustomResult()
            composeRule.onNodeWithContentDescription("Settings").performClick()
            composeRule.onNodeWithText("Editing prompts").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Back").performClick()
            assertCustomResult()

            composeRule.onNodeWithText("Custom").performClick()
            composeRule.onNode(hasSetTextAction() and hasText("Return a temporary custom edit."))
                .assertIsDisplayed()
            composeRule.onNodeWithText("Cancel").performClick()
            assertCustomResult()
            assertEquals(1, gateway.formatCalls)
            assertEquals(settingsBefore, runBlocking { app.settings.settingsFlow.first() })
            assertEquals(settingsBefore.copy(apiKey = "test-key"), settings.settingsFlow.value)
            assertEquals(emptyList<Any>(), runBlocking { app.history.results(entry.id).first() })
            assertEquals(entry, runBlocking { app.history.entry(entry.id).first() })
        } finally {
            runBlocking { app.history.delete(entry.id) }
        }
    }

    private fun assertCustomResult() {
        composeRule.onNodeWithText("Custom").assertIsSelected()
        composeRule.onNodeWithText("Edited").assertIsSelected()
        onView(withText("A temporary custom edit.")).check(matches(isDisplayed()))
    }

    @Composable private fun TestNavigation(app: WhisperDroidApplication, model: TranscriptionViewModel) {
        WhisperDroidTheme {
            val navigation = rememberNavController()
            NavHost(navigation, startDestination = "main") {
                composable("main") { MainScreen(navigation, app, model) { _, _, _ -> } }
                composable("settings") { SettingsScreen() }
            }
        }
    }

    private class Gateway : TranscriptionGateway {
        var formatCalls = 0
        override suspend fun transcribeStaged(stagedAudio: StagedAudio, apiKey: String, model: String, language: String?, onProgress: ((String) -> Unit)?): String =
            error("Restoring a custom edit must not transcribe audio")
        override suspend fun formatText(text: String, apiKey: String, prompt: String, model: String): FormattedTranscript {
            formatCalls++
            return FormattedTranscript("A temporary custom edit.", "Temporary summary")
        }
    }
}
