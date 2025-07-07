package com.valenzine.whisperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valenzine.whisperdroid.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val transcriptionApiKey = repository.transcriptionApiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val llmApiKey = repository.llmApiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val llmPrompt = repository.llmPromptFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val transcriptionModel = repository.transcriptionModelFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "whisper-1"
    )

    fun saveTranscriptionApiKey(apiKey: String) {
        viewModelScope.launch {
            repository.saveTranscriptionApiKey(apiKey)
        }
    }

    fun saveLlmApiKey(apiKey: String) {
        viewModelScope.launch {
            repository.saveLlmApiKey(apiKey)
        }
    }

    fun saveLlmPrompt(prompt: String) {
        viewModelScope.launch {
            repository.saveLlmPrompt(prompt)
        }
    }

    fun saveTranscriptionModel(model: String) {
        viewModelScope.launch {
            repository.saveTranscriptionModel(model)
        }
    }
}