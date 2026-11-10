package com.valenzine.whisperdroid.model

/** A deliberately small catalogue of models supported by this UI and API shape. */
data class TranscriptionModelOption(
    val id: String,
    val label: String,
    /** Opus in an OGG container must be remuxed for these file models. */
    val remuxesOpusOgg: Boolean = false
)

data class LlmModelOption(
    val id: String,
    val label: String,
    val description: String
)

object ModelCatalog {
    const val GPT_6_LUNA = "gpt-6-luna"

    val transcriptionModels = listOf(
        TranscriptionModelOption("gpt-transcribe", "GPT Transcribe (recommended)", remuxesOpusOgg = true),
        TranscriptionModelOption("gpt-4o-mini-transcribe", "GPT-4o mini Transcribe", remuxesOpusOgg = true),
        TranscriptionModelOption("gpt-4o-transcribe", "GPT-4o Transcribe", remuxesOpusOgg = true),
        TranscriptionModelOption("whisper-1", "Whisper-1 (compatibility)")
    )

    val llmModels = listOf(
        LlmModelOption("gpt-5.6-luna", "GPT-5.6 Luna (default)", "Cost-effective editing"),
        LlmModelOption(GPT_6_LUNA, "GPT-6 Luna", "Low-cost editing")
    )

    const val DEFAULT_TRANSCRIPTION_MODEL = "gpt-transcribe"
    const val DEFAULT_LLM_MODEL = "gpt-5.6-luna"

    fun transcriptionModel(id: String): TranscriptionModelOption =
        transcriptionModels.firstOrNull { it.id == id } ?: transcriptionModels.first()
}
