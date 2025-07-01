# whisper-droid

An Android application to transcribe audio files using the OpenAI Whisper API and format the text using a Large Language Model (LLM).

## Features

- Select an audio file (`.ogg`, `.opus`, etc.) from the device.
- Send the audio file to the OpenAI Whisper API for transcription.
- Display the raw transcription text.
- Send the transcribed text to an LLM (e.g., GPT-3.5-turbo) for automatic formatting (e.g., adding paragraphs).
- Display the formatted text.

## Setup

1.  Clone the repository.
2.  Create a `local.properties` file in the root of the project.
3.  Add your OpenAI API key to the `local.properties` file:
    ```
    OPENAI_API_KEY="YOUR_API_KEY_HERE"
    ```
4.  Build and run the application in Android Studio.