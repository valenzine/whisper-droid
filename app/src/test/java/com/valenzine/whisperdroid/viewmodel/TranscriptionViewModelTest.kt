package com.valenzine.whisperdroid.viewmodel

import com.valenzine.whisperdroid.repository.AppSettings
import com.valenzine.whisperdroid.repository.SettingsGateway
import com.valenzine.whisperdroid.repository.TranscriptionGateway
import com.valenzine.whisperdroid.repository.StagedAudio
import com.valenzine.whisperdroid.repository.FormattedTranscript
import com.valenzine.whisperdroid.repository.SavedPrompt
import java.io.File
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val audio = StagedAudio(File("test-audio"), "test-audio", "test-fingerprint")

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun stagedAudioWaitsUntilAnApiKeyIsSaved() = runTest {
        val gateway = RecordingGateway(CompletableDeferred("raw dictation"))
        val settings = FakeSettings(apiKey = "")
        val viewModel = TranscriptionViewModel(gateway, settings)
        launch { viewModel.processStagedAudio(audio) }
        runCurrent()

        assertEquals(TranscriptionUiState.WaitingForApiKey, viewModel.screenState.value.phase)
        assertEquals(0, gateway.transcriptionCalls)

        settings.setApiKey("test-key")
        advanceUntilIdle()

        assertEquals("raw dictation", viewModel.screenState.value.transcription)
        assertEquals(1, gateway.transcriptionCalls)
    }

    @Test
    fun autoProcessingOffKeepsTranscriptWithoutFormatting() = runTest {
        val gateway = RecordingGateway(transcriptionResult = CompletableDeferred("raw dictation"))
        val viewModel = TranscriptionViewModel(gateway, FakeSettings(autoProcess = false))
        viewModel.processStagedAudio(audio)

        assertEquals("raw dictation", viewModel.screenState.value.transcription)
        assertEquals("", viewModel.screenState.value.formattedText)
        assertEquals(0, gateway.formatCalls)
        assertEquals("gpt-transcribe", gateway.lastTranscriptionModel)
    }

    @Test
    fun autoProcessingUsesLatestPromptAndSelectedLlm() = runTest {
        val transcription = CompletableDeferred<String>()
        val settings = FakeSettings(autoProcess = true)
        val gateway = RecordingGateway(transcriptionResult = transcription)
        val viewModel = TranscriptionViewModel(gateway, settings)

        launch { viewModel.processStagedAudio(audio) }
        runCurrent()
        settings.update(prompt = "Use short paragraphs", llmModel = "test-formatting-model")
        transcription.complete("raw dictation")
        advanceUntilIdle()

        assertEquals(1, gateway.formatCalls)
        assertEquals("Use short paragraphs", gateway.lastPrompt)
        assertEquals("test-formatting-model", gateway.lastLlmModel)
        assertEquals("raw dictation", gateway.lastFormattedSource)
    }

    @Test
    fun manualEditUsesTheTappedPromptEvenWhenThePersistedSelectionDiffers() = runTest {
        val gateway = RecordingGateway(CompletableDeferred("raw dictation"))
        val settings = FakeSettings()
        settings.addPrompt(SavedPrompt("gist", "Gist", "Write a short gist."))
        val viewModel = TranscriptionViewModel(gateway, settings)
        viewModel.processStagedAudio(audio)

        assertTrue(viewModel.formatText(promptId = "gist"))
        advanceUntilIdle()

        assertEquals("Write a short gist.", gateway.lastPrompt)
        assertEquals("test-prompt", settings.settingsFlow.value.activePromptId)
        assertEquals(1, gateway.formatCalls)
    }

    @Test
    fun customPromptFormatsWithoutChangingSavedPromptSelection() = runTest {
        val gateway = RecordingGateway(CompletableDeferred("raw dictation"))
        val settings = FakeSettings()
        val viewModel = TranscriptionViewModel(gateway, settings)
        viewModel.processStagedAudio(audio)

        assertTrue(viewModel.formatText(customPrompt = "Return three bullets."))
        advanceUntilIdle()

        assertEquals("Return three bullets.", gateway.lastPrompt)
        assertEquals("test-prompt", settings.settingsFlow.value.activePromptId)
        assertEquals(listOf("test-prompt"), settings.settingsFlow.value.prompts.map { it.id })
        assertTrue(viewModel.customPromptState.value.isSelected)
        assertTrue(viewModel.customPromptState.value.belongsTo(viewModel.screenState.value))
        assertEquals("Return three bullets.", viewModel.customPromptState.value.prompt)

        assertTrue(viewModel.formatText())
        advanceUntilIdle()
        assertFalse(viewModel.customPromptState.value.isSelected)
    }

    @Test
    fun customPromptStateClearsForANewTranscription() = runTest {
        val gateway = RecordingGateway(CompletableDeferred("raw dictation"))
        val viewModel = TranscriptionViewModel(gateway, FakeSettings())
        viewModel.processStagedAudio(audio)
        assertTrue(viewModel.formatText(customPrompt = "Return three bullets."))
        advanceUntilIdle()

        viewModel.processStagedAudio(audio.copy(sha256 = "next-fingerprint"))

        assertFalse(viewModel.customPromptState.value.isSelected)
        assertFalse(viewModel.customPromptState.value.belongsTo(viewModel.screenState.value))
    }

    @Test
    fun formattingIsRejectedWhenTranscriptIsEmpty() = runTest {
        val gateway = RecordingGateway()
        val viewModel = TranscriptionViewModel(gateway, FakeSettings())

        assertFalse(viewModel.formatText())
        advanceUntilIdle()

        assertEquals(0, gateway.formatCalls)
    }

    @Test
    fun formattingFailurePreservesTranscriptAndPreviousFormattedOutput() = runTest {
        val gateway = RecordingGateway(transcriptionResult = CompletableDeferred("raw dictation"))
        val viewModel = TranscriptionViewModel(gateway, FakeSettings())
        viewModel.processStagedAudio(audio)
        assertTrue(viewModel.formatText())
        assertFalse(viewModel.formatText())
        advanceUntilIdle()

        gateway.formatError = IllegalStateException("formatting unavailable")
        assertTrue(viewModel.formatText())
        advanceUntilIdle()

        assertEquals("raw dictation", viewModel.screenState.value.transcription)
        assertEquals("edited dictation", viewModel.screenState.value.formattedText)
        assertEquals("formatting unavailable", viewModel.screenState.value.error)
    }

    @Test
    fun laterTranscriptionFailurePreservesPreviousTranscriptAndOutput() = runTest {
        val gateway = RecordingGateway(CompletableDeferred("raw dictation"))
        val viewModel = TranscriptionViewModel(gateway, FakeSettings())
        viewModel.processStagedAudio(audio)
        viewModel.formatText()
        advanceUntilIdle()

        gateway.transcriptionError = IllegalStateException("upload unavailable")
        viewModel.processStagedAudio(audio)

        assertEquals("raw dictation", viewModel.screenState.value.transcription)
        assertEquals("edited dictation", viewModel.screenState.value.formattedText)
        assertEquals("upload unavailable", viewModel.screenState.value.error)
    }

    @Test
    fun offlineTranscriptionFailurePreservesTheCurrentTranscript() = runTest {
        val gateway = RecordingGateway(CompletableDeferred("raw dictation"))
        val viewModel = TranscriptionViewModel(gateway, FakeSettings())
        viewModel.processStagedAudio(audio)
        viewModel.formatText()
        advanceUntilIdle()

        gateway.transcriptionError = UnknownHostException("api.openai.com")
        viewModel.processStagedAudio(audio)

        assertEquals("raw dictation", viewModel.screenState.value.transcription)
        assertEquals("edited dictation", viewModel.screenState.value.formattedText)
        assertEquals(
            "No internet connection. Turn off Airplane mode or connect to Wi-Fi or mobile data, then try again.",
            viewModel.screenState.value.error
        )
    }

    @Test
    fun offlineFormattingFailurePreservesTheCurrentTexts() = runTest {
        val gateway = RecordingGateway(CompletableDeferred("raw dictation"))
        val viewModel = TranscriptionViewModel(gateway, FakeSettings())
        viewModel.processStagedAudio(audio)
        advanceUntilIdle()

        gateway.formatError = UnknownHostException("api.openai.com")
        assertTrue(viewModel.formatText())
        advanceUntilIdle()

        assertEquals("raw dictation", viewModel.screenState.value.transcription)
        assertEquals("", viewModel.screenState.value.formattedText)
        assertEquals(
            "No internet connection. Turn off Airplane mode or connect to Wi-Fi or mobile data, then try again.",
            viewModel.screenState.value.error
        )
    }

    private class FakeSettings(
        autoProcess: Boolean = false,
        apiKey: String = "test-key"
    ) : SettingsGateway {
        private val promptId = "test-prompt"
        private val state = MutableStateFlow(AppSettings(
            apiKey = apiKey,
            llmPrompt = "Initial prompt",
            prompts = listOf(SavedPrompt(promptId, "Test prompt", "Initial prompt")),
            activePromptId = promptId,
            transcriptionModel = "gpt-transcribe",
            llmModel = "gpt-5.6-luna",
            autoProcess = autoProcess
        ))
        override val settingsFlow: StateFlow<AppSettings> = state

        fun addPrompt(prompt: SavedPrompt) {
            state.value = state.value.copy(prompts = state.value.prompts + prompt)
        }

        fun setApiKey(apiKey: String) {
            state.value = state.value.copy(apiKey = apiKey)
        }

        fun update(prompt: String, llmModel: String) {
            state.value = state.value.copy(
                llmPrompt = prompt,
                prompts = listOf(SavedPrompt(promptId, "Test prompt", prompt)),
                activePromptId = promptId,
                llmModel = llmModel
            )
        }
    }

    private class RecordingGateway(
        private val transcriptionResult: CompletableDeferred<String> = CompletableDeferred("")
    ) : TranscriptionGateway {
        var transcriptionCalls = 0
        var formatCalls = 0
        var lastPrompt: String? = null
        var lastLlmModel: String? = null
        var lastFormattedSource: String? = null
        var formatError: Throwable? = null

        var lastTranscriptionModel: String? = null
        var transcriptionError: Exception? = null

        override suspend fun transcribeStaged(
            stagedAudio: StagedAudio,
            apiKey: String,
            model: String,
            language: String?,
            onProgress: ((String) -> Unit)?
        ): String {
            transcriptionCalls += 1
            lastTranscriptionModel = model
            transcriptionError?.let { throw it }
            return transcriptionResult.await()
        }

        override suspend fun formatText(text: String, apiKey: String, prompt: String, model: String): FormattedTranscript {
            formatCalls += 1
            lastPrompt = prompt
            lastLlmModel = model
            lastFormattedSource = text
            formatError?.let { throw it }
            return FormattedTranscript("edited dictation", "An edited dictation.")
        }
    }

}
