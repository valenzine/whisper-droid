package com.valenzine.whisperdroid.repository

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.valenzine.whisperdroid.model.DEFAULT_LLM_PROMPT
import com.valenzine.whisperdroid.model.ModelCatalog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

const val DEFAULT_PROMPT_ID = "default"

data class SavedPrompt(
    val id: String,
    val name: String,
    val content: String,
    val isBuiltIn: Boolean = false
)

private fun builtInPrompt() = SavedPrompt(
    id = DEFAULT_PROMPT_ID,
    name = "Default prompt",
    content = DEFAULT_LLM_PROMPT,
    isBuiltIn = true
)

data class AppSettings(
    val apiKey: String = "",
    val llmPrompt: String = DEFAULT_LLM_PROMPT,
    val prompts: List<SavedPrompt> = listOf(builtInPrompt()),
    val activePromptId: String = DEFAULT_PROMPT_ID,
    val transcriptionModel: String = ModelCatalog.DEFAULT_TRANSCRIPTION_MODEL,
    val llmModel: String = ModelCatalog.DEFAULT_LLM_MODEL,
    val autoProcess: Boolean = false
) {
    val activePrompt: SavedPrompt
        get() = prompts.firstOrNull { it.id == activePromptId } ?: prompts.first()
}

interface SettingsGateway {
    val settingsFlow: Flow<AppSettings>
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsGateway {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    private val apiKey = stringPreferencesKey("api_key")
    private val llmPrompt = stringPreferencesKey("llm_prompt")
    private val savedPrompts = stringPreferencesKey("saved_prompts")
    private val activePromptId = stringPreferencesKey("active_prompt_id")
    private val transcriptionModel = stringPreferencesKey("transcription_model")
    private val autoProcess = booleanPreferencesKey("auto_process")

    private val gson = Gson()

    override val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        val prompts = decodePrompts(preferences[savedPrompts], preferences[llmPrompt])
        val selectedPromptId = preferences[activePromptId]
            ?.takeIf { id -> prompts.any { it.id == id } }
            ?: prompts.first().id
        AppSettings(
            apiKey = preferences[apiKey].orEmpty(),
            llmPrompt = prompts.first { it.id == selectedPromptId }.content,
            prompts = prompts,
            activePromptId = selectedPromptId,
            transcriptionModel = preferences[transcriptionModel] ?: ModelCatalog.DEFAULT_TRANSCRIPTION_MODEL,
            llmModel = preferences[llmModelKey] ?: ModelCatalog.DEFAULT_LLM_MODEL,
            autoProcess = preferences[autoProcess] ?: false
        )
    }

    /** Persist the Settings form in a single transaction so requests never see a partial form save. */
    suspend fun saveSettings(settings: AppSettings) {
        dataStore.edit {
            it[apiKey] = settings.apiKey
            val isLegacyCustomPrompt = settings.prompts == listOf(builtInPrompt()) &&
                settings.llmPrompt.isNotBlank() && settings.llmPrompt != DEFAULT_LLM_PROMPT
            val prompts = if (isLegacyCustomPrompt) {
                listOf(builtInPrompt(), SavedPrompt("migrated", "Migrated prompt", settings.llmPrompt))
            } else settings.prompts
            val activeId = if (isLegacyCustomPrompt) "migrated"
            else if (prompts.any { it.id == settings.activePromptId }) settings.activePromptId else prompts.last().id
            storePrompts(it, prompts, activeId)
            it[transcriptionModel] = settings.transcriptionModel
            it[llmModelKey] = settings.llmModel.takeUnless { model -> model == RETIRED_LLM_MODEL }
                ?: ModelCatalog.DEFAULT_LLM_MODEL
            it[autoProcess] = settings.autoProcess
        }
    }

