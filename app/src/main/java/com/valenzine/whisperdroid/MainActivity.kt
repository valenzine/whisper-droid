package com.valenzine.whisperdroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.valenzine.whisperdroid.ui.Navigation
import com.valenzine.whisperdroid.ui.theme.WhisperDroidTheme

class MainActivity : ComponentActivity() {
    private val sharedAudioStore: SharedAudioStore by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedAudioStore.offer(intent)

        setContent {
            WhisperDroidTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val sharedAudioUri = sharedAudioStore.pendingUri.collectAsState().value
                    Navigation(
                        sharedAudioUri = sharedAudioUri,
                        onSharedAudioConsumed = sharedAudioStore::consume
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedAudioStore.offer(intent)
    }
}
