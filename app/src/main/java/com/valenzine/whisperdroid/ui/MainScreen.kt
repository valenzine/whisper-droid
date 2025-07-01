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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModel
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModelFactory
import java.io.File

@Composable
fun MainScreen(navController: NavController, viewModel: TranscriptionViewModel = viewModel()) {
    val transcription by viewModel.transcription.collectAsState()
    val formattedText by viewModel.formattedText.collectAsState()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri ->
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "temp_audio_file")
            inputStream?.let {
                file.writeBytes(it.readBytes())
                viewModel.transcribeFile(file)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        TextField(
            value = transcription,
            onValueChange = { },
            label = { Text("Transcription") },
            modifier = Modifier.fillMaxWidth()
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
            modifier = Modifier.fillMaxWidth()
        )
    }
}