## Whisper Droid

Whisper Droid is an Android app for transcribing audio files using the OpenAI speech-to-text API, with optional LLM-powered formatting. Built with Jetpack Compose for a lightweight, modern UI.

## Features

- Select and transcribe audio files (`.ogg`, `.opus`, `.mp3`, `.wav`, `.aac`, `.flac`, etc.) from your device
- Automatic language detection or manual language selection (Automatic, English, Spanish, Italian)
- Support for current and compatible transcription models: `gpt-transcribe` (default), `gpt-4o-mini-transcribe`, `gpt-4o-transcribe`, and `whisper-1`
- Automatic remux/conversion of Opus/OGG to WebM when required by the chosen model
- Resizable, tabbed text panes for raw transcription and formatted output (drag the handle to resize)
- Copy transcription or formatted text to the clipboard
- Format transcribed text using an LLM backend for improved readability
- Settings screen with DataStore-backed persistence for API key, prompt, and model selection
- Optional automatic LLM processing after each successful transcription
- Inline prompt editing from the transcription view, with debounced autosave

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

Release builds require a durable signing key. Copy `keystore.properties.example` to
`keystore.properties`, fill in the values, and keep both that file and the keystore
outside version control. A release build fails clearly when signing is not configured.

Create the key once on a trusted machine and back it up separately from the repository:

```sh
keytool -genkeypair -v -keystore /secure/path/whisper-droid-upload.jks \
  -alias whisper-droid-upload -keyalg RSA -keysize 4096 -validity 10000
cp keystore.properties.example keystore.properties
./gradlew assembleRelease bundleRelease
```

Do not commit the keystore or `keystore.properties`, and do not generate a replacement
key in CI. GitHub Actions needs the base64-encoded keystore in
`RELEASE_KEYSTORE_BASE64` and the three password/alias values named below. Prefer the
GitHub web UI or a secret manager when setting them so credentials do not enter shell
history.

## Usage

- Tap "Select Audio File" to choose an audio file from your device.
- Select a transcription model in Settings and, optionally, a language on the transcription screen.
- The status bar shows progress (analyzing, converting, uploading, transcribing, formatting).
- Tap "Format Text" to send the transcript to the formatting engine; the UI switches to the Formatted tab after success.
- Enable automatic processing in the Processing card to format completed transcripts without a second tap. Expand the card to edit the prompt; changes are saved automatically.
- Use the Copy button to copy the currently visible text to the clipboard.

## Compatibility & notes

- Model compatibility: `gpt-transcribe`, `gpt-4o-mini-transcribe`, and `gpt-4o-transcribe` require supported containers. The app attempts to remux Opus/OGG into WebM when necessary. If conversion fails, use `whisper-1` or pre-convert the audio to MP3/WAV. Realtime transcription, snapshots, diarization, and speaker-segment UI are not part of version 0.2.0.
- API key handling: Settings now use a single unified API key field (stored in DataStore). The UI applies partial masking to reduce accidental exposure of the full key.
- Formatting backend: choose the recommended cost-effective `gpt-5.6-luna` or cheapest `gpt-5-nano` model in Settings. The prompt and selected model are persisted together with the API key and transcription model.
- File size limits: the app rejects files over 25 MB while copying them locally, before upload. It also surfaces helpful errors for invalid API keys, rate limits, unsupported formats, and server failures.

## Tech stack

- Kotlin / Jetpack Compose / Material 3
- Retrofit & OkHttp for networking
- DataStore for settings persistence
- MVVM architecture with ViewModel and Repository

## Contributing

Contributions are welcome. Open an issue to discuss ideas or report bugs, and submit pull requests for fixes or improvements.

## Installation identity and Android diagnostics

The canonical application ID is `com.valenzine.whisperdroid`. If Android shows two
launcher entries, inspect the installed packages and profiles rather than assuming a
signing problem:

```sh
adb shell pm list packages | grep whisper
adb shell dumpsys package com.valenzine.whisperdroid
adb shell pm list users
adb shell pm list packages --user USER_ID | grep whisper
```

Two package names indicate separate builds or a work/personal profile. Two entries
with one package indicate a launcher/profile duplicate. A certificate change prevents
an update but does not create a second same-profile installation. Before installing
the first signed release, remove the obsolete debug/profile copy and reinstall once;
later signed builds will update the durable installation.

To compare certificates, locate and pull the installed APK with
`adb shell pm path com.valenzine.whisperdroid` and `adb pull`, then run
`apksigner verify --print-certs` on both that APK and the new release APK. If only a
stale profile copy should be removed, use
`adb uninstall --user USER_ID com.valenzine.whisperdroid`; omit `--user` only when you
intend to uninstall the package for the primary device user as well.

## Continuous integration and releases

Pull requests and pushes to `main` or `development` run tests, lint, debug assembly,
and Android-test compilation. The uploaded debug APK is development-only: it uses the
debug certificate and is not a durable upgrade path.
Pushing a tag matching the Gradle `versionName` (for example `v0.2.0`) builds a signed
APK and AAB, verifies the APK signature, generates `SHA256SUMS`, and attaches all
three files to a GitHub Release.

Repository administrators must configure the encrypted Actions secrets
`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and
`RELEASE_KEY_PASSWORD`. Generate the upload keystore once, back it up securely, and
never generate a replacement key in CI.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
