package com.valenzine.whisperdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.net.Uri
import androidx.compose.ui.focus.onFocusChanged
import com.valenzine.whisperdroid.model.DEFAULT_LLM_PROMPT
import com.valenzine.whisperdroid.repository.SettingsRepository
import com.valenzine.whisperdroid.viewmodel.SettingsViewModel
import com.valenzine.whisperdroid.viewmodel.SettingsViewModelFactory
import com.valenzine.whisperdroid.viewmodel.TranscriptionUiState
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModel
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    sharedAudioUri: Uri? = null,
    onSharedAudioConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { com.valenzine.whisperdroid.repository.TranscriptionRepository(context) }
    val settingsRepository = remember { SettingsRepository(context) }
    val viewModel: TranscriptionViewModel = viewModel(factory = TranscriptionViewModelFactory(repository, settingsRepository))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepository))
    val screenState by viewModel.screenState.collectAsState()
    val appSettings by settingsViewModel.settings.collectAsState()
    val transcription = screenState.transcription
    val formattedText = screenState.formattedText
    val uiState = screenState.phase
    val snackbarHostState = remember { SnackbarHostState() }

    val languageOptions = listOf("Automatic", "English", "Spanish", "Italian")
    val languageCodes = mapOf(
        "Automatic" to null,
        "English" to "en",
        "Spanish" to "es",
        "Italian" to "it"
    )

    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("Automatic") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.transcribeUri(it, languageCodes[selectedLanguage]) }
    }

    LaunchedEffect(sharedAudioUri, screenState.isBusy, appSettings.apiKey) {
        sharedAudioUri?.let { uri ->
            if (settingsRepository.settingsFlow.first().apiKey.isBlank()) {
                snackbarHostState.showSnackbar("Add an OpenAI API key in Settings before transcribing.")
            } else {
                viewModel.transcribeUri(uri, languageCodes[selectedLanguage], onSharedAudioConsumed)
            }
        }
    }

    var activeTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Transcription", "Formatted")
    var transcriptionHeight by remember { mutableStateOf(150.dp) }
    var formattedHeight by remember { mutableStateOf(150.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var promptExpanded by rememberSaveable { mutableStateOf(false) }
    var promptDraft by rememberSaveable { mutableStateOf(appSettings.llmPrompt) }
    var promptFocused by remember { mutableStateOf(false) }

    LaunchedEffect(appSettings.llmPrompt) {
        if (!promptFocused) promptDraft = appSettings.llmPrompt
    }
    LaunchedEffect(promptDraft) {
        delay(500)
        if (promptDraft != appSettings.llmPrompt) settingsViewModel.saveLlmPrompt(promptDraft)
    }
    LaunchedEffect(screenState.phase) {
        val phase = screenState.phase
        if (phase is TranscriptionUiState.Success && phase.formattedText != null) {
            activeTabIndex = 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main content area becomes scrollable and takes remaining space.
        Column(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    settingsViewModel.saveLlmPrompt(promptDraft)
                    navController.navigate("settings")
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }

            // Language selection dropdown
            ExposedDropdownMenuBox(
                expanded = languageDropdownExpanded,
                onExpandedChange = { languageDropdownExpanded = !languageDropdownExpanded }
            ) {
                TextField(
                    value = selectedLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Language") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = languageDropdownExpanded
                        )
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = languageDropdownExpanded,
                    onDismissRequest = { languageDropdownExpanded = false }
                ) {
                    languageOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedLanguage = option
                                languageDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { launcher.launch("audio/*") }, enabled = !screenState.isBusy) {
                Text("Select Audio File")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Automatically process after transcription", modifier = Modifier.weight(1f))
                        Switch(
                            checked = appSettings.autoProcess,
                            onCheckedChange = settingsViewModel::setAutoProcess
                        )
                    }
                    TextButton(onClick = {
                        promptExpanded = !promptExpanded
                        if (!promptExpanded) settingsViewModel.saveLlmPrompt(promptDraft)
                    }) {
                        Text(if (promptExpanded) "Hide editing prompt" else "Edit processing prompt")
                    }
                    if (promptExpanded) {
                        OutlinedTextField(
                            value = promptDraft,
                            onValueChange = { promptDraft = it },
                            label = { Text("Processing instruction") },
                            modifier = Modifier.fillMaxWidth().onFocusChanged {
                                promptFocused = it.isFocused
                                if (!it.isFocused) settingsViewModel.saveLlmPrompt(promptDraft)
                            },
                            minLines = 4
                        )
                        TextButton(onClick = { promptDraft = DEFAULT_LLM_PROMPT }) {
                            Text("Reset to default prompt")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryTabRow(selectedTabIndex = activeTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = activeTabIndex == index, onClick = { activeTabIndex = index }) {
                        Text(text = title, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeTabIndex == 0) {
                TextField(
                    value = transcription,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Transcription") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(transcriptionHeight),
                    maxLines = Int.MAX_VALUE,
                    singleLine = false
                )
            } else {
                TextField(
                    value = formattedText,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Formatted Text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(formattedHeight),
                    maxLines = Int.MAX_VALUE,
                    singleLine = false
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .padding(vertical = 4.dp)
                    .pointerInput(activeTabIndex) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.y.toDp() }
                            if (activeTabIndex == 0) {
                                transcriptionHeight = (transcriptionHeight + deltaDp).coerceIn(80.dp, 800.dp)
                            } else {
                                formattedHeight = (formattedHeight + deltaDp).coerceIn(80.dp, 800.dp)
                            }
                        }
                    }
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val clipboardManager = LocalClipboardManager.current
            val coroutineScope = rememberCoroutineScope()
            val activeText = if (activeTabIndex == 0) transcription else formattedText

            Row {
                Button(onClick = {
                    viewModel.formatText()
                }, enabled = transcription.isNotBlank() && !screenState.isBusy) {
                    Text("Format Text")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(activeText))
                    coroutineScope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                }, enabled = activeText.isNotBlank()) {
                    Text("Copy")
                }
            }

        }

        // Bottom status area: fixed height so updates don't push main content around.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(top = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                when (val currentState = uiState) {
                    is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.PreparingFile -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Analyzing ${currentState.fileName}...")
                            Text("File size: ${formatFileSize(currentState.fileSize)}")
                        }
                    }
                    is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.TranscodingFile -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Converting ${currentState.fileName}")
                            Text("From ${currentState.fromFormat} to ${currentState.toFormat}...")
                        }
                    }
                    is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.UploadingFile -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Uploading ${currentState.fileName}")
                            Text("Using model: ${currentState.model}")
                        }
                    }
                    is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Transcribing audio...")
                    }
                    is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.FormattingText -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Formatting text...")
                    }
                    is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.Error -> {
                        // Show snackbar and reset state once for this error
                        LaunchedEffect(snackbarHostState, currentState) {
                            snackbarHostState.showSnackbar(
                                message = currentState.message,
                                actionLabel = "Dismiss"
                            )
                            viewModel.resetState()
                        }
                    }
                    is com.valenzine.whisperdroid.viewmodel.TranscriptionUiState.Success -> {
                        LaunchedEffect(snackbarHostState, currentState.transcription) {
                            snackbarHostState.showSnackbar("Transcription complete!")
                        }
                    }
                    else -> {
                        // idle - keep this area minimal to avoid layout jumps
                        Text("")
                    }
                }
            }

            // Snackbar host placed in the bottom area so messages appear without shifting main UI
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                SnackbarHost(hostState = snackbarHostState)
            }
        }
    }
}

// Helper function to format file size
private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.getDefault(), "%.1f KB", kb)
        else -> "$bytes bytes"
    }
}
