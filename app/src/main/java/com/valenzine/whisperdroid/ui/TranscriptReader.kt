package com.valenzine.whisperdroid.ui

import android.graphics.Typeface
import android.text.style.UnderlineSpan
import android.util.TypedValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Link
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TranscriptReader(
    text: String?,
    formatted: Boolean,
    promptName: String? = null,
    onBack: () -> Unit
) {
    val title = promptName ?: if (formatted) "Edited" else "Transcript"
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
                TranscriptBodyText(text, renderMarkdown = true)
            }
        }
    }
}

@Composable
internal fun TranscriptBodyText(
    text: String,
    renderMarkdown: Boolean,
    bodyStyle: TextStyle? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val lineSpacing = with(density) { 3.dp.toPx() }
    val markwon = if (renderMarkdown) remember(context, density, markerColor, dividerColor) {
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
    } else null
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSizePx = with(density) { (bodyStyle?.fontSize ?: 18.sp).toPx() }
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextIsSelectable(true)
            }
        },
        update = { view ->
            if (view.textSize != textSizePx) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            view.setTextColor(textColor)
            if (bodyStyle == null) {
                view.setLineSpacing(lineSpacing, 1f)
            } else {
                view.typeface = Typeface.DEFAULT
                view.includeFontPadding = false
                view.letterSpacing = with(density) { bodyStyle.letterSpacing.toPx() } / textSizePx
                val lineHeightPx = with(density) { bodyStyle.lineHeight.toPx().roundToInt() }
                TextViewCompat.setLineHeight(view, lineHeightPx)
            }
            val render = Triple(markwon, text, textSizePx)
            if (view.tag != render) {
                if (markwon == null) view.text = text else markwon.setMarkdown(view, text)
                view.tag = render
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun TranscriptReaderLoading(formatted: Boolean, onBack: () -> Unit) {
    val title = if (formatted) "Edited" else "Transcript"
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
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
