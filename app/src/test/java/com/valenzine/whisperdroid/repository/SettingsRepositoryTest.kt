package com.valenzine.whisperdroid.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
            llmModel = "gpt-5-nano",
            autoProcess = true
        )

        repository.saveSettings(expected)

        assertEquals(expected, repository.settingsFlow.first())
    }

    @Test
    fun promptAndAutoUpdatesPreserveSelectedModels() = runTest {
        val repository = newRepository()
        repository.saveSettings(
            AppSettings(
                apiKey = "sk-test",
                llmPrompt = "Initial prompt",
                transcriptionModel = "whisper-1",
                llmModel = "gpt-5-nano"
            )
        )

        repository.saveLlmPrompt("Updated prompt")
        repository.saveAutoProcess(true)

        val settings = repository.settingsFlow.first()
        assertEquals("whisper-1", settings.transcriptionModel)
        assertEquals("gpt-5-nano", settings.llmModel)
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

    private fun TestScope.newRepository(): SettingsRepository {
        val file = File.createTempFile("settings-test", ".preferences_pb").apply { delete() }
        return SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        )
    }
}
