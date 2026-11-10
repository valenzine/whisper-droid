package com.valenzine.whisperdroid.repository

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.valenzine.whisperdroid.model.DEFAULT_LLM_PROMPT
import com.valenzine.whisperdroid.model.ModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val llmModelKey = stringPreferencesKey("llm_model")
private const val RETIRED_LLM_MODEL = "gpt-5-nano"

internal object RetiredLlmModelMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[llmModelKey] == RETIRED_LLM_MODEL

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply { this[llmModelKey] = ModelCatalog.DEFAULT_LLM_MODEL }

    override suspend fun cleanUp() = Unit
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(RetiredLlmModelMigration) }
)

data class AppSettings(
    val apiKey: String = "",
    val llmPrompt: String = DEFAULT_LLM_PROMPT,
    val transcriptionModel: String = ModelCatalog.DEFAULT_TRANSCRIPTION_MODEL,
    val llmModel: String = ModelCatalog.DEFAULT_LLM_MODEL,
    val autoProcess: Boolean = false
)

interface SettingsGateway {
    val settingsFlow: Flow<AppSettings>
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsGateway {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    private val apiKey = stringPreferencesKey("api_key")
    private val llmPrompt = stringPreferencesKey("llm_prompt")
    private val transcriptionModel = stringPreferencesKey("transcription_model")
    private val autoProcess = booleanPreferencesKey("auto_process")

    override val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            apiKey = preferences[apiKey].orEmpty(),
            llmPrompt = preferences[llmPrompt]?.takeIf(String::isNotBlank) ?: DEFAULT_LLM_PROMPT,
            transcriptionModel = preferences[transcriptionModel] ?: ModelCatalog.DEFAULT_TRANSCRIPTION_MODEL,
            llmModel = preferences[llmModelKey] ?: ModelCatalog.DEFAULT_LLM_MODEL,
            autoProcess = preferences[autoProcess] ?: false
        )
    }

    /** Persist the Settings form in a single transaction so requests never see a partial form save. */
    suspend fun saveSettings(settings: AppSettings) {
        dataStore.edit {
            it[apiKey] = settings.apiKey
            it[llmPrompt] = settings.llmPrompt.ifBlank { DEFAULT_LLM_PROMPT }
            it[transcriptionModel] = settings.transcriptionModel
            it[llmModelKey] = settings.llmModel.takeUnless { model -> model == RETIRED_LLM_MODEL }
                ?: ModelCatalog.DEFAULT_LLM_MODEL
            it[autoProcess] = settings.autoProcess
        }
    }

    suspend fun saveLlmPrompt(prompt: String) = dataStore.edit {
        it[llmPrompt] = prompt.ifBlank { DEFAULT_LLM_PROMPT }
    }
    suspend fun saveAutoProcess(enabled: Boolean) = dataStore.edit { it[autoProcess] = enabled }
}
