# Phase 6: Voice Pipeline — STT, TTS, Wake Word, Voice Mode

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: Phase 2 (Provider Router — for voice model capability routing)
**Blocks**: Phase 8 (Documentation)

---

## 1. Objective

Full voice interaction: wake word detection → speech-to-text → agent processing → text-to-speech → audio output. Supports streaming TTS for real-time LLM output reading.

---

## 2. Research Checklist

- [ ] **WhisperKit Android** (Argmax) — optimized on-device STT port
- [ ] **whisper.cpp** — GGML models on Android via NDK/JNI
- [ ] **Google ML Kit GenAI Speech Recognition** — on-device, no download
- [ ] **Google Cloud Speech-to-Text** — real-time streaming, 125+ languages
- [ ] **Picovoice Orca** — streaming TTS, sub-50ms latency, on-device
- [ ] **Kokoro** — Apache 2.0, 82M params, best size/quality on-device
- [ ] **Piper TTS** — 100+ voices, ONNX, open-source
- [ ] **Speechmatics** — cloud streaming TTS, 27x cheaper than ElevenLabs
- [ ] **ElevenLabs** — ultra-realistic cloud TTS, voice cloning
- [ ] **Picovoice Porcupine** — wake word detection (custom keyword)
- [ ] **OpenWakeWord** — open-source wake word
- [ ] Android AudioRecord / AudioTrack APIs
- [ ] Audio focus management — requestAudioFocus(), ducking
- [ ] Bluetooth audio routing — SCO, A2DP
- [ ] VAD (Voice Activity Detection) — Silero VAD, WebRTC VAD

---

## 3. Architecture

### 3.1 Module Structure

```
android/app/src/main/java/com/guappa/app/
└── voice/
    ├── VoicePipeline.kt              — orchestrates full STT → Agent → TTS flow
    ├── VoiceModeManager.kt           — manage voice mode state (idle/listening/processing/speaking)
    ├── StreamingVoiceSession.kt      — continuous voice conversation session
    │
    ├── stt/
    │   ├── STTInterface.kt           — unified STT interface (transcribe, stream)
    │   ├── WhisperKitSTT.kt          — WhisperKit Android (Argmax, best on-device)
    │   ├── WhisperCppSTT.kt          — whisper.cpp JNI (fallback, more models)
    │   ├── GoogleMLKitSTT.kt         — ML Kit GenAI Speech Recognition (no download)
    │   ├── GoogleCloudSTT.kt         — Google Cloud STT (best real-time streaming)
    │   ├── DeepgramSTT.kt            — Deepgram Nova-2 cloud
    │   ├── OpenAIWhisperSTT.kt       — OpenAI Whisper API (cloud, best noisy audio)
    │   ├── VoskSTT.kt                — Vosk offline (lightweight fallback)
    │   └── STTSelector.kt            — auto-select best STT for device + config
    │
    ├── tts/
    │   ├── TTSInterface.kt           — unified TTS interface (speak, stream, stop)
    │   ├── PicovoiceOrcaTTS.kt       — Picovoice Orca (streaming, <50ms, on-device) ★ PRIMARY ★
    │   ├── KokoroTTS.kt              — Kokoro neural TTS (Apache 2.0, 82M params)
    │   ├── PiperTTS.kt               — Piper ONNX (100+ voices, on-device)
    │   ├── AndroidNativeTTS.kt       — Android TextToSpeech (fallback, no streaming)
    │   ├── SpeechmaticsTTS.kt        — Speechmatics cloud (~150ms, streaming)
    │   ├── ElevenLabsTTS.kt          — ElevenLabs cloud (ultra-realistic, cloning)
    │   ├── OpenAITTS.kt              — OpenAI TTS cloud (6 voices, streaming)
    │   ├── GoogleCloudTTS.kt         — Google Cloud TTS (380+ voices, streaming)
    │   ├── DeepgramTTS.kt            — Deepgram Aura cloud (streaming)
    │   └── TTSSelector.kt            — auto-select best TTS for device + config
    │
    ├── wakeword/
    │   ├── WakeWordDetector.kt       — wake word detection loop
    │   ├── PorcupineDetector.kt      — Picovoice Porcupine ("Привет Guappa")
    │   ├── OpenWakeWordDetector.kt   — open-source wake word (custom keywords)
    │   └── WakeWordConfig.kt         — keyword, sensitivity, always-on settings
    │
    ├── vad/
    │   ├── VADInterface.kt           — Voice Activity Detection interface
    │   ├── SileroVAD.kt              — Silero VAD (ONNX, most accurate)
    │   └── WebRTCVAD.kt              — WebRTC VAD (lighter, lower accuracy)
    │
    └── audio/
        ├── AudioRouter.kt            — audio focus, speaker/earpiece/BT routing
        ├── AudioRecorder.kt          — microphone capture (AudioRecord, 16kHz mono)
        ├── AudioPlayer.kt            — playback (AudioTrack for streaming, MediaPlayer for files)
        ├── AudioFocusManager.kt      — request/abandon audio focus, handle interruptions
        ├── BluetoothAudioRouter.kt   — SCO for call-like audio, A2DP for media
        └── AudioBufferPool.kt        — reusable audio buffers for efficiency
```

