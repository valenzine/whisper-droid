package com.valenzine.whisperdroid.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.history.TranscriptEntry
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    navController: NavController,
    app: WhisperDroidApplication,
    openReader: (Long, Boolean) -> Unit
) {
    val entries by app.history.entries.collectAsState(initial = emptyList())
    val screenState by app.transcription.screenState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var clearAllConfirmation by remember { mutableStateOf(false) }
    var retranscribeFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
    val retranscribeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val fingerprint = retranscribeFingerprint
        retranscribeFingerprint = null
        if (uri != null && fingerprint != null) {
            if (app.audioSession.enqueue(uri, app.audioSession.shareLanguage, fingerprint) == null) {
                scope.launch { snackbar.showSnackbar("The audio queue is full. Select this audio again later.") }
            } else {
                navController.popBackStack()
            }
        }
    }

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
                        onReadRaw = { openReader(entry.id, false) },
                        onReadFormatted = { openReader(entry.id, true) },
                        onRetranscribe = {
                            retranscribeFingerprint = entry.audioFingerprint
                            retranscribeLauncher.launch("audio/*")
                        },
                        onDelete = { app.transcription.deleteHistoryEntry(entry.id) }
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
    onReadFormatted: () -> Unit,
    onRetranscribe: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        var moreExpanded by remember { mutableStateOf(false) }
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions for ${entry.displayName}")
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Re-transcribe") },
                            onClick = {
                                moreExpanded = false
                                onRetranscribe()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            enabled = !isBusy,
                            onClick = {
                                moreExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            if (entry.summary.isNotBlank()) {
                Text(
                    entry.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${entry.sourceOrigin} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.createdAt))}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpen, enabled = !isBusy) { Text("Open") }
                TextButton(onClick = onReadRaw) { Text("Read raw") }
                if (entry.formattedText.isNotBlank()) {
                    TextButton(onClick = onReadFormatted) { Text("Read formatted") }
                }
            }
        }
    }
}
