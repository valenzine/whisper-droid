package com.valenzine.whisperdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valenzine.whisperdroid.viewmodel.SettingsViewModel
import com.valenzine.whisperdroid.viewmodel.SettingsViewModelFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val repository = remember { com.valenzine.whisperdroid.repository.SettingsRepository(context) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(repository))
    val transcriptionApiKey by viewModel.transcriptionApiKey.collectAsState()
    val llmApiKey by viewModel.llmApiKey.collectAsState()
    val llmPrompt by viewModel.llmPrompt.collectAsState()
    val transcriptionModel by viewModel.transcriptionModel.collectAsState()

    var tempTranscriptionApiKey by remember { mutableStateOf("") }
    var tempLlmApiKey by remember { mutableStateOf("") }
    var tempLlmPrompt by remember { mutableStateOf("") }
    var tempTranscriptionModel by remember { mutableStateOf("whisper-1") }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showSnackbar by remember { mutableStateOf(false) }

    val transcriptionModels = listOf("whisper-1", "gpt-4o-mini-transcribe")

    // Keep temp values in sync with ViewModel when they change
    LaunchedEffect(transcriptionApiKey) {
        tempTranscriptionApiKey = transcriptionApiKey
    }
    LaunchedEffect(llmApiKey) {
        tempLlmApiKey = llmApiKey
    }
    LaunchedEffect(llmPrompt) {
        tempLlmPrompt = llmPrompt
    }
    LaunchedEffect(transcriptionModel) {
        tempTranscriptionModel = transcriptionModel
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        activity?.onBackPressedDispatcher?.onBackPressed()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            ExposedDropdownMenuBox(
                expanded = isModelDropdownExpanded,
                onExpandedChange = { isModelDropdownExpanded = !isModelDropdownExpanded }
            ) {
                TextField(
                    value = tempTranscriptionModel,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Transcription Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = isModelDropdownExpanded,
                    onDismissRequest = { isModelDropdownExpanded = false }
                ) {
                    transcriptionModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                tempTranscriptionModel = model
                                isModelDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = tempLlmPrompt,
                onValueChange = { tempLlmPrompt = it },
                label = { Text("LLM Instruction (transcribed text will be added automatically)") },
                placeholder = { Text("Format the following text with paragraphs:") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                viewModel.saveTranscriptionApiKey(tempTranscriptionApiKey)
                viewModel.saveLlmApiKey(tempLlmApiKey)
                viewModel.saveLlmPrompt(tempLlmPrompt)
                viewModel.saveTranscriptionModel(tempTranscriptionModel)
                showSnackbar = true
            }) {
                Text("Save")
            }

            if (showSnackbar) {
                LaunchedEffect(snackbarHostState) {
                    snackbarHostState.showSnackbar("Settings saved!")
                    showSnackbar = false
                }
            }
        }
    }
}