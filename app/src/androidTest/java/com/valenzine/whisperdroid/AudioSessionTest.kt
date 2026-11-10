package com.valenzine.whisperdroid

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valenzine.whisperdroid.repository.StagedAudio
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioSessionTest {
    @Test
    fun stagesOneQueuedAudioAndRejectsAThirdWhileProcessingInOrder() = runBlocking {
        withTimeout(5_000) {
            val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
            val startOrder = Channel<String>(2)
            val releaseFirst = CompletableDeferred<Unit>()
            val firstFile = File.createTempFile("share-session-first", ".audio", cache)
            val nextFile = File.createTempFile("share-session-next", ".audio", cache)
            try {
                val session = AudioSession(
                    scope = scope,
                    stage = { uri, _, _ ->
                        val name = requireNotNull(uri.lastPathSegment)
                        StagedAudio(if (name == "first") firstFile else nextFile, name, name)
                    },
                    process = { audio, _, _ ->
                        startOrder.send(audio.displayName)
                        if (audio.displayName == "first") releaseFirst.await()
                    },
                    showError = { throw AssertionError(it) }
                )
                requireNotNull(session.enqueue(Uri.parse("content://sender/first"), null)).await()
                assertEquals("first", startOrder.receive())

                requireNotNull(session.enqueue(Uri.parse("content://sender/next"), null)).await()
                assertNull(session.enqueue(Uri.parse("content://sender/rejected"), null))
                assertFalse(startOrder.tryReceive().isSuccess)

                releaseFirst.complete(Unit)
                assertEquals("next", startOrder.receive())
            } finally {
                scope.cancel()
                firstFile.delete()
                nextFile.delete()
            }
        }
    }

    @Test
    fun remembersOnlyPickerAudioForReTranscription() = runBlocking {
        withTimeout(5_000) {
            val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
            val processed = Channel<String>(2)
            val localFile = File.createTempFile("picker-audio", ".audio", cache)
            val sharedFile = File.createTempFile("shared-audio", ".audio", cache)
            val localUri = Uri.parse("content://files/local")
            val sharedUri = Uri.parse("content://telegram/shared")
            try {
                val session = AudioSession(
                    scope = scope,
                    stage = { uri, _, _ ->
                        val name = requireNotNull(uri.lastPathSegment)
                        StagedAudio(if (name == "local") localFile else sharedFile, name, name)
                    },
                    process = { audio, _, _ -> processed.send(audio.sha256) },
                    showError = { throw AssertionError(it) }
                )

                requireNotNull(session.enqueue(localUri, null, isLocalPicker = true)).await()
                assertEquals("local", processed.receive())
                assertEquals(localUri, session.reusableLocalUri("local"))

                requireNotNull(session.enqueue(sharedUri, null, sourceOrigin = "Telegram")).await()
                assertEquals("shared", processed.receive())
                assertNull(session.reusableLocalUri("shared"))
            } finally {
                scope.cancel()
                localFile.delete()
                sharedFile.delete()
            }
        }
    }
}
