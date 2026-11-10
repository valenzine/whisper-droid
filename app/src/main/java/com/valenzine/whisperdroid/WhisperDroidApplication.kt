package com.valenzine.whisperdroid

import android.app.Application
import com.valenzine.whisperdroid.history.TranscriptHistoryDatabase
import com.valenzine.whisperdroid.history.TranscriptHistoryRepository
import com.valenzine.whisperdroid.repository.SettingsRepository
import com.valenzine.whisperdroid.repository.TranscriptionRepository
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class WhisperDroidApplication : Application() {
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { TranscriptionRepository(this) }
    internal val settings by lazy { SettingsRepository(this) }
    internal val history by lazy {
        TranscriptHistoryRepository(TranscriptHistoryDatabase.create(this).transcriptHistoryDao())
    }
    internal val transcription by lazy {
        TranscriptionViewModel(repository, settings, history).also { it.restoreLatest() }
    }
    private val cleanup by lazy {
        processScope.async(Dispatchers.IO) { repository.clearAbandonedAudio() }
    }
    internal val audioSession by lazy {
        val startupCleanup = cleanup
        AudioSession(
            scope = processScope,
            stage = { uri ->
                startupCleanup.await()
                repository.stageUri(uri)
            },
            process = transcription::processStagedAudio,
            showError = transcription::showAudioError
        )
    }
}
