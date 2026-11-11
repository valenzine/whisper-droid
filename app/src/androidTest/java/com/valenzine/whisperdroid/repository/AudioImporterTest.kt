package com.valenzine.whisperdroid.repository

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valenzine.whisperdroid.AudioSession
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioImporterTest {
    @Test fun stalledReadIsCanceledAndHealthyQueuedImportProceeds() = runBlocking {
        withTimeout(20_000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "import-timeout-test").apply { mkdirs() }
            val importer = AudioImporter(context, directory, timeoutMillis = 2_000)
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val errors = Channel<Exception>(2)
            val processed = Channel<String>(2)
            context.contentResolver.query(uri("reset"), null, null, null, null)?.close()
            try {
                val session = AudioSession(scope, importer::stage, { audio, _, _ -> processed.send(audio.displayName) }, { errors.trySend(it) })
                val stalled = requireNotNull(session.enqueue(uri("stall-read"), null, isShared = true))
                withContext(Dispatchers.IO) {
                    while (openedCount() < 1) delay(10)
                }
                val healthy = requireNotNull(session.enqueue(uri("healthy"), null))
                stalled.await()
                assertTrue(errors.receive().message.orEmpty().contains("timed out"))
                healthy.await()
                assertEquals("healthy.wav", processed.receive())
                // A completed/canceled import leaves neither a staged nor partial file.
                withContext(Dispatchers.IO) {
                    while (directory.listFiles().orEmpty().isNotEmpty()) delay(10)
                }
                assertNotNull(session.enqueue(uri("healthy"), null))
                assertEquals("healthy.wav", processed.receive())
            } finally {
                scope.cancel()
                context.contentResolver.query(uri("release"), null, null, null, null)?.close()
                directory.deleteRecursively()
            }
        }
    }

    @Test fun readsFiveMinuteDurationFromTheStagedAudio() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "import-duration-test")
        val audio = AudioImporter(context, directory).stage(uri("five-minutes"), "Shared audio", 123L)
        try {
            assertEquals(300_000L, audio.durationMillis)
            assertEquals(123L, audio.importedAt)
            assertTrue(audio.file.length() > 0)
        } finally { directory.deleteRecursively() }
    }

    @Test fun repeatedUncooperativeQueriesDoNotExhaustImportWorkers() = runBlocking {
        withTimeout(20_000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "import-query-timeout-test").apply { mkdirs() }
            context.contentResolver.query(uri("reset"), null, null, null, null)?.close()
            val importer = AudioImporter(context, directory, timeoutMillis = 2_000)
            try {
                repeat(2) { index ->
                    try {
                        importer.stage(uri("stall-query"), "Shared audio", 0L)
                        fail("The stalled provider should time out")
                    } catch (error: java.io.IOException) {
                        assertTrue(error.message.orEmpty().contains("timed out"))
                        assertEquals(index + 1, openedCount())
                    }
                }
                val audio = AudioImporter(context, directory).stage(uri("healthy"), "Files", 0L)
                assertEquals("healthy.wav", audio.displayName)
                audio.deleteTemporaryFile()
                assertTrue(directory.listFiles().orEmpty().isEmpty())
            } finally {
                context.contentResolver.query(uri("release"), null, null, null, null)?.close()
                directory.deleteRecursively()
            }
        }
    }

    private fun openedCount(): Int {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.contentResolver.query(uri("state"), null, null, null, null)!!.use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    private fun uri(path: String) = Uri.parse("content://com.valenzine.whisperdroid.test.import/$path")
}

