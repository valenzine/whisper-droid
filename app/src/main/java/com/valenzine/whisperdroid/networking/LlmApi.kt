package com.valenzine.whisperdroid.networking

import retrofit2.http.Body
import retrofit2.http.POST

interface LlmApi {
    @POST("v1/chat/completions")
    suspend fun formatText(@Body request: LlmRequest): LlmResponse
}

data class LlmRequest(
    val model: String,
    val messages: List<Message>
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