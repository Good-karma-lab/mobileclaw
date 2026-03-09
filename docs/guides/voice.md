# Voice Interaction Guide

GUAPPA supports full voice conversations: speak to her, and she speaks back. This guide covers all voice features and configuration.

## Voice Pipeline Overview

The voice pipeline works in stages:

1. **Input** -- Microphone captures your voice (tap-to-speak or wake word activation)
2. **VAD** -- Voice Activity Detection determines when you start and stop speaking
3. **STT** -- Speech-to-text converts your audio to text
4. **Processing** -- GUAPPA's agent processes your request (including tool use)
5. **TTS** -- Text-to-speech converts the response to audio
6. **Output** -- Audio plays through speaker, earpiece, or Bluetooth

## Tap-to-Speak

The simplest way to use voice:

1. Open the **Voice** screen.
2. Tap the **microphone button** (or tap the plasma orb).
3. Speak your request.
4. GUAPPA detects when you stop speaking (via VAD) and processes your request.
5. The response plays as audio.

You can also tap the microphone again to manually stop recording before VAD triggers.

## Wake Word

Enable hands-free activation so GUAPPA listens for a trigger phrase.

### Setup

1. Go to **Settings** > **Voice** > **Wake Word**.
2. Toggle **Enable Wake Word** on.
3. The default wake phrase is **"Hey GUAPPA"**.
4. Adjust **Sensitivity** if needed (higher = more responsive but more false positives).

### How It Works

When wake word detection is enabled, GUAPPA continuously listens for the trigger phrase using a lightweight on-device detector (Picovoice Porcupine or OpenWakeWord). This runs at very low power consumption. When the wake phrase is detected:

1. GUAPPA plays a chime to confirm activation.
2. The microphone switches to full recording mode.
3. VAD monitors for speech start and end.
4. The rest of the pipeline proceeds as with tap-to-speak.

### Tips

- Wake word detection works offline -- it does not send audio to the cloud.
- If GUAPPA activates too often from background noise, lower the sensitivity.
- If GUAPPA misses your wake word, speak more clearly or increase sensitivity.

## Voice Activity Detection (VAD)

VAD automatically detects when you start and stop speaking, so you do not need to tap a button to end recording.

### Settings

- **VAD Engine:** Choose between Silero VAD (more accurate, slightly heavier) or WebRTC VAD (lighter, less accurate).
- **Silence timeout:** How long GUAPPA waits after you stop speaking before processing (default: 1.5 seconds). Increase this if GUAPPA cuts you off mid-pause.
- **Speech threshold:** Minimum audio level to count as speech. Increase in noisy environments.

## Speech-to-Text (STT)

STT converts your spoken words to text. GUAPPA supports multiple STT engines:

| Engine | Type | Languages | Notes |
|--------|------|-----------|-------|
| Whisper (on-device) | Local | 99+ | Best accuracy. Uses whisper.rn. Requires model download. |
| Google ML Kit | Local | 50+ | No download needed. Good for quick setup. |
| OpenAI Whisper API | Cloud | 99+ | Best for noisy audio. Requires OpenAI API key. |
| Google Cloud STT | Cloud | 125+ | Real-time streaming. Requires Google Cloud key. |
| Deepgram | Cloud | 30+ | Fast. Requires Deepgram API key. |

### Choosing an STT Engine

- **No internet, best accuracy:** Whisper (on-device)
- **No internet, no download:** Google ML Kit
- **Fastest with internet:** Deepgram or Google Cloud STT
- **Noisy environment:** OpenAI Whisper API

### Configuration

Go to **Settings** > **Voice** > **Speech-to-Text** to select your engine and language.

For on-device Whisper, you need to download the model first (Settings > Voice > Download Whisper Model). The base model is approximately 150 MB.

## Text-to-Speech (TTS)

TTS converts GUAPPA's text responses to spoken audio.

| Engine | Type | Voices | Notes |
|--------|------|--------|-------|
| Android Native TTS | Local | Varies by device | No download. Quality varies. No streaming. |
| Piper TTS | Local | 100+ | Open-source, ONNX, good quality. |
| Kokoro TTS | Local | Multiple | Neural TTS, 82M params, Apache 2.0. |
| Picovoice Orca | Local | Multiple | Streaming, sub-50ms latency. |
| OpenAI TTS | Cloud | 6 | High quality, streaming. |
| Google Cloud TTS | Cloud | 380+ | Many languages and voices. |
| ElevenLabs | Cloud | Many | Ultra-realistic, voice cloning. |
| Deepgram Aura | Cloud | Multiple | Streaming, low latency. |

### Choosing a TTS Engine

- **Free, no internet:** Android Native TTS or Piper TTS
- **Best on-device quality:** Kokoro TTS or Picovoice Orca
- **Best cloud quality:** ElevenLabs or OpenAI TTS
- **Most voice options:** Google Cloud TTS

### Configuration

Go to **Settings** > **Voice** > **Text-to-Speech** to select your engine, voice, and speaking rate.

## Audio Routing

GUAPPA handles audio routing automatically:

- **Speaker:** Default output when no headphones or Bluetooth are connected.
- **Earpiece:** Used when the phone is held to your ear (proximity sensor).
- **Wired headphones:** Automatically routes when plugged in.
- **Bluetooth:** Routes to connected Bluetooth audio devices (headphones, car systems, smart speakers).

GUAPPA requests audio focus from Android when speaking and releases it when done, so it plays nicely with music and other audio apps.

## Troubleshooting Voice Issues

**Microphone not working:**
- Check that GUAPPA has microphone permission (Settings > Apps > GUAPPA > Permissions).
- Make sure no other app is using the microphone.
- Restart the app.

**STT produces wrong text:**
- Try a different STT engine.
- Speak more clearly and closer to the microphone.
- Reduce background noise.
- For Whisper, try downloading a larger model for better accuracy.

**TTS not producing audio:**
- Check device volume is not muted.
- Try switching to Android Native TTS to rule out engine issues.
- Check Bluetooth connection if using wireless audio.

**Wake word not triggering:**
- Increase wake word sensitivity in Settings.
- Speak the wake phrase clearly: "Hey GUAPPA."
- Make sure wake word is enabled in Settings > Voice.

**Audio cuts out during Bluetooth playback:**
- Some Bluetooth devices have aggressive power saving. Check your device's Bluetooth settings.
- Try SCO mode for call-like audio quality vs. A2DP for media quality in Settings > Voice > Audio Routing.