### 3.2 Voice Pipeline Flow

```
[Wake Word]──▶ WakeWordDetector.kt
                      │ "Привет, Guappa!"
                      ▼
[Listening] ──▶ AudioRecorder (16kHz, mono, PCM)
                      │
                      ├── VAD (detect speech start/end)
                      │
                      ▼
[Processing] ─▶ STT Engine (WhisperKit / Google ML Kit / Cloud)
                      │ "Какая погода завтра?"
                      ▼
              ──▶ Agent Orchestrator (MessageBus → LLM → Tools)
                      │ "Завтра будет солнечно, +22°C"
                      ▼
[Speaking] ──▶ TTS Engine (Picovoice Orca streaming / Cloud)
                      │ (audio chunks streamed as LLM generates text)
                      ▼
              ──▶ AudioPlayer (AudioTrack, streaming playback)
                      │
                      ▼
[Idle] ──────▶ Return to wake word detection
```

### 3.3 Streaming TTS from LLM Output

**Critical feature:** Read LLM output aloud in real-time as it's generated, not after full response.

```kotlin
class StreamingVoiceSession(
    private val stt: STTInterface,
    private val tts: TTSInterface,
    private val providerRouter: ProviderRouter,
    private val audioRouter: AudioRouter,
) {
    /**
     * Process voice input → stream LLM response → stream TTS output.
     *
     * Key insight: Buffer LLM text by sentence, start TTS on each sentence.
     * This gives ~500ms latency from LLM start to audio start.
     */
    suspend fun processVoiceInput(audioData: ByteArray) {
        // 1. STT
        val transcript = stt.transcribe(audioData)

        // 2. Request audio focus
        audioRouter.requestFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)

        // 3. Stream LLM response
        val sentenceBuffer = StringBuilder()
        val ttsQueue = Channel<String>(capacity = 10)

        // Producer: collect LLM chunks, split into sentences
        val producerJob = scope.launch {
            providerRouter.chatStream(buildRequest(transcript)).collect { chunk ->
                sentenceBuffer.append(chunk.text)

                // Check if we have a complete sentence
                val text = sentenceBuffer.toString()
                val sentenceEnd = text.indexOfAny(charArrayOf('.', '!', '?', '\n', '。'))
                if (sentenceEnd >= 0) {
                    val sentence = text.substring(0, sentenceEnd + 1).trim()
                    if (sentence.isNotBlank()) {
                        ttsQueue.send(sentence)
                    }
                    sentenceBuffer.clear()
                    sentenceBuffer.append(text.substring(sentenceEnd + 1))
                }
            }
            // Flush remaining text
            val remaining = sentenceBuffer.toString().trim()
            if (remaining.isNotBlank()) {
                ttsQueue.send(remaining)
            }
            ttsQueue.close()
        }

        // Consumer: TTS each sentence, play audio
        for (sentence in ttsQueue) {
            if (tts.supportsStreaming()) {
                // Stream TTS → AudioTrack
                tts.speakStreaming(sentence).collect { audioChunk ->
                    audioPlayer.write(audioChunk)
                }
            } else {
                // Non-streaming: wait for full audio, then play
                val audio = tts.speak(sentence)
                audioPlayer.play(audio)
            }
        }

        // 4. Release audio focus
        audioRouter.abandonFocus()
    }
}
```

---

## 4. STT Engine Selection

