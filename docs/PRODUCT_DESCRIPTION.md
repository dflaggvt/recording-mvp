# MemoryStream: Product Description

**Version:** 0.2.0  
**Platform:** Android (native, Kotlin)  
**Application ID:** `com.memorystream`

---

## 1. Product Vision

MemoryStream is a private memory prosthetic that captures the soundscape of your life and makes it navigable.

Think about the way photos work. You take them without thinking, and months later you scroll through them, search them, and relive moments you'd otherwise have forgotten. The AI in your camera roll -- face recognition, object detection, location tags -- is invisible. It just makes browsing better. The real value is seeing the photo and remembering the moment.

MemoryStream does the same thing, but for sound.

You live your life. MemoryStream captures continuously and invisibly -- every conversation, every ambient moment, every place. Behind the scenes, it transcribes, identifies speakers, extracts what was said and promised, checks for contradictions, learns your places. You never think about any of that while it's happening. Later, you ask your memory a question, browse your day, or get a gentle nudge about something that matters. The intelligence is the index. The audio is the memory.

The tagline says it: *"A private memory prosthetic."* A prosthetic isn't a tool you pick up. It's an extension of yourself. Your memory, stored in sound.

---

## 2. What Is Built Today

The intelligence layer is strong. MemoryStream currently has continuous recording, dual-engine transcription (real-time and post-processing), on-device speaker identification, semantic search with AI-synthesized answers, factual claim extraction, cross-conversation consistency checking, proactive insights with 8 types, commitment detection with geofenced reminders, daily narrative summaries, and location-aware place learning.

The experience layer is taking shape. The current interface includes a natural-language search screen, a browsable audio timeline with infinite scroll and place-colored chunks, a mini player that persists across screens, a full-screen audio player with seek and speaker-labeled transcripts, a day-in-review narrative, and a developer tools panel. Ambient-aware presentation and serendipitous discovery ("On this day last year...") are on the roadmap.

---

## 3. Core Capabilities

### 3.1 Continuous Audio Capture

MemoryStream runs as an Android foreground service (`RecordingService`) that captures audio continuously once enabled. There is no record button -- the user enables "Continuous Memory" during onboarding or in Settings, and the app listens from that point forward.

**Technical specifics:**

- **Audio source:** `VOICE_RECOGNITION` via Android's `AudioRecord` API
- **Preferred input:** USB microphone (USB device or USB headset types are auto-detected and preferred). Falls back to built-in microphone if no USB device is present.
- **Format:** PCM 16-bit, 16 kHz, mono
- **Buffer size:** Dynamically computed via `AudioRecord.getMinBufferSize()`, with a floor of 4096 bytes
- **Audio effects:** Three hardware-accelerated audio effects are attached when available:
  - Noise Suppressor
  - Automatic Gain Control
  - Acoustic Echo Canceler
- **Chunking:** Audio is segmented into 5-minute chunks. Each chunk is encoded as AAC-LC at 64 kbps and stored as an M4A file in app-private external storage (`audio_chunks/`), named `chunk_{timestamp}.m4a`.
- **Wake lock:** A partial wake lock is acquired for up to 8 hours to prevent the CPU from sleeping during recording.
- **Battery optimization:** The app requests exemption from Android's battery optimization on launch.
- **Foreground notification:** A persistent notification is displayed while recording, tappable to return to the app.
- **Continuous Memory model:** Recording state is persisted in SharedPreferences (`continuous_memory_enabled`). On app launch, if continuous memory is enabled and permissions are granted, recording resumes automatically without user intervention.

### 3.2 Dual Transcription Pipeline

Every audio chunk passes through a two-stage transcription system for both speed and accuracy.

**Stage 1: Deepgram (real-time via WebSocket)**

Audio is streamed to Deepgram's Nova-3 model in real time as it is captured.

- **Endpoint:** `wss://api.deepgram.com/v1/listen`
- **Model:** `nova-3`
- **Configuration:**
  - Encoding: `linear16`, 16 kHz, 1 channel
  - Speaker diarization enabled (`diarize=true`)
  - Punctuation, smart formatting, and numeral conversion enabled
  - Utterance end detection at 1500 ms silence
  - Profanity filter off, filler words off
  - Speaker name keywords boosted (weight 2x) for recognition
- **Interim results** are streamed to the UI for a live transcript display
- **Final utterances** are committed to the database individually with timestamps and speaker diarization labels
- **Auto-reconnect:** Exponential backoff (2s, 4s, 8s, 16s, 32s) up to 5 attempts
- **Binary streaming:** PCM audio is sent as raw bytes via WebSocket binary frames

