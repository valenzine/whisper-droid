package com.valenzine.whisperdroid.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valenzine.whisperdroid.repository.SettingsGateway
import com.valenzine.whisperdroid.repository.TranscriptionGateway
import com.valenzine.whisperdroid.history.TranscriptHistoryRepository
import com.valenzine.whisperdroid.history.TranscriptEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TranscriptionScreenState(
    val transcription: String = "",
    val formattedText: String = "",
    val selectedFileName: String? = null,
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
    private val admissionLock = Any()
    private var currentHistoryEntryId: Long? = null

    fun resetState() {
        _screenState.value = _screenState.value.copy(phase = TranscriptionUiState.Idle, error = null, errorDetails = null)
    }

    fun showHistoryEntry(entry: TranscriptEntry) {
        currentHistoryEntryId = entry.id
        _screenState.value = _screenState.value.copy(
            transcription = entry.rawText,
            formattedText = entry.formattedText,
            selectedFileName = entry.displayName,
            phase = TranscriptionUiState.Success(entry.rawText, entry.formattedText.takeIf(String::isNotBlank)),
            error = null,
            errorDetails = null
        )
    }

    fun deleteHistoryEntry(id: Long) = viewModelScope.launch {
        historyRepository?.delete(id)
        if (currentHistoryEntryId == id) clearDisplayedEntry()
    }

    fun clearHistory() = viewModelScope.launch {
        historyRepository?.clear()
        clearDisplayedEntry()
    }

    private fun clearDisplayedEntry() {
        currentHistoryEntryId = null
        _screenState.value = TranscriptionScreenState()
    }

    /** Atomically accepts one URI and consumes its event before creating the work coroutine. */
    fun transcribeUri(
        uri: Uri,
        language: String? = null,
        onAccepted: () -> Unit = {}
    ): Boolean = synchronized(admissionLock) {
        if (_screenState.value.isBusy) return@synchronized false
        val fileName = uri.lastPathSegment ?: "Audio file"
        _screenState.value = _screenState.value.copy(selectedFileName = fileName, phase = TranscriptionUiState.Loading, error = null, errorDetails = null)
        onAccepted()
        viewModelScope.launch {
            var stagedAudio: com.valenzine.whisperdroid.repository.StagedAudio? = null
            try {
                stagedAudio = repository.stageUri(uri)
                historyRepository?.find(stagedAudio.sha256)?.let { entry ->
                    currentHistoryEntryId = entry.id
                    _screenState.value = _screenState.value.copy(
                        transcription = entry.rawText,
                        formattedText = entry.formattedText,
                        selectedFileName = entry.displayName,
                        phase = TranscriptionUiState.Success(entry.rawText, entry.formattedText.takeIf(String::isNotBlank)),
                        error = null,
                        errorDetails = null
                    )
                    return@launch
                }
                val settings = settingsRepository.settingsFlow.first()
                if (settings.apiKey.isBlank()) {
                    setError("Add an OpenAI API key in Settings before transcribing.")
                    return@launch
                }
                val result = repository.transcribeStaged(stagedAudio, settings.apiKey, settings.transcriptionModel, language) { progressMessage ->
                    val selectedName = _screenState.value.selectedFileName ?: "Audio file"
                    when {
                        progressMessage.contains("Converting", ignoreCase = true) -> setPhase(TranscriptionUiState.TranscodingFile(selectedName, selectedName.substringAfterLast('.', "audio").uppercase(), "WebM"))
                        progressMessage.contains("Uploading", ignoreCase = true) -> setPhase(TranscriptionUiState.UploadingFile(selectedName, progressMessage.substringAfter("to ").substringBefore("...")))
                        else -> setPhase(TranscriptionUiState.Loading)
                    }
                }
                currentHistoryEntryId = historyRepository?.saveRaw(stagedAudio.sha256, stagedAudio.displayName, result)?.id
                _screenState.value = _screenState.value.copy(transcription = result, formattedText = "", phase = TranscriptionUiState.Success(result), error = null, errorDetails = null)
                if (settingsRepository.settingsFlow.first().autoProcess && result.isNotBlank()) {
                    setPhase(TranscriptionUiState.FormattingText)
                    formatTextInternal(result)
                }
            } catch (e: Exception) {
                setError(messageFor(e, "Transcription failed"), e.message)
            } finally {
                stagedAudio?.file?.delete()
            }
        }
        true
    }

    fun retranscribeUri(entry: TranscriptEntry, uri: Uri, language: String? = null): Boolean = synchronized(admissionLock) {
        if (_screenState.value.isBusy) return@synchronized false
        _screenState.value = _screenState.value.copy(
            selectedFileName = entry.displayName,
            phase = TranscriptionUiState.Loading,
            error = null,
            errorDetails = null
        )
        viewModelScope.launch {
            var stagedAudio: com.valenzine.whisperdroid.repository.StagedAudio? = null
            try {
                stagedAudio = repository.stageUri(uri)
                if (stagedAudio.sha256 != entry.audioFingerprint) {
                    setError("Selected audio does not match this history entry.")
                    return@launch
                }
                val settings = settingsRepository.settingsFlow.first()
                if (settings.apiKey.isBlank()) {
                    setError("Add an OpenAI API key in Settings before transcribing.")
                    return@launch
                }
                val result = repository.transcribeStaged(stagedAudio, settings.apiKey, settings.transcriptionModel, language) { progressMessage ->
                    val selectedName = entry.displayName
                    when {
                        progressMessage.contains("Converting", ignoreCase = true) -> setPhase(TranscriptionUiState.TranscodingFile(selectedName, selectedName.substringAfterLast('.', "audio").uppercase(), "WebM"))
                        progressMessage.contains("Uploading", ignoreCase = true) -> setPhase(TranscriptionUiState.UploadingFile(selectedName, progressMessage.substringAfter("to ").substringBefore("...")))
                        else -> setPhase(TranscriptionUiState.Loading)
                    }
                }
                historyRepository?.replaceTranscription(entry.id, stagedAudio.displayName, result)
                currentHistoryEntryId = entry.id
                _screenState.value = _screenState.value.copy(
                    transcription = result,
                    formattedText = "",
                    selectedFileName = stagedAudio.displayName,
                    phase = TranscriptionUiState.Success(result),
                    error = null,
                    errorDetails = null
                )
                if (settingsRepository.settingsFlow.first().autoProcess && result.isNotBlank()) {
                    setPhase(TranscriptionUiState.FormattingText)
                    formatTextInternal(result)
                }
            } catch (e: Exception) {
                setError(messageFor(e, "Transcription failed"), e.message)
            } finally {
                stagedAudio?.file?.delete()
            }
        }
        true
    }

    internal fun startTranscription(
        selectedFileName: String? = null,
        onAccepted: () -> Unit = {},
        request: suspend (settings: com.valenzine.whisperdroid.repository.AppSettings, onProgress: (String) -> Unit) -> String
    ): Boolean = synchronized(admissionLock) {
        if (_screenState.value.isBusy) return@synchronized false
        _screenState.value = _screenState.value.copy(
            selectedFileName = selectedFileName,
            phase = TranscriptionUiState.Loading,
            error = null,
            errorDetails = null
        )
        onAccepted()
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.apiKey.isBlank()) {
                setError("Add an OpenAI API key in Settings before transcribing.")
                return@launch
            }
            try {
                val result = request(settings) { progressMessage ->
                    val fileName = _screenState.value.selectedFileName ?: "Audio file"
                    when {
                        progressMessage.contains("Analyzing", ignoreCase = true) -> {
                            setPhase(TranscriptionUiState.Loading)
                        }
                        progressMessage.contains("Converting", ignoreCase = true) -> {
                            setPhase(
                                TranscriptionUiState.TranscodingFile(
                                    fileName = fileName,
                                    fromFormat = fileName.substringAfterLast('.', "audio").uppercase(),
                                    toFormat = "WebM"
                                )
                            )
                        }
                        progressMessage.contains("Uploading", ignoreCase = true) -> {
                            val model = progressMessage.substringAfter("to ").substringBefore("...")
                            setPhase(TranscriptionUiState.UploadingFile(fileName, model))
                        }
                        else -> setPhase(TranscriptionUiState.Loading)
                    }
                }
                _screenState.value = _screenState.value.copy(
                    transcription = result,
                    formattedText = "",
                    phase = TranscriptionUiState.Success(result),
                    error = null,
                    errorDetails = null
                )
                if (settingsRepository.settingsFlow.first().autoProcess && result.isNotBlank()) {
                    setPhase(TranscriptionUiState.FormattingText)
                    formatTextInternal(result)
                }
            } catch (e: Exception) {
                setError(messageFor(e, "Transcription failed"), e.message)
            }
        }
        true
    }

    fun formatText(): Boolean = synchronized(admissionLock) {
        val current = _screenState.value
        if (current.isBusy || current.transcription.isBlank()) return@synchronized false
        setPhase(TranscriptionUiState.FormattingText)
        viewModelScope.launch {
            formatTextInternal(current.transcription)
        }
        true
    }

    private suspend fun formatTextInternal(source: String) {
        val settings = settingsRepository.settingsFlow.first()
        if (settings.apiKey.isBlank()) {
            setError("Add an OpenAI API key in Settings before formatting.")
            return
        }
        try {
            val result = repository.formatText(source, settings.apiKey, settings.llmPrompt, settings.llmModel)
            _screenState.value = _screenState.value.copy(
                formattedText = result,
                phase = TranscriptionUiState.Success(source, result),
                error = null,
                errorDetails = null
            )
            currentHistoryEntryId?.let { historyRepository?.saveFormatted(it, result) }
        } catch (e: Exception) {
            setError(e.message ?: "Formatting failed", e.message)
        }
    }

    private fun setPhase(phase: TranscriptionUiState) {
        _screenState.value = _screenState.value.copy(phase = phase, error = null, errorDetails = null)
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
