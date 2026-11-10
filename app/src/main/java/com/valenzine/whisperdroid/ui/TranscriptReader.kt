package com.valenzine.whisperdroid.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TranscriptReader(text: String?, formatted: Boolean, onBack: () -> Unit) {
    val title = if (formatted) "Formatted text" else "Raw transcription"
    ReaderScaffold(title, text, onBack) { insets ->
        if (text == null) {
            Box(Modifier.fillMaxSize().padding(insets).padding(16.dp)) {
                Text("This transcript is unavailable.")
            }
        } else {
            Box(
                Modifier.fillMaxSize().padding(insets).testTag("transcript-reader-scroll")
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
internal fun TranscriptReaderLoading(formatted: Boolean, onBack: () -> Unit) {
    val title = if (formatted) "Formatted text" else "Raw transcription"
    ReaderScaffold(title, null, onBack) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).padding(16.dp)) {
            Text("Loading transcript…")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScaffold(
    title: String,
    text: String?,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !text.isNullOrBlank(),
                        onClick = {
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, text))) }
                        }
                    ) { Text("Copy") }
                }
            )
        }
    ) { insets ->
        content(insets)
    }
}
