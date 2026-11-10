package com.valenzine.whisperdroid.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptReaderRestorationTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun restoresScrollWhenTextArrivesAfterRecreation() = runAndroidComposeUiTest<ComponentActivity> {
        val restorationTester = StateRestorationTester(this)
        val longText = List(300) { "Restored transcript line $it" }.joinToString("\n")
        var text by mutableStateOf<String?>(longText)
        restorationTester.setContent {
            DisposableEffect(Unit) {
                onDispose { text = null }
            }
            MaterialTheme { TranscriptReader(text = text, formatted = false, onBack = {}) }
        }

        onNodeWithTag("transcript-reader-scroll").performTouchInput { swipeUp() }
        restorationTester.emulateSaveAndRestore()
        onNodeWithText("This transcript is unavailable.").assertIsDisplayed()

        text = longText
        waitForIdle()
        onNodeWithTag("transcript-reader-scroll").assertIsDisplayed()
    }
}