**Stage 2: OpenAI Whisper (post-processing upgrade)**

After a chunk is finalized, the M4A file is sent to OpenAI's Whisper API for a higher-accuracy transcript.

- **Endpoint:** `https://api.openai.com/v1/audio/transcriptions`
- **Model:** `whisper-1`
- **File size limit:** 25 MB per file
- **Language:** English
- When the Whisper transcript is available, it replaces the Deepgram transcript, a new summary is generated, and the chunk is re-embedded

**Pre-recorded Diarization (`DeepgramDiarizer`)**

A separate REST-based diarization pipeline processes completed audio files through Deepgram's pre-recorded API.

- **Endpoint:** `https://api.deepgram.com/v1/listen` (REST, not WebSocket)
- **Model:** `nova-3`
- **Features:** Diarization with utterance boundaries
- **Output:** `DiarizedUtterance` objects with text, start/end times (seconds), and speaker labels
- **Fallback:** If utterance-level parsing fails, falls back to word-level diarization with speaker change detection

### 3.3 Speaker Identification

MemoryStream identifies who is speaking using a combination of Deepgram's speaker diarization and an on-device neural speaker encoder that matches diarization labels to enrolled voice profiles.

**Neural Speaker Encoder (`NeuralSpeakerEncoder`)**

- **Model:** WeSpeaker ResNet34, loaded via ONNX Runtime for Android (v1.17.0)
- **Model file:** `wespeaker_resnet34.onnx` (bundled in app assets)
- **Input pipeline:** Raw PCM (16 kHz) is converted to an 80-bin mel spectrogram:
  - FFT size: 512, hop length: 160 samples (10 ms), 80 triangular mel filter banks, log-power scale, Hanning windowing
- **Output:** 256-dimensional L2-normalized speaker embedding vector
- **Inference:** Entirely on-device

**Speaker Enrollment**

Users enroll speakers by recording a 5-30 second voice sample from the People screen.

- Minimum: 5 seconds (16,000 PCM samples at 16 kHz)
- Maximum: 30 seconds
- One speaker can be designated as the "primary user"
- Six speaker colors assigned cyclically: blue, pink, green, orange, purple, teal

**Speaker Matching**

After each chunk is transcribed:

1. Groups utterances by Deepgram diarization label
2. Extracts corresponding audio segments from the decoded chunk
3. Computes a voiceprint for each label's combined audio
4. Compares against enrolled speakers using cosine similarity
5. Assigns a speaker identity if the similarity exceeds the 0.65 threshold
6. Marks unmatched labels as "unknown" and sends a notification

### 3.4 Semantic Search & Answer Synthesis

The Ask screen allows users to query their entire conversation history using natural language.

**Embedding Engine (`OpenAIEmbeddingEngine`)**

- **Model:** OpenAI `text-embedding-3-small`
- **Dimensions:** 1536
- **Text limit:** 8,000 characters per input (truncated if exceeded)
- **Batch support:** Up to 50 texts per API call, with automatic chunking for larger batches
- **Embedding targets:**
  - Individual utterances embedded in batches (flushed every 30 seconds or when 10 accumulate)
  - Chunk-level summaries embedded after Whisper upgrade

**Search Engine (`SemanticSearchEngine`)**

- Dual-layer search: queries matched against both chunk-level and utterance-level embeddings
- Ranking: cosine similarity
- Default top 10 results (search screen overrides to 15), merged across both layers

**Answer Synthesis (`AnswerSynthesizer`)**

- **Model:** GPT-4o-mini
- **Temperature:** 0.3
- **Max tokens:** 200
- Top 5 results sent with original query
- Attributes quotes to identified speakers by name
- Includes place names in context

### 3.5 Claim Extraction & Consistency Checking

MemoryStream extracts factual claims from conversations and checks them against each other over time -- your memory being honest with you.

**Claim Extraction (`ClaimExtractor`)**

Each chunk's transcript is analyzed by GPT-4o to extract specific, verifiable claims.

- **Model:** GPT-4o (not mini -- higher accuracy is important for factual extraction)
- **Temperature:** 0.2 (highly deterministic)
- **Max tokens:** 800
- **Claim types:** factual, preference, plan, opinion, location_claim, time_claim
- **Per claim:** normalized text, raw quote, topic label, type, optional speaker hint
- **Embedding:** Each extracted claim is embedded via OpenAI for later similarity comparison
- **Storage:** Claims stored in the `claims` table with full provenance (chunk, speaker, timestamp, place)
- **Selectivity:** 3-8 claims per typical 5-minute conversation

