## Whisper Droid

Whisper Droid is an Android app for transcribing audio files using the OpenAI Whisper API, with optional LLM-powered formatting. Built with Jetpack Compose for a lightweight, modern UI.

## Features

- Select and transcribe audio files (`.ogg`, `.opus`, `.mp3`, `.wav`, `.aac`, `.flac`, etc.) from your device
- Automatic language detection or manual language selection (Automatic, English, Spanish, Italian)
- Support for multiple transcription models: `whisper-1`, `gpt-4o-mini-transcribe`, and `gpt-4o-transcribe`
- Automatic remux/conversion of Opus/OGG to WebM when required by the chosen model
- Resizable, tabbed text panes for raw transcription and formatted output (drag the handle to resize)
- Copy transcription or formatted text to the clipboard
- Format transcribed text using an LLM backend for improved readability
- Settings screen with DataStore-backed persistence for API key, prompt, and model selection

## Quick start

1. Clone the repository and open it in Android Studio:

```sh
git clone https://github.com/valenzine/whisper-droid.git
cd whisper-droid
```

2. Build and run on a device or emulator (Android Studio recommended). You can also assemble a debug APK from the command line:

```sh
./gradlew assembleDebug
```

## Usage

- Tap "Select Audio File" to choose an audio file from your device.
- Select a transcription model and (optionally) a language.
- The status bar shows progress (analyzing, converting, uploading, transcribing, formatting).
- Tap "Format" to send the transcript to the formatting engine; the UI switches to the Formatted tab automatically.
- Use the Copy button to copy the currently visible text to the clipboard.

## Compatibility & notes

- Model compatibility: `gpt-4o-mini-transcribe` and `gpt-4o-transcribe` may require Opus-in-WebM input. The app attempts to remux Opus/OGG into WebM when necessary. Some devices lack codecs or platform support for remuxing; if conversion fails, use `whisper-1` or pre-convert your audio to MP3/WAV.
- API key handling: Settings now use a single unified API key field (stored in DataStore). The UI applies partial masking to reduce accidental exposure of the full key.
- Formatting backend: formatting requests are sent to an LLM model configured in the code (currently `gpt-4.1-nano` for text editing/formatting).
- File size limits: the OpenAI API enforces maximum upload sizes; large files may fail with a 413 error. The app surfaces helpful error messages for common failure modes (invalid API key, rate limits, server errors).

## Tech stack

- Kotlin / Jetpack Compose / Material 3
- Retrofit & OkHttp for networking
- DataStore for settings persistence
- MVVM architecture with ViewModel and Repository

## Contributing

Contributions are welcome. Open an issue to discuss ideas or report bugs, and submit pull requests for fixes or improvements.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.