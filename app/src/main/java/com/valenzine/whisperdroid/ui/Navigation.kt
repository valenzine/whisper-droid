package com.valenzine.whisperdroid.ui

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
            route = "reader/{id}/{formatted}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("formatted") { type = NavType.BoolType }
            )
        ) { destination ->
            val arguments = requireNotNull(destination.arguments)
            val id = arguments.getLong("id")
            val formatted = arguments.getBoolean("formatted")
            val readerState by produceState<ReaderEntryState>(ReaderEntryState.Loading, id) {
                app.history.entry(id).collect { entry -> value = ReaderEntryState.Loaded(entry) }
            }
            when (val state = readerState) {
                ReaderEntryState.Loading -> TranscriptReaderLoading(formatted) { navController.popBackStack() }
                is ReaderEntryState.Loaded -> {
                    val text = if (formatted) state.entry?.formattedText else state.entry?.rawText
                    TranscriptReader(text, formatted) { navController.popBackStack() }
                }
            }
        }
        composable("settings") { SettingsScreen() }
        composable("history") {
            HistoryScreen(navController, app) { id, formatted ->
                navController.navigate("reader/$id/$formatted")
            }
        }
    }
}

private sealed interface ReaderEntryState {
    data object Loading : ReaderEntryState
    data class Loaded(val entry: com.valenzine.whisperdroid.history.TranscriptEntry?) : ReaderEntryState
}
