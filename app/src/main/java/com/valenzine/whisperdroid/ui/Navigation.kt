package com.valenzine.whisperdroid.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun Navigation(sharedAudioUri: Uri? = null) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") { MainScreen(navController = navController, sharedAudioUri = sharedAudioUri) }
        composable("settings") { SettingsScreen() }
    }
}