package com.valenzine.whisperdroid.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.valenzine.whisperdroid.WhisperDroidApplication

@Composable
internal fun Navigation(app: WhisperDroidApplication) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            val activity = LocalActivity.current
            BackHandler(enabled = activity?.isTaskRoot == true && currentEntry?.destination?.route == "main") {
                activity?.let { if (!it.moveTaskToBack(false)) it.finish() }
            }
            MainScreen(navController, app) { id, formatted ->
                navController.navigate("reader/$id/$formatted")
            }
        }
        composable(
            route = "reader/{id}/{formatted}?prompt={prompt}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("formatted") { type = NavType.BoolType },
                navArgument("prompt") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { destination ->
            val arguments = requireNotNull(destination.arguments)
            val id = arguments.getLong("id")
            val formatted = arguments.getBoolean("formatted")
            val promptId = arguments.getString("prompt")
            val readerState by produceState<ReaderEntryState>(ReaderEntryState.Loading, id, formatted, promptId) {
                if (promptId == null) {
                    app.history.entry(id).collect { entry ->
                        value = ReaderEntryState.Loaded(if (formatted) entry?.formattedText else entry?.rawText)
                    }
                } else {
                    app.history.results(id).collect { results ->
                        val result = results.firstOrNull { it.promptId == promptId }
                        value = ReaderEntryState.Loaded(result?.formattedText, result?.promptName)
                    }
                }
            }
            when (val state = readerState) {
                ReaderEntryState.Loading -> TranscriptReaderLoading(formatted) { navController.popBackStack() }
                is ReaderEntryState.Loaded -> {
                    TranscriptReader(state.text, formatted, state.promptName) { navController.popBackStack() }
                }
            }
        }
        composable("settings") { SettingsScreen() }
        composable("history") {
            HistoryScreen(
                navController,
                app,
                openRawReader = { id -> navController.navigate("reader/$id/false") },
                openPromptResults = { id -> navController.navigate("history/$id/prompts") }
            )
        }
        composable(
            route = "history/{id}/prompts",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { destination ->
            val id = requireNotNull(destination.arguments).getLong("id")
            HistoryPromptResultsScreen(
                entryId = id,
                app = app,
                onBack = { navController.popBackStack() },
                openResult = { promptId -> navController.navigate("reader/$id/true?prompt=${Uri.encode(promptId)}") }
            )
        }
    }
}

private sealed interface ReaderEntryState {
    data object Loading : ReaderEntryState
    data class Loaded(val text: String?, val promptName: String? = null) : ReaderEntryState
}
