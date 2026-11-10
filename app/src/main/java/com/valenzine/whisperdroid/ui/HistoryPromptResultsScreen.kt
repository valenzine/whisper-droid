package com.valenzine.whisperdroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.repository.AppSettings
import com.valenzine.whisperdroid.repository.SavedPrompt
import com.valenzine.whisperdroid.viewmodel.TranscriptionUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryPromptResultsScreen(
    entryId: Long,
    app: WhisperDroidApplication,
    onBack: () -> Unit,
    openResult: (String) -> Unit
) {
    val results by remember(entryId) { app.history.results(entryId) }.collectAsState(initial = emptyList())
    val settings by app.settings.settingsFlow.collectAsState(initial = AppSettings())
    val screenState by app.transcription.screenState.collectAsState()
    val isThisRun = screenState.formattingEntryId == entryId
    val isFormatting = isThisRun && screenState.phase is TranscriptionUiState.FormattingText
    var selectedPromptId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePromptId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompt results") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (results.isEmpty()) "Choose a prompt to format the saved transcription."
                        else "Open a saved result or run a prompt on the raw transcription.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isFormatting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Formatting…")
                        }
                    } else {
                        Button(
                            onClick = { selectedPromptId = settings.activePromptId },
                            enabled = !screenState.isBusy
                        ) { Text("Run a prompt") }
                    }
                    if (isThisRun) {
                        screenState.error?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            items(results, key = { it.promptId }) { result ->
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ListItem(
                    modifier = Modifier.clickable { openResult(result.promptId) },
                    headlineContent = {
                        Text(result.promptName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = result.summary.takeIf(String::isNotBlank)?.let { summary ->
                        { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    },
                    trailingContent = {
                        IconButton(
                            onClick = { deletePromptId = result.promptId },
                            enabled = !screenState.isBusy
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete result for ${result.promptName}")
                        }
                    }
                )
            }
        }
    }

    selectedPromptId?.let { promptId ->
        val selected = settings.prompts.firstOrNull { it.id == promptId }
        ChooseHistoryPromptDialog(
            prompts = settings.prompts,
            selectedPromptId = promptId,
            savedPromptIds = results.map { it.promptId }.toSet(),
            onSelect = { selectedPromptId = it },
            onDismiss = { selectedPromptId = null },
            canRun = selected != null && !screenState.isBusy,
            onRun = {
                if (app.transcription.formatHistoryEntry(entryId, promptId)) selectedPromptId = null
            }
        )
    }

    results.firstOrNull { it.promptId == deletePromptId }?.let { result ->
        AlertDialog(
            onDismissRequest = { deletePromptId = null },
            title = { Text("Delete this result?") },
            text = { Text("Remove the saved output for “${result.promptName}”.") },
            confirmButton = {
                TextButton(
                    enabled = !screenState.isBusy,
                    onClick = {
                        app.transcription.deletePromptResult(entryId, result.promptId)
                        deletePromptId = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletePromptId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ChooseHistoryPromptDialog(
    prompts: List<SavedPrompt>,
    selectedPromptId: String,
    savedPromptIds: Set<String>,
    canRun: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onRun: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a prompt") },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp).selectableGroup()) {
                items(prompts, key = { it.id }) { prompt ->
                    val selected = prompt.id == selectedPromptId
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(prompt.id) })
                            .heightIn(min = 48.dp).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Column {
                            Text(prompt.name)
                            if (prompt.id in savedPromptIds) {
                                Text("Replaces the saved result", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRun, enabled = canRun) {
                Text(if (selectedPromptId in savedPromptIds) "Run again" else "Run prompt")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
