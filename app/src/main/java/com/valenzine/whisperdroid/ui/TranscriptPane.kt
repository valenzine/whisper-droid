package com.valenzine.whisperdroid.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valenzine.whisperdroid.R
import kotlinx.coroutines.launch

@Composable
internal fun TranscriptPane(
    rawText: String,
    editedText: String,
    selectedEdited: Boolean,
    onSelectFormatted: (Boolean) -> Unit,
    textHeight: Dp,
    onExpand: (() -> Unit)?,
    onFormat: () -> Boolean,
    isBusy: Boolean,
    isFormatting: Boolean = false
) {
    val text = if (selectedEdited) editedText else rawText
    val label = if (selectedEdited) "Edited text" else "Transcript"
    val hasTranscript = rawText.isNotBlank()
    val hasEdited = editedText.isNotBlank()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            if (hasTranscript) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = if (selectedEdited) 1 else 0,
                        modifier = Modifier.weight(1f),
                        divider = {}
                    ) {
                        Tab(selected = !selectedEdited, onClick = { onSelectFormatted(false) }, text = { Text("Transcript") })
                        Tab(selected = selectedEdited, onClick = { onSelectFormatted(true) }, text = { Text("Edited") })
                    }
                    CopyTranscriptButton(text, label)
                    IconButton(enabled = onExpand != null && text.isNotBlank(), onClick = { onExpand?.invoke() }) {
                        Icon(painterResource(R.drawable.ic_fullscreen), contentDescription = "Open full screen")
                    }
                }
                HorizontalDivider()
            }
            Box(
                Modifier.fillMaxWidth().height(if (hasTranscript) textHeight else 96.dp)
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                if (text.isBlank()) {
                    Text(
                        if (hasTranscript) "No saved edit for this prompt." else "Your transcription will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    TranscriptBodyText(
                        text,
                        renderMarkdown = selectedEdited,
                        bodyStyle = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            if (hasTranscript && (isFormatting || !hasEdited || selectedEdited)) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    when {
                        isFormatting -> {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Editing…", modifier = Modifier.padding(vertical = 12.dp))
                        }
                        hasEdited -> TextButton(onClick = { onFormat() }, enabled = !isBusy) { Text("Run again") }
                        else -> Button(onClick = { onFormat() }, enabled = !isBusy) { Text("Edit transcript") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CopyTranscriptButton(text: String?, label: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    IconButton(
        enabled = !text.isNullOrBlank(),
        onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text))) }
        }
    ) {
        Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy transcript")
    }
}
