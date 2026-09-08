package com.valenzine.whisperdroid.viewmodel

import android.net.Uri
import com.valenzine.whisperdroid.repository.AppSettings
import com.valenzine.whisperdroid.repository.SettingsGateway
import com.valenzine.whisperdroid.repository.TranscriptionGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun rejectsSecondTranscriptionWhileFirstRequestIsActive() = runTest {
        val transcription = CompletableDeferred<String>()
        val gateway = RecordingGateway(transcriptionResult = transcription)
        val viewModel = TranscriptionViewModel(gateway, FakeSettings())
        var acceptedEvents = 0

        assertTrue(viewModel.startTranscription(onAccepted = { acceptedEvents += 1 }) { _, _ ->
            gateway.transcriptionCalls += 1
            transcription.await()
        })
        assertFalse(viewModel.startTranscription(onAccepted = { acceptedEvents += 1 }) { _, _ -> "second result" })

        transcription.complete("first result")
        advanceUntilIdle()
        assertEquals("first result", viewModel.screenState.value.transcription)
        assertEquals(1, gateway.transcriptionCalls)
        assertEquals(1, acceptedEvents)
    }

    @Test
    fun autoProcessingOffKeepsTranscriptWithoutFormatting() = runTest {
        val gateway = RecordingGateway(transcriptionResult = CompletableDeferred("raw dictation"))
        val viewModel = TranscriptionViewModel(gateway, FakeSettings(autoProcess = false))
        var selectedModel: String? = null

        assertTrue(viewModel.startTranscription { settings, _ ->
            selectedModel = settings.transcriptionModel
            "raw dictation"
        })
        advanceUntilIdle()

        assertEquals("raw dictation", viewModel.screenState.value.transcription)
        assertEquals("", viewModel.screenState.value.formattedText)
        assertEquals(0, gateway.formatCalls)
        assertEquals("gpt-transcribe", selectedModel)
    }

    @Test
    fun autoProcessingUsesLatestPromptAndSelectedLlm() = runTest {
        val transcription = CompletableDeferred<String>()
        val settings = FakeSettings(autoProcess = true)
        val gateway = RecordingGateway(transcriptionResult = transcription)
        val viewModel = TranscriptionViewModel(gateway, settings)

        assertTrue(viewModel.startTranscription { _, _ -> transcription.await() })
        runCurrent()
        settings.update(prompt = "Use short paragraphs", llmModel = "gpt-5-nano")
        transcription.complete("raw dictation")
        advanceUntilIdle()

        assertEquals(1, gateway.formatCalls)
        assertEquals("Use short paragraphs", gateway.lastPrompt)
        assertEquals("gpt-5-nano", gateway.lastLlmModel)
        assertEquals("raw dictation", gateway.lastFormattedSource)
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
        viewModel.startTranscription { _, _ -> "raw dictation" }
        advanceUntilIdle()
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
        val viewModel = TranscriptionViewModel(RecordingGateway(), FakeSettings())
        viewModel.startTranscription { _, _ -> "raw dictation" }
        advanceUntilIdle()
        viewModel.formatText()
        advanceUntilIdle()

        viewModel.startTranscription { _, _ -> throw IllegalStateException("upload unavailable") }
        advanceUntilIdle()

        assertEquals("raw dictation", viewModel.screenState.value.transcription)
        assertEquals("edited dictation", viewModel.screenState.value.formattedText)
        assertEquals("upload unavailable", viewModel.screenState.value.error)
    }

    private class FakeSettings(
        autoProcess: Boolean = false
    ) : SettingsGateway {
        private val state = MutableStateFlow(AppSettings(
            apiKey = "test-key",
            llmPrompt = "Initial prompt",
            transcriptionModel = "gpt-transcribe",
            llmModel = "gpt-5.6-luna",
            autoProcess = autoProcess
        ))
        override val settingsFlow: StateFlow<AppSettings> = state

        fun update(prompt: String, llmModel: String) {
            state.value = state.value.copy(llmPrompt = prompt, llmModel = llmModel)
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

        override suspend fun transcribeUri(
            uri: Uri,
            apiKey: String,
            model: String,
            language: String?,
            onProgress: ((String) -> Unit)?
        ): String {
            transcriptionCalls += 1
            return transcriptionResult.await()
        }

        override suspend fun formatText(text: String, apiKey: String, prompt: String, model: String): String {
            formatCalls += 1
            lastPrompt = prompt
            lastLlmModel = model
            lastFormattedSource = text
            formatError?.let { throw it }
            return "edited dictation"
        }
    }

}
