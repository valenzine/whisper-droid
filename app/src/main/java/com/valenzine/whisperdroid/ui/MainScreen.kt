package com.valenzine.whisperdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModel
import com.valenzine.whisperdroid.viewmodel.TranscriptionViewModelFactory
import java.io.File
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, sharedAudioUri: Uri? = null) {
    val context = LocalContext.current
    val repository = remember { com.valenzine.whisperdroid.repository.TranscriptionRepository(context) }
    val viewModel: TranscriptionViewModel = viewModel(factory = TranscriptionViewModelFactory(repository))
    val transcription by viewModel.transcription.collectAsState()
    val formattedText by viewModel.formattedText.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Language selection state
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
        uri?.let { processAudioFile(it, context, viewModel, languageCodes[selectedLanguage]) }
    }

    // Process shared audio file when the screen loads
    LaunchedEffect(sharedAudioUri) {
        sharedAudioUri?.let { uri ->
            processAudioFile(uri, context, viewModel, languageCodes[selectedLanguage])
        }
    }

    // Tabs and resizable text area state
    var activeTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Transcription", "Formatted")
    var transcriptionHeight by remember { mutableStateOf(150.dp) }
    var formattedHeight by remember { mutableStateOf(150.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                IconButton(onClick = { navController.navigate("settings") }) {
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
                    modifier = Modifier.menuAnchor().fillMaxWidth()
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

            Button(onClick = { launcher.launch("audio/*") }) {
                Text("Select Audio File")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TabRow(selectedTabIndex = activeTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = activeTabIndex == index, onClick = { activeTabIndex = index }) {
                        Text(text = title, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content area (only one tab visible at a time). Each tab remembers its own height.
            // Content area (only one tab visible at a time). Each tab remembers its own height.
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

            // Drag handle that adjusts the height of the currently visible tab's area
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
                // visible affordance for the drag handle
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Keep the format and copy buttons available below the tabs
            val clipboardManager = LocalClipboardManager.current
            val coroutineScope = rememberCoroutineScope()

            Row {
                Button(onClick = {
                    viewModel.formatText()
                    activeTabIndex = 1 // switch to Formatted tab after requesting format
                }) {
                    Text("Format Text")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = {
                    val textToCopy = if (activeTabIndex == 0) transcription else formattedText
                    clipboardManager.setText(AnnotatedString(textToCopy))
                    coroutineScope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                }) {
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

// Helper function to process audio files from URI
private fun processAudioFile(uri: Uri, context: android.content.Context, viewModel: TranscriptionViewModel, languageCode: String?) {
    val inputStream = context.contentResolver.openInputStream(uri)
    
    // Get the original filename from the URI to preserve the extension
    val originalFileName = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    } catch (e: Exception) {
        null
    }
    
    // Create temp file with preserved extension or fallback to generic name
    val fileName = originalFileName ?: "temp_audio_file"
    val file = File(context.cacheDir, fileName)
    
    inputStream?.let {
        file.writeBytes(it.readBytes())
        viewModel.transcribeFile(file, languageCode)
    }
}

// Helper function to format file size
private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes bytes"
    }
}