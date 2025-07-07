package com.valenzine.whisperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valenzine.whisperdroid.repository.TranscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Exception

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
    private val repository: TranscriptionRepository
) : ViewModel() {

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription

    private val _uiState = MutableStateFlow<TranscriptionUiState>(TranscriptionUiState.Idle)
    val uiState: StateFlow<TranscriptionUiState> = _uiState

    private val _formattedText = MutableStateFlow("")
    val formattedText: StateFlow<String> = _formattedText

    fun resetState() {
        _uiState.value = TranscriptionUiState.Idle
        _transcription.value = ""
        _formattedText.value = ""
    }

    fun transcribeFile(file: File, language: String? = null) {
        viewModelScope.launch {
            try {
                // Prepare file info
                val fileName = file.name
                val fileSize = file.length()
                val fileExtension = file.extension.lowercase()

                _uiState.value = TranscriptionUiState.PreparingFile(fileName, fileSize)

                val result = repository.transcribeFile(file, language) { progressMessage ->
                    when {
                        progressMessage.contains("Analyzing", ignoreCase = true) -> {
                            _uiState.value = TranscriptionUiState.PreparingFile(fileName, fileSize)
                        }
                        progressMessage.contains("Converting", ignoreCase = true) -> {
                            val fromFormat = fileExtension.uppercase()
                            _uiState.value = TranscriptionUiState.TranscodingFile(
                                fileName = fileName,
                                fromFormat = fromFormat,
                                toFormat = "WebM"
                            )
                        }
                        progressMessage.contains("Uploading", ignoreCase = true) -> {
                            val model = progressMessage.substringAfter("to ").substringBefore("...")
                            _uiState.value = TranscriptionUiState.UploadingFile(fileName, model)
                        }
                        else -> {
                            _uiState.value = TranscriptionUiState.Loading
                        }
                    }
                }

                _transcription.value = result
                _uiState.value = TranscriptionUiState.Success(result)
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("File conversion failed") == true -> {
                        e.message!! // Use the detailed message from repository
                    }
                    e.message?.contains("No audio track found") == true -> {
                        "Invalid audio file. No audio track found in the selected file."
                    }
                    e.message?.contains("400") == true && e.message?.contains("corrupted or unsupported") == true -> {
                        // Special handling for the Opus + gpt-4o-mini-transcribe issue
                        val settingsRepo = com.valenzine.whisperdroid.repository.SettingsRepository(repository.getContext())
                        val model = settingsRepo.transcriptionModelFlow.first()
                        val fileExtension = file.extension.lowercase()

                        if ((fileExtension == "opus" || fileExtension == "ogg") && model == "gpt-4o-mini-transcribe") {
                            "Opus/OGG files are not supported by $model. Try using whisper-1 model instead, or convert your file to MP3/AAC format first."
                        } else {
                            "File format not supported or file corrupted. Supported formats: MP3, AAC, WAV, FLAC, OGG (whisper-1 only)"
                        }
                    }
                    e.message?.contains("400") == true -> "File format not supported or file corrupted"
                    e.message?.contains("401") == true -> "Invalid API key"
                    e.message?.contains("413") == true -> "File too large (max 25MB)"
                    e.message?.contains("429") == true -> "Rate limit exceeded, please try again later"
                    e.message?.contains("500") == true -> "Server error, please try again"
                    e.message?.contains("network") == true -> "Network error, check your connection"
                    else -> "Transcription failed"
                }
                _uiState.value = TranscriptionUiState.Error(errorMessage, e.message)
            }
        }
    }

    fun formatText() {
        viewModelScope.launch {
            try {
                _uiState.value = TranscriptionUiState.FormattingText
                val result = repository.formatText(_transcription.value)
                _formattedText.value = result
                _uiState.value = TranscriptionUiState.Success(_transcription.value, result)
            } catch (e: Exception) {
                _uiState.value = TranscriptionUiState.Error(e.message ?: "Formatting failed")
            }
        }
    }
}