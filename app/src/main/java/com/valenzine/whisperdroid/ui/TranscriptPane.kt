package com.valenzine.whisperdroid.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
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
    text: String,
    label: String,
    emptyText: String,
    textHeight: Dp,
    onExpand: (() -> Unit)?
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                CopyTranscriptButton(text, label)
                IconButton(enabled = onExpand != null && text.isNotBlank(), onClick = { onExpand?.invoke() }) {
                    Icon(painterResource(R.drawable.ic_fullscreen), contentDescription = "Open full screen")
                }
            }
            HorizontalDivider()
            Box(
                Modifier.fillMaxWidth().height(textHeight)
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                if (text.isBlank()) {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodyLarge)
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
