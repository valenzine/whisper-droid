package com.valenzine.whisperdroid.repository

import android.content.Context
import com.valenzine.whisperdroid.networking.*
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class TranscriptionRepository(private val context: Context) {

    private val settingsRepository = SettingsRepository(context)

    private val transcriptionApi:
            TranscriptionApi
    private val llmApi: LlmApi

    init {
        // Add logging interceptor for debugging HTTP requests
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        transcriptionApi = retrofit.create(TranscriptionApi::class.java)
        llmApi = retrofit.create(LlmApi::class.java)
    }


    suspend fun transcribeFile(file: File): String {
        val apiKey = settingsRepository.transcriptionApiKeyFlow.first()
        
        // Handle unsupported formats by faking the file extension and MIME type
        val originalName = file.name
        val fakeFileName = when {
            originalName.endsWith(".opus", ignoreCase = true) -> {
                originalName.substringBeforeLast(".") + ".mp3"
            }
            originalName.endsWith(".ogg", ignoreCase = true) -> {
                originalName.substringBeforeLast(".") + ".mp3"
            }
            else -> originalName
        }
        
        // Use audio/mpeg MIME type for unsupported formats to trick the API
        val mimeType = when {
            originalName.endsWith(".opus", ignoreCase = true) -> "audio/mpeg"
            originalName.endsWith(".ogg", ignoreCase = true) -> "audio/mpeg"
            else -> java.net.URLConnection.guessContentTypeFromName(file.name) 
                ?: context.contentResolver.getType(android.net.Uri.fromFile(file))
                ?: "audio/mpeg" // Default to audio/mpeg as fallback
        }
        
        // Prepare file part with fake filename and MIME type
        val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", fakeFileName, requestFile)
        // Prepare model request body
        val modelRequestBody = "whisper-1".toRequestBody("text/plain".toMediaTypeOrNull())
        // Call API with authorization header and model RequestBody
        val response = try {
            transcriptionApi.transcribe(
                authorization = "Bearer $apiKey",
                file = filePart,
                model = modelRequestBody
            )
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            throw Exception("HTTP ${e.code()} error: $errorBody")
        }
        return response.text
    }

    suspend fun formatText(text: String): String {
        val apiKey = settingsRepository.llmApiKeyFlow.first()
        val customPrompt = settingsRepository.llmPromptFlow.first()
        
        // Use custom prompt if provided, otherwise use default
        // Always append the transcribed text to the prompt
        val userPrompt = if (customPrompt.isNotBlank()) {
            "$customPrompt\n\n$text"
        } else {
            "Format the following text with paragraphs:\n\n$text"
        }
        
        val request = LlmRequest(
            model = "gpt-4.1-mini",
            messages = listOf(
                Message("system", "You are a helpful assistant that formats text."),
                Message("user", userPrompt)
            )
        )
        // Include the API key in the Authorization header
        val response = llmApi.formatText(
            authorization = "Bearer $apiKey",
            request = request
        )
        return response.choices.first().message.content
    }
}