**Consistency Checking (`ConsistencyChecker`)**

When new claims are extracted, they are compared against historical claims from the same speaker.

- **Model:** GPT-4o
- **Temperature:** 0.1
- **Max tokens:** 300
- **Similarity threshold:** 0.72 cosine similarity between claim embeddings triggers evaluation
- **Classification:** consistent, update (legitimate mind-change), or contradiction
- **Contradiction severity:** minor, notable, significant
- **Location cross-reference:** Location-type claims are automatically checked against the actual GPS location recorded at the time of the conversation
- **Output:** `inconsistency`-type insights with neutral, factual summaries presenting both statements with dates

### 3.6 Proactive Intelligence

The proactive analysis engine (`ProactiveAnalyzer`) automatically reviews recent conversations and surfaces insights.

**How it works:**

1. Triggered after each chunk is processed (throttled to minimum 5-minute intervals)
2. Gathers the last 20 embedded chunks from the past 48 hours
3. Includes recent factual claims grouped by speaker for inconsistency detection
4. Sends formatted context to GPT-4o-mini
5. Returns a JSON array of insights, deduplicated and stored

**Insight types (8):**

| Type | Description | Expiry |
|------|-------------|--------|
| `commitment` | A promise, plan, or decision someone made | 7 days |
| `followup` | A future event or concern worth checking back on | 14 days |
| `friction` | A recurring topic suggesting unresolved tension | 30 days |
| `preference` | A desire or wish expressed in passing | Never |
| `positive` | A compliment, expression of love, gratitude | Never |
| `unfinished` | A conversation topic that was interrupted | 30 days |
| `timesensitive` | A specific date, deadline, or event approaching | 7 days |
| `inconsistency` | A contradiction between statements across conversations | 14 days |

**GPT configuration:**

- **Model:** GPT-4o-mini
- **Temperature:** 0.4
- **Max tokens:** 800
- **Tone:** Direct and assertive. The system prompt explicitly forbids hedging ("It seems like", "It sounds like"). Insights state what was actually said, using exact quotes and speaker names.
- **Deduplication:** Stable `id_hint` string or SHA-256 hash of type + body
- **Selectivity:** 2-3 insights max per analysis

### 3.7 Commitment Detection & Geofencing

**Commitment Detection (`CommitmentDetector`)**

Each chunk's transcript is analyzed by GPT-4o-mini to extract commitments:

- **Types:** promise, decision, plan, preference, reminder
- **Temperature:** 0.1
- **Max tokens:** 1,000
- Stored as a JSON string on the chunk entity

**Geofenced Reminders (`CommitmentGeofencer`)**

When a `commitment`-type insight has a `placeHint` (e.g., `"grocery_store"`, `"pharmacy"`):

1. Searches the known places database for a match
2. Registers a geofence (200-meter radius, 7-day expiration, enter trigger)
3. When the user enters the area, a notification fires with the commitment details

### 3.8 Daily Summaries & Day Review

**Daily Summary Generator (`DailySummaryGenerator`)**

Generates AI-powered narrative summaries of each day's conversations.

- **Basic summary:** GPT-4o-mini, temperature 0.4, max 150 tokens, 4000-char context. Produces 1-2 sentence recap.
- **Rich narrative:** GPT-4o-mini, temperature 0.5, max 400 tokens, 6000-char context. Produces 3-5 sentence second-person narrative with speaker names, places, decisions, and emotional moments.
- **Caching:** Summaries stored in the `daily_summaries` table and returned from cache on subsequent requests
- **Weekly retrieval:** Supports generating summaries for an entire week

### 3.9 Location Intelligence

**GPS Tracking (`LocationProvider`)**

- Google Play Services `FusedLocationProviderClient` with balanced power/accuracy priority
- Each chunk tagged with latitude, longitude, and resolved place name
- Reverse geocoding via Android's `Geocoder` API

**Place Learning (`PlaceResolver`)**

- Database of known places with coordinates, labels, radii, and visit counts
- New locations automatically created when the user visits an unrecognized location
- Auto-labels "Home" after 3 visits to the same location (within 150-meter radius)
- Haversine formula for great-circle distance matching

---

## 4. Architecture

### 4.1 Pattern

MVVM with dependency injection via Dagger Hilt:

- **UI layer:** Jetpack Compose screens with ViewModels exposing `StateFlow`
- **Service layer:** Foreground service orchestrating recording, transcription, and the full processing pipeline
- **Intelligence layer:** Claim extraction, consistency checking, proactive analysis, commitment detection, answer synthesis, daily summaries
- **Data layer:** Room database with repository pattern, 7 entities, 7 DAOs
- **Audio layer:** Capture, encoding, decoding, playback, and speaker identification

