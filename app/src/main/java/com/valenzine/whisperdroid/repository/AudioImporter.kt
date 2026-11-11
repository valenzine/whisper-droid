package com.valenzine.whisperdroid.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/** A timed-out provider must not retain a thread needed by the next import. */
internal class AudioImporter(
    private val context: Context,
    private val directory: File,
    private val timeoutMillis: Long = 30_000
) {
    suspend fun stage(uri: Uri, sourceOrigin: String, importedAt: Long): StagedAudio =
        withContext(Dispatchers.Main.immediate) {
            // One disposable worker process at a time; transcription has its own queue slot.
            imports.withLock {
                val token = UUID.randomUUID().toString()
                val requestDirectory = File(directory, "import-$token").apply { mkdirs() }
                try {
                    withTimeout(timeoutMillis) {
                        awaitImport(uri, sourceOrigin, importedAt, token, requestDirectory)
                    }
                } catch (error: TimeoutCancellationException) {
                    throw IOException("Audio import timed out. Please try again.", error)
                }
            }
        }

    private suspend fun awaitImport(uri: Uri, origin: String, importedAt: Long, token: String, requestDirectory: File): StagedAudio =
        suspendCancellableCoroutine { continuation ->
            var workerPid: Int? = null
            var service: Messenger? = null
            var bound = false
            lateinit var connection: ServiceConnection
            fun cleanPartial() {
                requestDirectory.deleteRecursively()
            }
            fun release(killWorker: Boolean) {
                if (killWorker) {
                    workerPid?.let { Process.killProcess(it) }
                    runCatching { service?.send(Message.obtain(null, AudioImportService.CANCEL)) }
                }
                if (bound) {
                    bound = false
                    runCatching { context.unbindService(connection) }
                }
                if (killWorker) cleanPartial()
            }
            val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
                when (message.what) {
                    AudioImportService.STARTED -> {
                        workerPid = message.arg1
                        if (!continuation.isActive) release(true)
                    }
                    AudioImportService.FINISHED -> {
                        val result = message.data
                        if (!continuation.isActive) {
                            release(true)
                        } else {
                            release(false)
                            val error = result.getString("error")
                            if (error != null) {
                                cleanPartial()
                                continuation.resumeWith(Result.failure(IOException(error)))
                            } else {
                                val audio = StagedAudio(
                                    File(requireNotNull(result.getString("file"))),
                                    requireNotNull(result.getString("name")),
                                    requireNotNull(result.getString("sha256")), origin, importedAt,
                                    result.getLong("duration").takeIf { it > 0 }
                                )
                                continuation.resume(audio) { _, abandoned, _ -> abandoned.deleteTemporaryFile() }
                            }
                        }
                    }
                }
                true
            })
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    service = Messenger(binder)
                    if (!continuation.isActive) {
                        release(true)
                        return
                    }
                    val request = Message.obtain(null, AudioImportService.IMPORT).apply {
                        replyTo = replies
                        data = Bundle().apply {
                            putString("uri", uri.toString())
                            putString("directory", requestDirectory.absolutePath)
                            putString("token", token)
                            putLong("timeout", timeoutMillis)
                        }
                    }
                    runCatching { service?.send(request) }.onFailure {
                        release(true)
                        if (continuation.isActive) continuation.resumeWith(Result.failure(IOException("Could not start audio import", it)))
                    }
                }
                // BIND_AUTO_CREATE reconnects after an unexpected worker crash within the deadline.
                override fun onServiceDisconnected(name: ComponentName) { service = null; workerPid = null }
                override fun onNullBinding(name: ComponentName) {
                    release(true)
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IOException("Could not start audio import")))
                }
            }
            try {
                bound = context.bindService(Intent(context, AudioImportService::class.java), connection, Context.BIND_AUTO_CREATE)
                if (!bound) {
                    cleanPartial()
                    continuation.resumeWith(Result.failure(IOException("Could not start audio import")))
                }
            } catch (error: Exception) {
                cleanPartial()
                continuation.resumeWith(Result.failure(error))
            }
            continuation.invokeOnCancellation {
                if (Looper.myLooper() == Looper.getMainLooper()) release(true)
                else Handler(Looper.getMainLooper()).post { release(true) }
            }
        }

    companion object {
        private val imports = Mutex()
    }
}
