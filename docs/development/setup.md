# Guappa Development Setup

## Prerequisites

- Node.js 20+
- Java 17 (Temurin recommended)
- Android SDK 34+, NDK 26
- Android emulator or physical device (API 28+)
- Maestro CLI (`curl -Ls "https://get.maestro.mobile.dev" | bash`)

## Quick Start

```bash
# Clone and install
cd mobile-app
npm install

# Start Metro bundler
npx expo start

# Build and run on emulator (separate terminal)
cd android && ./gradlew installDebug
```

## Environment Variables / API Keys

Set these in the app's Config screen or via Maestro test setup:

| Key | Purpose | Required |
|-----|---------|----------|
| OpenRouter API Key | Cloud LLM access | For cloud chat |
| Deepgram API Key | STT/TTS | For voice features |
| Telegram Bot Token | Telegram channel | For Telegram integration |
| Brave Search API Key | Web search tool | For web search |

## Running Tests

### Unit Tests (Kotlin)
```bash
cd mobile-app/android
./gradlew testDebugUnitTest
```

### Unit Tests (TypeScript)
```bash
cd mobile-app
npx jest
```

### E2E Tests (Maestro)
```bash
# Requires running emulator + app installed
~/.maestro/bin/maestro test mobile-app/.maestro/guappa_all_screens_smoke.yaml
~/.maestro/bin/maestro test mobile-app/.maestro/  # Run all
```

### Instrumented Tests (UI Automator)
```bash
cd mobile-app/android
./gradlew connectedAndroidTest
```

## Project Structure

```
mobile-app/
├── src/                          # React Native TypeScript
│   ├── screens/tabs/             # 5 main screens
│   ├── components/               # Reusable components
│   ├── hooks/                    # Custom hooks (voice, TTS, VAD)
│   ├── native/                   # Native module TypeScript wrappers
│   ├── swarm/                    # Neural swarm visualization
│   ├── theme/                    # Colors, typography, animations
│   └── voice/                    # Voice engine manager, Deepgram TTS
├── android/app/src/main/java/    # Kotlin backend
│   └── com/guappa/app/
│       ├── agent/                # Orchestrator, planner, session
│       ├── providers/            # LLM providers
│       ├── tools/impl/           # 78+ tool implementations
│       ├── memory/               # 5-tier memory system
│       ├── proactive/            # Event-driven engine
│       ├── channels/             # Messenger integrations
│       ├── swarm/                # World Wide Swarm Protocol
│       ├── config/               # Live config, hot-swap
│       └── voice/                # Android SpeechRecognizer
├── .maestro/                     # E2E test flows (100+ YAML)
└── docs/                         # Documentation
```

## Building for Release

```bash
cd mobile-app/android
./gradlew assembleRelease
```

## Adding a New Tool

1. Create `mobile-app/android/app/src/main/java/com/guappa/app/tools/impl/YourTool.kt`
2. Implement `Tool` interface with `name`, `description`, `parameters`, `execute()`
3. Register in `ToolRegistry.kt`
4. Add unit test in `src/test/.../tools/`
5. Add Maestro E2E test in `.maestro/`

## Adding a New Provider

1. Implement `Provider` interface in `providers/`
2. Register in `ProviderFactory.kt`
3. Add to `ProviderRouter` capability mapping
4. Add config fields to `GuappaConfigStore`
5. Update `ConfigScreen.tsx` UI
