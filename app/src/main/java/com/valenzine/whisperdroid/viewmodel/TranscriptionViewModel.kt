package com.valenzine.whisperdroid.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.valenzine.whisperdroid.repository.TranscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class TranscriptionViewModel(context: Context) : ViewModel() {
    private val repository = TranscriptionRepository(context)

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription

    private val _formattedText = MutableStateFlow("")
    val formattedText: StateFlow<String> = _formattedText

    fun transcribeFile(file: File) {
        viewModelScope.launch {
            try {
                _transcription.value = repository.transcribeFile(file)
            } catch (e: Exception) {
                // TODO: Handle error
            }
        }
    }

    fun formatText() {
        viewModelScope.launch {
            try {
                _formattedText.value = repository.formatText(_transcription.value)
            } catch (e: Exception) {
                // TODO: Handle error
            }
        }
    }
}

class TranscriptionViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TranscriptionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TranscriptionViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}