    suspend fun saveLlmPrompt(prompt: String) = dataStore.edit { preferences ->
        val prompts = decodePrompts(preferences[savedPrompts], preferences[llmPrompt])
        val activeId = preferences[activePromptId]?.takeIf { id -> prompts.any { it.id == id } } ?: prompts.first().id
        val updatedPrompts = prompts.map { saved ->
            if (saved.id == activeId) saved.copy(content = prompt.ifBlank { DEFAULT_LLM_PROMPT }) else saved
        }
        storePrompts(preferences, updatedPrompts, activeId)
    }

    suspend fun selectPrompt(id: String) = dataStore.edit { preferences ->
        val prompts = decodePrompts(preferences[savedPrompts], preferences[llmPrompt])
        if (prompts.any { it.id == id }) storePrompts(preferences, prompts, id)
    }

    suspend fun createPrompt(name: String, content: String) = dataStore.edit { preferences ->
        val prompts = decodePrompts(preferences[savedPrompts], preferences[llmPrompt])
        val prompt = SavedPrompt(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            content = content.ifBlank { DEFAULT_LLM_PROMPT }
        )
        storePrompts(preferences, prompts + prompt, prompt.id)
    }

    suspend fun updatePrompt(prompt: SavedPrompt) = dataStore.edit { preferences ->
        val prompts = decodePrompts(preferences[savedPrompts], preferences[llmPrompt])
        val updated = prompts.map { saved ->
            if (saved.id == prompt.id) prompt.copy(
                name = if (prompt.isBuiltIn) saved.name else prompt.name.trim(),
                content = prompt.content.ifBlank { DEFAULT_LLM_PROMPT },
                isBuiltIn = saved.isBuiltIn
            ) else saved
        }
        val activeId = preferences[activePromptId]?.takeIf { id -> updated.any { it.id == id } } ?: updated.first().id
        storePrompts(preferences, updated, activeId)
    }

    suspend fun deletePrompt(id: String) = dataStore.edit { preferences ->
        val prompts = decodePrompts(preferences[savedPrompts], preferences[llmPrompt])
        val prompt = prompts.firstOrNull { it.id == id } ?: return@edit
        if (prompt.isBuiltIn) return@edit
        val remaining = prompts.filterNot { it.id == id }
        val activeId = if (preferences[activePromptId] == id) DEFAULT_PROMPT_ID else preferences[activePromptId] ?: DEFAULT_PROMPT_ID
        storePrompts(preferences, remaining, activeId)
    }
    suspend fun saveAutoProcess(enabled: Boolean) = dataStore.edit { it[autoProcess] = enabled }

    private fun decodePrompts(serialized: String?, legacyPrompt: String?): List<SavedPrompt> {
        if (!serialized.isNullOrBlank()) {
            val decoded = runCatching {
                gson.fromJson<List<SavedPrompt>>(serialized, object : TypeToken<List<SavedPrompt>>() {}.type)
            }.getOrNull().orEmpty()
            if (decoded.isNotEmpty()) return decoded.ensureBuiltIn()
        }
        val oldPrompt = legacyPrompt?.takeIf(String::isNotBlank)
        return if (oldPrompt == null || oldPrompt == DEFAULT_LLM_PROMPT) listOf(builtInPrompt())
        else listOf(builtInPrompt(), SavedPrompt("migrated", "Migrated prompt", oldPrompt))
    }

    private fun List<SavedPrompt>.ensureBuiltIn(): List<SavedPrompt> =
        if (any { it.id == DEFAULT_PROMPT_ID }) this else listOf(builtInPrompt()) + this

    private fun storePrompts(preferences: MutablePreferences, prompts: List<SavedPrompt>, selectedId: String) {
        val normalized = prompts.ensureBuiltIn()
        preferences[savedPrompts] = gson.toJson(normalized)
        preferences[activePromptId] = selectedId.takeIf { id -> normalized.any { it.id == id } } ?: DEFAULT_PROMPT_ID
        preferences[llmPrompt] = normalized.first { it.id == preferences[activePromptId] }.content
    }
}