### 4.2 Processing Pipeline

```
USB Mic / Built-in Mic
    → AudioCaptureManager (PCM 16kHz)
        ├→ DeepgramClient (WebSocket) → Live transcript + Utterances
        │       └→ Utterances batched → OpenAI Embeddings → Room DB
        └→ AudioChunkScheduler → ChunkRecorder (AAC M4A)
                └→ Processing Channel
                        ├→ TranscriptionWorker (Deepgram transcript → chunk)
                        ├→ WhisperTranscriber (accuracy upgrade)
                        ├→ DeepgramDiarizer (pre-recorded diarization)
                        ├→ OpenAI Embedding (chunk-level)
                        ├→ CommitmentDetector (extract promises)
                        ├→ ClaimExtractor (extract factual claims)
                        ├→ ConsistencyChecker (compare against history)
                        ├→ SpeakerIdentifier (match voices)
                        └→ ProactiveAnalyzer (generate insights)
                                ├→ InsightNotifier (notifications)
                                └→ CommitmentGeofencer (location reminders)
```

### 4.3 Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Kotlin | 1.9.22 |
| UI Framework | Jetpack Compose | BOM 2024.06.00 |
| Design System | Material 3 | (via Compose BOM) |
| Typography | Google Fonts (Inter) | via ui-text-google-fonts |
| Navigation | Navigation Compose | 2.7.6 |
| Dependency Injection | Dagger Hilt | 2.50 |
| Symbol Processing | KSP | 1.9.22-1.0.17 |
| Database | Room | 2.6.1 |
| Async/Concurrency | Kotlin Coroutines | 1.7.3 |
| HTTP Client | OkHttp | 4.12.0 |
| JSON | Gson | 2.10.1 |
| On-Device ML | ONNX Runtime Android | 1.17.0 |
| Location Services | Google Play Services Location | 21.1.0 |
| Media | AndroidX Media | 1.7.0 |
| Build System | Android Gradle Plugin | 8.2.2 |
| Native Build | CMake | 3.22.1 |
| Compile/Target SDK | Android 14 | API 34 |
| Minimum SDK | Android 8.0 | API 26 |

### 4.4 External APIs

| Service | Use | Model/Endpoint |
|---------|-----|----------------|
| Deepgram | Real-time transcription | Nova-3 via WebSocket |
| Deepgram | Pre-recorded diarization | Nova-3 via REST |
| OpenAI | Transcription upgrade | Whisper-1 via REST |
| OpenAI | Text embeddings | text-embedding-3-small via REST |
| OpenAI | Answer synthesis | GPT-4o-mini via REST |
| OpenAI | Commitment detection | GPT-4o-mini via REST |
| OpenAI | Proactive insights | GPT-4o-mini via REST |
| OpenAI | Daily summaries | GPT-4o-mini via REST |
| OpenAI | Claim extraction | GPT-4o via REST |
| OpenAI | Consistency checking | GPT-4o via REST |

### 4.5 On-Device Models

| Model | File | Purpose | Output |
|-------|------|---------|--------|
| WeSpeaker ResNet34 | `wespeaker_resnet34.onnx` | Speaker embedding | 256-dim vector |

---

## 5. Data Model

Room database (`memorystream.db`) at schema version 10 with 7 entities.

### 5.1 MemoryChunkEntity

| Field | Type | Description |
|-------|------|-------------|
| `id` | String (PK) | UUID |
| `startTimestamp` | Long | Recording start time (epoch ms) |
| `endTimestamp` | Long | Recording end time (epoch ms) |
| `transcript` | String? | Full transcript (Deepgram, then upgraded by Whisper) |
| `summary` | String? | First 3 sentences, max 500 chars |
| `commitments` | String? | JSON array of detected commitments |
| `embeddingVector` | FloatArray? | 1536-dim OpenAI embedding |
| `audioFilePath` | String | Path to M4A file |
| `status` | ChunkStatus | RECORDING, PENDING_TRANSCRIPTION, TRANSCRIBING, TRANSCRIBED, EMBEDDING, EMBEDDED, ERROR |
| `latitude` | Double? | GPS latitude at recording time |
| `longitude` | Double? | GPS longitude at recording time |
| `placeName` | String? | Resolved place name or address |

### 5.2 UtteranceEntity

