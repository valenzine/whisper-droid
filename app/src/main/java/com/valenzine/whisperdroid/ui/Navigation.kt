package com.valenzine.whisperdroid.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.valenzine.whisperdroid.WhisperDroidApplication

@Composable
internal fun Navigation(app: WhisperDroidApplication) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") { MainScreen(navController, app) }
        composable("settings") { SettingsScreen() }
    }
}
