package com.valenzine.whisperdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.* 
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModel
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModelFactory
import java.io.File
import android.net.Uri

@Composable
fun MainScreen(navController: NavController, sharedAudioUri: Uri? = null) {
    val context = LocalContext.current
    val repository = remember { com.valenzine.whisperdroid.repository.TranscriptionRepository(context) }
    val viewModel: TranscriptionViewModel = viewModel(factory = TranscriptionViewModelFactory(repository))
    val transcription by viewModel.transcription.collectAsState()
    val formattedText by viewModel.formattedText.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri ->
            processAudioFile(uri, context, viewModel)
        }
    }

    // Process shared audio file when the screen loads
    LaunchedEffect(sharedAudioUri) {
        sharedAudioUri?.let { uri ->
            processAudioFile(uri, context, viewModel)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SnackbarHost(hostState = snackbarHostState)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { navController.navigate("settings") }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        Button(onClick = { launcher.launch("audio/*") }) {
            Text("Select Audio File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show loading indicator for transcription
        if (uiState is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.Loading) {
            CircularProgressIndicator()
            Text("Transcribing audio...")
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show loading indicator for formatting
        if (uiState is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.FormattingText) {
            CircularProgressIndicator()
            Text("Formatting text...")
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show error message
        if (uiState is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.Error) {
            val errorMessage = (uiState as com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.Error).message
            LaunchedEffect(snackbarHostState, errorMessage) {
                snackbarHostState.showSnackbar("Error: $errorMessage")
            }
        }

        // Show success message
        if (uiState is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.Success) {
            val transcriptionText = (uiState as com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.Success).transcription
            LaunchedEffect(snackbarHostState, transcriptionText) {
                snackbarHostState.showSnackbar("Transcription complete!")
            }
        }

        TextField(
            value = transcription,
            onValueChange = { },
            label = { Text("Transcription") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 150.dp),
            maxLines = 10,
            singleLine = false
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { viewModel.formatText() }) {
            Text("Format Text")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = formattedText,
            onValueChange = { },
            label = { Text("Formatted Text") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 150.dp),
            maxLines = 10,
            singleLine = false
        )
    }
}

// Helper function to process audio files from URI
private fun processAudioFile(uri: Uri, context: android.content.Context, viewModel: TranscriptionViewModel) {
    val inputStream = context.contentResolver.openInputStream(uri)
    val file = File(context.cacheDir, "temp_audio_file")
    inputStream?.let {
        file.writeBytes(it.readBytes())
        viewModel.transcribeFile(file)
    }
}