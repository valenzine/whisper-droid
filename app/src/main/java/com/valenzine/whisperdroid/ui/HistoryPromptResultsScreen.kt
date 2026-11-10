package com.valenzine.whisperdroid.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valenzine.whisperdroid.R
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.history.PREVIOUS_RESULT_PROMPT_ID
import com.valenzine.whisperdroid.repository.AppSettings
import com.valenzine.whisperdroid.viewmodel.TranscriptionUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryPromptResultsScreen(
    entryId: Long,
    app: WhisperDroidApplication,
    onBack: () -> Unit,
    openResult: (String) -> Unit
) {
    val loadedResults by remember(entryId) { app.history.results(entryId) }.collectAsState(initial = null)
    val results = loadedResults.orEmpty()
    val settings by app.settings.settingsFlow.collectAsState(initial = AppSettings())
    val state by app.transcription.screenState.collectAsState()
    val options = settings.prompts.map { PromptOption(it.id, it.name, isAvailable = true) } +
        results.filterNot { result -> settings.prompts.any { it.id == result.promptId } }.map { result ->
            val name = if (result.promptId == PREVIOUS_RESULT_PROMPT_ID) "Earlier edit" else result.promptName
            PromptOption(result.promptId, name, isAvailable = false)
        }

    var chosenId by rememberSaveable(entryId) { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable(entryId) { mutableStateOf<String?>(null) }
    val defaultId = results.firstOrNull()?.promptId ?: settings.activePromptId
    val selected = options.firstOrNull { it.id == chosenId } ?: options.firstOrNull { it.id == defaultId }
    val result = results.firstOrNull { it.promptId == selected?.id }
    val isEarlierEdit = selected?.id == PREVIOUS_RESULT_PROMPT_ID
    val isSelectedRun = state.formattingEntryId == entryId && state.formattingPromptId == selected?.id
    val isEditing = isSelectedRun && state.phase is TranscriptionUiState.FormattingText

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option.id == selected?.id,
                        onClick = { chosenId = option.id },
                        label = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = CircleShape,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                }
            }
            val provenance = when {
                isEarlierEdit -> "Prompt unknown"
                selected?.isAvailable == false -> "Prompt removed"
                result != null && result.promptName != selected?.name -> "Edited with ${result.promptName}"
                else -> null
            }
            provenance?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (isSelectedRun) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (result == null) {
                    Text(
                        if (loadedResults == null) "Loading edits…" else "No saved edit for this prompt.",
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    key(result.promptId) {
                        Box(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp)
                        ) {
                            MarkdownTranscriptText(result.formattedText)
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isEditing -> {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Editing…", modifier = Modifier.padding(start = 8.dp))
                    }
                    selected?.isAvailable == true -> {
                        val run = { app.transcription.formatHistoryEntry(entryId, selected.id) }
                        val enabled = loadedResults != null && !state.isBusy
                        if (result == null) {
                            Button(onClick = { run() }, enabled = enabled) { Text("Edit transcript") }
                        } else {
                            TextButton(onClick = { run() }, enabled = enabled) { Text("Run again") }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (result != null) {
                    CopyTranscriptButton(result.formattedText, selected?.name ?: "Edited")
                    IconButton(onClick = { openResult(result.promptId) }) {
                        Icon(painterResource(R.drawable.ic_fullscreen), contentDescription = "Open full screen")
                    }
                    IconButton(onClick = { deleteId = result.promptId }, enabled = !state.isBusy) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected edit")
                    }
                }
            }
        }
    }

    results.firstOrNull { it.promptId == deleteId }?.let { saved ->
        val name = if (saved.promptId == PREVIOUS_RESULT_PROMPT_ID) "Earlier edit" else saved.promptName
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete this edit?") },
            text = { Text("Remove the saved output for “$name”.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        app.transcription.deletePromptResult(entryId, saved.promptId)
                        deleteId = null
                    },
                    enabled = !state.isBusy
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
        )
    }
}

private data class PromptOption(val id: String, val name: String, val isAvailable: Boolean)
