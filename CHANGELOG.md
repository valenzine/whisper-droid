# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [0.1.3] - 2026-09-08
### Added
- Android 12 and newer splash-screen resources.
- A pinned Gradle daemon JVM toolchain for consistent builds between Android Studio and the command line.

### Changed
- Updated the project to Android Gradle Plugin 9.4.0, Gradle 9.7.1, Kotlin and Compose compiler plugin 2.3.21, Java 17, and current Android dependencies.
- Updated the compile and target SDK levels to Android 16 (API 36).
- Migrated the Compose build configuration to the current Kotlin Compose plugin.

### Fixed
- Kept the main screen clear of the status bar, navigation bar, and display cutouts under Android's enforced edge-to-edge layout.
- Corrected status-bar icon contrast in light and dark themes.
- Moved Android 12-specific splash-screen attributes into the appropriate versioned resources.

## [0.1.2] - 2025-08-16
### Added
- Support for the `gpt-4o-transcribe` model in addition to existing transcription models. 
- Copy-to-clipboard action for transcription and formatted text.
- Resizable tabbed text areas on the main screen (drag to resize transcription/formatted panes).

### Changed
- Unified API key storage and usage (single API key field in Settings). Legacy key fields removed and consolidated.
- Partial password masking for the API key field (first N characters visible, rest masked) to improve UX while avoiding accidental key exposure.
- When requesting text formatting the UI now switches automatically to the "Formatted" tab.
- `MainScreen` and `SettingsScreen` UI refactor for improved layout and usability, including a top app bar with back navigation in Settings.
- Updated Android Gradle Plugin to 8.11.1.

### Fixed
- Various UI/UX tweaks and minor bug fixes related to tab switching, state handling, and error messaging in transcription/formatting flows.

## [0.1.1] - 2025-07-07
### Changed
- Improved language selection: Language parameter is now passed through the UI, ViewModel, and Repository, allowing users to select "Automatic" or a specific language for transcription.
- Updated `TranscriptionRepository` and `TranscriptionApi` to support an optional language parameter for the Whisper API.
- Refactored `MainScreen` to include a language dropdown and properly pass the selected language to the transcription process.
- Updated `TranscriptionViewModel` to accept and forward the language parameter.
- Minor UI and code structure improvements for better maintainability.

### Fixed
- Fixed build errors related to missing or incorrect function parameters and imports.
- Ensured that if no language is selected, the app defaults to automatic language detection.

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
