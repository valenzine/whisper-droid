# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [0.1.0] - 2025-07-07
### Added
- First proof-of-concept version.
- Audio file selection from device and support for sharing audio files into the app (via Android share intent).
- Transcription of audio files using OpenAI Whisper API and support for multiple models ("whisper-1", "gpt-4o-mini-transcribe").
- Automatic conversion of Opus/OGG files to WebM for OpenAI compatibility (for "gpt-4o-mini-transcribe" only).
- Text formatting and summarization using LLM via OpenAI API.
- Basic UI built with Jetpack Compose, including main screen and settings screen.
- Navigation between main and settings screens using Navigation Compose.
- Settings screen for managing API keys, LLM prompt, and transcription model (with DataStore persistence).
- DataStore integration for secure storage of API keys, prompt, and model selection.
- Networking layer using Retrofit and OkHttp with logging for debugging.
- ViewModel and repository architecture for separation of concerns and state management.
- File analysis, progress feedback, and error handling for file operations and API calls.
