# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [0.3.1] - 2026-11-11

### Added
- A Custom action for one-off editing instructions, without saving the prompt or its result to history (#17).
- Confirmation before uploading shared audio longer than five minutes or with an unknown duration (#18).

### Fixed
- Time out stalled audio imports, cancel provider reads, remove partial files, and keep the queue moving (#19).
- Show complete summaries in history instead of cutting them off after two lines (#20).

## [0.3.0] - 2026-11-10

### Added
- On-device transcript history with source, local import time, and a brief summary. Delete individual entries or clear all history.
- Exact-audio detection that opens a saved transcript without a second API request.
- Saved editing prompts, with migration of existing custom prompts. History keeps the latest successful edit per prompt and lets you view, rerun, or delete each result.
- A selectable full-screen reader with Markdown, Copy, and Back.

### Changed
- Use compact Transcript and Edited tabs, inline Markdown, prompt pills, and a wider resize handle. Automatic editing is in Settings.
- Selecting a prompt shows its saved edit when available. History can rerun a prompt using the saved transcript, and Re-transcribe is available when the app can still reopen the original file.

### Fixed
- Shared audio opens in Whisper Droid’s own task. Processing can continue when you return to the sending app, and saved transcripts are available after relaunch.
- Show a recoverable warning when the device is offline.
- Preserve unsaved prompt drafts when Android recreates the screen.

## [0.2.1] - 2026-11-10

### Added
- GPT-6 Luna as an optional formatting model.

### Changed
- Migrate saved GPT-5 nano selections to GPT-5.6 Luna ahead of the GPT-5 nano shutdown.

## [0.2.0] - 2026-09-08
### Added
- Optional automatic LLM processing and inline prompt editing with persistence.
- `gpt-transcribe` plus compatible transcription model options and cost-effective `gpt-5.6-luna`/`gpt-5-nano` processing choices.
- Release signing configuration, GitHub verification/release workflows, APK signature checks, and checksums.

### Changed
- Raised the durable release identity to version code 5 and version name 0.2.0 while keeping application ID `com.valenzine.whisperdroid`.
- Release builds now fail when a local or CI signing key is not configured.

### Fixed
- Consumed each shared-audio intent once so returning from Settings cannot re-transcribe the previous file.

### Documentation
- Added Android package/profile diagnosis and signed-release migration instructions.

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
