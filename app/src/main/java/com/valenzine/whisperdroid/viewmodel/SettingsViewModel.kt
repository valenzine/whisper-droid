package com.valenzine.whisperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valenzine.whisperdroid.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val apiKey = repository.apiKeyFlow.stateIn(
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

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            repository.saveApiKey(key)
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