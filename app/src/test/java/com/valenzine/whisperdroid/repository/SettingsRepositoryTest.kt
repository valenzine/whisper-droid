package com.valenzine.whisperdroid.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.valenzine.whisperdroid.model.DEFAULT_LLM_PROMPT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @Test
    fun defaultsUseRecommendedModelsEffectivePromptAndDisabledAutoProcessing() = runTest {
        val settings = newRepository().settingsFlow.first()

        assertEquals("gpt-transcribe", settings.transcriptionModel)
        assertEquals("gpt-5.6-luna", settings.llmModel)
        assertEquals(false, settings.autoProcess)
        assertEquals(DEFAULT_LLM_PROMPT, settings.llmPrompt)
    }

    @Test
    fun saveSettingsPersistsTheEntireFormAtomically() = runTest {
        val repository = newRepository()
        val expected = AppSettings(
            apiKey = "sk-test",
            llmPrompt = "Make concise paragraphs.",
            transcriptionModel = "gpt-4o-mini-transcribe",
            llmModel = "gpt-6-luna",
            autoProcess = true
        )

        repository.saveSettings(expected)

        val actual = repository.settingsFlow.first()
        assertEquals(expected.apiKey, actual.apiKey)
        assertEquals(expected.transcriptionModel, actual.transcriptionModel)
        assertEquals("migrated", actual.activePromptId)
        assertEquals(expected.llmPrompt, actual.llmPrompt)
    }

    @Test
    fun promptAndAutoUpdatesPreserveSelectedModels() = runTest {
        val repository = newRepository()
        repository.saveSettings(
            AppSettings(
                apiKey = "sk-test",
                llmPrompt = "Initial prompt",
                transcriptionModel = "whisper-1",
                llmModel = "gpt-6-luna"
            )
        )

        repository.saveLlmPrompt("Updated prompt")
        repository.saveAutoProcess(true)

        val settings = repository.settingsFlow.first()
        assertEquals("whisper-1", settings.transcriptionModel)
        assertEquals("gpt-6-luna", settings.llmModel)
        assertEquals("Updated prompt", settings.llmPrompt)
        assertEquals(true, settings.autoProcess)
    }

    @Test
    fun blankPromptIsStoredAndExposedAsTheEffectiveDefault() = runTest {
        val repository = newRepository()

        repository.saveSettings(AppSettings(llmPrompt = "   "))
        assertEquals(DEFAULT_LLM_PROMPT, repository.settingsFlow.first().llmPrompt)

        repository.saveLlmPrompt("")
        assertEquals(DEFAULT_LLM_PROMPT, repository.settingsFlow.first().llmPrompt)
    }

    @Test
    fun savedNanoModelMigratesWithoutChangingOtherSettings() = runTest {
        val model = stringPreferencesKey("llm_model")
        val prompt = stringPreferencesKey("llm_prompt")
        val legacy = preferencesOf(model to "gpt-5-nano", prompt to "Keep my wording.")

        assertEquals(true, RetiredLlmModelMigration.shouldMigrate(legacy))
        val migrated = RetiredLlmModelMigration.migrate(legacy)
        assertEquals("gpt-5.6-luna", migrated[model])
        assertEquals("Keep my wording.", migrated[prompt])
        assertEquals(false, RetiredLlmModelMigration.shouldMigrate(migrated))
    }

    @Test
    fun savedPromptsCanBeSelectedEditedAndDeletedWithoutRemovingTheDefault() = runTest {
        val repository = newRepository()

        repository.createPrompt("Meeting notes", "Write concise meeting notes.")
        val created = repository.settingsFlow.first()
        val custom = created.activePrompt
        assertEquals("Meeting notes", custom.name)
        assertEquals("Write concise meeting notes.", created.llmPrompt)

        repository.updatePrompt(custom.copy(name = "Notes", content = "Keep the speaker's style."))
        assertEquals("Keep the speaker's style.", repository.settingsFlow.first().llmPrompt)

        repository.deletePrompt(custom.id)
        val deleted = repository.settingsFlow.first()
        assertEquals(DEFAULT_PROMPT_ID, deleted.activePromptId)
        assertEquals(DEFAULT_LLM_PROMPT, deleted.llmPrompt)
        assertEquals(1, deleted.prompts.size)
    }

    private fun TestScope.newRepository(): SettingsRepository {
        val file = File.createTempFile("settings-test", ".preferences_pb").apply { delete() }
        return SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        )
    }
}
