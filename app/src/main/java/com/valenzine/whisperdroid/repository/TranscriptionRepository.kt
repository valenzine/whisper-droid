package com.valenzine.whisperdroid.repository

import android.content.Context
import com.valenzine.whisperdroid.networking.*
import kotlinx.coroutines.flow.first
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class TranscriptionRepository(context: Context) {

    private val settingsRepository = SettingsRepository(context)

    private val transcriptionApi:
    TranscriptionApi
    private val llmApi: LlmApi

    init {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request().newBuilder()
            val apiKey = kotlinx.coroutines.runBlocking { settingsRepository.transcriptionApiKeyFlow.first() }
            request.addHeader("Authorization", "Bearer $apiKey")
            chain.proceed(request.build())
        }.build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        transcriptionApi = retrofit.create(TranscriptionApi::class.java)
        llmApi = retrofit.create(LlmApi::class.java)
    }


    suspend fun transcribeFile(file: File): String {
        val requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val model = MultipartBody.Part.createFormData("model", "whisper-1")
        val response = transcriptionApi.transcribe(body, model)
        return response.text
    }

    suspend fun formatText(text: String): String {
        val request = LlmRequest(
            model = "gpt-3.5-turbo",
            messages = listOf(
                Message("system", "You are a helpful assistant that formats text."),
                Message("user", "Format the following text with paragraphs:\n\n$text")
            )
        )
        val response = llmApi.formatText(request)
        return response.choices.first().message.content
    }
}