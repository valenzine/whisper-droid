package com.valenzine.whisperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valenzine.whisperdroid.repository.AppSettings
import com.valenzine.whisperdroid.repository.FormattedTranscript
import com.valenzine.whisperdroid.repository.SavedPrompt
import com.valenzine.whisperdroid.repository.SettingsGateway
import com.valenzine.whisperdroid.repository.StagedAudio
import com.valenzine.whisperdroid.repository.TranscriptionGateway
import com.valenzine.whisperdroid.history.TranscriptHistoryRepository
import com.valenzine.whisperdroid.history.TranscriptEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TranscriptionScreenState(
    val transcription: String = "",
    val formattedText: String = "",
    val selectedFileName: String? = null,
    val historyEntryId: Long? = null,
    val formattingEntryId: Long? = null,
    val formattingPromptId: String? = null,
    val phase: TranscriptionUiState = TranscriptionUiState.Idle,
    val error: String? = null,
    val errorDetails: String? = null
) {
    val isBusy: Boolean get() = phase !is TranscriptionUiState.Idle && phase !is TranscriptionUiState.Success && phase !is TranscriptionUiState.Error
}

sealed interface TranscriptionUiState {
    object Idle : TranscriptionUiState
    data class PreparingFile(val fileName: String, val fileSize: Long) : TranscriptionUiState
    data class TranscodingFile(val fileName: String, val fromFormat: String, val toFormat: String) : TranscriptionUiState
    data class UploadingFile(val fileName: String, val model: String) : TranscriptionUiState
    object Loading : TranscriptionUiState
    object WaitingForApiKey : TranscriptionUiState
    object FormattingText : TranscriptionUiState
    data class Success(val transcription: String, val formattedText: String? = null) : TranscriptionUiState
    data class Error(val message: String, val details: String? = null) : TranscriptionUiState
}

