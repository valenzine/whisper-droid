package com.valenzine.whisperdroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.valenzine.whisperdroid.WhisperDroidApplication

@Composable
internal fun Navigation(app: WhisperDroidApplication) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
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
            val entry by remember(id) { app.history.entry(id) }.collectAsState(initial = null)
            val text = if (formatted) entry?.formattedText else entry?.rawText
            TranscriptReader(text, formatted) { navController.popBackStack() }
        }
        composable("settings") { SettingsScreen() }
    }
}