Settings allow per-capability model selection (Phase 2). For STT:

```kotlin
class STTSelector(
    private val config: StateFlow<ProviderRouterConfig>,
    private val hardwareProbe: HardwareProbe,
) {
    fun selectEngine(): STTInterface {
        val sttConfig = config.value.audioSTT

        // If user explicitly configured
        if (sttConfig.providerId.isNotBlank()) {
            return getEngine(sttConfig.providerId, sttConfig.modelId)
        }

        // Auto-select based on device and connectivity
        val caps = hardwareProbe.probe()
        val hasNetwork = NetworkUtils.isConnected()

        return when {
            // Online + configured cloud → best quality
            hasNetwork && config.value.providerKeys["google"]?.apiKey?.isNotBlank() == true ->
                GoogleCloudSTT()

            // Online + no specific config → OpenAI Whisper API
            hasNetwork && config.value.providerKeys["openai"]?.apiKey?.isNotBlank() == true ->
                OpenAIWhisperSTT()

            // Offline + powerful device → WhisperKit Android
            caps.totalRamMb >= 6000 -> WhisperKitSTT(model = "whisper-small")

            // Offline + mid-range → Google ML Kit (lightweight)
            caps.totalRamMb >= 4000 -> GoogleMLKitSTT()

            // Offline + low-end → Vosk (smallest footprint)
            else -> VoskSTT()
        }
    }
}
```

---

## 5. TTS Engine Selection

```kotlin
class TTSSelector(
    private val config: StateFlow<ProviderRouterConfig>,
    private val hardwareProbe: HardwareProbe,
) {
    fun selectEngine(): TTSInterface {
        val ttsConfig = config.value.audioTTS

        if (ttsConfig.providerId.isNotBlank()) {
            return getEngine(ttsConfig.providerId, ttsConfig.modelId)
        }

        val hasNetwork = NetworkUtils.isConnected()

        return when {
            // For streaming LLM output: Picovoice Orca (on-device, <50ms)
            PicovoiceOrcaTTS.isAvailable() -> PicovoiceOrcaTTS()

            // Online + cheap cloud streaming
            hasNetwork && config.value.providerKeys.containsActiveKey("speechmatics") ->
                SpeechmaticsTTS()

            // Offline: Kokoro (best quality/size)
            KokoroTTS.isAvailable() -> KokoroTTS()

            // Offline: Piper (100+ voices)
            PiperTTS.isAvailable() -> PiperTTS()

            // Ultimate fallback: Android native TTS (no streaming!)
            else -> AndroidNativeTTS()
        }
    }
}
```

---

## 6. Wake Word Detection

```kotlin
class WakeWordDetector(
    private val config: WakeWordConfig,
    private val audioRecorder: AudioRecorder,
) {
    /**
     * Continuously listen for wake word in background.
     * Uses Picovoice Porcupine for custom keyword detection.
     *
     * Power consumption: ~2-5% battery per hour (acceptable for always-on)
     */
    fun start(): Flow<WakeWordEvent> = flow {
        val porcupine = Porcupine.Builder()
            .setKeywordPaths(listOf(config.keywordModelPath))
            .setSensitivities(listOf(config.sensitivity))
            .build(context)

        val frameLength = porcupine.frameLength  // typically 512 samples
        val sampleRate = porcupine.sampleRate    // 16000 Hz

        audioRecorder.start(sampleRate, frameLength)

        try {
            while (currentCoroutineContext().isActive) {
                val audioFrame = audioRecorder.readFrame(frameLength)
                val keywordIndex = porcupine.process(audioFrame)
                if (keywordIndex >= 0) {
                    emit(WakeWordEvent.Detected(keyword = config.keyword))
                }
            }
        } finally {
            porcupine.delete()
            audioRecorder.stop()
        }
    }
}
```

---

## 7. Voice Activity Detection (VAD)