| Field | Type | Description |
|-------|------|-------------|
| `id` | String (PK) | UUID |
| `chunkId` | String? | Parent chunk ID |
| `timestamp` | Long | When the utterance was spoken (epoch ms) |
| `text` | String | Transcribed text |
| `embeddingVector` | FloatArray? | 1536-dim OpenAI embedding |
| `isEmbedded` | Boolean | Whether embedding has been computed |
| `speakerId` | String? | Matched speaker ID |
| `diarizationLabel` | Int? | Deepgram speaker label |

### 5.3 SpeakerEntity

| Field | Type | Description |
|-------|------|-------------|
| `id` | String (PK) | UUID |
| `name` | String | Display name |
| `voiceprint` | FloatArray? | 256-dim ONNX speaker embedding |
| `isPrimary` | Boolean | Whether this is the primary user |
| `enrolledAt` | Long | Enrollment timestamp (epoch ms) |
| `color` | Int | ARGB color for UI display |

### 5.4 InsightEntity

| Field | Type | Description |
|-------|------|-------------|
| `id` | String (PK) | Stable ID from GPT hint or SHA-256 hash |
| `type` | String | One of 8 types |
| `title` | String | Short headline (5-8 words) |
| `body` | String | Natural language description (1-2 sentences) |
| `sourceTimestamp` | Long | Approximate timestamp of source conversation |
| `createdAt` | Long | When the insight was generated |
| `expiresAt` | Long? | Auto-expiry timestamp (null = never) |
| `dismissedAt` | Long? | When the user dismissed the insight |
| `notifiedAt` | Long? | When the notification was sent |
| `placeHint` | String? | Location category for geofencing |

### 5.5 KnownPlaceEntity

| Field | Type | Description |
|-------|------|-------------|
| `id` | String (PK) | UUID |
| `label` | String | Human-readable name |
| `latitude` | Double | GPS latitude |
| `longitude` | Double | GPS longitude |
| `radiusMeters` | Float | Match radius (default 150m) |
| `visitCount` | Int | Number of visits detected |
| `lastVisitedAt` | Long | Most recent visit timestamp |

### 5.6 DailySummaryEntity

| Field | Type | Description |
|-------|------|-------------|
| `dayTimestamp` | Long (PK) | Start of the day (epoch ms) |
| `summary` | String | AI-generated narrative summary |
| `totalDurationMs` | Long | Total recording duration for the day |
| `chunkCount` | Int | Number of chunks recorded |
| `places` | String? | Comma-separated list of distinct places |
| `generatedAt` | Long | When the summary was generated |

### 5.7 ClaimEntity

| Field | Type | Description |
|-------|------|-------------|
| `id` | String (PK) | UUID |
| `chunkId` | String | Source chunk ID |
| `speakerId` | String? | Matched speaker ID |
| `speakerName` | String? | Speaker name (from extraction hint) |
| `timestamp` | Long | When the claim was made (epoch ms) |
| `topic` | String | Short topic label (2-4 words) |
| `claimText` | String | Normalized, context-free assertion |
| `rawQuote` | String | Exact words spoken |
| `type` | String | factual, preference, plan, opinion, location_claim, time_claim |
| `placeName` | String? | Location where the claim was made |
| `embeddingVector` | FloatArray? | 1536-dim embedding for similarity comparison |
| `createdAt` | Long | When the claim was extracted |

### 5.8 Schema Evolution

1. **v1:** Initial schema with `memory_chunks`
2. **v2:** Added `commitments` column
3. **v3:** Created `utterances` table
4. **v4:** Added speaker identification; created `speakers` table
5. **v5:** Created `insights` table
6. **v6:** Added location fields to chunks and insights; created `known_places` table
7. **v7:** Created `daily_summaries` table
8. **v8:** Created `claims` table
9. **v9:** Added `speechRatio` column to chunks (for VAD)
10. **v10:** Recreated `memory_chunks` table (removed `speechRatio`, schema cleanup)

---

## 6. User Interface

Jetpack Compose with Material 3. Inter font via Google Fonts. Custom dark/light color schemes (dark primary: `#7B9FD4`, light primary: `#3A6EA5`).

### 6.1 Navigation

Two bottom navigation tabs, with a mini audio player above them when audio is playing:

| Tab | Icon | Route |
|-----|------|-------|
| Ask | Search | `ask` |
| Timeline | GraphicEq | `timeline` |

Additional screens accessed via navigation (hidden from bottom bar):

| Screen | Route | Access |
|--------|-------|--------|
| Onboarding | `onboarding` | First launch only |
| Settings | `settings` | From Ask screen gear icon |
| People | `speakers` | From navigation |
| Day Review | `day_review/{dayTimestamp}` | From Timeline or Ask screen |
| Full Player | `full_player` | From mini player tap |
| Debug | `debug` | From Settings "Developer Tools" |

