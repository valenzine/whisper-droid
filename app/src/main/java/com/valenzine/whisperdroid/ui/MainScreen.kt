package com.valenzine.whisperdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.net.Uri
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.viewmodel.SettingsViewModel
import com.valenzine.whisperdroid.viewmodel.SettingsViewModelFactory
import com.valenzine.whisperdroid.viewmodel.TranscriptionUiState
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

    var activeTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastDisplayedResult by rememberSaveable { mutableStateOf<String?>(null) }
    val tabs = listOf("Transcription", "Formatted")
    var transcriptionHeight by remember { mutableStateOf(150.dp) }
    var formattedHeight by remember { mutableStateOf(150.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Whisper Droid",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = { navController.navigate("history") }) { Text("History") }
                IconButton(onClick = {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Processing", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (appSettings.autoProcess) "Automatic formatting is on" else "Format manually when ready",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(checked = appSettings.autoProcess, onCheckedChange = settingsViewModel::setAutoProcess)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        appSettings.prompts.forEach { prompt ->
                            FilterChip(
                                selected = prompt.id == appSettings.activePromptId,
                                onClick = { settingsViewModel.selectPrompt(prompt.id) },
                                label = {
                                    Text(
                                        prompt.name,
                                        modifier = Modifier.widthIn(max = 180.dp),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                },
                                shape = CircleShape,
                                modifier = Modifier.padding(end = 8.dp)
                            )
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
            val isFormatted = activeTabIndex == 1
            Button(
                onClick = { viewModel.formatText() },
                enabled = transcription.isNotBlank() && !screenState.isBusy,
                modifier = Modifier.padding(vertical = 8.dp)
            ) { Text("Format") }

            key(activeTabIndex, currentEntry) {
                TranscriptPane(
                    text = if (isFormatted) formattedText else transcription,
                    label = if (isFormatted) "Formatted text" else "Raw transcription",
                    emptyText = if (isFormatted) "Formatted text will appear here." else "Your transcription will appear here.",
                    textHeight = if (isFormatted) formattedHeight else transcriptionHeight,
                    onExpand = currentEntry?.let { id -> { openReader(id, isFormatted) } }
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
