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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.net.Uri
import com.valenzine.whisperdroid.R
import com.valenzine.whisperdroid.WhisperDroidApplication
import com.valenzine.whisperdroid.viewmodel.SettingsViewModel
import com.valenzine.whisperdroid.viewmodel.SettingsViewModelFactory
import com.valenzine.whisperdroid.viewmodel.TranscriptionUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    navController: NavController,
    app: WhisperDroidApplication,
    openReader: (id: Long, formatted: Boolean, promptId: String?) -> Unit
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

    fun enqueueAudio(uri: Uri) {
        if (audioSession.enqueue(uri, languageCodes[selectedLanguage], isLocalPicker = true) == null) {
            screenScope.launch { snackbarHostState.showSnackbar("The audio queue is full. Select this audio again later.") }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { enqueueAudio(it) }
    }

    var transcriptionHeight by remember { mutableStateOf(150.dp) }
    var formattedHeight by remember { mutableStateOf(150.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val currentEntry = screenState.historyEntryId
    val allPromptResults by remember(currentEntry) {
        currentEntry?.let(app.history::results) ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val promptResults = allPromptResults.filter { it.entryId == currentEntry }
    var pendingPromptId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPromptId = pendingPromptId
        ?.takeIf { id -> appSettings.prompts.any { it.id == id } }
        ?: appSettings.activePromptId
    LaunchedEffect(appSettings.activePromptId) {
        if (pendingPromptId == appSettings.activePromptId) pendingPromptId = null
    }

    val selectedResult = promptResults.firstOrNull { it.promptId == selectedPromptId }
    var selectedTab by rememberSaveable(currentEntry) { mutableStateOf<Boolean?>(null) }
    val isEdited = selectedTab ?: (selectedResult != null)
    val formattingCurrent = uiState is TranscriptionUiState.FormattingText &&
        (screenState.formattingEntryId == null || screenState.formattingEntryId == currentEntry) &&
        (screenState.formattingPromptId == null || screenState.formattingPromptId == selectedPromptId)

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = languageDropdownExpanded,
                    onExpandedChange = { languageDropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedLanguage,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
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

                val audioPickerLabel = "Select audio file"
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(audioPickerLabel) } },
                    state = rememberTooltipState()
                ) {
                    FilledIconButton(
                        onClick = { launcher.launch("audio/*") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_audio_file), contentDescription = audioPickerLabel)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                appSettings.prompts.forEach { prompt ->
                    FilterChip(
                        selected = prompt.id == selectedPromptId,
                        onClick = {
                            if (pendingPromptId != null || prompt.id != appSettings.activePromptId) {
                                pendingPromptId = prompt.id
                                settingsViewModel.selectPrompt(prompt.id)
                            }
                            selectedTab = true
                        },
                        label = { Text(prompt.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = CircleShape,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            key(isEdited, currentEntry, selectedPromptId.takeIf { isEdited }) {
                TranscriptPane(
                    rawText = transcription,
                    editedText = selectedResult?.formattedText.orEmpty(),
                    selectedEdited = isEdited,
                    onSelectFormatted = { selectedTab = it },
                    textHeight = if (isEdited) formattedHeight else transcriptionHeight,
                    onExpand = currentEntry?.let { id ->
                        { openReader(id, isEdited, selectedPromptId.takeIf { isEdited }) }
                    },
                    onFormat = {
                        viewModel.formatText(selectedPromptId).also { started ->
                            if (started && !isEdited) selectedTab = null
                        }
                    },
                    isBusy = screenState.isBusy,
                    isFormatting = formattingCurrent
                )
            }

            if (transcription.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .padding(vertical = 4.dp)
                        .pointerInput(isEdited) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaDp = with(density) { dragAmount.y.toDp() }
                                if (!isEdited) {
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
                    TranscriptionUiState.FormattingText -> {
                        if (!formattingCurrent) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Editing…")
                        }
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