```kotlin
class SileroVAD : VADInterface {
    private var ortSession: OrtSession? = null

    /**
     * Detect speech start and end in audio stream.
     * Used to know when user finished speaking (before sending to STT).
     *
     * Silero VAD: ONNX model, ~2MB, runs at 100x realtime on mobile.
     */
    override fun detectSpeech(audioFrame: ShortArray): VADResult {
        val input = preprocessAudio(audioFrame)
        val result = ortSession!!.run(mapOf("input" to input))
        val probability = (result[0].value as FloatArray)[0]

        return when {
            probability > 0.6f -> VADResult.SPEECH
            probability < 0.2f -> VADResult.SILENCE
            else -> VADResult.UNCERTAIN
        }
    }

    /**
     * Streaming VAD: track speech segments.
     * Emit SpeechStart when speech begins, SpeechEnd after N ms of silence.
     */
    fun streamingVAD(audioStream: Flow<ShortArray>): Flow<VADEvent> = flow {
        var isSpeaking = false
        var silenceFrames = 0
        val silenceThreshold = 30  // ~500ms of silence at 16kHz with 512-sample frames

        audioStream.collect { frame ->
            when (detectSpeech(frame)) {
                VADResult.SPEECH -> {
                    silenceFrames = 0
                    if (!isSpeaking) {
                        isSpeaking = true
                        emit(VADEvent.SpeechStart)
                    }
                }
                VADResult.SILENCE -> {
                    if (isSpeaking) {
                        silenceFrames++
                        if (silenceFrames >= silenceThreshold) {
                            isSpeaking = false
                            emit(VADEvent.SpeechEnd)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
```

---

## 8. Audio Focus & Routing

```kotlin
class AudioFocusManager(private val context: Context) {
    private val audioManager = context.getSystemService<AudioManager>()!!

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    // Phone call or other app took focus → pause TTS
                    stopTTS()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Briefly lost focus → pause
                    pauseTTS()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Regained focus → resume
                    resumeTTS()
                }
            }
        }
        .build()

    fun requestFocus(): Boolean {
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
```

---

## 9. Configuration

```kotlin
data class VoiceConfig(
    // STT (configured via capability-based settings in Phase 2)
    // Falls back to auto-select if not explicitly configured

    // TTS (configured via capability-based settings in Phase 2)
    // Falls back to auto-select if not explicitly configured

    // Wake word
    val wakeWordEnabled: Boolean = false,
    val wakeWordKeyword: String = "hey guappa",
    val wakeWordSensitivity: Float = 0.5f,  // 0.0-1.0
    val wakeWordAlwaysOn: Boolean = false,   // always listen (battery impact!)

    // VAD
    val vadEngine: String = "silero",  // "silero" or "webrtc"
    val vadSilenceThresholdMs: Int = 500,

    // Audio
    val preferBluetoothAudio: Boolean = true,
    val ttsVolume: Float = 1.0f,
    val sttSampleRate: Int = 16_000,

    // Voice mode behavior
    val autoPlayTTS: Boolean = true,      // auto-play voice responses
    val continuousMode: Boolean = false,  // continuous conversation (like Google Assistant)
    val beepOnListen: Boolean = true,     // play beep when listening starts
)
```

---

## 10. Test Plan

| Test | Description |
|------|-------------|
| `STT_WhisperKit_Transcribe` | Audio file → correct transcript |
| `STT_GoogleMLKit_Streaming` | Streaming audio → realtime transcript |
| `TTS_Orca_Streaming` | Text → streaming audio chunks |
| `TTS_Kokoro_Generate` | Text → complete audio buffer |
| `WakeWord_Detection` | Audio with keyword → detected |
| `WakeWord_FalsePositive` | Audio without keyword → not detected |
| `VAD_SpeechDetection` | Speech audio → SPEECH result |
| `VAD_SilenceDetection` | Silence → SILENCE result |
| `VoicePipeline_E2E` | Wake → speak → response → audio played |
| `StreamingTTS_Sentences` | LLM stream → sentence-by-sentence TTS |
| `AudioFocus_PhoneCall` | TTS playing → incoming call → TTS paused |

---

## 11. Acceptance Criteria

- [ ] STT transcribes speech with > 90% accuracy (English and Russian)
- [ ] TTS speaks responses with natural prosody
- [ ] Streaming TTS starts playing < 1s after LLM starts responding
- [ ] Wake word detection works with < 5% false positive rate
- [ ] VAD correctly detects speech start and end
- [ ] Audio focus properly managed (phone calls pause TTS)
- [ ] Bluetooth audio routing works
- [ ] Voice pipeline survives screen off / background
- [ ] Settings allow separate STT and TTS engine selection
- [ ] Continuous voice mode works (Google Assistant style)
