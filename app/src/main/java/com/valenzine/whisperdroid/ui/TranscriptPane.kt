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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    formattedText: String,
    selectedFormatted: Boolean,
    onSelectFormatted: (Boolean) -> Unit,
    textHeight: Dp,
    onExpand: (() -> Unit)?,
    onFormat: () -> Boolean,
    isBusy: Boolean,
    isFormatting: Boolean = false
) {
    val text = if (selectedFormatted) formattedText else rawText
    val label = if (selectedFormatted) "Formatted text" else "Raw transcription"
    val hasTranscript = rawText.isNotBlank()
    val hasFormatted = formattedText.isNotBlank()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            if (hasTranscript) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        SegmentedButton(
                            selected = !selectedFormatted,
                            onClick = { onSelectFormatted(false) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {},
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Raw") }
                        SegmentedButton(
                            selected = selectedFormatted,
                            onClick = { onSelectFormatted(true) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {},
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Formatted") }
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
                        if (hasTranscript) "No formatted text yet." else "Your transcription will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (hasTranscript && (isFormatting || !hasFormatted || selectedFormatted)) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    when {
                        isFormatting -> {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Formatting…", modifier = Modifier.padding(vertical = 12.dp))
                        }
                        hasFormatted -> TextButton(onClick = { onFormat() }, enabled = !isBusy) { Text("Reformat") }
                        else -> Button(onClick = { onFormat() }, enabled = !isBusy) { Text("Format") }
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
