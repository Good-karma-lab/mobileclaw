# GUAPPA

**Your phone is the agent.** GUAPPA turns any Android device into an autonomous AI agent with liquid glass UI, multi-provider intelligence, and World Wide Swarm connectivity.

## Architecture

```
┌─────────────────────────────────────────────┐
│  React Native + Expo 54 (TypeScript)        │
│  ┌─────────────────────────────────────────┐│
│  │ Liquid Glass Design System              ││
│  │ GlassCard│Button│Input│Toggle│Modal│... ││
│  └─────────────────────────────────────────┘│
│  ┌──────┬──────┬──────┬──────┬──────┐      │
│  │Voice │ Chat │ Cmd  │Swarm │Config│      │
│  │Screen│Screen│Center│Screen│Screen│      │
│  └──────┴──────┴──────┴──────┴──────┘      │
│  FloatingDock (morphing nav)                │
├─────────────────────────────────────────────┤
│  NativeModules Bridge                       │
│  GuappaAgent │ GuappaConfig                 │
├─────────────────────────────────────────────┤
│  Kotlin Android Backend (com.guappa.app)    │
│  ┌──────────────────────────────────────┐   │
│  │ ProviderRouter (OpenAI│Anthropic│    │   │
│  │   Gemini│OpenRouter│13 total)        │   │
│  ├──────────────────────────────────────┤   │
│  │ ToolEngine (15 tools, 30s timeout)   │   │
│  ├──────────────────────────────────────┤   │
│  │ ReAct Orchestrator (5-iteration max) │   │
│  ├──────────────────────────────────────┤   │
│  │ ProactiveEngine (triggers/schedules) │   │
│  ├──────────────────────────────────────┤   │
│  │ ChannelHub (Telegram│Discord│Slack)  │   │
│  ├──────────────────────────────────────┤   │
│  │ SwarmManager (WWSP connector client) │   │
│  └──────────────────────────────────────┘   │
│  MessageBus (SharedFlow pub/sub)            │
└─────────────────────────────────────────────┘
```

## Screens

| Screen | Purpose |
|--------|---------|
| **Voice** | Plasma orb visualization, mic input, real-time transcription |
| **Chat** | Streaming glass message bubbles, ReAct tool execution |
| **Command Center** | Tasks, schedules, triggers, 5-tier memory viewer |
| **Swarm** | World Wide Swarm — peers, feed, Ed25519 identity |
| **Config** | Capability-first settings (how GUAPPA thinks/sees/speaks/connects) |

## Key Features

- **Multi-provider AI**: OpenAI, Anthropic, Google Gemini, OpenRouter + 9 more via OpenAI-compatible base
- **15 native tools**: alarms, SMS, calls, contacts, browser, web search, calculator, translation, image analysis
- **Proactive agent**: time/event/location/condition-based triggers with SharedPreferences persistence
- **Channel hub**: Telegram bot, Discord webhook, Slack webhook, email intent
- **World Wide Swarm**: WWSP connector, EC keypair in Android Keystore, peer discovery, task delegation
- **Onboarding wizard**: 4-step flow (welcome → provider setup → model download → permissions)
- **Glass design system**: 13 reusable glass components with translucent surfaces and animations
- **122 Maestro E2E tests**: Comprehensive coverage across all screens

## Security

- API keys stored in Android Keystore / EncryptedSharedPreferences only
- Swarm identity keys never leave secure hardware
- No tokens committed to git
- Tool execution sandboxed with 30s timeout
- Deny-by-default permission model

## Development

```bash
# Install dependencies
cd mobile-app && npm install

# Start Metro bundler
npx expo start

# Build Android debug
cd android && ./gradlew assembleDebug

# Verify Kotlin compiles
./gradlew compileDebugKotlin

# Run Maestro tests
maestro test .maestro/guappa_all_screens_smoke.yaml
```

## Package Structure (Kotlin)

```
com.guappa.app/
├── agent/          # MessageBus, GuappaConfig, Session, Orchestrator
├── providers/      # Provider interface, Router, OpenAI/Anthropic/Gemini
├── tools/          # Tool interface, Engine, Registry, 15 implementations
├── proactive/      # ProactiveTrigger, Engine, NotificationManager
├── channels/       # Channel interface, Hub, Telegram/Discord/Slack/Email
├── swarm/          # SwarmIdentity, Manager, ConnectorClient, PeerInfo
├── config/         # ConfigBridge NativeModule (JS↔Kotlin sync)
└── *.kt            # Activity, Application, native modules
```

## License

Proprietary. All rights reserved.
