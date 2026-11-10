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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.net.Uri
import androidx.compose.ui.focus.onFocusChanged
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.model.DEFAULT_LLM_PROMPT
import com.valenzine.whisperdroid.viewmodel.SettingsViewModel
import com.valenzine.whisperdroid.viewmodel.SettingsViewModelFactory
import com.valenzine.whisperdroid.viewmodel.TranscriptionUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    navController: NavController,
    app: WhisperDroidApplication,
    openReader: (id: Long, formatted: Boolean) -> Unit
) {
    val viewModel = app.transcription
    val audioSession = app.audioSession
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app.settings))
    val screenState by viewModel.screenState.collectAsState()
    val appSettings by settingsViewModel.settings.collectAsState()
    val historyEntries by app.history.entries.collectAsState(initial = emptyList())
    val transcription = screenState.transcription
    val formattedText = screenState.formattedText
    val uiState = screenState.phase
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.audioErrorMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    val languageOptions = listOf("Automatic", "English", "Spanish", "Italian")
    val languageCodes = mapOf(
        "Automatic" to null,
        "English" to "en",
        "Spanish" to "es",
        "Italian" to "it"
    )

    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var selectedLanguage by rememberSaveable {
        mutableStateOf(languageOptions.first { languageCodes[it] == audioSession.shareLanguage })
    }

    fun enqueueAudio(uri: Uri, expectedFingerprint: String? = null) {
        if (audioSession.enqueue(uri, languageCodes[selectedLanguage], expectedFingerprint) == null) {
            screenScope.launch { snackbarHostState.showSnackbar("The audio queue is full. Select this audio again later.") }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { enqueueAudio(it) }
    }
    var retranscribeFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
    val retranscribeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        retranscribeFingerprint?.let { fingerprint -> uri?.let { enqueueAudio(it, fingerprint) } }
        retranscribeFingerprint = null
    }

    var activeTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastDisplayedResult by rememberSaveable { mutableStateOf<String?>(null) }
    val tabs = listOf("Transcription", "Formatted")
    var transcriptionHeight by remember { mutableStateOf(150.dp) }
    var formattedHeight by remember { mutableStateOf(150.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var promptExpanded by rememberSaveable { mutableStateOf(false) }
    var promptDraft by rememberSaveable { mutableStateOf(appSettings.llmPrompt) }
    var promptFocused by remember { mutableStateOf(false) }
    var clearHistoryConfirmation by remember { mutableStateOf(false) }
    var promptMenuExpanded by remember { mutableStateOf(false) }
    var showCreatePrompt by remember { mutableStateOf(false) }
    var showRenamePrompt by remember { mutableStateOf(false) }
    var showDeletePrompt by remember { mutableStateOf(false) }

    LaunchedEffect(appSettings.llmPrompt) {
        if (!promptFocused) promptDraft = appSettings.llmPrompt
    }
    LaunchedEffect(promptDraft) {
        delay(500)
        if (promptDraft != appSettings.llmPrompt) {
            settingsViewModel.updatePrompt(appSettings.activePrompt.copy(content = promptDraft))
        }
    }
    LaunchedEffect(screenState.phase) {
        val phase = screenState.phase
        if (phase is TranscriptionUiState.Success) {
            val result = "${screenState.historyEntryId}:${phase.hashCode()}"
            if (lastDisplayedResult != result) {
                activeTabIndex = if (phase.formattedText == null) 0 else 1
                lastDisplayedResult = result
            }
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
                    settingsViewModel.updatePrompt(appSettings.activePrompt.copy(content = promptDraft))
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
                                audioSession.shareLanguage = languageCodes[option]
                                languageDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { launcher.launch("audio/*") }) {
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
                    ExposedDropdownMenuBox(
                        expanded = promptMenuExpanded,
                        onExpandedChange = { promptMenuExpanded = !promptMenuExpanded }
                    ) {
                        TextField(
                            value = appSettings.activePrompt.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Active processing prompt") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(promptMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = promptMenuExpanded,
                            onDismissRequest = { promptMenuExpanded = false }
                        ) {
                            appSettings.prompts.forEach { prompt ->
                                DropdownMenuItem(
                                    text = { Text(prompt.name) },
                                    onClick = {
                                        settingsViewModel.selectPrompt(prompt.id)
                                        promptMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showCreatePrompt = true }) { Text("New prompt") }
                        if (!appSettings.activePrompt.isBuiltIn) {
                            TextButton(onClick = { showRenamePrompt = true }) { Text("Rename") }
                            TextButton(onClick = { showDeletePrompt = true }) { Text("Delete") }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (!appSettings.activePrompt.isBuiltIn) {
                            TextButton(onClick = {
                                promptExpanded = !promptExpanded
                                if (!promptExpanded) {
                                    settingsViewModel.updatePrompt(appSettings.activePrompt.copy(content = promptDraft))
                                }
                            }) {
                                Text(if (promptExpanded) "Hide editor" else "Edit")
                            }
                        }
                    }
                    if (promptExpanded) {
                        OutlinedTextField(
                            value = promptDraft,
                            onValueChange = { promptDraft = it },
                            label = { Text("Processing instruction") },
                            modifier = Modifier.fillMaxWidth().onFocusChanged {
                                promptFocused = it.isFocused
                                if (!it.isFocused) {
                                    settingsViewModel.updatePrompt(appSettings.activePrompt.copy(content = promptDraft))
                                }
                            },
                            minLines = 4
                        )
                        TextButton(onClick = { promptDraft = DEFAULT_LLM_PROMPT }) {
                            Text("Reset to default prompt")
                        }
                    }
                }
            }

            if (historyEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { clearHistoryConfirmation = true }, enabled = !screenState.isBusy) { Text("Clear all") }
                        }
                        historyEntries.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { viewModel.showHistoryEntry(entry) }, enabled = !screenState.isBusy, modifier = Modifier.weight(1f)) {
                                    Text(entry.displayName, maxLines = 1)
                                }
                                TextButton(onClick = {
                                    retranscribeFingerprint = entry.audioFingerprint
                                    retranscribeLauncher.launch("audio/*")
                                }) { Text("Re-transcribe") }
                                TextButton(onClick = { viewModel.deleteHistoryEntry(entry.id) }, enabled = !screenState.isBusy) { Text("Clear") }
                            }
                            Row {
                                TextButton(onClick = { openReader(entry.id, false) }) { Text("Read raw") }
                                if (entry.formattedText.isNotBlank()) {
                                    TextButton(onClick = { openReader(entry.id, true) }) { Text("Read formatted") }
                                }
                            }
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

            val currentEntry = screenState.historyEntryId
            val selectedText = if (activeTabIndex == 0) transcription else formattedText
            if (currentEntry != null && selectedText.isNotBlank()) {
                TextButton(onClick = { openReader(currentEntry, activeTabIndex == 1) }) {
                    Text("Full screen")
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

            Row {
                Button(onClick = {
                    viewModel.formatText()
                }, enabled = transcription.isNotBlank() && !screenState.isBusy) {
                    Text("Format Text")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(selectedText))
                    coroutineScope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                }, enabled = selectedText.isNotBlank()) {
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
                    TranscriptionUiState.WaitingForApiKey -> {
                        Text("Add an OpenAI API key in Settings to continue.")
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
    if (clearHistoryConfirmation) {
        AlertDialog(
            onDismissRequest = { clearHistoryConfirmation = false },
            title = { Text("Clear transcription history?") },
            text = { Text("This removes all saved transcriptions and their duplicate detection records.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    clearHistoryConfirmation = false
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { clearHistoryConfirmation = false }) { Text("Cancel") } }
        )
    }

    if (showCreatePrompt) {
        PromptDialog(
            title = "New prompt",
            initialName = "",
            initialContent = DEFAULT_LLM_PROMPT,
            confirmLabel = "Create",
            onDismiss = { showCreatePrompt = false },
            onConfirm = { name, content ->
                settingsViewModel.createPrompt(name, content)
                showCreatePrompt = false
                promptExpanded = true
            }
        )
    }
    if (showRenamePrompt) {
        PromptDialog(
            title = "Rename prompt",
            initialName = appSettings.activePrompt.name,
            initialContent = appSettings.activePrompt.content,
            confirmLabel = "Save",
            showContent = false,
            onDismiss = { showRenamePrompt = false },
            onConfirm = { name, _ ->
                settingsViewModel.updatePrompt(appSettings.activePrompt.copy(name = name))
                showRenamePrompt = false
            }
        )
    }
    if (showDeletePrompt) {
        AlertDialog(
            onDismissRequest = { showDeletePrompt = false },
            title = { Text("Delete prompt?") },
            text = { Text("${appSettings.activePrompt.name} will no longer be available for processing.") },
            confirmButton = {
                TextButton(onClick = {
                    settingsViewModel.deletePrompt(appSettings.activePrompt.id)
                    showDeletePrompt = false
                    promptExpanded = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeletePrompt = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PromptDialog(
    title: String,
    initialName: String,
    initialContent: String,
    confirmLabel: String,
    showContent: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var content by remember(initialContent) { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                if (showContent) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Processing instruction") },
                        minLines = 4
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, content) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
