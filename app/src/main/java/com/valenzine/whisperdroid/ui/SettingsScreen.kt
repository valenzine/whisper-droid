package com.valenzine.whisperdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valenzine.whisperdroid.viewmodel.SettingsViewModel
import com.valenzine.whisperdroid.viewmodel.SettingsViewModelFactory

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repository = remember { com.valenzine.whisperdroid.repository.SettingsRepository(context) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(repository))
    val transcriptionApiKey by viewModel.transcriptionApiKey.collectAsState()
    val llmApiKey by viewModel.llmApiKey.collectAsState()

    var tempTranscriptionApiKey by remember { mutableStateOf("") }
    var tempLlmApiKey by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var showSnackbar by remember { mutableStateOf(false) }

    // Keep temp values in sync with ViewModel when they change
    LaunchedEffect(transcriptionApiKey) {
        tempTranscriptionApiKey = transcriptionApiKey
    }
    LaunchedEffect(llmApiKey) {
        tempLlmApiKey = llmApiKey
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SnackbarHost(hostState = snackbarHostState)
        TextField(
            value = tempTranscriptionApiKey,
            onValueChange = { tempTranscriptionApiKey = it },
            label = { Text("Transcription API Key") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = tempLlmApiKey,
            onValueChange = { tempLlmApiKey = it },
            label = { Text("LLM API Key") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            viewModel.saveTranscriptionApiKey(tempTranscriptionApiKey)
            viewModel.saveLlmApiKey(tempLlmApiKey)
            showSnackbar = true
        }) {
            Text("Save")
        }

        if (showSnackbar) {
            LaunchedEffect(snackbarHostState) {
                snackbarHostState.showSnackbar("API keys saved!")
                showSnackbar = false
            }
        }
    }
}