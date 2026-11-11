package com.valenzine.whisperdroid.repository

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Private process boundary: even a provider that ignores cancellation can be terminated. */
class AudioImportService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var importing = false
    private val watchdog = Runnable { Process.killProcess(Process.myPid()) }
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            CANCEL -> Process.killProcess(Process.myPid())
            IMPORT -> {
                if (!importing) {
                    importing = true
                    val request = message.data
                    val reply = message.replyTo
                    reply.send(Message.obtain(null, STARTED, Process.myPid(), 0))
                    handler.postDelayed(watchdog, request.getLong("timeout", 30_000))
                    Thread({
                        val partial = AtomicReference<File?>()
                        val result = Bundle()
                        try {
                            val audio = AudioFileStager(this, File(requireNotNull(request.getString("directory")))).copy(
                                Uri.parse(request.getString("uri")),
                                requireNotNull(request.getString("token")), partial
                            )
                            result.putString("file", audio.file.absolutePath)
                            result.putString("name", audio.displayName)
                            result.putString("sha256", audio.sha256)
                            result.putLong("duration", audio.durationMillis ?: -1L)
                        } catch (error: Throwable) {
                            partial.get()?.delete()
                            result.putString("error", error.message ?: "Could not open selected audio")
                        }
                        handler.post {
                            handler.removeCallbacks(watchdog)
                            importing = false
                            runCatching { reply.send(Message.obtain(null, FINISHED).apply { data = result }) }
                        }
                    }, "audio-import").start()
                }
            }
        }
        true
    })

    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent): Boolean {
        if (importing) Process.killProcess(Process.myPid())
        return false
    }

    companion object {
        internal const val IMPORT = 1
        internal const val CANCEL = 2
        internal const val STARTED = 3
        internal const val FINISHED = 4
    }
}
