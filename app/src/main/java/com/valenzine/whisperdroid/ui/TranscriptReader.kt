package com.valenzine.whisperdroid.ui

import android.text.style.UnderlineSpan
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Link

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
            val context = LocalContext.current
            val density = LocalDensity.current
            val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
            val dividerColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
            val lineSpacing = with(density) { 3.dp.toPx() }
            val markwon = remember(context, density, markerColor, dividerColor) {
                Markwon.builderNoCore(context)
                    .usePlugin(CorePlugin.create().hasExplicitMovementMethod(true))
                    .usePlugin(object : AbstractMarkwonPlugin() {
                        override fun configureTheme(builder: MarkwonTheme.Builder) {
                            with(density) {
                                builder
                                    .bulletWidth(5.dp.roundToPx())
                                    .bulletListItemStrokeWidth(1.dp.roundToPx())
                                    .listItemColor(markerColor)
                                    .blockQuoteWidth(2.dp.roundToPx())
                                    .blockQuoteColor(dividerColor)
                                    .headingBreakColor(dividerColor)
                                    .headingTextSizeMultipliers(floatArrayOf(1.5f, 1.28f, 1.12f, 1f, 1f, 1f))
                            }
                        }

                        override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                            builder.setFactory(Link::class.java) { _, _ -> UnderlineSpan() }
                        }
                    })
                    .build()
            }
            val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
            Box(
                Modifier.fillMaxSize().padding(insets).testTag("transcript-reader-scroll")
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            setTextIsSelectable(true)
                            textSize = 18f
                        }
                    },
                    update = { view ->
                        view.setTextColor(textColor)
                        view.setLineSpacing(lineSpacing, 1f)
                        val render = markwon to text
                        if (view.tag != render) {
                            markwon.setMarkdown(view, text)
                            view.tag = render
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { CopyTranscriptButton(text, title) }
            )
        }
    ) { insets ->
        content(insets)
    }
}