class TranscriptionViewModel(
    private val repository: TranscriptionGateway,
    private val settingsRepository: SettingsGateway,
    private val historyRepository: TranscriptHistoryRepository? = null
) : ViewModel() {
    private val _screenState = MutableStateFlow(TranscriptionScreenState())
    val screenState: StateFlow<TranscriptionScreenState> = _screenState
    private val operation = Mutex()
    private val audioErrors = Channel<String>(Channel.BUFFERED)
    val audioErrorMessages = audioErrors.receiveAsFlow()

    fun resetState() {
        if (_screenState.value.phase is TranscriptionUiState.Error) {
            setPhase(TranscriptionUiState.Idle)
        }
    }

    fun restoreLatest() = viewModelScope.launch {
        operation.withLock {
            try {
                val latest = historyRepository?.latest()
                if (latest != null && _screenState.value == TranscriptionScreenState()) displayEntry(latest)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                setError("Could not restore transcript history.", error.message)
            }
        }
    }

    fun showHistoryEntry(entry: TranscriptEntry) {
        if (!operation.isLocked) displayEntry(entry)
    }

    private fun displayEntry(entry: TranscriptEntry) {
        _screenState.value = TranscriptionScreenState(
            transcription = entry.rawText,
            formattedText = entry.formattedText,
            selectedFileName = entry.displayName,
            historyEntryId = entry.id,
            phase = TranscriptionUiState.Success(entry.rawText, entry.formattedText.takeIf(String::isNotBlank))
        )
    }

    fun deleteHistoryEntry(id: Long) = editHistory {
        historyRepository?.delete(id)
        if (_screenState.value.historyEntryId == id) _screenState.value = TranscriptionScreenState()
    }

    fun clearHistory() = editHistory {
        historyRepository?.clear()
        _screenState.value = TranscriptionScreenState()
    }

    fun deletePromptResult(entryId: Long, promptId: String) = editHistory {
        historyRepository?.deletePromptResult(entryId, promptId)
        if (_screenState.value.historyEntryId == entryId) {
            historyRepository?.entry(entryId)?.first()?.let(::displayEntry)
        }
    }

    private fun editHistory(edit: suspend () -> Unit) {
        if (!operation.tryLock()) return
        viewModelScope.launch {
            try {
                edit()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                setError("Could not update transcript history.", error.message)
            } finally {
                operation.unlock()
            }
        }
    }

    internal fun showAudioError(error: Exception) {
        audioErrors.trySend(messageFor(error, "Could not open selected audio"))
    }

    internal suspend fun processStagedAudio(
        audio: StagedAudio,
        language: String? = null,
        expectedFingerprint: String? = null
    ) = operation.withLock {
        setPhase(TranscriptionUiState.Loading)
        try {
            if (expectedFingerprint != null && expectedFingerprint != audio.sha256) {
                setError("Selected audio does not match this history entry.")
                return@withLock
            }
            val saved = historyRepository?.find(audio.sha256)
            if (expectedFingerprint == null && saved != null) {
                displayEntry(saved)
                return@withLock
            }
            if (expectedFingerprint != null && saved == null) {
                setError("This history entry has been cleared.")
                return@withLock
            }
            var settings = settingsRepository.settingsFlow.first()
            if (settings.apiKey.isBlank()) {
                setPhase(TranscriptionUiState.WaitingForApiKey)
                settings = settingsRepository.settingsFlow.first { it.apiKey.isNotBlank() }
                setPhase(TranscriptionUiState.Loading)
            }
            val result = repository.transcribeStaged(audio, settings.apiKey, settings.transcriptionModel, language) { progress ->
                val fileName = audio.displayName
                when {
                    progress.contains("Converting", ignoreCase = true) -> setPhase(
                        TranscriptionUiState.TranscodingFile(fileName, fileName.substringAfterLast('.', "audio").uppercase(), "WebM")
                    )
                    progress.contains("Uploading", ignoreCase = true) -> setPhase(
                        TranscriptionUiState.UploadingFile(fileName, progress.substringAfter("to ").substringBefore("..."))
                    )
                    else -> setPhase(TranscriptionUiState.Loading)
                }
            }
            val entryId = if (saved == null) {
                historyRepository?.saveRaw(audio.sha256, audio.displayName, result, audio.sourceOrigin, audio.importedAt)?.id
            } else {
                historyRepository.replaceTranscription(saved.id, audio.displayName, result)
                saved.id
            }
            val autoProcess = settingsRepository.settingsFlow.first().autoProcess && result.isNotBlank()
            _screenState.value = TranscriptionScreenState(
                transcription = result,
                selectedFileName = audio.displayName,
                historyEntryId = entryId,
                phase = if (autoProcess) TranscriptionUiState.FormattingText else TranscriptionUiState.Success(result)
            )
            if (autoProcess) formatTextInternal(result, entryId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            setError(messageFor(error, "Transcription failed"), error.message)
        }
    }

    fun formatText(promptId: String? = null): Boolean {
        val current = _screenState.value
        if (current.transcription.isBlank() || !operation.tryLock()) return false
        _screenState.value = current.copy(
            phase = TranscriptionUiState.FormattingText,
            formattingEntryId = current.historyEntryId,
            formattingPromptId = promptId,
            error = null,
            errorDetails = null
        )
        viewModelScope.launch {
            try {
                formatTextInternal(current.transcription, current.historyEntryId, promptId)
            } finally {
                operation.unlock()
            }
        }
        return true
    }

    fun formatHistoryEntry(entryId: Long, promptId: String): Boolean {
        if (!operation.tryLock()) return false
        val previousState = _screenState.value
        _screenState.value = previousState.copy(
            formattingEntryId = entryId,
            formattingPromptId = promptId,
            phase = TranscriptionUiState.FormattingText,
            error = null,
            errorDetails = null
        )
        viewModelScope.launch {
            try {
                val entry = historyRepository?.entry(entryId)?.first()
                val settings = settingsRepository.settingsFlow.first()
                val prompt = settings.prompts.firstOrNull { it.id == promptId }
                if (entry == null || prompt == null || settings.apiKey.isBlank()) {
                    setError(
                        when {
                            settings.apiKey.isBlank() -> "Add an OpenAI API key in Settings before formatting."
                            entry == null -> "This history entry has been cleared."
                            else -> "This prompt is no longer available."
                        }
                    )
                    return@launch
                }
                val result = formatAndSave(entry.rawText, entryId, settings, prompt)
                if (_screenState.value.historyEntryId == entryId) {
                    _screenState.value = _screenState.value.copy(
                        formattedText = result.text,
                        formattingEntryId = null,
                        formattingPromptId = null,
                        phase = TranscriptionUiState.Success(_screenState.value.transcription, result.text)
                    )
                } else {
                    _screenState.value = previousState.copy(formattingEntryId = null, formattingPromptId = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                setError(error.message ?: "Formatting failed", error.message)
            } finally {
                operation.unlock()
            }
        }
        return true
    }

    private suspend fun formatTextInternal(source: String, entryId: Long?, promptId: String? = null) {
        try {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.apiKey.isBlank()) {
                setError("Add an OpenAI API key in Settings before formatting.")
                return
            }
            val prompt = if (promptId == null) settings.activePrompt else settings.prompts.firstOrNull { it.id == promptId }
            if (prompt == null) {
                setError("This prompt is no longer available.")
                return
            }
            _screenState.value = _screenState.value.copy(formattingEntryId = entryId, formattingPromptId = prompt.id)
            val result = formatAndSave(source, entryId, settings, prompt)
            _screenState.value = _screenState.value.copy(
                formattedText = result.text,
                formattingEntryId = null,
                formattingPromptId = null,
                phase = TranscriptionUiState.Success(source, result.text),
                error = null,
                errorDetails = null
            )
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            setError(e.message ?: "Formatting failed", e.message)
        }
    }

    private suspend fun formatAndSave(
        source: String,
        entryId: Long?,
        settings: AppSettings,
        prompt: SavedPrompt
    ): FormattedTranscript {
        val result = repository.formatText(source, settings.apiKey, prompt.content, settings.llmModel)
        if (entryId != null) {
            historyRepository?.savePromptResult(entryId, prompt.id, prompt.name, result.text, result.summary)
        }
        return result
    }

    private fun setPhase(phase: TranscriptionUiState) {
        _screenState.value = _screenState.value.copy(
            phase = phase,
            formattingEntryId = null,
            formattingPromptId = null,
            error = null,
            errorDetails = null
        )
    }

    private fun setError(message: String, details: String? = null) {
        _screenState.value = _screenState.value.copy(
            phase = TranscriptionUiState.Error(message, details),
            error = message,
            errorDetails = details
        )
    }

    private fun messageFor(error: Exception, fallback: String): String = when {
        error.message?.contains("401") == true -> "Invalid API key"
        error.message?.contains("413") == true || error.message?.contains("too large", true) == true -> "File too large (max 25 MB)"
        error.message?.contains("429") == true -> "Rate limit exceeded, please try again later"
        error.message?.contains("network", true) == true -> "Network error, check your connection"
        else -> error.message ?: fallback
    }
}
