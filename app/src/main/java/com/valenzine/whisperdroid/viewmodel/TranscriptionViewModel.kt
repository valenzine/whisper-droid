package com.valenzine.whisperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valenzine.whisperdroid.repository.TranscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Exception

sealed interface TranscriptionUiState {
    object Idle : TranscriptionUiState
    object Loading : TranscriptionUiState
    object FormattingText : TranscriptionUiState
    data class Success(val transcription: String, val formattedText: String? = null) : TranscriptionUiState
    data class Error(val message: String) : TranscriptionUiState
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

    fun transcribeFile(file: File) {
        viewModelScope.launch {
            try {
                _uiState.value = TranscriptionUiState.Loading
                val result = repository.transcribeFile(file)
                _transcription.value = result
                _uiState.value = TranscriptionUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = TranscriptionUiState.Error(e.message ?: "Transcription failed")
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