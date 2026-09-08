package com.valenzine.whisperdroid

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Holds one incoming audio share until the transcription screen accepts it. */
class SharedAudioStore : ViewModel() {
    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri

    fun offer(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("audio/") != true) return
        val uri = intent.sharedAudioUri() ?: return
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.data = null
        intent.clipData = null
        _pendingUri.value = uri
    }

    fun consume() {
        _pendingUri.value = null
    }

    private fun Intent.sharedAudioUri(): Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
