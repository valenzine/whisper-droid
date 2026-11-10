package com.valenzine.whisperdroid.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptHistoryRepositoryTest {
    private lateinit var database: TranscriptHistoryDatabase
    private lateinit var repository: TranscriptHistoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TranscriptHistoryDatabase::class.java
        ).build()
        repository = TranscriptHistoryRepository(database.transcriptHistoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rawTranscriptPersistsAndCanBeFoundByFingerprint() = runBlocking {
        val saved = repository.saveRaw("fingerprint", "dictation.m4a", "raw transcript", "Audio", 1L)

        val found = repository.find("fingerprint")

        assertEquals(saved.id, found?.id)
        assertEquals("raw transcript", found?.rawText)
        assertEquals("", found?.formattedText)
    }

    @Test
    fun formattingUpdatesTheExistingHistoryEntry() = runBlocking {
        val saved = repository.saveRaw("fingerprint", "dictation.m4a", "raw transcript", "Audio", 1L)

        repository.saveFormatted(saved.id, "formatted transcript", "A formatted transcript.")

        val entry = repository.entries.first().single()
        assertEquals(saved.id, entry.id)
        assertEquals("raw transcript", entry.rawText)
        assertEquals("formatted transcript", entry.formattedText)
    }

    @Test
    fun deletingAnEntryRemovesItsFingerprintMatch() = runBlocking {
        val saved = repository.saveRaw("fingerprint", "dictation.m4a", "raw transcript", "Audio", 1L)

        repository.delete(saved.id)

        assertNull(repository.find("fingerprint"))
        assertEquals(emptyList<TranscriptEntry>(), repository.entries.first())
    }

    @Test
    fun promptResultsReplacePerPromptProjectLatestAndCascadeWithTheirEntry() = runBlocking {
        val saved = repository.saveRaw("fingerprint", "dictation.m4a", "raw transcript", "Audio", 1L)

        repository.savePromptResult(saved.id, "tidy", "Tidy", "first tidy", "First")
        repository.savePromptResult(saved.id, "brief", "Brief", "brief result", "Brief")
        repository.savePromptResult(saved.id, "tidy", "Tidy", "replacement tidy", "Replacement")

        val results = repository.results(saved.id).first()
        assertEquals(2, results.size)
        assertEquals("replacement tidy", results.first { it.promptId == "tidy" }.formattedText)

        repository.deletePromptResult(saved.id, "brief")
        assertEquals(1, repository.results(saved.id).first().size)
        assertEquals("replacement tidy", repository.entries.first().single().formattedText)

        repository.delete(saved.id)
        assertEquals(emptyList<TranscriptPromptResult>(), repository.results(saved.id).first())
    }
}
