package com.valenzine.whisperdroid.viewmodel

import com.valenzine.whisperdroid.repository.AppSettings
import com.valenzine.whisperdroid.repository.FormattedTranscript
import com.valenzine.whisperdroid.repository.SettingsGateway
import com.valenzine.whisperdroid.repository.StagedAudio
import com.valenzine.whisperdroid.repository.TranscriptionGateway
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedAudioConfirmationTest {
    private val dispatcher = StandardTestDispatcher()
    private val gateway = Gateway()
    private val settings = object : SettingsGateway {
        override val settingsFlow = MutableStateFlow(AppSettings(apiKey = "test-key", autoProcess = true))
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun exactlyFiveMinutesProcessesAutomatically() = runTest {
        TranscriptionViewModel(gateway, settings).processStagedAudio(audio(300_000))
        assertEquals(1, gateway.uploads)
        assertEquals(1, gateway.edits)
    }

    @Test fun longerAudioWaitsBeforeUploadingAndThenProcessesOnApproval() = runTest {
        val model = TranscriptionViewModel(gateway, settings)
        launch { model.processStagedAudio(audio(300_001)) }
        runCurrent()
        assertTrue(model.screenState.value.phase is TranscriptionUiState.AwaitingAudioConfirmation)
        assertEquals(0, gateway.uploads)
        assertEquals(0, gateway.edits)
        model.confirmSharedAudio(true)
        advanceUntilIdle()
        assertEquals(1, gateway.uploads)
        assertEquals(1, gateway.edits)
    }

    @Test fun cancelUnknownDurationPreventsUploadAndEditingAndAllowsNextAudio() = runTest {
        val model = TranscriptionViewModel(gateway, settings)
        launch { model.processStagedAudio(audio(null)) }
        runCurrent()
        assertTrue(model.screenState.value.phase is TranscriptionUiState.AwaitingAudioConfirmation)
        model.confirmSharedAudio(false)
        advanceUntilIdle()
        assertEquals(0, gateway.uploads)
        assertEquals(0, gateway.edits)
        assertEquals(TranscriptionScreenState(), model.screenState.value)
        model.processStagedAudio(audio(1_000))
        assertEquals(1, gateway.uploads)
    }

    @Test fun pickerAndExplicitRetranscriptionDoNotRequireShareApproval() = runTest {
        TranscriptionViewModel(gateway, settings).processStagedAudio(audio(null).copy(isShared = false))
        assertEquals(1, gateway.uploads)
    }

    private fun audio(duration: Long?) = StagedAudio(File("audio"), "audio", "hash", durationMillis = duration, isShared = true)

    private class Gateway : TranscriptionGateway {
        var uploads = 0
        var edits = 0
        override suspend fun transcribeStaged(stagedAudio: StagedAudio, apiKey: String, model: String, language: String?, onProgress: ((String) -> Unit)?): String {
            uploads++
            return "transcript"
        }
        override suspend fun formatText(text: String, apiKey: String, prompt: String, model: String): FormattedTranscript {
            edits++
            return FormattedTranscript("edited", "summary")
        }
    }
}