Start destination: `onboarding` on first launch, `ask` for returning users. Onboarding state persisted in SharedPreferences (`onboarding_complete`).

A global `AudioPlaybackManager` is shared across screens via a Hilt-scoped `NavPlaybackHolder`, enabling persistent playback as the user navigates.

### 6.2 Onboarding Screen

The first screen new users see. Animated, minimal, and intentional.

- Breathing mic icon (56dp, pulsing scale animation)
- "MemoryStream" title
- "Listens so you don't have to." tagline
- "Conversations are privately transcribed and indexed on your device. Commitments are tracked automatically."
- USB microphone detection indicator
- "Enable Continuous Memory" toggle (starts recording and navigates to Ask screen)
- "You're all set." confirmation with checkmark

### 6.3 Ask Screen (Main)

The primary surface. A conversational interface to your memory.

- **Greeting:** Time-of-day greeting with primary speaker name ("Good morning, Daryl")
- **Listening indicator:** Animated waveform bars when recording is active, with "Listening" label
- **Settings:** Gear icon (top right) navigates to Settings
- **Search bar:** Rounded text field, placeholder "Ask your memory anything..."
- **Day Review card:** "Your day, remembered." with subtitle "Tap to see your day summarized with conversations, places, and commitments."
- **Idle state:** Recent insight cards (dismissible, color-coded by type with accent lines) or "I'm listening. Ask me anything when you need to remember."
- **Search results:** Shimmer loading animation, answer card with "Thinking..." dots, collapsible sources ("Based on N recording(s)") showing speaker names, colors, place names, and relative timestamps
- **Empty search state:** "Nothing found in your recordings."

### 6.4 Timeline Screen

The audio equivalent of a camera roll. A scrollable, infinite-scroll timeline of every conversation, organized by day.

- **Day sections:** Each day shows a header with the date and an arrow to navigate to that day's full review
- **Chunk cards:** Each recording segment shows:
  - Place name with color-coded dot (blue for Home, green for Work, amber for other, gray for unknown)
  - Time range
  - Transcript preview (2 lines, ellipsis)
  - Play button to start audio playback
- **Infinite scroll:** Loads older days as the user scrolls to the bottom
- **Navigation:** Tapping a day header navigates to the Day Review for that day; tapping play starts the audio in the global player

### 6.5 Day Review Screen

A narrative view of a single day, accessed from the Timeline.

- **Header:** Back button, "Your day, remembered." title
- **Empty state:** "No recordings yet today. I'm listening."
- **Stats strip:** Duration recorded, place count, conversation count
- **Narrative card:** Rich AI-generated summary of the day (rounded card, 20dp corners)
- **Timeline strip:** Visual representation of recording segments (44dp height, color-coded by place)
- **Sections:**
  - "Timeline" -- chronological conversation segments with place labels, tap to expand transcript
  - "Follow through" -- commitment-type insights with complete/dismiss actions
  - "Inconsistencies" -- contradiction insights with red accent
- **Transcript sheet:** Bottom sheet (300dp) with full transcript text, play/pause controls
- **Audio playback:** Play/pause per chunk with progress indicator

### 6.6 Audio Player

A persistent audio playback experience that follows you across screens.

**Mini Player**

Appears above the bottom navigation bar when audio is playing.

- Place name, time, progress bar
- Play/pause and close buttons
- Tappable to open the full player

**Full Player**

A dedicated screen for immersive audio playback.

- Back button, date/time/place header
- Large play/pause button with skip previous/next
- Seek slider with elapsed/remaining time
- Speaker-labeled transcript below, auto-scrolling to the current utterance
- Filter chips for speaker filtering

### 6.7 Commitments Screen

A dedicated checklist for things you said you'd do (accessible via navigation, not a bottom tab).

- **Commitment cards:** Title, body, relative time, place hint, complete button with animation
- **Overdue highlighting:** Amber accent line for 3+ days old, stronger for 7+ days
- **Empty state:** "Nothing to follow up on.", "You're on track."

### 6.8 People Screen (Speakers)

- **Speaker list:** Avatar circle (first initial, color-coded), name (star if primary), enrollment date, "Voice enrolled" badge, delete button
- **Enrollment dialog:** Name input, "This is me (primary user)" checkbox, recording instructions, 64dp record button, timer, "Save voice profile" (enabled after 5+ seconds)
- **Delete confirmation:** Warning dialog with cancel/remove
- **Empty state:** "No enrolled speakers", "Enroll yourself first, then add family and friends"

### 6.9 Settings Screen

