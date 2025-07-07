package com.valenzine.whisperdroid.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val transcriptionApiKey = stringPreferencesKey("transcription_api_key")
    private val llmApiKey = stringPreferencesKey("llm_api_key")
    private val llmPrompt = stringPreferencesKey("llm_prompt")
    private val transcriptionModel = stringPreferencesKey("transcription_model")

    val transcriptionApiKeyFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[transcriptionApiKey] ?: ""
        }

    val llmApiKeyFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[llmApiKey] ?: ""
        }

    val llmPromptFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[llmPrompt] ?: ""
        }

    val transcriptionModelFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[transcriptionModel] ?: "whisper-1"  // Default to whisper-1
        }

    suspend fun saveTranscriptionApiKey(apiKey: String) {
        context.dataStore.edit {
            it[transcriptionApiKey] = apiKey
        }
    }

    suspend fun saveLlmApiKey(apiKey: String) {
        context.dataStore.edit {
            it[llmApiKey] = apiKey
        }
    }

    suspend fun saveLlmPrompt(prompt: String) {
        context.dataStore.edit {
            it[llmPrompt] = prompt
        }
    }

    suspend fun saveTranscriptionModel(model: String) {
        context.dataStore.edit {
            it[transcriptionModel] = model
        }
    }
}