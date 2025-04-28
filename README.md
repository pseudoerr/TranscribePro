# TranscribePro - Speech-to-Text App

TranscribePro is a high-performance speech-to-text application that utilizes the whisper.cpp library for efficient on-device transcription.

## Technical Overview

TranscribePro uses an optimized Whisper model for speech recognition, with JNI to interface between Kotlin and C/C++. The architecture includes an intelligent audio file management system that provides caching, automatic cleanup, and efficient storage of audio recordings and transcriptions.

## Features

- Record audio directly from the microphone
- Select existing audio files from storage
- Efficient speech-to-text transcription using whisper.cpp
- Timestamps for audio segments
- Save transcriptions alongside audio files
- Intelligent audio file management

## Getting Started

1. Build and run the app
2. Press "Select Audio" to start recording or choose an existing file
3. Choose the language for transcription
4. Press "Transcribe" to convert speech to text
5. Press "Save" to store the transcription 