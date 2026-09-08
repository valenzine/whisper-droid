package com.valenzine.whisperdroid.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.valenzine.whisperdroid.model.ModelCatalog
import com.valenzine.whisperdroid.networking.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

interface TranscriptionGateway {
    suspend fun transcribeUri(
        uri: Uri,
        apiKey: String,
        model: String,
        language: String? = null,
        onProgress: ((String) -> Unit)? = null
    ): String

    suspend fun formatText(text: String, apiKey: String, prompt: String, model: String): String
}

class TranscriptionRepository(context: Context) : TranscriptionGateway {
    private val context = context.applicationContext

    private val transcriptionApi: TranscriptionApi
    private val llmApi: LlmApi

    init {
        val client = OkHttpClient.Builder()
            .apply {
                if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    })
                }
            }
            // Increase timeouts to handle slower networks / larger uploads
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            // Overall call timeout (optional) - slightly longer than writeTimeout
            .callTimeout(6, TimeUnit.MINUTES)
            // Keep-alive pings can help detect broken connections earlier
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        transcriptionApi = retrofit.create(TranscriptionApi::class.java)
        llmApi = retrofit.create(LlmApi::class.java)
    }

    /**
     * Remuxes Opus/OGG files into a WebM container for compatible transcription models.
     * 
     * @param inputFile The input Opus/OGG file
     * @return The converted WebM file
     * @throws Exception If conversion fails for any reason
     */
    private fun convertOpusToWebM(inputFile: File, model: String): File {
        
        val outputFile = File(inputFile.parent, inputFile.nameWithoutExtension + "_converted.webm")
        
        try {
            // Use the MediaCodec implementation for WebM conversion
            return convertOpusToWebMMediaCodec(inputFile)
            
        } catch (e: Exception) {
            
            // Clean up any partial files
            if (outputFile.exists()) outputFile.delete()
            
            throw Exception("Opus to WebM conversion failed: ${e.message}. Raw Opus/OGG cannot be sent to $model. Try whisper-1 instead.")
        }
    }
    
    /**
     * Converts Opus/OGG files to WebM format using Android's MediaCodec API.
     * This method remuxes Opus audio from OGG container to WebM container without re-encoding.
     * WebM is a more modern container format that's better supported by web APIs.
     *
     * @param inputFile The input Opus/OGG file
     * @return The converted WebM file
     * @throws Exception If MediaCodec conversion fails (missing codec, invalid file format, etc.)
     */
    private fun convertOpusToWebMMediaCodec(inputFile: File): File {
        val outputFile = File(inputFile.parent, inputFile.nameWithoutExtension + "_converted.webm")
        
        try {
            // Set up extractor for input file
            val extractor = android.media.MediaExtractor()
            try {
                val uri = android.net.Uri.fromFile(inputFile)
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                extractor.release()
                throw Exception("Cannot process this Opus file. Please use whisper-1 model instead.")
            }

            try {
                // Find the audio track
                var audioTrackIndex = -1
                var inputFormat: android.media.MediaFormat? = null

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME)

                    if (mime?.startsWith("audio/") == true) {
                        extractor.selectTrack(i)
                        audioTrackIndex = i
                        inputFormat = format
                        break
                    }
                }

                if (audioTrackIndex == -1) {
                    throw Exception("No audio track found in the input file")
                }
                val selectedFormat = inputFormat ?: throw Exception("No audio track found in the input file")

                val muxer = android.media.MediaMuxer(
                    outputFile.absolutePath,
                    android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                )
                var muxerStarted = false
                try {
                    val outputFormat = android.media.MediaFormat.createAudioFormat(
                        "audio/opus",
                        selectedFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE),
                        selectedFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    )
                    if (selectedFormat.containsKey("csd-0")) {
                        outputFormat.setByteBuffer("csd-0", selectedFormat.getByteBuffer("csd-0"))
                    }
                    if (selectedFormat.containsKey("csd-1")) {
                        outputFormat.setByteBuffer("csd-1", selectedFormat.getByteBuffer("csd-1"))
                    }

                    val trackIndex = muxer.addTrack(outputFormat)
                    muxer.start()
                    muxerStarted = true

                    val bufferInfo = android.media.MediaCodec.BufferInfo()
                    val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        bufferInfo.flags = 0

                        muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                } finally {
                    if (muxerStarted) runCatching { muxer.stop() }
                    muxer.release()
                }
            } finally {
                extractor.release()
            }
            
            if (outputFile.exists() && outputFile.length() > 0) {
                return outputFile
            } else {
                throw Exception("WebM conversion failed - output file is empty or missing")
            }
            
        } catch (e: Exception) {
            // Clean up
            if (outputFile.exists()) outputFile.delete()
            throw Exception("WebM conversion failed: ${e.message}")
        }
    }
    
    override suspend fun transcribeUri(
        uri: Uri,
        apiKey: String,
        model: String,
        language: String?,
        onProgress: ((String) -> Unit)?
    ): String = withContext(Dispatchers.IO) {
        val copiedFile = copyUriToCache(uri)
        try {
            transcribeFile(copiedFile, apiKey, model, language, onProgress)
        } finally {
            copiedFile.delete()
        }
    }

    private suspend fun copyUriToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        val metadata = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = nameIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
            val size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            name to size
        }
        val displayName = metadata?.first ?: "audio"
        val declaredSize = metadata?.second
        require(declaredSize == null || declaredSize <= MAX_UPLOAD_BYTES) { "File too large (max 25 MB)" }
        val extension = displayName.substringAfterLast('.', "")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(16)
        val suffix = if (extension.isNotBlank()) ".${extension}" else ".audio"
        val temporary = File.createTempFile("audio-${UUID.randomUUID()}-", suffix, context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_UPLOAD_BYTES) { "File too large (max 25 MB)" }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Could not open selected audio file")
            temporary
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private suspend fun transcribeFile(
        file: File,
        apiKey: String,
        model: String,
        language: String? = null,
        onProgress: ((String) -> Unit)? = null
    ): String {
        val originalName = file.name
        val isOpus = originalName.endsWith(".opus", ignoreCase = true)
        val isOgg = originalName.endsWith(".ogg", ignoreCase = true)
        val needsTranscoding = ModelCatalog.transcriptionModel(model).remuxesOpusOgg && (isOpus || isOgg)

        onProgress?.invoke("Analyzing file...")
        
        
        val (fileToUpload, cleanupNeeded) = if (needsTranscoding) {
            // Remux into a supported WebM container without re-encoding the audio.
            onProgress?.invoke("Converting ${originalName.substringAfterLast(".")} to WebM...")
            try {
                val convertedFile = convertOpusToWebM(file, model)
                convertedFile to true
            } catch (e: Exception) {
                // Conversion failed - provide helpful error message
                val errorMsg = when {
                    e.message?.contains("WebM conversion failed") == true -> 
                        e.message!! // Use the detailed message from convertOpusToWebM
                    else -> 
                        "Opus file conversion failed for $model. Use whisper-1 for Opus files, or convert the file to WAV/MP3 first."
                }
                throw Exception(errorMsg)
            }
        } else {
            // Use original file
            file to false
        }
        
        onProgress?.invoke("Preparing file for upload...")
        
        // Determine MIME type and filename based on model and conversion
        val (finalMimeType, finalFileName) = when {
            needsTranscoding -> {
                // We're now outputting WebM files (efficient Opus in WebM container)
                if (fileToUpload.name.endsWith(".webm", ignoreCase = true)) {
                    // WebM file - use audio/webm MIME type
                    "audio/webm" to fileToUpload.name
                } else {
                    // Fallback for other converted formats
                    "audio/mpeg" to fileToUpload.name
                }
            }
            model == "whisper-1" && (originalName.endsWith(".opus", ignoreCase = true) || originalName.endsWith(".ogg", ignoreCase = true)) -> {
                // Fake MIME type for whisper-1 compatibility
                "audio/mpeg" to "${originalName.substringBeforeLast(".")}.mp3"
            }
            else -> {
                // Use proper MIME type detection for supported formats
                val mimeType = when {
                    fileToUpload.name.endsWith(".aac", ignoreCase = true) -> "audio/aac"
                    fileToUpload.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                    fileToUpload.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                    fileToUpload.name.endsWith(".webm", ignoreCase = true) -> "audio/webm"
                    else -> java.net.URLConnection.guessContentTypeFromName(fileToUpload.name)
                        ?: context.contentResolver.getType(android.net.Uri.fromFile(fileToUpload))
                        ?: "audio/mpeg"
                }
                mimeType to fileToUpload.name
            }
        }
        
        // Prepare file part
        val requestFile = fileToUpload.asRequestBody(finalMimeType.toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", finalFileName, requestFile)
        val modelRequestBody = model.toRequestBody("text/plain".toMediaTypeOrNull())
        val languagePart = language?.takeIf { it.isNotBlank() }?.let {
            it.toRequestBody("text/plain".toMediaTypeOrNull())
        }

        onProgress?.invoke("Uploading to ${model}...")

        val response = try {
            transcriptionApi.transcribe(
                authorization = "Bearer $apiKey",
                file = filePart,
                model = modelRequestBody,
                language = languagePart
            )
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val enhancedError = when (e.code()) {
                400 -> {
                    if (errorBody?.contains("unsupported") == true || errorBody?.contains("corrupted") == true) {
                        if (originalName.endsWith(".opus", ignoreCase = true) || originalName.endsWith(".ogg", ignoreCase = true)) {
                            if (ModelCatalog.transcriptionModel(model).remuxesOpusOgg) {
                                "Opus file failed to convert or is unsupported by $model. Try using whisper-1 model instead, or convert to MP3/M4A/WAV format."
                            } else {
                                "Opus files are not fully supported by $model. Try using whisper-1 model instead, or convert to MP3/M4A/WAV format."
                            }
                        } else {
                            "Audio file format is unsupported or file may be corrupted. Supported formats: MP3, AAC, WAV, FLAC (whisper-1 also supports Opus/OGG)."
                        }
                    } else {
                        "Invalid request: $errorBody"
                    }
                }
                401 -> "Invalid API key. Please check your OpenAI API key in settings."
                413 -> "File too large. Maximum file size is 25MB."
                429 -> "Rate limit exceeded. Please wait and try again."
                500, 502, 503, 504 -> "Server error. Please try again in a few moments."
                else -> "HTTP ${e.code()} error: $errorBody"
            }
            throw Exception(enhancedError)
        } finally {
            // Clean up converted file if needed  
            if (cleanupNeeded && fileToUpload != file) {
                fileToUpload.delete()
            }
        }

        return response.text
    }

    override suspend fun formatText(text: String, apiKey: String, prompt: String, model: String): String {
        require(text.isNotBlank()) { "There is no transcription to format" }
        val userPrompt = "$prompt\n\n$text"
        
        val request = LlmRequest(
            model = model,
            messages = listOf(
                Message("system", "You are a direct text formatting engine. Provide ONLY the formatted text requested by the user, with no additional commentary, introductions, or conclusions."),
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
    
    private companion object {
        const val MAX_UPLOAD_BYTES = 25L * 1024L * 1024L
    }
}
