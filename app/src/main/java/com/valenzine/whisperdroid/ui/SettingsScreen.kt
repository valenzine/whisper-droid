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
// Visibility icons not present in this project's icon set; use existing icons instead
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val repository = remember { com.valenzine.whisperdroid.repository.SettingsRepository(context) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(repository))
    val apiKey by viewModel.apiKey.collectAsState()
    val llmPrompt by viewModel.llmPrompt.collectAsState()
    val transcriptionModel by viewModel.transcriptionModel.collectAsState()

    var tempApiKey by remember { mutableStateOf("") }
    var tempLlmPrompt by remember { mutableStateOf("") }
    var tempTranscriptionModel by remember { mutableStateOf("whisper-1") }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showSnackbar by remember { mutableStateOf(false) }

    val transcriptionModels = listOf("whisper-1", "gpt-4o-mini-transcribe")

    // Keep temp values in sync with ViewModel when they change
    LaunchedEffect(apiKey) {
        tempApiKey = apiKey
    }
    LaunchedEffect(llmPrompt) {
        tempLlmPrompt = llmPrompt
    }
    LaunchedEffect(transcriptionModel) {
        tempTranscriptionModel = transcriptionModel
    }

    // VisualTransformation that shows the first `visibleCount` characters and masks the rest
    fun partialPasswordVisualTransformation(visibleCount: Int): VisualTransformation {
        return VisualTransformation { text ->
            val original = text.text
            if (original.length <= visibleCount) {
                TransformedText(AnnotatedString(original), object : OffsetMapping {
                    override fun originalToTransformed(offset: Int) = offset
                    override fun transformedToOriginal(offset: Int) = offset
                })
            } else {
                val visible = original.take(visibleCount)
                val masked = "•".repeat(original.length - visibleCount)
                val transformed = visible + masked
                TransformedText(AnnotatedString(transformed), object : OffsetMapping {
                    override fun originalToTransformed(offset: Int) = offset
                    override fun transformedToOriginal(offset: Int) = offset
                })
            }
        }
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
            // Single unified API key field
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = tempApiKey,
                    onValueChange = { tempApiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = partialPasswordVisualTransformation(12)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LLM API Key field removed - we use the single unified key above

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
                // Save unified API key
                viewModel.saveApiKey(tempApiKey)
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