package com.valenzine.whisperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valenzine.whisperdroid.repository.AppSettings
import com.valenzine.whisperdroid.repository.SettingsRepository
import com.valenzine.whisperdroid.repository.SavedPrompt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun save(settings: AppSettings) = viewModelScope.launch { repository.saveSettings(settings) }
    fun selectPrompt(id: String) = viewModelScope.launch { repository.selectPrompt(id) }
    fun createPrompt(name: String, content: String) = viewModelScope.launch { repository.createPrompt(name, content) }
    fun updatePrompt(prompt: SavedPrompt) = viewModelScope.launch { repository.updatePrompt(prompt) }
    fun updatePromptContent(id: String, content: String) = viewModelScope.launch {
        repository.updatePromptContent(id, content)
    }
    fun deletePrompt(id: String) = viewModelScope.launch { repository.deletePrompt(id) }
    fun setAutoProcess(enabled: Boolean) = viewModelScope.launch { repository.saveAutoProcess(enabled) }
}
