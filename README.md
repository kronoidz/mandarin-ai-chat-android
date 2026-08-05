# Mandarin AI Chat

An Android chat app for practicing Mandarin Chinese with an AI language tutor. You type
messages in Chinese, the AI responds with natural conversation and provides corrective
feedback when you make mistakes.

<table>
<tr><td><strong>Pinyin overlay</strong> — tap a toggle to show pinyin readings above every Hanzi character.</td></tr>
<tr><td><strong>Text-to-speech</strong> — tap the speaker button on any bot message to hear it spoken aloud in Mandarin.</td></tr>
<tr><td><strong>AI feedback</strong> — the tutor gently corrects grammar, word choice, and unnatural phrasing in English, only when needed.</td></tr>
<tr><td><strong>Configurable reasoning</strong> — adjust how much the model "thinks" before replying (DeepSeek feature).</td></tr>
</table>

## Screenshots

<!-- TODO: add screenshots -->

## Architecture

```
User message
    │
    ▼
┌───────────────────────────────────────────────┐
│  MainActivity                                 │
│  ┌───────────────┐  ┌──────────────────────┐  │
│  │ ChatAdapter   │  │   ChatService        │  │
│  │ (RecyclerView)│  │   (OkHttp + SSE)     │──▶ OpenAI-compatible API
│  └───────────────┘  └──────────────────────┘  │
│  ┌──────────────┐  ┌──────────────────────┐   │
│  │PinyinTextView│  │ TextToSpeechService  │──▶ Google Cloud TTS
│  │              │  │ (JWT + RS256 auth)   │   │
│  └──────┬───────┘  └──────────┬───────────┘   │
│         │                     │               │
│    ┌────▼────────┐    ┌───────▼──────────┐    │
│    │ PinyinDict  │    │  TtsAudioCache    │   │
│    │ (Unihan DB) │    │  (MD5 → MP3 disk) │   │
│    └─────────────┘    └───────────────────┘   │
└───────────────────────────────────────────────┘
```

### Pinyin dictionary

The `pinyin_dict.bin` file in `app/src/main/assets/` is generated from the
[Unihan database](https://www.unicode.org/reports/tr38/) `kMandarin` field.
Use the included script to regenerate it:

```bash
python3 tools/generate_pinyin_dict.py /path/to/Unihan_Readings.txt
```

The binary format is compact and designed for fast O(1) lookups on-device with zero memory overhead beyond the raw dictionary size.

## Requirements

- Android Studio (latest stable recommended)
- JDK 17+
- Android SDK 36 with minimum SDK 26 (Android 8.0)
- An API key for an OpenAI-compatible chat completions endpoint (e.g. [DeepSeek](https://platform.deepseek.com/), OpenAI, or any compatible provider)
- (Optional) A Google Cloud service account with the Text-to-Speech API enabled for audio playback

## Configuration

### 1. Create `local.properties`

Copy this template into a file named `local.properties` in the project root (it is
gitignored and will never be committed):

```properties
sdk.dir=/path/to/your/Android/Sdk
OPENAI_API_URL=https://api.deepseek.com
OPENAI_API_KEY=sk-your-api-key-here
OPENAI_MODEL=deepseek-v4-flash
GOOGLE_TTS_CREDENTIALS_PATH=/absolute/path/to/service-account-key.json
```

| Property | Required | Description |
|---|---|---|
| `sdk.dir` | Yes | Path to your Android SDK (Android Studio sets this automatically) |
| `OPENAI_API_URL` | Yes | Base URL for the OpenAI-compatible API |
| `OPENAI_API_KEY` | Yes | Your API key |
| `OPENAI_MODEL` | Yes | Model name (e.g. `deepseek-v4-flash`, `gpt-4o-mini`) |
| `GOOGLE_TTS_CREDENTIALS_PATH` | No | Absolute path to a Google Cloud service account JSON key. If omitted or empty, speaker buttons are hidden |

### 2. Google Cloud TTS (optional)

To enable the speaker buttons:

1. [Create a Google Cloud project](https://console.cloud.google.com/) and enable the
   [Text-to-Speech API](https://cloud.google.com/text-to-speech).
2. Create a service account and download its JSON key file.
3. Set `GOOGLE_TTS_CREDENTIALS_PATH` in `local.properties` to the absolute path of that file.

The credentials JSON is embedded into the app at build time via `BuildConfig`. Without it,
the speaker buttons are hidden and no TTS requests are made.

> ⚠️ **Warning:** The service account private key is baked into the APK. Do not distribute
> a release build that contains real credentials.

### 3. Choosing an AI provider

The app works with any OpenAI-compatible chat completions API:

| Provider | `OPENAI_API_URL` | `OPENAI_MODEL` (example) |
|---|---|---|
| DeepSeek | `https://api.deepseek.com` | `deepseek-chat` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| Ollama (local) | `http://<host>:11434/v1` | `llama3` |
| Any compatible proxy | `https://your-proxy.example.com/v1` | your model |

> **Note:** The app sends a system prompt instructing the model to reply in structured JSON
> with `response` and optional `feedback` fields. For best results, use a model that supports
> `response_format: json_object` and follows system instructions reliably.

## Building

### From Android Studio

1. Open the project root in Android Studio.
2. Wait for Gradle sync to complete.
3. Select **Run ▶** to build and deploy to a connected device or emulator.

### From the command line

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing configuration)
./gradlew assembleRelease
```

The APK is output to `app/build/outputs/apk/`.

## Thinking effort

DeepSeek models support a `thinking` parameter that controls how much reasoning the model
does before generating a response. The settings screen provides a slider with five levels:

| Level | `thinking_effort` value |
|---|---|
| Disabled | `disabled` |
| Low | `low` |
| High | `high` |
| Extra High | `xhigh` |
| Maximum | `max` |

The setting is persisted locally and sent with every request.
