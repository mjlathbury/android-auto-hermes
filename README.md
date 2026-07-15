# Hermes Drive — on-device voice assistant for Android Auto

Talk to Hermes while driving. You speak a question, Android Auto voice-dictates it,
the app runs a **small LLM fully on your phone** (no network, car/tunnel-safe), and
Android Auto reads the answer aloud over the car speakers.

## How it works

```
You speak ─▶ Android Auto voice dictation
        └─▶ RemoteInput delivers text to MessageReplyReceiver
                 └─▶ DriveAssistantService (foreground) runs LiteRT-LM on-device
                          └─▶ answer streams as a MessagingStyle notification
                                   └─▶ AA reads it aloud  ─▶  tap Reply ─▶ loop
```

- **No microphone code, no TTS code** — Android Auto does both via the MessagingStyle
  notification + `RemoteInput` path (the same sanctioned hook carGPT uses).
- **No network at inference time** — the model is on-device (LiteRT-LM / GPU backend).
- **Sideload only** — no Play Store review; install the APK directly on your phone.

## Device target

Built and tested for a **Redmi Note 14 4G** (Helio G99, Mali-G57 MC2, 8 GB RAM, Android 16).
minSdk 26 so it runs on older cars/phones too.

## Default model

- **Fast (default):** `Gemma3-1B-IT` (~0.5 GB `.litertlm`) — near-instant on the G57.
- **Quality (in-app toggle):** `Gemma3-4B-IT` (~2 GB) — smarter, slower.

## Build

Requires JDK 17 + Android SDK (platform-34, build-tools 34.0.0).

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Unit tests (pure-Kotlin `ChatSession`, no device needed):

```bash
./gradlew testDebugUnitTest
```

## Install & run on the phone

1. `adb install app/build/outputs/apk/debug/app-debug.apk`
2. Open **Hermes Drive** from the app drawer once (creates the notification channel + lets you
   grant the notification permission).
3. Download the model: in the app, pick Fast/Quality, paste the `.litertlm` URL, tap **Download
   model**. Or push it directly:
   ```bash
   adb push gemma3-1b-it.litertlm /data/data/com.hermes.drive/files/gemma3-1b-it.litertlm
   ```
   (Gemma3-1B-IT `.litertlm` lives in the `litert-community` org on HuggingFace.)
4. Connect to the car (or run the **Desktop Head Unit** emulator) and tap Reply on the
   Hermes notification to speak. The first answer after a fresh start takes a few seconds
   while the model loads; subsequent turns are fast.

## Test the voice loop

- **On-phone:** the MessagingStyle notification works as a normal notification — tap Reply,
  dictate, hear the answer via TTS.
- **In-car / DHU:** connect Android Auto or run Google's Desktop Head Unit (DHU) on a
  workstation, which renders the exact car UI and uses the same voice dictation + read-aloud.

## Architecture notes

- `DriveAssistantService` — foreground service; owns the LLM engine + chat history; streams
  each answer back as the same ongoing MessagingStyle notification.
- `ChatSession` — pure-Kotlin history (unit-tested on the JVM), prunes to the last N turns.
- `LiteRtEngine` — wraps `com.google.ai.edge.litertlm` (GPU backend), one Conversation per session.
- `SettingsStore` — DataStore for model size + optional cloud fallback.
- `ModelManager` — checks for / downloads the `.litertlm` into `filesDir`.

## Optional cloud fallback

Off by default. If you enable it and supply an OpenAI-compatible base URL, the app can route
to a larger model when you have strong signal (free-tier users can point it at the local
FreeLLMAPI proxy). On-device remains the default path.
