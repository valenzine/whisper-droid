package com.valenzine.whisperdroid.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/** Runs only in the disposable audio-import process. */
internal class AudioFileStager(private val context: Context, private val directory: File) {
    fun copy(
        uri: Uri,
        token: String,
        partial: AtomicReference<File?>
    ): StagedAudio {
        val metadata = context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null, null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = nameIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
            val size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            name to size
        }
        val displayName = metadata?.first ?: "audio"
        require(metadata?.second?.let { it <= MAX_UPLOAD_BYTES } != false) {
            "File too large (max 25 MB)"
        }
        val extension = displayName.substringAfterLast('.', "").lowercase()
            .replace(Regex("[^a-z0-9]"), "").take(16)
        val temporary = File.createTempFile("audio-$token-", if (extension.isBlank()) ".audio" else ".$extension", directory)
        partial.set(temporary)
        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r", null)
            ?: error("Could not open selected audio file")
        val digest = MessageDigest.getInstance("SHA-256")
        descriptor.use {
            descriptor.createInputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_UPLOAD_BYTES) { "File too large (max 25 MB)" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
            }
        }
        val duration = MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(temporary.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
        return StagedAudio(
            temporary, displayName,
            digest.digest().joinToString("") { byte -> "%02x".format(byte) },
            durationMillis = duration
        )
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 25L * 1024L * 1024L
    }
}
