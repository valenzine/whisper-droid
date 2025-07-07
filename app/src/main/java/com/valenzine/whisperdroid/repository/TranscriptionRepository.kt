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

    // Public getter for context (needed by ViewModel)
    fun getContext(): Context = context

    private val settingsRepository = SettingsRepository(context)

    private val transcriptionApi: TranscriptionApi
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

    /**
     * Converts Opus/OGG files to WebM format for compatibility with OpenAI's gpt-4o-mini-transcribe API.
     * Uses Android MediaCodec APIs to transcode Opus to Opus in WebM container - efficient and OpenAI-compatible.
     * NEVER sends raw Opus files to non-whisper models.
     * 
     * @param inputFile The input Opus/OGG file
     * @return The converted WebM file
     * @throws Exception If conversion fails for any reason
     */
    private fun convertOpusToWebM(inputFile: File): File {
        println("Converting ${inputFile.name} to WebM format for OpenAI compatibility...")
        println("CRITICAL: This conversion is required for gpt-4o-mini-transcribe - raw Opus files are NOT supported")
        
        val outputFile = File(inputFile.parent, inputFile.nameWithoutExtension + "_converted.webm")
        
        // Check if file has Opus magic headers for better diagnostics
        val hasOpusMagic = isOpusFileByMagic(inputFile)
        if (!hasOpusMagic) {
            println("WARNING: File does not appear to have valid Opus headers. Conversion may fail.")
        }
        
        println("Using Android MediaCodec for Opus to WebM conversion")
        println("Input file: ${inputFile.absolutePath}")
        println("Output file: ${outputFile.absolutePath}")
        println("Input file size: ${inputFile.length()} bytes")
        
        try {
            // Use the MediaCodec implementation for WebM conversion
            return convertOpusToWebMMediaCodec(inputFile)
            
        } catch (e: Exception) {
            println("MediaCodec conversion failed: ${e.message}")
            
            // Clean up any partial files
            if (outputFile.exists()) outputFile.delete()
            
            println("CONVERSION FAILED: gpt-4o-mini-transcribe requires actual format conversion, not raw Opus files")
            throw Exception("Opus to WebM conversion failed: ${e.message}. Raw Opus files cannot be sent to gpt-4o-mini-transcribe. Try using whisper-1 model instead.")
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
        println("Starting MediaCodec conversion from Opus to WebM...")
        val outputFile = File(inputFile.parent, inputFile.nameWithoutExtension + "_converted.webm")
        
        try {
            // Check if we have WebM muxer available
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
            val codecs = codecList.codecInfos
            var hasOpusDecoder = false
            
            for (codec in codecs) {
                if (codec.isEncoder) continue
                for (type in codec.supportedTypes) {
                    if (type == "audio/opus") {
                        println("Found Opus decoder: ${codec.name}")
                        hasOpusDecoder = true
                    }
                }
            }
            
            if (!hasOpusDecoder) {
                println("ERROR: No Opus decoder found in MediaCodec")
                throw Exception("Device does not support Opus decoding through MediaCodec. Please use whisper-1 model for Opus files.")
            }
            
            // Set up extractor for input file
            val extractor = android.media.MediaExtractor()
            try {
                val uri = android.net.Uri.fromFile(inputFile)
                extractor.setDataSource(context, uri, null)
                println("MediaExtractor initialized successfully")
            } catch (e: Exception) {
                println("MediaExtractor failed to open the file: ${e.message}")
                throw Exception("Cannot process this Opus file. Please use whisper-1 model instead.")
            }
            
            // Find the audio track
            var audioTrackIndex = -1
            var inputFormat: android.media.MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                println("Found track: $mime")
                
                if (mime?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    audioTrackIndex = i
                    inputFormat = format
                    println("Selected audio track: $mime")
                    break
                }
            }
            
            if (audioTrackIndex == -1 || inputFormat == null) {
                throw Exception("No audio track found in the input file")
            }
            
            // Set up MediaMuxer for WebM output
            val muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
            
            // Create output format for WebM
            val outputFormat = android.media.MediaFormat.createAudioFormat("audio/opus", 
                inputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE),
                inputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT))
            
            // Copy codec-specific data if available
            if (inputFormat.containsKey("csd-0")) {
                outputFormat.setByteBuffer("csd-0", inputFormat.getByteBuffer("csd-0"))
            }
            if (inputFormat.containsKey("csd-1")) {
                outputFormat.setByteBuffer("csd-1", inputFormat.getByteBuffer("csd-1"))
            }
            
            val trackIndex = muxer.addTrack(outputFormat)
            muxer.start()
            
            // Copy samples from input to output
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            
            try {
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = 0 // Reset flags for MediaCodec compatibility
                    
                    muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                    extractor.advance()
                }
                
                println("WebM remuxing complete")
                
            } finally {
                muxer.stop()
                muxer.release()
                extractor.release()
            }
            
            if (outputFile.exists() && outputFile.length() > 0) {
                println("WebM conversion successful - output size: ${outputFile.length()} bytes")
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
    
    /**
     * Logs all available audio codecs and checks for WebM/Opus support.
     * 
     * Returns a pair of booleans (hasWebMSupport, hasOpusDecoder)
     */
    private fun checkAndLogAudioCodecs(): Pair<Boolean, Boolean> {
        val codecList = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
        val codecs = codecList.codecInfos
        var foundWebMSupport = false
        var foundOpusDecoder = false
        
        println("=== Available Audio Decoders ===")
        for (codec in codecs) {
            if (codec.isEncoder) continue
            for (type in codec.supportedTypes) {
                if (type == "audio/opus") {
                    println("Opus decoder found: ${codec.name}")
                    foundOpusDecoder = true
                }
                else if (type.startsWith("audio/")) {
                    println("Decoder: ${codec.name}, Type: $type")
                }
            }
        }
        println("=== End of Audio Decoder List ===")
        
        // Check for WebM support through MediaMuxer capabilities
        try {
            val formats = android.media.MediaMuxer.OutputFormat::class.java.fields
            for (field in formats) {
                if (field.name.contains("WEBM", ignoreCase = true)) {
                    foundWebMSupport = true
                    println("WebM output format supported")
                    break
                }
            }
        } catch (e: Exception) {
            println("Could not check WebM support: ${e.message}")
        }
        
        return Pair(foundWebMSupport, foundOpusDecoder)
    }

    suspend fun transcribeFile(file: File, language: String? = null, onProgress: ((String) -> Unit)? = null): String {
        val apiKey = settingsRepository.transcriptionApiKeyFlow.first()
        val model = settingsRepository.transcriptionModelFlow.first()
        
        println("=== TranscriptionRepository Debug ===")
        println("API Key length: ${apiKey.length}")
        println("Model: '$model'")
        println("File: '${file.name}'")
        println("File exists: ${file.exists()}")
        println("File size: ${file.length()}")
        
        // Log available audio encoders and check for WebM support if transcoding is needed
        val originalName = file.name
        val isOpus = originalName.endsWith(".opus", ignoreCase = true)
        val isOgg = originalName.endsWith(".ogg", ignoreCase = true)
        val isGpt4Mini = model == "gpt-4o-mini-transcribe"
        val needsTranscoding = isGpt4Mini && (isOpus || isOgg)

        if (needsTranscoding) {
            // Log available encoders and decoders for informational purposes
            val (hasWebMSupport, hasOpusDecoder) = checkAndLogAudioCodecs()
            println("Device WebM support available: $hasWebMSupport (needed for efficient conversion)")
            println("Device Opus decoder available: $hasOpusDecoder (needed for conversion)")
        }

        onProgress?.invoke("Analyzing file...")
        
        println("=== Transcoding Logic ===")
        println("File: '$originalName'")
        println("Is Opus: $isOpus")
        println("Is OGG: $isOgg") 
        println("Is gpt-4o-mini-transcribe: $isGpt4Mini")
        println("Needs transcoding: $needsTranscoding")
        println("=========================")
        
        val (fileToUpload, cleanupNeeded) = if (needsTranscoding) {
            // Convert to WebM for gpt-4 model compatibility (lossy compression, smaller file size)
            onProgress?.invoke("Converting ${originalName.substringAfterLast(".")} to WebM...")
            println("TranscriptionRepository: Converting $originalName to WebM for $model")
            try {
                val convertedFile = convertOpusToWebM(file)
                println("TranscriptionRepository: Conversion successful, output: ${convertedFile.name}")
                convertedFile to true
            } catch (e: Exception) {
                println("TranscriptionRepository: Conversion failed: ${e.message}")
                // Conversion failed - provide helpful error message
                val errorMsg = when {
                    e.message?.contains("Device does not support Opus decoding") == true -> 
                        "Your device doesn't support Opus audio decoding. Use whisper-1 model for Opus files, or convert to WAV/MP3 first."
                    e.message?.contains("Not a valid Opus file") == true ->
                        "The file doesn't appear to be a valid Opus file. Try using whisper-1 model instead or convert to WAV/MP3 first."
                    e.message?.contains("WebM conversion failed") == true -> 
                        e.message!! // Use the detailed message from convertOpusToWebM
                    else -> 
                        "Opus file conversion failed for gpt-4o-mini-transcribe. Use whisper-1 model for Opus files, or convert your file to WAV/MP3 format first."
                }
                throw Exception(errorMsg)
            }
        } else {
            println("TranscriptionRepository: Using original file (no conversion needed)")
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

        // Log debug information
        println("TranscriptionRepository: Using model: $model")
        println("TranscriptionRepository: Original filename: $originalName")
        println("TranscriptionRepository: Final filename: $finalFileName")
        println("TranscriptionRepository: MIME type: $finalMimeType")
        println("TranscriptionRepository: Needs transcoding: $needsTranscoding")
        println("TranscriptionRepository: File size: ${fileToUpload.length()} bytes")

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
                            if (model == "gpt-4o-mini-transcribe") {
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

    suspend fun formatText(text: String): String {
        val apiKey = settingsRepository.llmApiKeyFlow.first()
        val customPrompt = settingsRepository.llmPromptFlow.first()
        
        // Use custom prompt if provided, otherwise use default
        // Always append the transcribed text to the prompt
        val userPrompt = if (customPrompt.isNotBlank()) {
            "$customPrompt\n\n$text"
        } else {
            "You are an editor working with spoken dictation. Please edit the following transcript for grammar, punctuation, and readability while retaining my original tone and speaking style. Make sure the text flows naturally and is organized into logical paragraphs. Return only the edited text in the original language, respecting regional language specifics. Do not translate:\n\n$text"
        }
        
        val request = LlmRequest(
            model = "gpt-4.1-nano",
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
    
    /**
     * Checks if a file is an Opus file by examining its magic bytes.
     * Looks for both "OggS" header and "OpusHead" identifier.
     *
     * @param file The file to check
     * @return true if the file appears to be a valid Opus file
     */
    private fun isOpusFileByMagic(file: File): Boolean {
        try {
            file.inputStream().use { input ->
                val buffer = ByteArray(100)  // Read first 100 bytes
                val bytesRead = input.read(buffer)
                
                if (bytesRead >= 4) {
                    // Check for "OggS" magic
                    if (buffer[0] == 'O'.code.toByte() && 
                        buffer[1] == 'g'.code.toByte() && 
                        buffer[2] == 'g'.code.toByte() && 
                        buffer[3] == 'S'.code.toByte()) {
                        
                        // Look for "OpusHead" in the buffer
                        val content = buffer.sliceArray(0 until bytesRead)
                        val opusHeadBytes = "OpusHead".toByteArray()
                        
                        // Simple substring search
                        outer@ for (i in 0..content.size - opusHeadBytes.size) {
                            for (j in opusHeadBytes.indices) {
                                if (content[i + j] != opusHeadBytes[j]) {
                                    continue@outer
                                }
                            }
                            return true // Found "OpusHead"
                        }
                    }
                }
                return false
            }
        } catch (e: Exception) {
            println("Error checking Opus magic: ${e.message}")
            return false
        }
    }
}