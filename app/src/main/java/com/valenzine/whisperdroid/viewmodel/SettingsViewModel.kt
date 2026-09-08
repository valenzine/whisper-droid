package com.valenzine.whisperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valenzine.whisperdroid.repository.AppSettings
import com.valenzine.whisperdroid.repository.SettingsRepository
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
    fun saveLlmPrompt(prompt: String) = viewModelScope.launch { repository.saveLlmPrompt(prompt) }
    fun setAutoProcess(enabled: Boolean) = viewModelScope.launch { repository.saveAutoProcess(enabled) }
}
