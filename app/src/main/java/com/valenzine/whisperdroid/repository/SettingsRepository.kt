package com.valenzine.whisperdroid.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
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

const val DEFAULT_PROMPT_ID = "default"
internal const val MIGRATED_PROMPT_ID = "migrated"

private val legacyLlmPromptKey = stringPreferencesKey("llm_prompt")
private val savedPromptsKey = stringPreferencesKey("saved_prompts")
private val activePromptIdKey = stringPreferencesKey("active_prompt_id")
private val promptGson = Gson()

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

private fun List<SavedPrompt>.ensureBuiltIn(): List<SavedPrompt> =
    if (any { it.id == DEFAULT_PROMPT_ID }) this else listOf(builtInPrompt()) + this

internal fun decodePrompts(serialized: String?): List<SavedPrompt> {
    val decoded = runCatching {
        promptGson.fromJson<List<SavedPrompt>>(serialized, object : TypeToken<List<SavedPrompt>>() {}.type)
    }.getOrNull().orEmpty()
    return decoded.ensureBuiltIn().takeIf { decoded.isNotEmpty() } ?: listOf(builtInPrompt())
}

private fun storePrompts(preferences: MutablePreferences, prompts: List<SavedPrompt>, selectedId: String) {
    val normalized = prompts.ensureBuiltIn()
    val activeId = selectedId.takeIf { id -> normalized.any { it.id == id } } ?: DEFAULT_PROMPT_ID
    preferences[savedPromptsKey] = promptGson.toJson(normalized)
    preferences[activePromptIdKey] = activeId
    preferences[legacyLlmPromptKey] = normalized.first { it.id == activeId }.content
}

internal object SavedPromptMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[savedPromptsKey].isNullOrBlank()

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        val legacyPrompt = currentData[legacyLlmPromptKey]?.takeIf(String::isNotBlank)
        if (legacyPrompt == null || legacyPrompt == DEFAULT_LLM_PROMPT) {
            storePrompts(this, listOf(builtInPrompt()), DEFAULT_PROMPT_ID)
        } else {
            storePrompts(
                this,
                listOf(builtInPrompt(), SavedPrompt(MIGRATED_PROMPT_ID, "Migrated prompt", legacyPrompt)),
                MIGRATED_PROMPT_ID
            )
        }
    }

    override suspend fun cleanUp() = Unit
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(RetiredLlmModelMigration, SavedPromptMigration) }
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
    private val transcriptionModel = stringPreferencesKey("transcription_model")
    private val autoProcess = booleanPreferencesKey("auto_process")

    override val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        val prompts = decodePrompts(preferences[savedPromptsKey])
        val selectedPromptId = preferences[activePromptIdKey]
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
            it[transcriptionModel] = settings.transcriptionModel
            it[llmModelKey] = settings.llmModel.takeUnless { model -> model == RETIRED_LLM_MODEL }
                ?: ModelCatalog.DEFAULT_LLM_MODEL
            it[autoProcess] = settings.autoProcess
        }
    }

    suspend fun selectPrompt(id: String) = dataStore.edit { preferences ->
        val prompts = decodePrompts(preferences[savedPromptsKey])
        if (prompts.any { it.id == id }) storePrompts(preferences, prompts, id)
    }

    suspend fun createPrompt(name: String, content: String) = dataStore.edit { preferences ->
        val prompts = decodePrompts(preferences[savedPromptsKey])
        val prompt = SavedPrompt(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            content = content.ifBlank { DEFAULT_LLM_PROMPT }
        )
        storePrompts(preferences, prompts + prompt, prompt.id)
    }

    suspend fun updatePrompt(prompt: SavedPrompt) = dataStore.edit { preferences ->
        val prompts = decodePrompts(preferences[savedPromptsKey])
        val updated = prompts.map { saved ->
            if (saved.id != prompt.id || saved.isBuiltIn) saved
            else prompt.copy(name = prompt.name.trim(), content = prompt.content.ifBlank { DEFAULT_LLM_PROMPT })
        }
        val activeId = preferences[activePromptIdKey]?.takeIf { id -> updated.any { it.id == id } } ?: updated.first().id
        storePrompts(preferences, updated, activeId)
    }

    suspend fun deletePrompt(id: String) = dataStore.edit { preferences ->
        val prompts = decodePrompts(preferences[savedPromptsKey])
        val prompt = prompts.firstOrNull { it.id == id } ?: return@edit
        if (prompt.isBuiltIn) return@edit
        val remaining = prompts.filterNot { it.id == id }
        val activeId = if (preferences[activePromptIdKey] == id) DEFAULT_PROMPT_ID else preferences[activePromptIdKey] ?: DEFAULT_PROMPT_ID
        storePrompts(preferences, remaining, activeId)
    }
    suspend fun saveAutoProcess(enabled: Boolean) = dataStore.edit { it[autoProcess] = enabled }

}
