package com.valenzine.whisperdroid.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valenzine.whisperdroid.MainActivity
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.repository.StagedAudio
import com.valenzine.whisperdroid.viewmodel.TranscriptionUiState
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAudioDialogTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun cancelLongSharedAudioRestoresThePreviousTranscript() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WhisperDroidApplication
        composeRule.waitForIdle()
        val previous = app.transcription.screenState.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val job = scope.launch {
                app.transcription.processStagedAudio(
                    StagedAudio(File("not-uploaded"), "long-audio.wav", "new-long-audio", durationMillis = 300_001, isShared = true)
                )
            }
            composeRule.waitUntil(5_000) { app.transcription.screenState.value.phase is TranscriptionUiState.AwaitingAudioConfirmation }
            composeRule.onNodeWithText("Transcribe shared audio?").assertIsDisplayed()
            composeRule.onNodeWithText("Cancel").performClick()
            composeRule.waitUntil(5_000) { job.isCompleted }
            assertEquals(previous, app.transcription.screenState.value)
        } finally { scope.cancel() }
    }
}
