package com.valenzine.whisperdroid.ui

import android.text.Spanned
import android.text.style.ClickableSpan
import android.widget.TextView

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.noties.markwon.core.spans.BulletListItemSpan
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.core.spans.OrderedListItemSpan
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptReaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRawTranscription() {
        composeRule.setContent {
            MaterialTheme {
                TranscriptReader(text = "Raw dictation", formatted = false, onBack = {})
            }
        }

        composeRule.onNodeWithText("Raw transcription").assertIsDisplayed()
        onView(withText("Raw dictation")).check(matches(isDisplayed()))
    }

    @Test
    fun rendersMarkdownAsNativeFormattedText() {
        composeRule.setContent {
            MaterialTheme {
                TranscriptReader(
                    text = "# Heading\n\n- *emphasized* item\n\n> quoted\n\n`code`\n\n[Example](https://example.com)",
                    formatted = true,
                    onBack = {}
                )
            }
        }

        onView(withText(containsString("emphasized item"))).check { view, error ->
            if (error != null) throw error
            val textView = view as TextView
            val rendered = textView.text as Spanned
            assertTrue(rendered.toString().contains("quoted"))
            assertTrue(rendered.toString().contains("code"))
            assertFalse(rendered.toString().contains("# Heading"))
            assertTrue(rendered.getSpans(0, "Heading".length, HeadingSpan::class.java).isNotEmpty())
            assertTrue(textView.isTextSelectable)
            assertTrue(textView.movementMethod?.canSelectArbitrarily() == true)
            assertTrue(rendered.getSpans(0, rendered.length, ClickableSpan::class.java).isEmpty())
        }
    }

    @Test
    fun alignsWrappedAndNestedMarkdownLists() {
        val wrappedItem = "A long first bullet " + "with enough words to wrap on a phone ".repeat(6)
        val markdown = "Overview\n\n- $wrappedItem\n  - Nested bullet\n- Another bullet\n\n1. First step\n2. Second step"
        composeRule.setContent {
            MaterialTheme { TranscriptReader(text = markdown, formatted = true, onBack = {}) }
        }

        onView(withText(containsString("Nested bullet"))).check { view, error ->
            if (error != null) throw error
            val textView = view as TextView
            val rendered = textView.text as Spanned
            val layout = textView.layout
            val firstLine = layout.getLineForOffset(rendered.indexOf("A long first bullet"))
            val nestedLine = layout.getLineForOffset(rendered.indexOf("Nested bullet"))
            val numberedLine = layout.getLineForOffset(rendered.indexOf("First step"))

            assertEquals(3, rendered.getSpans(0, rendered.length, BulletListItemSpan::class.java).size)
            assertEquals(2, rendered.getSpans(0, rendered.length, OrderedListItemSpan::class.java).size)
            fun textStart(line: Int) = layout.getPrimaryHorizontal(layout.getLineStart(line))

            assertTrue(nestedLine > firstLine + 1)
            assertTrue(textStart(firstLine) > textStart(0))
            assertEquals(textStart(firstLine), textStart(firstLine + 1), 1f)
            assertTrue(textStart(nestedLine) > textStart(firstLine))
            assertEquals(textStart(firstLine), textStart(numberedLine), 1f)
        }
    }

    @Test
    fun rendersFormattedTextAndReturnsToCaller() {
        var returned = false
        composeRule.setContent {
            MaterialTheme {
                TranscriptReader(text = "Formatted paragraph.", formatted = true) { returned = true }
            }
        }

        composeRule.onNodeWithText("Formatted text").assertIsDisplayed()
        onView(withText("Formatted paragraph.")).check(matches(isDisplayed()))
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.runOnIdle { assertTrue(returned) }
    }

    @Test
    fun rendersLoadingSeparatelyFromUnavailableText() {
        composeRule.setContent {
            MaterialTheme { TranscriptReaderLoading(formatted = false, onBack = {}) }
        }

        composeRule.onNodeWithText("Loading transcript…").assertIsDisplayed()
    }

    @Test
    fun transcriptPaneHasCopyAndExpandActions() {
        var expanded = false
        composeRule.setContent {
            MaterialTheme {
                TranscriptPane(
                    rawText = "Raw dictation",
                    formattedText = "",
                    selectedFormatted = false,
                    onSelectFormatted = {},
                    textHeight = 150.dp,
                    onExpand = { expanded = true },
                    onFormat = { true },
                    isBusy = false
                )
            }
        }
        composeRule.onNodeWithContentDescription("Copy transcript").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open full screen").performClick()
        composeRule.runOnIdle { assertTrue(expanded) }
    }

}
