# THINKLET Realtime Companion

English | [日本語](README.ja.md)

An app for THINKLET that connects the camera video and microphone audio to the OpenAI Realtime API, so you can ask for advice by voice in Japanese.

## Overview

When both of your hands are busy, looking something up is hard unless someone is standing next to you.
Wear a THINKLET, leave this app running, and you can ask questions out loud and get short spoken advice back.
The app streams microphone audio to the Realtime API and also sends camera frames at a fixed cadence.
The model takes what is in front of you into account, so you do not have to describe the scene in words.

This sample ships a generic realtime conversation mode plus two purpose-built use cases: cooking support and English conversation practice.

### Features

- WebSocket connection to the OpenAI Realtime API
- Microphone audio streaming
- Camera recording and frame sampling with CameraX
- Playback of OpenAI audio responses
- Status announcements via Android TextToSpeech
- Session recording (event log, API usage, timeline, video, audio, sent frames)
- A script that generates an HTML review for inspecting saved sessions

## Requirements

- Android minSdk 27
- Android targetSdk 35
- Java 17
- Kotlin 2.0.21
- Android Gradle Plugin 8.9.1
- Intended for THINKLET LC01

## Setup

### Build and install

Open this repository in Android Studio, or use the Gradle wrapper from the command line.
It depends on libraries such as Jetpack Compose, CameraX, OkHttp, and kotlinx.coroutines, and does not use the THINKLET App SDK.

```bash
./gradlew build
```

To install on a device:

```bash
./gradlew installDebug
```

### Grant camera and microphone permissions

The camera and microphone permissions must be granted on first launch (and after every reinstall).

```bash
adb shell pm grant com.yujif.thinklet.realtimecompanion android.permission.CAMERA
adb shell pm grant com.yujif.thinklet.realtimecompanion android.permission.RECORD_AUDIO
```

### Key config for launching the app (optional)

This config assigns the app to a long press of the THINKLET Launcher's center button.

```bash
adb push device-config/key_config_realtimecompanion.json /sdcard/Android/data/ai.fd.thinklet.app.launcher/files/key_config.json
```

### Configure the API key

After launching the app, enter your OpenAI API key on screen.
On a real THINKLET, mirroring the screen to a PC with `scrcpy` makes it easy to copy and paste the key from the PC side.

This app uses an experimental BYOK (Bring Your Own Key) approach: a regular OpenAI API key is used directly on the device.
There is no backend that issues ephemeral tokens.
The key you enter is encrypted with a non-exportable AES-GCM key in the Android Keystore, stored in the app's private storage on the device, and excluded from Android cloud backup and device-to-device transfer.
It is never written into the repository, but how strongly the stored key is protected depends on the device's own security mechanisms.

- Use a dedicated key with a usage limit for experiments.
- Delete keys you no longer need with the "Delete key" button in the app.

Using the Realtime API incurs API costs.
Before running long sessions or increasing the camera frame cadence, check the pricing and how much data you will be sending.

### Install Josee TTS

