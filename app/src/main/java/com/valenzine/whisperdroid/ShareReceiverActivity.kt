package com.valenzine.whisperdroid

import android.content.ContentResolver
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/** Owns the incoming URI grant until the audio has been copied into private cache. */
class ShareReceiverActivity : ComponentActivity() {
    private val intake: ShareIntake by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = sharedAudioUri()
        if (uri == null) {
            Toast.makeText(this, "Could not open shared audio.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val session = (application as WhisperDroidApplication).audioSession
        val staged = intake.receive { session.enqueue(uri, session.shareLanguage, sourceOrigin = sourceOrigin(uri)) }
        if (staged == null) {
            Toast.makeText(this, "The audio queue is full. Share this audio again later.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                staged.await()
                startActivity(
                    Intent.makeMainActivity(ComponentName(this@ShareReceiverActivity, MainActivity::class.java))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            }
        }
    }

    private fun sharedAudioUri(): Uri? {
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("audio/") != true) return null
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: return null
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return uri.takeIf {
            checkUriPermission(it, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun sourceOrigin(uri: Uri): String {
        val caller = if (android.os.Build.VERSION.SDK_INT >= 35) callingPackage else null
        return when {
            caller in setOf("org.telegram.messenger", "org.telegram.messenger.web") || uri.authority.orEmpty().contains("telegram", true) -> "Telegram"
            caller in setOf("com.whatsapp", "com.whatsapp.w4b") || uri.authority.orEmpty().contains("whatsapp", true) -> "WhatsApp"
            uri.authority.orEmpty().contains("documents", true) || uri.authority.orEmpty().contains("downloads", true) -> "Files"
            else -> "Shared audio"
        }
    }
}

internal class ShareIntake : ViewModel() {
    private var received = false
    private var staged: Deferred<Unit>? = null

    fun receive(enqueue: () -> Deferred<Unit>?): Deferred<Unit>? {
        if (!received) {
            received = true
            staged = enqueue()
        }
        return staged
    }
}
