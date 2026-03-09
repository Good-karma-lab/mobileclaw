# E2E Voice & Agent Memory Test Suite

## Overview

Tests a full agent interaction loop:

1. **Memory Store** — Tell agent "My wife's name is Kate"
2. **Call Hook** — Ask agent to notify on incoming calls
3. **Call Emulation** — Simulate incoming call via ADB
4. **Memory Recall** — Ask "What is my wife's name?" → expect "Kate"
5. **Voice STT** — Use TTS→BlackHole→Emulator mic to test speech recognition

## Prerequisites

### 1. BlackHole Virtual Audio Cable

```bash
brew install --cask blackhole-2ch
```

### 2. Audio Routing

| Setting | Value |
|---------|-------|
| **System Sound Output** | BlackHole 2ch |
| **Emulator → Extended Controls → Microphone** | BlackHole 2ch |
| **System Volume** | 100% |

> **Tip:** Create a macOS "Multi-Output Device" in Audio MIDI Setup if you want to hear audio AND route it to BlackHole simultaneously.

### 3. Software

```bash
brew install ffmpeg
# Maestro
curl -Ls "https://get.maestro.mobile.dev" | bash
```

### 4. App Configuration

- Valid LLM provider API key (OpenRouter, OpenAI, etc.)
- Deepgram API key for voice STT
- App onboarded (skip setup wizard)

## Running

### Full E2E (all 5 phases, orchestrated)

```bash
bash mobile-app/.maestro/e2e_agent_memory_call_voice.sh
```

### Individual Phases (for debugging)

```bash
# Phase 1: Store memory
maestro test mobile-app/.maestro/e2e_phase1_memory_store.yaml

# Phase 2: Set up call hook
maestro test mobile-app/.maestro/e2e_phase2_call_hook_setup.yaml

# Phase 3: Emulate call + verify (run adb command first)
adb emu gsm call 5551234567
sleep 10
adb emu gsm cancel 5551234567
maestro test mobile-app/.maestro/e2e_phase3_call_emulate_verify.yaml

# Phase 4: Memory recall
maestro test mobile-app/.maestro/e2e_phase4_memory_recall.yaml

# Phase 5: Voice STT (start TTS playback in parallel)
bash mobile-app/.maestro/play_tts.sh "What is my wife's name?" &
maestro test mobile-app/.maestro/e2e_phase5_voice_stt_blackhole.yaml
```

### TTS Helper

```bash
# Play text through BlackHole (background)
bash mobile-app/.maestro/play_tts.sh "Hello world"

# Play and wait for completion
bash mobile-app/.maestro/play_tts.sh "Hello world" --wait

# Custom voice and rate
bash mobile-app/.maestro/play_tts.sh "Hello" --voice Samantha --rate 150
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| STT gets no audio | Check System Output = BlackHole 2ch, volume = 100% |
| STT gets garbled text | Use `--rate 150` for slower speech |
| Emulator mic not picking up | Open Emulator Extended Controls → Microphone → set to BlackHole 2ch |
| "Backend error" in agent | Check provider API key in Settings |
| Call hook not firing | Verify `IncomingCallReceiver` is registered in AndroidManifest.xml |
| Maestro can't find elements | Run `maestro studio` to inspect element IDs |

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│ macOS 'say'  │────▶│ BlackHole 2ch │────▶│ Emulator Mic    │
│ (TTS engine) │     │ (virtual cable)│     │ (audio input)   │
└─────────────┘     └──────────────┘     └────────┬────────┘
                                                   │
                                                   ▼
                                          ┌─────────────────┐
                                          │ App STT Engine   │
                                          │ (Deepgram/Google)│
                                          └────────┬────────┘
                                                   │
                                                   ▼
                                          ┌─────────────────┐
                                          │ Agent processes  │
                                          │ voice query      │
                                          └─────────────────┘
```