THINKLET ships with an English TTS engine, but this app announces status in Japanese, so a Japanese TTS such as [Josee TTS](https://github.com/FairyDevicesRD/droid.josee.tts) must be installed.
Follow that repository's instructions to build the APK and install it with `adb install`.

The TTS engine, voice models, and dictionaries are not bundled in this repository.

## Usage

### Launching the app

If you configured the key config during setup, a long press of the center button launches the app.

### Selecting a use case

Volume up moves to the next use case, volume down moves to the previous one, and a short press of the center button starts the selected use case.
The default at launch is the generic realtime conversation.

To install a build that starts with cooking support or English conversation practice already selected, pass the use case ID to the Gradle property `realtimeCompanionUseCase` when making a debug build.

```bash
./gradlew installDebug -PrealtimeCompanionUseCase=cooking_support
./gradlew installDebug -PrealtimeCompanionUseCase=english_conversation_learning
```

When the property is omitted or set to an unknown value, the app starts as `generic_realtime_conversation`.

### Stopping

A short press of the center button stops the realtime session.
Use cases can be switched while stopped.

### Extracting and analyzing recordings

The procedure for pulling recordings (session data) off a device and building a report is documented in [skills/thinklet-realtime-session-review/SKILL.md](skills/thinklet-realtime-session-review/SKILL.md).

## Session data

### What is recorded

Runtime recordings are stored in the app's external files directory on the THINKLET.

```text
/sdcard/Android/data/com.yujif.thinklet.realtimecompanion/files/sessions/
```

Main contents:

- `metadata.json`
- `events.jsonl`
- `usage.jsonl`
- `timeline.jsonl`
- `frames/`
- `camera.mp4`
- `mic.pcm`
- `assistant.pcm`
- `cooking-memory.json` (only for `cooking_support`)

Audio is saved as assistant speech in `assistant.pcm` and microphone input in `mic.pcm`.
Non-cooking use cases such as `generic_realtime_conversation` and `english_conversation_learning` do not feed cooking memory into the prompt and do not write `cooking-memory.json`.

> [!WARNING]
> There is a storage cap per session.
> `camera.mp4` is capped on its own at 1 GB by CameraX, and everything else (`events.jsonl`, `timeline.jsonl`, `usage.jsonl`, `mic.pcm`, `assistant.pcm`, `frames/`) is capped at 500 MB in total.
> Starting a session requires at least 1.5 GB of free space, matching the combined cap, and the total size including the recording is monitored while the session runs.
> The session is automatically and safely stopped when a cap is reached, when camera recording fails, or when a write to any of these files fails.

To delete a saved session, remove that session's directory under `sessions/`.
There is currently no in-app UI for bulk deletion.

### Generating the review HTML

A script is provided for generating an HTML review so saved sessions can be inspected on a PC.

```bash
python3 scripts/build_session_review_html.py --sessions-root /path/to/device-sessions --output review.html
```

The generated `review.html` can be opened directly in a browser (`file:///path/to/review.html`).

When reviewing a cooking support or English conversation session, specify the use case at generation time so the view matches.
Without it, the session is rendered as a `generic_realtime_conversation` review.
Specifying `english_conversation_learning` hides the `update_cooking_memory` diagnostics, which are specific to cooking support.

```bash
python3 scripts/build_session_review_html.py \
  --sessions-root /path/to/device-sessions \
  --output review.html \
  --use-case-id cooking_support \
  --use-case-label "Cooking Support"
python3 scripts/build_session_review_html.py \
  --sessions-root /path/to/device-sessions \
  --output review.html \
  --use-case-id english_conversation_learning \
  --use-case-label "English Conversation Learning"
```

## Development

### Directory layout

```text
app/              The Android app itself
device-config/    Key config for the THINKLET Launcher
gradle/           Gradle wrapper and version catalog
scripts/          Helper scripts for session review
skills/           Session review procedure
tests/            Tests for the Python helper scripts
```

Main Kotlin packages:

- `audio/`: microphone input and assistant audio playback
- `camera/`: CameraX recording, frame sampling, and image send control
- `core/`: session path management, cost estimation, button control, and log masking
- `memory/`: cooking memory model and persistence
- `openai/`: Realtime API WebSocket connection and per-use-case prompt configuration
- `session/`: session recording and clock
- `tts/`: Japanese TextToSpeech announcements

### Tests

Android/Kotlin unit tests:

```bash
./gradlew test
```

Python tests for the session review generation script:

```bash
python3 -m unittest discover -s tests
```

### Contributing

Please read `CONTRIBUTING.md` before proposing changes.
Do not include API keys, signing materials, raw session data from real devices, or personal information in public issues or pull requests.

### Reporting vulnerabilities

If you find a vulnerability, please read `SECURITY.md`.
Do not paste vulnerability details, API keys, bearer tokens, or real-device session recordings (audio, video, logs) into public issues.

## License

This repository is published under the MIT License. See `LICENSE` for details.

When using external dependencies that are not bundled here, such as Josee TTS, follow the license terms of each distributor.
Obtain Fairy Devices distributions such as the THINKLET App SDK from their official source.

THINKLET is a registered trademark of Fairy Devices Inc.
This repository is a research and development reference implementation for THINKLET, and is not an official Fairy Devices Inc. app.