- **"Continuous Memory" card:** Toggle switch with "Listening" / "Off" status, description "Always-on recording, transcription, and indexing"
- **"About" card:** "MemoryStream v0.2.0", "A private memory prosthetic"
- **"Developer Tools" card:** "Pipeline inspector, claim viewer, replay tools" -- navigates to Debug screen

### 6.10 Debug Screen (Developer Tools)

A pipeline inspector for development.

- **Summary stats:** Chunk count, claim count, insight count, average speech percentage
- **Recent chunks list:** Claim count, transcript preview
- **Chunk detail view:** Metadata (time, place, status, transcript length, chunk ID), full transcript, claims list with type badges, inconsistencies
- **Pipeline replay:** Individual buttons for "Extract Claims", "Check Consistency", "Proactive Analysis", and "Run Full Pipeline"

---

## 7. Permissions & Privacy

### 7.1 Android Permissions (11)

| Permission | Purpose |
|-----------|---------|
| `RECORD_AUDIO` | Microphone access |
| `FOREGROUND_SERVICE` | Background service execution |
| `FOREGROUND_SERVICE_MICROPHONE` | Foreground service type: microphone |
| `FOREGROUND_SERVICE_LOCATION` | Foreground service type: location |
| `WAKE_LOCK` | Prevent CPU sleep during recording |
| `POST_NOTIFICATIONS` | Insight and recording notifications |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent OS from killing the service |
| `INTERNET` | API calls |
| `ACCESS_FINE_LOCATION` | Precise GPS location |
| `ACCESS_COARSE_LOCATION` | Approximate location |
| `ACCESS_BACKGROUND_LOCATION` | Location when app is in background |

### 7.2 Hardware Features

| Feature | Required |
|---------|----------|
| `android.hardware.microphone` | Yes |
| `android.hardware.usb.host` | No (optional) |

### 7.3 Privacy Model

- All audio and data stored locally on the device. No cloud storage, no user accounts, no data sync.
- API calls are transient: audio and text are sent for processing but not retained by external services.
- API keys configured at build time via `local.properties` and injected through `BuildConfig`.
- No analytics or telemetry.
- Speaker voiceprints never leave the device.

---

## 8. System Requirements

| Requirement | Specification |
|-------------|--------------|
| Android version | 8.0 (API 26) or higher |
| Architecture | arm64-v8a |
| Microphone | Built-in or USB-C (USB recommended) |
| Internet | Required for transcription and AI features |
| API keys | Deepgram + OpenAI (configured in `local.properties`) |
| JDK | 17 |
| Storage | ~100 MB for app + models; ~480 KB per minute for audio (~29 MB per hour) |

---

## 9. Notification Channels

| Channel | ID | Purpose |
|---------|-----|---------|
| Recording | `recording_channel` | Persistent recording notification, unknown speaker alerts |
| Memory Insights | `insights_channel` | Individual insight notifications, grouped summaries |

---

## 10. Project Structure

