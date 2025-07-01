package com.valenzine.whisperdroid.networking

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TranscriptionApi {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Part file: MultipartBody.Part,
        @Part model: MultipartBody.Part
    ): TranscriptionResponse
}

data class TranscriptionResponse(
    val text: String
)