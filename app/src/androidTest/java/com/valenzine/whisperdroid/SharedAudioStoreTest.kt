package com.valenzine.whisperdroid

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAudioStoreTest {
    @Test
    fun audioShareIsOfferedOnceAndConsumptionClearsBothStoreAndIntent() {
        val uri = Uri.parse("content://example.provider/audio/recording.m4a")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
        val store = SharedAudioStore()

        store.offer(intent)

        assertEquals(uri, store.pendingUri.value)
        val remainingStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        assertNull(remainingStream)

        store.consume()

        assertNull(store.pendingUri.value)

        // Re-offering the Activity's consumed intent (for example after Settings or recreation)
        // cannot enqueue the same URI again because the stream extra was removed at extraction.
        store.offer(intent)
        assertNull(store.pendingUri.value)
    }

    @Test
    fun newerAudioShareReplacesPendingShare() {
        val first = Uri.parse("content://example.provider/audio/first.m4a")
        val second = Uri.parse("content://example.provider/audio/second.m4a")
        val store = SharedAudioStore()

        store.offer(Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, first))
        store.offer(Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, second))

        assertEquals(second, store.pendingUri.value)
    }

    @Test
    fun nonAudioSharesAreIgnored() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://example.provider/not-audio"))
        val store = SharedAudioStore()

        store.offer(intent)

        assertNull(store.pendingUri.value)
    }
}
