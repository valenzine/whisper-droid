package com.valenzine.whisperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.valenzine.whisperdroid.repository.SettingsRepository
import com.valenzine.whisperdroid.repository.TranscriptionRepository
import com.valenzine.whisperdroid.history.TranscriptHistoryRepository

class TranscriptionViewModelFactory(
    private val repository: TranscriptionRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: TranscriptHistoryRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TranscriptionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TranscriptionViewModel(repository, settingsRepository, historyRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
