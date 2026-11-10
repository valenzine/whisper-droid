package com.valenzine.whisperdroid.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.history.TranscriptEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.IOException
import android.text.format.DateFormat as AndroidDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    navController: NavController,
    app: WhisperDroidApplication,
    openRawReader: (Long) -> Unit,
    openPromptResults: (Long) -> Unit
) {
    val entries by app.history.entries.collectAsState(initial = emptyList())
    val screenState by app.transcription.screenState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var clearAllConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { clearAllConfirmation = true },
                        enabled = entries.isNotEmpty() && !screenState.isBusy
                    ) { Text("Clear all") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(insets).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No saved transcriptions yet.", style = MaterialTheme.typography.titleMedium)
                Text("Completed transcriptions will appear here.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        isBusy = screenState.isBusy,
                        onOpen = {
                            app.transcription.showHistoryEntry(entry)
                            navController.popBackStack()
                        },
                        onReadRaw = { openRawReader(entry.id) },
                        onPromptResults = { openPromptResults(entry.id) },
                        reusableUri = { app.audioSession.reusableLocalUri(entry.audioFingerprint) },
                        onRetranscribe = { uri ->
                            if (app.audioSession.enqueue(uri, app.audioSession.shareLanguage, entry.audioFingerprint) == null) {
                                scope.launch { snackbar.showSnackbar("The audio queue is full. Try again later.") }
                            } else {
                                navController.popBackStack()
                            }
                        },
                        onDelete = {
                            app.audioSession.forgetReusableLocalUri(entry.audioFingerprint)
                            app.transcription.deleteHistoryEntry(entry.id)
                        }
                    )
                }
            }
        }
    }

    if (clearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { clearAllConfirmation = false },
            title = { Text("Clear transcription history?") },
            text = { Text("This removes all saved transcriptions and their duplicate detection records.") },
            confirmButton = {
                TextButton(onClick = {
                    app.transcription.clearHistory()
                    app.audioSession.clearReusableLocalUris()
                    clearAllConfirmation = false
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { clearAllConfirmation = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HistoryEntryCard(
    entry: TranscriptEntry,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onReadRaw: () -> Unit,
    onPromptResults: () -> Unit,
    reusableUri: () -> Uri?,
    onRetranscribe: (Uri) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        var moreExpanded by remember { mutableStateOf(false) }
        var reusableAudio by remember(entry.id) { mutableStateOf<Uri?>(null) }
        val context = LocalContext.current
        LaunchedEffect(moreExpanded) {
            reusableAudio = null
            if (moreExpanded) {
                reusableUri()?.takeIf { uri -> canReadAudio(context, uri) }?.let { reusableAudio = it }
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${entry.sourceOrigin} · ${historyTimestamp(entry.createdAt, AndroidDateFormat.is24HourFormat(LocalContext.current))}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions for ${entry.sourceOrigin} at ${historyTimestamp(entry.createdAt, AndroidDateFormat.is24HourFormat(LocalContext.current))}")
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            enabled = !isBusy,
                            onClick = {
                                moreExpanded = false
                                onDelete()
                            }
                        )
                        reusableAudio?.let { uri ->
                            DropdownMenuItem(
                                text = { Text("Re-transcribe") },
                                enabled = !isBusy,
                                onClick = {
                                    moreExpanded = false
                                    onRetranscribe(uri)
                                }
                            )
                        }
                    }
                }
            }
            if (entry.summary.isNotBlank()) {
                Text(
                    entry.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpen, enabled = !isBusy) { Text("Open") }
                TextButton(onClick = onReadRaw) { Text("Raw") }
                TextButton(onClick = onPromptResults) {
                    Text(if (entry.formattedText.isNotBlank()) "Prompt results" else "Format")
                }
            }
        }
    }
}

private suspend fun canReadAudio(context: android.content.Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (_: SecurityException) {
        false
    } catch (_: IOException) {
        false
    }
}

private fun historyTimestamp(timestamp: Long, use24HourClock: Boolean): String {
    val locale = Locale.getDefault()
    val skeleton = if (use24HourClock) "dMMMHHmm" else "dMMMhm"
    val pattern = AndroidDateFormat.getBestDateTimePattern(locale, skeleton)
    return SimpleDateFormat(pattern, locale).format(Date(timestamp))
}
