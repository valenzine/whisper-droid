package com.valenzine.whisperdroid.networking

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface LlmApi {
    @POST("v1/chat/completions")
    suspend fun formatText(
        @Header("Authorization") authorization: String,
        @Body request: LlmRequest
    ): LlmResponse
}

data class LlmRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("reasoning_effort") val reasoningEffort: String? = null
)

data class Message(
    val role: String,
    val content: String
)

data class LlmResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)