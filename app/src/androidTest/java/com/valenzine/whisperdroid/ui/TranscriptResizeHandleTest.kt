package com.valenzine.whisperdroid.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valenzine.whisperdroid.MainActivity
import com.valenzine.whisperdroid.WhisperDroidApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TranscriptResizeHandleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fullWidthResizeHandleGrowsTranscriptFromItsOuterEdge() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WhisperDroidApplication
        val entry = runBlocking {
            app.history.saveRaw(
                fingerprint = UUID.randomUUID().toString(),
                displayName = "resize-test.wav",
                rawText = "A saved transcript that makes the transcript viewport resizable.",
                sourceOrigin = "Test",
                importedAt = System.currentTimeMillis()
            )
        }
        try {
            val saved = runBlocking { requireNotNull(app.history.entry(entry.id).first()) }
            composeRule.runOnIdle { app.transcription.showHistoryEntry(saved) }

            val handle = composeRule.onNodeWithContentDescription("Resize transcript").assertIsDisplayed()
            val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
            val initialBounds = handle.getUnclippedBoundsInRoot()
            assertTrue(initialBounds.bottom - initialBounds.top >= 48.dp)
            assertTrue((rootBounds.right - rootBounds.left) - (initialBounds.right - initialBounds.left) <= 40.dp)

            handle.performTouchInput {
                swipe(start = Offset(8f, 4f), end = Offset(8f, 140f))
            }
            composeRule.waitForIdle()

            val resizedBounds = composeRule.onNodeWithContentDescription("Resize transcript")
                .getUnclippedBoundsInRoot()
            assertTrue(resizedBounds.top > initialBounds.top)

            handle.performSemanticsAction(SemanticsActions.SetProgress) { setHeight -> setHeight(800f) }
            handle.performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Edit transcript").assertIsDisplayed()
            assertTrue(rootBounds.bottom - handle.getUnclippedBoundsInRoot().bottom < 56.dp)
        } finally {
            runBlocking { app.history.delete(entry.id) }
        }
    }
}
