# MemoryStream

A personal memory engine prototype for Android. Continuously captures audio from a USB microphone, transcribes on-device with Whisper, and provides semantic search over stored transcripts.

## Requirements

- Android device running API 26+ (Android 8.0) with arm64-v8a architecture
- USB-C microphone (e.g., RODE Wireless Micro)
- JDK 17
- Android SDK with NDK 25+ and CMake 3.22.1

## Building

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## ML Models

Before running, you need to place two model files in `app/src/main/assets/models/`:

1. **Whisper tiny** (`ggml-tiny.bin`, ~75MB):
   ```bash
   cd app/src/main/assets/models/
   curl -L -o ggml-tiny.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin
   ```

2. **MiniLM embedding model** (`all-MiniLM-L6-v2.onnx`):
   Export from Hugging Face with tokenizer baked in, or use a pre-exported ONNX model.

## Installing on device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

- **Foreground Service** with wake lock for 24h continuous recording
- **5-minute AAC chunks** stored locally
- **whisper.cpp** (via JNI) for on-device transcription
- **ONNX Runtime** for sentence embeddings
- **Room/SQLite** for structured storage
- **Cosine similarity** search over embedding vectors
- **Jetpack Compose** UI with Material 3
