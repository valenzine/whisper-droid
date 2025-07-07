# Whisper Droid

Whisper Droid is an Android app for transcribing audio files using the OpenAI Whisper API, with advanced formatting powered by a Large Language Model (LLM). Built with Jetpack Compose for a modern, responsive UI.

## Features

- Select and transcribe audio files (`.ogg`, `.opus`, `.mp3`, `.wav`, `.aac`, `.flac`, etc.) from your device
- Automatic language detection or manual selection (English, Spanish, Italian)
- Support for multiple OpenAI models: `whisper-1` and `gpt-4o-mini-transcribe`
- Automatic conversion of Opus/OGG files to WebM for compatibility with `gpt-4o-mini-transcribe`
- Real-time progress feedback (analyzing, converting, uploading, transcribing, formatting)
- Error handling for unsupported/corrupted files, API issues, and network problems
- View and copy raw transcription text
- Format transcribed text using an LLM (e.g., GPT-3.5-turbo) for improved readability
- Settings screen for API keys, LLM prompt, and model selection (with DataStore persistence)
- Secure storage of API keys and settings
- Modern UI with Jetpack Compose and Material 3
- Navigation between main and settings screens

## Screenshots

*Add screenshots here if available*

## Getting Started

1. **Clone the repository:**
    ```sh
    git clone https://github.com/valenzine/whisper-droid.git
    cd whisper-droid
    ```
2. **Open the project in Android Studio** and build/run on your device or emulator.

## Usage

- Tap "Select Audio File" to pick an audio file from your device.
- Choose the transcription language (Automatic, English, Spanish, Italian).
- Wait for the transcription and formatting to complete.
- View, copy, or format the transcribed text as needed.
- Configure API keys, LLM prompt, and model in the Settings screen.

## Tech Stack

- **Kotlin** / **Jetpack Compose** / **Material 3**
- **Retrofit** & **OkHttp** for networking
- **DataStore** for secure settings storage
- **MVVM** architecture with ViewModel and Repository
- **OpenAI Whisper API** and **LLM API**

## Contributing

Pull requests and issues are welcome! Please open an issue to discuss your ideas or report bugs.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.