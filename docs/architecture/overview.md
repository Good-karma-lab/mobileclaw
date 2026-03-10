# Guappa Architecture Overview

## System Architecture

```
┌──────────────────────────────────────────────────┐
│                  React Native UI                  │
│  ┌──────┐ ┌───────┐ ┌────────┐ ┌─────┐ ┌──────┐ │
│  │ Chat │ │Command│ │ Config │ │Voice│ │Swarm │ │
│  └──┬───┘ └───┬───┘ └───┬────┘ └──┬──┘ └──┬───┘ │
│     │         │         │         │        │      │
│  ┌──┴─────────┴─────────┴─────────┴────────┴──┐  │
│  │           Native Modules Bridge             │  │
│  └──┬─────────┬─────────┬─────────┬────────┬──┘  │
└─────┼─────────┼─────────┼─────────┼────────┼─────┘
      │         │         │         │        │
┌─────┼─────────┼─────────┼─────────┼────────┼─────┐
│     ▼         ▼         ▼         ▼        ▼      │
│  Kotlin Android Backend                           │
│  ┌──────────────────────────────────────────────┐ │
│  │ GuappaOrchestrator (ReAct Loop)              │ │
│  │  ├─ GuappaPlanner (task decomposition)       │ │
│  │  ├─ GuappaSession (conversation state)       │ │
│  │  ├─ GuappaPersona (system prompt)            │ │
│  │  └─ MessageBus (event pub/sub)               │ │
│  └──────────────────────────────────────────────┘ │
│                                                    │
│  ┌────────────┐ ┌──────────┐ ┌──────────────────┐ │
│  │ProviderRouter│ │ToolEngine│ │  MemoryManager  │ │
│  │ ├─Anthropic │ │ 78 tools │ │ 5-tier memory   │ │
│  │ ├─OpenAI    │ │ ├─Device │ │ ├─Working       │ │
│  │ ├─Gemini    │ │ ├─Web    │ │ ├─Short-term    │ │
│  │ ├─OpenRouter│ │ ├─File   │ │ ├─Long-term     │ │
│  │ └─Local LLM │ │ ├─Social │ │ ├─Episodic      │ │
│  └────────────┘ │ └─AI     │ │ └─Semantic       │ │
│                  └──────────┘ └──────────────────┘ │
│                                                    │
│  ┌──────────────┐ ┌───────────┐ ┌──────────────┐  │
│  │ProactiveEngine│ │ChannelHub│ │ SwarmManager │  │
│  │ ├─Triggers   │ │ ├─Telegram│ │ ├─Identity   │  │
│  │ ├─Receivers  │ │ ├─Discord │ │ ├─Connector  │  │
│  │ ├─Timing     │ │ ├─Slack   │ │ ├─TaskPoller │  │
│  │ └─Notifier   │ │ ├─SMS    │ │ └─Reputation │  │
│  └──────────────┘ │ └─Email  │ └──────────────┘  │
│                    └───────────┘                    │
│                                                    │
│  ┌──────────────────────────────────────────────┐  │
│  │ Infrastructure                                │  │
│  │ ├─ Room Database (sessions, facts, tasks)    │  │
│  │ ├─ SecurePrefs (encrypted config)            │  │
│  │ ├─ WorkManager (scheduled jobs)              │  │
│  │ ├─ Foreground Service (always-on)            │  │
│  │ └─ Boot Receiver (auto-start)                │  │
│  └──────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────┘
```

## Data Flow

1. **User Input** → React Native UI → Native Module Bridge → GuappaOrchestrator
2. **ReAct Loop**: Plan → Act (tool call or LLM) → Observe → Repeat
3. **Provider Routing**: ProviderRouter selects best LLM based on capability needed
4. **Tool Execution**: ToolEngine dispatches to 78+ registered tools
5. **Memory**: Facts extracted from conversations, promoted through tiers
6. **Proactive**: BroadcastReceivers → MessageBus → EventReactor → Agent responds

## Module Boundaries

| Module | Responsibility | Key Classes |
|--------|---------------|-------------|
| `agent/` | Orchestration, planning, sessions | `GuappaOrchestrator`, `GuappaPlanner`, `GuappaSession` |
| `providers/` | LLM provider management | `ProviderRouter`, `Provider`, `CapabilityInferrer` |
| `tools/` | Tool registry and execution | `ToolEngine`, `ToolRegistry`, 78 tool implementations |
| `memory/` | 5-tier memory system | `MemoryManager`, `GuappaDatabase`, `EmbeddingService` |
| `proactive/` | Event-driven agent actions | `ProactiveEngine`, `TriggerManager`, receivers |
| `channels/` | Messenger integrations | `ChannelHub`, `TelegramChannel`, etc. |
| `swarm/` | World Wide Swarm Protocol | `SwarmManager`, `SwarmConnectorClient` |
| `config/` | Live configuration | `GuappaConfigStore`, hot-swap classes |
| `voice/` | Android SpeechRecognizer | `AndroidSTTModule` |

## Voice Pipeline

```
Microphone → VAD (energy) → STT Engine → Transcript → Agent → TTS → Speaker
                              │
                              ├─ Deepgram Nova-3 (cloud, best accuracy)
                              ├─ Deepgram Flux (voice agent mode)
                              ├─ Whisper.rn (on-device, offline)
                              └─ Android SpeechRecognizer (free fallback)
```

## Local Inference Stack

```
Device Hardware → HardwareProbe (SoC/NPU detection)
                     │
                     ├─ Model recommendation (RAM-based)
                     │
                     ▼
llama.rn (GGUF) ←── ModelDownloader
     │                   │
     ▼                   ▼
NanoHTTPD Server    Progress UI
(OpenAI-compat)     (download/load)
     │
     ▼
ProviderRouter (local://localhost:PORT)
```
