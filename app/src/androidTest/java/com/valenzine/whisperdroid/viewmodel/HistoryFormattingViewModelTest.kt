package com.valenzine.whisperdroid.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valenzine.whisperdroid.history.TranscriptHistoryDatabase
import com.valenzine.whisperdroid.history.TranscriptHistoryRepository
import com.valenzine.whisperdroid.repository.AppSettings
import com.valenzine.whisperdroid.repository.FormattedTranscript
import com.valenzine.whisperdroid.repository.SavedPrompt
import com.valenzine.whisperdroid.repository.SettingsGateway
import com.valenzine.whisperdroid.repository.StagedAudio
import com.valenzine.whisperdroid.repository.TranscriptionGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryFormattingViewModelTest {
    private lateinit var database: TranscriptHistoryDatabase
    private lateinit var history: TranscriptHistoryRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TranscriptHistoryDatabase::class.java).build()
        history = TranscriptHistoryRepository(database.transcriptHistoryDao())
    }

    @After fun tearDown() = database.close()

    @Test
    fun historyRerunUsesRequestedPromptAndNeverTranscribes() = runBlocking {
        val entry = history.saveRaw("fingerprint", "note.m4a", "saved raw", "Audio", 1L)
        history.savePromptResult(entry.id, "first", "First", "old result", "Old")
        val settings = FakeSettings()
        val gateway = Gateway()
        val viewModel = TranscriptionViewModel(gateway, settings, history)

        assertTrue(viewModel.formatHistoryEntry(entry.id, "second"))
        withTimeout(5_000) { viewModel.screenState.first { !it.isBusy } }

        assertEquals(0, gateway.transcriptionCalls)
        assertEquals("saved raw", gateway.formattedSource)
        assertEquals("Second prompt", gateway.prompt)
        assertEquals("first", settings.settingsFlow.value.activePromptId)
        val saved = history.results(entry.id).first()
        assertEquals(mapOf("first" to "old result", "second" to "second result"), saved.associate { it.promptId to it.formattedText })

        gateway.failure = IllegalStateException("format failed")
        assertTrue(viewModel.formatHistoryEntry(entry.id, "second"))
        val failed = withTimeout(5_000) { viewModel.screenState.first { it.phase is TranscriptionUiState.Error } }
        assertEquals(entry.id, failed.formattingEntryId)
        assertEquals("format failed", failed.error)
        assertEquals(saved, history.results(entry.id).first())
    }

    private class FakeSettings : SettingsGateway {
        override val settingsFlow: StateFlow<AppSettings> = MutableStateFlow(
            AppSettings(
                apiKey = "key",
                prompts = listOf(
                    SavedPrompt("first", "First", "First prompt"),
                    SavedPrompt("second", "Second", "Second prompt")
                ),
                activePromptId = "first"
            )
        )
    }

    private class Gateway : TranscriptionGateway {
        var transcriptionCalls = 0
        var formattedSource: String? = null
        var prompt: String? = null
        var failure: Exception? = null
        override suspend fun transcribeStaged(stagedAudio: StagedAudio, apiKey: String, model: String, language: String?, onProgress: ((String) -> Unit)?): String {
            transcriptionCalls++
            return "unexpected"
        }
        override suspend fun formatText(text: String, apiKey: String, prompt: String, model: String): FormattedTranscript {
            formattedSource = text
            this.prompt = prompt
            failure?.let { throw it }
            return FormattedTranscript("second result", "Second")
        }
    }
}
