# AGENTS.md — Guappa Agent Engineering Protocol

This file defines the default working protocol for coding agents in this repository.

## 1) Project Snapshot

Guappa is a Kotlin-native Android AI organism with a React Native UI.

Core architecture:
- **Runtime**: Kotlin on Android (providers, tools, memory, swarm, proactive engine)
- **UI**: React Native with Expo (TypeScript, neural-swarm visual language)
- **Bridge**: React Native Native Modules connecting TS ↔ Kotlin

Key directories:
- `mobile-app/android/app/src/main/java/com/guappa/app/` — Kotlin backend
- `mobile-app/src/` — React Native TypeScript frontend
- `mobile-app/.maestro/` — E2E test flows
- `docs/plans/` — Design documents and phase plans

Extension points:
- `providers/` — LLM provider implementations (OpenAI, Anthropic, Gemini)
- `tools/` — Tool implementations (shell, file, browser, device)
- `channels/` — Communication channels (Telegram, etc.)
- `swarm/` — World Wide Swarm Protocol
- `proactive/` — Proactive intelligence engine
- `memory/` — Context and memory management

## 2) Engineering Principles

- **KISS**: Straightforward control flow, explicit error paths
- **YAGNI**: No speculative features or premature abstractions
- **SRP**: Each module focused on one concern
- **Fail Fast**: Explicit errors for unsupported states
- **Secure by Default**: Deny-by-default, never log secrets

## 3) Validation

```bash
# Kotlin unit tests
cd mobile-app/android && ./gradlew testDebugUnitTest

# TypeScript tests
cd mobile-app && npx jest

# Android build
cd mobile-app/android && ./gradlew assembleDebug

# E2E tests (requires running emulator + Metro)
~/.maestro/bin/maestro test mobile-app/.maestro/
```

## 4) Agent Workflow

1. Read before write — inspect existing code before editing
2. One concern per change
3. Implement minimal patch
4. Validate with tests
5. Document impact

## 5) Naming Conventions

- Kotlin: `PascalCase` types, `camelCase` functions, `SCREAMING_SNAKE` constants
- TypeScript: `PascalCase` components/types, `camelCase` functions
- Test IDs: `kebab-case` for Maestro accessibility
- Native modules: `Guappa*Module` / `Guappa*Package`

## 6) Anti-Patterns

- Do not add heavy dependencies for minor convenience
- Do not silently weaken security policy
- Do not mix formatting changes with functional changes
- Do not include personal/sensitive data in code or tests
