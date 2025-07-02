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
        // Load native library for Opus decoding
        try {
            System.loadLibrary("opus_jni")
            println("Successfully loaded opus_jni native library")
        } catch (e: UnsatisfiedLinkError) {
            println("Failed to load opus_jni native library: ${e.message}")
        }

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

    // Native method declarations for Opus conversion
    private external fun decodeOpusToPCM(inputPath: String, outputPath: String): Boolean
    // NOTE: This method is stubbed in the native code and will always fail.
    // We keep the declaration for compatibility but never call it.
    private external fun convertOpusToAacNative(inputPath: String, outputPath: String): Boolean // Not used - MediaCodec used instead


    /**
     * Converts Opus/OGG files to WAV format for compatibility with OpenAI's gpt-4o-mini-transcribe API.
     * Uses Android MediaCodec APIs to transcode Opus to PCM WAV - simple and OpenAI-compatible.
     * NEVER sends raw Opus files to non-whisper models.
     * 
     * @param inputFile The input Opus/OGG file
     * @return The converted WAV file
     * @throws Exception If conversion fails for any reason
     */
    private fun convertOpusToAac(inputFile: File): File {
        println("Converting ${inputFile.name} to WAV format for OpenAI compatibility...")
        println("CRITICAL: This conversion is required for gpt-4o-mini-transcribe - raw Opus files are NOT supported")
        
        val outputFile = File(inputFile.parent, inputFile.nameWithoutExtension + "_converted.wav")
        
        // Check if file has Opus magic headers for better diagnostics
        val hasOpusMagic = isOpusFileByMagic(inputFile)
        if (!hasOpusMagic) {
            println("WARNING: File does not appear to have valid Opus headers. Conversion may fail.")
        }
        
        println("Using Android MediaCodec for Opus to WAV conversion")
        println("Input file: ${inputFile.absolutePath}")
        println("Output file: ${outputFile.absolutePath}")
        println("Input file size: ${inputFile.length()} bytes")
        
        try {
            // Use the MediaCodec implementation for WAV conversion
            return convertOpusToWavMediaCodec(inputFile)
            
        } catch (e: Exception) {
            println("MediaCodec conversion failed: ${e.message}")
            
            // Clean up any partial files
            if (outputFile.exists()) outputFile.delete()
            
            println("CONVERSION FAILED: gpt-4o-mini-transcribe requires actual format conversion, not raw Opus files")
            throw Exception("Opus to WAV conversion failed: ${e.message}. Raw Opus files cannot be sent to gpt-4o-mini-transcribe. Try using whisper-1 model instead.")
        }
    }
    
    /**
     * Converts Opus/OGG files to WAV format using Android's MediaCodec API.
     * This is the primary conversion method used for all Opus/OGG files.
     * WAV is uncompressed PCM audio - simple and universally supported.
     *
     * @param inputFile The input Opus/OGG file
     * @return The converted WAV file
     * @throws Exception If MediaCodec conversion fails (missing codec, invalid file format, etc.)
     */
    private fun convertOpusToWavMediaCodec(inputFile: File): File {
        println("Starting MediaCodec conversion from Opus to WAV...")
        val outputFile = File(inputFile.parent, inputFile.nameWithoutExtension + "_converted.wav")
        
        try {
            // Check if we have an Opus decoder available
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
            val codecs = codecList.codecInfos
            var hasOpusDecoder = false
            var opusDecoderName = ""
            
            for (codec in codecs) {
                if (codec.isEncoder) continue
                for (type in codec.supportedTypes) {
                    if (type == "audio/opus") {
                        println("Found Opus decoder: ${codec.name}")
                        hasOpusDecoder = true
                        opusDecoderName = codec.name
                        break
                    }
                }
                if (hasOpusDecoder) break
            }
            
            if (!hasOpusDecoder) {
                println("ERROR: No Opus decoder found in MediaCodec")
                throw Exception("Device does not support Opus decoding through MediaCodec. Please use whisper-1 model for Opus files.")
            } else {
                println("Using MediaCodec Opus decoder: $opusDecoderName")
            }
            
            // Check if file is a valid Opus file by examining magic bytes
            val hasOpusMagic = isOpusFileByMagic(inputFile)
            println("File has Opus magic headers: $hasOpusMagic")
            
            // Set up extractor
            val extractor = android.media.MediaExtractor()
            try {
                val uri = android.net.Uri.fromFile(inputFile)
                extractor.setDataSource(context, uri, null)
                println("MediaExtractor initialized successfully")
            } catch (e: Exception) {
                println("MediaExtractor failed to open the file: ${e.message}")
                if (hasOpusMagic) {
                    println("File has Opus magic but MediaExtractor couldn't open it")
                    println("This likely means the file is Opus but your device lacks the proper codec")
                    throw Exception("Device cannot process this Opus file. Please use whisper-1 model instead.")
                } else {
                    throw Exception("Not a valid Opus file or missing codec: ${e.message}")
                }
            }
            
            // Find the audio track
            var audioTrackIndex = -1
            var trackMimeType = ""
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                println("Found track: $mime")
                
                if (mime?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    audioTrackIndex = i
                    trackMimeType = mime
                    println("Selected audio track: $mime")
                    break
                }
            }
            
            if (audioTrackIndex == -1) {
                if (hasOpusMagic) {
                    println("File has Opus magic headers but MediaExtractor found no audio tracks")
                    println("This means your device doesn't have the proper codec to decode this Opus file")
                    throw Exception("Device cannot decode this Opus file. Please use whisper-1 model instead.")
                } else {
                    throw Exception("No audio track found in the input file - may not be a valid audio file")
                }
            }
            
            // Get source format
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val inputMime = inputFormat.getString(android.media.MediaFormat.KEY_MIME)
            val sampleRate = inputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            val duration = if (inputFormat.containsKey(android.media.MediaFormat.KEY_DURATION)) 
                inputFormat.getLong(android.media.MediaFormat.KEY_DURATION) else 0
            
            println("Input audio: $inputMime, $sampleRate Hz, $channelCount channels, duration: $duration μs")
            
            // Create decoder
            val decoder = android.media.MediaCodec.createDecoderByType(inputMime!!)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            
            // Set up for WAV file writing
            val wavOutputStream = java.io.FileOutputStream(outputFile)
            val pcmData = mutableListOf<ByteArray>()
            
            // Set up buffers
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            val TIMEOUT_US = 10000L
            var sawInputEOS = false
            var sawOutputEOS = false
            
            try {
                while (!sawOutputEOS) {
                    // Handle decoder input
                    if (!sawInputEOS) {
                        val inputBufferId = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufferId >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferId, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                                println("Decoder: End of stream reached")
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                    
                    // Handle decoder output
                    val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outputBufferId >= 0) {
                        val decoderOutputBuffer = decoder.getOutputBuffer(outputBufferId)!!
                        
                        if (bufferInfo.size > 0) {
                            // Copy PCM data
                            val pcmBytes = ByteArray(bufferInfo.size)
                            decoderOutputBuffer.position(bufferInfo.offset)
                            decoderOutputBuffer.get(pcmBytes, 0, bufferInfo.size)
                            pcmData.add(pcmBytes)
                        }
                        
                        decoder.releaseOutputBuffer(outputBufferId, false)
                        
                        if ((bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                            println("Decoder: End of stream reached")
                        }
                    } else if (outputBufferId == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = decoder.outputFormat
                        println("Decoder output format changed: $newFormat")
                    }
                }
                
                println("Decoding complete, writing WAV file...")
                
                // Calculate total PCM data size
                val totalPcmSize = pcmData.sumOf { it.size }
                
                // Write WAV header
                writeWavHeader(wavOutputStream, sampleRate, channelCount.toShort(), totalPcmSize)
                
                // Write PCM data
                for (pcmChunk in pcmData) {
                    wavOutputStream.write(pcmChunk)
                }
                
                wavOutputStream.close()
                println("WAV file written successfully")
                
            } catch (e: Exception) {
                println("Error during MediaCodec conversion: ${e.message}")
                throw e
            } finally {
                // Release resources
                decoder.stop()
                decoder.release()
                extractor.release()
                try { wavOutputStream.close() } catch (e: Exception) { }
            }
            
            if (outputFile.exists() && outputFile.length() > 0) {
                println("MediaCodec conversion successful - output size: ${outputFile.length()} bytes")
                return outputFile
            } else {
                throw Exception("MediaCodec conversion failed - output file is empty or missing")
            }
        } catch (e: Exception) {
            // Clean up
            if (outputFile.exists()) outputFile.delete()
            throw Exception("MediaCodec WAV conversion failed: ${e.message}")
        }
    }
    
    /**
     * Writes a WAV file header for PCM audio data.
     */
    private fun writeWavHeader(outputStream: java.io.FileOutputStream, sampleRate: Int, channels: Short, pcmDataSize: Int) {
        val byteRate = sampleRate * channels * 2 // 16-bit samples
        val blockAlign = (channels * 2).toShort()
        val fileSize = 36 + pcmDataSize
        
        // WAV header
        outputStream.write("RIFF".toByteArray())
        outputStream.write(intToByteArray(fileSize))
        outputStream.write("WAVE".toByteArray())
        
        // Format chunk
        outputStream.write("fmt ".toByteArray())
        outputStream.write(intToByteArray(16)) // Chunk size
        outputStream.write(shortToByteArray(1)) // Audio format (PCM)
        outputStream.write(shortToByteArray(channels))
        outputStream.write(intToByteArray(sampleRate))
        outputStream.write(intToByteArray(byteRate))
        outputStream.write(shortToByteArray(blockAlign))
        outputStream.write(shortToByteArray(16)) // Bits per sample
        
        // Data chunk
        outputStream.write("data".toByteArray())
        outputStream.write(intToByteArray(pcmDataSize))
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
    
    /**
     * Logs all available audio codecs and checks for:
     * 1. AAC (audio/mp4a-latm) encoder support
     * 2. Opus (audio/opus) decoder support
     * 
     * Returns a pair of booleans (hasAacEncoder, hasOpusDecoder)
     */
    private fun checkAndLogAudioCodecs(): Pair<Boolean, Boolean> {
        val codecList = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
        val codecs = codecList.codecInfos
        var foundAac = false
        var foundOpusDecoder = false
        
        println("=== Available Audio Encoders ===")
        for (codec in codecs) {
            if (!codec.isEncoder) continue
            for (type in codec.supportedTypes) {
                if (type.startsWith("audio/")) {
                    println("Encoder: ${codec.name}, Type: $type")
                    if (type == "audio/mp4a-latm") foundAac = true
                }
            }
        }
        println("=== End of Audio Encoder List ===")
        
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
        
        return Pair(foundAac, foundOpusDecoder)
    }

    suspend fun transcribeFile(file: File, onProgress: ((String) -> Unit)? = null): String {
        val apiKey = settingsRepository.transcriptionApiKeyFlow.first()
        val model = settingsRepository.transcriptionModelFlow.first()
        
        println("=== TranscriptionRepository Debug ===")
        println("API Key length: ${apiKey.length}")
        println("Model: '$model'")
        println("File: '${file.name}'")
        println("File exists: ${file.exists()}")
        println("File size: ${file.length()}")
        
        // Log available audio encoders and check for AAC support if transcoding is needed
        val originalName = file.name
        val isOpus = originalName.endsWith(".opus", ignoreCase = true)
        val isOgg = originalName.endsWith(".ogg", ignoreCase = true)
        val isGpt4Mini = model == "gpt-4o-mini-transcribe"
        val needsTranscoding = isGpt4Mini && (isOpus || isOgg)

        if (needsTranscoding) {
            // Log available encoders and decoders for informational purposes
            val (hasAac, hasOpusDecoder) = checkAndLogAudioCodecs()
            println("Device AAC encoder available: $hasAac (not used - converting to WAV instead)")
            println("Device Opus decoder available: $hasOpusDecoder (needed for MediaCodec conversion)")
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
            // Convert to WAV for gpt-4 model compatibility (uncompressed but universally supported by OpenAI)
            onProgress?.invoke("Converting ${originalName.substringAfterLast(".")} to WAV...")
            println("TranscriptionRepository: Converting $originalName to WAV for $model")
            try {
                val convertedFile = convertOpusToAac(file)
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
                    e.message?.contains("Opus to WAV conversion failed") == true -> 
                        e.message!! // Use the detailed message from convertOpusToAac
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
                // We're now outputting WAV files (uncompressed PCM, OpenAI compatible)
                if (fileToUpload.name.endsWith(".wav", ignoreCase = true)) {
                    // WAV file - use audio/wav MIME type
                    "audio/wav" to fileToUpload.name
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
                    fileToUpload.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
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
                model = modelRequestBody
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
            "Format the following text with paragraphs:\n\n$text"
        }
        
        val request = LlmRequest(
            model = "gpt-4.1-nano",
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