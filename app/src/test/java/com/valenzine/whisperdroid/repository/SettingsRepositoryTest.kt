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
    fun settingsSavePreservesTheActivePromptAndPromptList() = runTest {
        val repository = newRepository()
        repository.createPrompt("Meeting notes", "Make concise paragraphs.")
        val promptState = repository.settingsFlow.first()

        repository.saveSettings(
            AppSettings(
            apiKey = "sk-test",
            transcriptionModel = "gpt-4o-mini-transcribe",
            llmModel = "gpt-6-luna",
            autoProcess = true
            )
        )

        val actual = repository.settingsFlow.first()
        assertEquals(promptState.prompts, actual.prompts)
        assertEquals(promptState.activePromptId, actual.activePromptId)
        assertEquals("Make concise paragraphs.", actual.llmPrompt)
        assertEquals("gpt-4o-mini-transcribe", actual.transcriptionModel)
    }

    @Test
    fun promptAndAutoUpdatesPreserveSelectedModels() = runTest {
        val repository = newRepository()
        repository.saveSettings(
            AppSettings(
                apiKey = "sk-test",
                transcriptionModel = "whisper-1",
                llmModel = "gpt-6-luna"
            )
        )
        repository.createPrompt("Initial", "Initial prompt")
        val customPrompt = repository.settingsFlow.first().activePrompt

        repository.updatePrompt(customPrompt.copy(content = "Updated prompt"))
        repository.saveAutoProcess(true)

        val settings = repository.settingsFlow.first()
        assertEquals("whisper-1", settings.transcriptionModel)
        assertEquals("gpt-6-luna", settings.llmModel)
        assertEquals("Updated prompt", settings.llmPrompt)
        assertEquals(true, settings.autoProcess)
    }

    @Test
    fun legacyCustomPromptMigratesVerbatimAndBecomesActive() = runTest {
        val legacyText = " Keep this exact instruction.  "
        val legacy = preferencesOf(stringPreferencesKey("llm_prompt") to legacyText)

        val migrated = SavedPromptMigration.migrate(legacy)

        assertEquals(MIGRATED_PROMPT_ID, migrated[stringPreferencesKey("active_prompt_id")])
        val prompts = decodePrompts(migrated[stringPreferencesKey("saved_prompts")])
        assertEquals(legacyText, prompts.first { it.id == MIGRATED_PROMPT_ID }.content)
    }

    @Test
    fun builtInDefaultKeepsItsIdentityWhileItsContentCanBeCustomizedAndReset() = runTest {
        val repository = newRepository()

        repository.updatePrompt(SavedPrompt(DEFAULT_PROMPT_ID, "Changed", "Changed content"))

        val default = repository.settingsFlow.first().activePrompt
        assertEquals("Default prompt", default.name)
        assertEquals("Changed content", default.content)
        assertEquals(true, default.isBuiltIn)

        repository.resetDefaultPrompt()
        assertEquals(DEFAULT_LLM_PROMPT, repository.settingsFlow.first().activePrompt.content)
    }

    @Test
    fun decodedPromptsReplaceAndDeduplicateDefaultEntries() {
        val prompts = decodePrompts(
            """[
                {"id":"default","name":"Changed","content":"Changed","isBuiltIn":false},
                {"id":"default","name":"Duplicate","content":"Duplicate","isBuiltIn":false},
                {"id":"custom","name":"Custom","content":"Keep this","isBuiltIn":false}
            ]"""
        )

        assertEquals(2, prompts.size)
        assertEquals(DEFAULT_PROMPT_ID, prompts.first().id)
        assertEquals("Default prompt", prompts.first().name)
        assertEquals("Changed", prompts.first().content)
        assertEquals(true, prompts.first().isBuiltIn)
        assertEquals("custom", prompts.last().id)
    }

    @Test
    fun delayedPromptUpdateTargetsItsOriginalPromptInsteadOfTheActivePrompt() = runTest {
        val repository = newRepository()
        repository.createPrompt("First", "First content")
        val first = repository.settingsFlow.first().activePrompt
        repository.createPrompt("Second", "Second content")
        val second = repository.settingsFlow.first().activePrompt

        repository.updatePrompt(first.copy(content = "Updated first content"))

        val settings = repository.settingsFlow.first()
        assertEquals(second.id, settings.activePromptId)
        assertEquals("Second content", settings.llmPrompt)
        assertEquals("Updated first content", settings.prompts.first { it.id == first.id }.content)
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
        return SettingsRepository(PreferenceDataStoreFactory.create(
            migrations = listOf(SavedPromptMigration),
            scope = backgroundScope
        ) { file })
    }
}