```
recording-mvp/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/
│   │   │   └── wespeaker_resnet34.onnx
│   │   ├── cpp/
│   │   │   └── CMakeLists.txt
│   │   └── java/com/memorystream/
│   │       ├── MemoryStreamApp.kt
│   │       ├── api/
│   │       │   └── ApiConfig.kt
│   │       ├── audio/
│   │       │   ├── AudioCaptureManager.kt
│   │       │   ├── AudioChunkScheduler.kt
│   │       │   ├── AudioDecoder.kt
│   │       │   ├── AudioPlaybackManager.kt
│   │       │   ├── ChunkRecorder.kt
│   │       │   ├── NeuralSpeakerEncoder.kt
│   │       │   ├── SpeakerIdentifier.kt
│   │       │   └── VoiceFeatureExtractor.kt
│   │       ├── data/
│   │       │   ├── db/
│   │       │   │   ├── AppDatabase.kt
│   │       │   │   ├── ClaimEntity.kt        (+ ClaimDao)
│   │       │   │   ├── Converters.kt
│   │       │   │   ├── DailySummaryEntity.kt  (+ DailySummaryDao)
│   │       │   │   ├── InsightDao.kt
│   │       │   │   ├── InsightEntity.kt
│   │       │   │   ├── KnownPlaceEntity.kt    (+ KnownPlaceDao)
│   │       │   │   ├── MemoryChunkDao.kt
│   │       │   │   ├── MemoryChunkEntity.kt
│   │       │   │   ├── SpeakerDao.kt
│   │       │   │   ├── SpeakerEntity.kt
│   │       │   │   ├── UtteranceDao.kt
│   │       │   │   └── UtteranceEntity.kt
│   │       │   ├── model/
│   │       │   │   └── MemoryChunk.kt         (ChunkStatus + ChunkResult)
│   │       │   └── repository/
│   │       │       └── MemoryRepository.kt
│   │       ├── di/
│   │       │   └── AppModule.kt
│   │       ├── embedding/
│   │       │   ├── OpenAIEmbeddingEngine.kt
│   │       │   └── SemanticSearchEngine.kt
│   │       ├── intelligence/
│   │       │   ├── AnswerSynthesizer.kt
│   │       │   ├── ClaimExtractor.kt
│   │       │   ├── CommitmentDetector.kt
│   │       │   ├── ConsistencyChecker.kt
│   │       │   ├── DailySummaryGenerator.kt
│   │       │   └── ProactiveAnalyzer.kt
│   │       ├── service/
│   │       │   ├── CommitmentGeofencer.kt
│   │       │   ├── GeofenceReceiver.kt
│   │       │   ├── InsightNotifier.kt
│   │       │   ├── LocationProvider.kt
│   │       │   ├── PlaceResolver.kt
│   │       │   └── RecordingService.kt
│   │       ├── transcription/
│   │       │   ├── DeepgramClient.kt
│   │       │   ├── DeepgramDiarizer.kt
│   │       │   ├── TranscriptionWorker.kt
│   │       │   └── WhisperTranscriber.kt
│   │       └── ui/
│   │           ├── MainActivity.kt
│   │           ├── commitments/
│   │           ├── debug/
│   │           ├── navigation/       (NavGraph.kt, NavPlaybackHolder.kt)
│   │           ├── onboarding/
│   │           ├── player/           (MiniPlayer.kt, FullPlayerScreen.kt, FullPlayerViewModel.kt)
│   │           ├── review/
│   │           ├── search/
│   │           ├── settings/
│   │           ├── speakers/
│   │           ├── theme/
│   │           └── timeline/         (TimelineScreen.kt, TimelineViewModel.kt)
│   └── schemas/                       (Room schema exports, versions 4-10)
├── build.gradle.kts
├── local.properties                   (API keys, gitignored)
└── docs/
```

---

## 11. Configuration

### 11.1 API Keys

Stored in `local.properties` at the project root (gitignored), injected at build time:

```properties
deepgram.api.key=YOUR_DEEPGRAM_KEY
openai.api.key=YOUR_OPENAI_KEY
```

Available in code as `BuildConfig.DEEPGRAM_API_KEY` and `BuildConfig.OPENAI_API_KEY`.

### 11.2 User Settings

| Setting | Storage | Key |
|---------|---------|-----|
| Continuous Memory | SharedPreferences | `continuous_memory_enabled` |
| Onboarding Complete | SharedPreferences | `onboarding_complete` |

SharedPreferences name: `memorystream_prefs`.

---

## 12. Roadmap

The intelligence layer is mature. The experience layer is actively being built. Here is what exists and what is coming.

**Browsable Audio Timeline** -- IN PROGRESS
The Timeline tab is live: an infinite-scroll, day-by-day view of every conversation, color-coded by place, with play buttons and navigation to Day Review. Future enhancements include waveform previews, week/month views, and richer visual annotation.

**First-Class Audio Playback** -- IN PROGRESS
A persistent mini player and full-screen player are live: seek slider, skip previous/next, speaker-labeled transcript that scrolls with playback. Future enhancements include speed control, skip silence, waveform visualization, and clip sharing.

**Ambient Awareness** -- PLANNED
Not every chunk is the same. A quiet morning at home, a loud restaurant dinner, a tense car ride, a playground with kids -- these are different auditory experiences. The app should present them differently. Average volume, background noise character, number of voices -- these features make browsing richer.

**Serendipitous Discovery** -- PLANNED
"One year ago today, you were at the beach with Sarah." Surfacing forgotten moments you didn't know to search for. The kind of thing that makes the product emotionally resonant.

---

## 13. Version History

- **v0.1.0:** Initial MVP -- continuous recording, Deepgram transcription, basic semantic search, speaker identification, proactive insights (7 types), commitment detection, geofencing, location tracking, Whisper upgrade
- **v0.2.0 (current):** Claim extraction and consistency checking (GPT-4o), Deepgram upgraded to Nova-3, pre-recorded diarization, daily summaries with rich narratives, browsable Timeline screen with infinite scroll, persistent mini player and full-screen audio player, Day Review screen, Commitments screen, onboarding flow, continuous memory model (replacing manual record button), Debug screen for pipeline inspection, assertive insight tone, 8th insight type (inconsistency), database evolved to v10
