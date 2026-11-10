package com.valenzine.whisperdroid

import android.net.Uri
import com.valenzine.whisperdroid.repository.StagedAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/** Reserves one running and one waiting audio slot, including while a share is being copied. */
internal class AudioSession(
    private val scope: CoroutineScope,
    private val stage: suspend (Uri, String) -> StagedAudio,
    private val process: suspend (StagedAudio, String?, String?) -> Unit,
    private val showError: (Exception) -> Unit
) {
    private class Request(
        val staged: Deferred<StagedAudio>,
        val language: String?,
        val expectedFingerprint: String?,
        val sourceOrigin: String
    )

    private val slots = Semaphore(2)
    private val requests = Channel<Request>(2)
    var shareLanguage: String? = null

    init {
        scope.launch {
            for (request in requests) {
                try {
                    val audio = request.staged.await()
                    try {
                        process(audio, request.language, request.expectedFingerprint)
                    } finally {
                        audio.file.delete()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    showError(error)
                } finally {
                    slots.release()
                }
            }
        }
    }

    /** The receiver can release its URI grant as soon as the returned signal completes. */
    fun enqueue(uri: Uri, language: String?, expectedFingerprint: String? = null, sourceOrigin: String = "Audio"): Deferred<Unit>? {
        if (!slots.tryAcquire()) return null

        val copyFinished = CompletableDeferred<Unit>()
        val staged = scope.async(start = CoroutineStart.LAZY) {
            try {
                stage(uri, sourceOrigin)
            } finally {
                copyFinished.complete(Unit)
            }
        }
        check(requests.trySend(Request(staged, language, expectedFingerprint, sourceOrigin)).isSuccess)
        staged.start()
        return copyFinished
    }
}
