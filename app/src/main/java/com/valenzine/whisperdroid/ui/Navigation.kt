package com.valenzine.whisperdroid.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun Navigation(
    sharedAudioUri: Uri? = null,
    onSharedAudioConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                navController = navController,
                sharedAudioUri = sharedAudioUri,
                onSharedAudioConsumed = onSharedAudioConsumed
            )
        }
        composable("settings") { SettingsScreen() }
    }
}
