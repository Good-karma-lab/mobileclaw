# Phase 9: Testing & QA — Maestro E2E, Unit Tests, Resilience, CI

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: Phase 1-7
**Blocks**: —

---

## 1. Objective

Comprehensive test coverage: unit tests (JUnit 5 + MockK), integration tests (Espresso), E2E tests (Maestro), resilience tests, performance benchmarks, and CI pipeline.

---

## 2. Test Framework Stack

| Layer | Framework | Purpose | Coverage Target |
|-------|-----------|---------|-----------------|
| **Unit** | JUnit 5 + MockK | Kotlin logic | 80%+ per module |
| **Android Unit** | Robolectric | Android API mocking | All Android-dependent logic |
| **Integration** | AndroidX Test + Espresso | Component integration | Critical paths |
| **E2E UI** | Maestro v2.2 | Full user flows | 40+ flows |
| **Performance** | AndroidX Benchmark | Latency, memory, throughput | Key operations |
| **Cloud Testing** | Firebase Test Lab | Multi-device matrix | 10+ device configs |
| **Resilience** | Custom + Maestro | Chaos engineering | 10+ scenarios |

---

## 3. Maestro E2E Test Flows

```
maestro/
├── flows/
│   ├── core/
│   │   ├── 01-app-launch.yaml            — app starts, service running
│   │   ├── 02-onboarding.yaml            — first setup, permissions
│   │   ├── 03-provider-setup.yaml        — configure API key
│   │   └── 04-dynamic-model-fetch.yaml   — models fetched from API
│   │
│   ├── chat/
│   │   ├── 10-chat-basic.yaml            — send message, get response
│   │   ├── 11-chat-streaming.yaml        — streaming renders correctly
│   │   ├── 12-chat-tool-use.yaml         — tool call + result in conversation
│   │   ├── 13-chat-multi-turn.yaml       — 10-turn conversation
│   │   └── 14-chat-context-compaction.yaml — long conversation triggers summarization
│   │
│   ├── tools/
│   │   ├── 20-set-alarm.yaml             — "set alarm" → alarm created
│   │   ├── 21-send-sms.yaml             — "send SMS" → SMS composed
│   │   ├── 22-web-search.yaml           — "search for X" → results shown
│   │   ├── 23-web-fetch.yaml            — "read this URL" → content displayed
│   │   ├── 24-take-photo.yaml           — "take photo" → camera opens
│   │   ├── 25-set-timer.yaml            — "set timer" → timer started
│   │   ├── 26-open-twitter.yaml         — "post tweet" → Twitter opens
│   │   ├── 27-read-email.yaml           — "read email" → email content
│   │   ├── 28-image-analyze.yaml        — "analyze this image" → description
│   │   ├── 29-calculator.yaml           — "calculate X" → correct result
│   │   └── 30-translate.yaml            — "translate X" → translation
│   │
│   ├── voice/
│   │   ├── 35-voice-input.yaml           — tap mic → speak → transcript
│   │   ├── 36-voice-output.yaml          — TTS plays response
│   │   └── 37-wake-word.yaml             — wake word → command → action
│   │
│   ├── notifications/
│   │   ├── 40-push-background.yaml       — bg task → push received
│   │   ├── 41-inline-reply.yaml          — reply from notification
│   │   ├── 42-proactive-question.yaml    — agent asks → user replies
│   │   └── 43-morning-briefing.yaml      — scheduled briefing appears
│   │
│   ├── settings/
│   │   ├── 50-provider-switch.yaml       — change provider → immediate effect
│   │   ├── 51-model-switch.yaml          — change model → new model used
│   │   ├── 52-capability-config.yaml     — per-capability model selection
│   │   ├── 53-local-model.yaml           — download model → local inference
│   │   ├── 54-channel-config.yaml        — add Telegram → connected
│   │   └── 55-tool-toggle.yaml           — disable tool → agent can't use
│   │
│   ├── memory/
│   │   ├── 60-fact-remember.yaml         — tell fact → restart → remembers
│   │   ├── 61-long-conversation.yaml     — 50+ messages → summarization works
│   │   └── 62-memory-export.yaml         — export → import → data intact
│   │
│   └── resilience/
│       ├── 70-app-restart.yaml           — kill → reopen → session restored
│       ├── 71-device-reboot.yaml         — reboot → service auto-starts
│       ├── 72-network-loss.yaml          — airplane → reconnect → resume
│       ├── 73-provider-down.yaml         — provider 500 → fallback works
│       ├── 74-oom-recovery.yaml          — low memory → graceful handling
│       ├── 75-permission-revoke.yaml     — revoke permission → error message
│       ├── 76-battery-saver.yaml         — battery saver → reduced mode
│       └── 77-update-migration.yaml      — install update → data preserved
│
├── config/
│   ├── maestro-config.yaml               — global test config
│   └── devices.yaml                      — target device matrix
│
└── helpers/
    ├── setup-provider.yaml               — helper: configure test provider
    └── clear-data.yaml                   — helper: clear app data
```

---

## 4. CI Pipeline

```yaml
# .github/workflows/android-ci.yml
name: Android CI

on:
  push:
    branches: [main, claude/*]
  pull_request:
    branches: [main]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: 17 }
      - run: ./gradlew testDebugUnitTest
      - uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: app/build/reports/tests/

  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew lintDebug
      - run: npx markdownlint-cli docs/**/*.md

  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/

  maestro-e2e:
    runs-on: ubuntu-latest
    needs: build
    steps:
      - uses: actions/checkout@v4
      - uses: actions/download-artifact@v4
        with: { name: debug-apk }
      - name: Start emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          script: |
            adb install app-debug.apk
            curl -Ls https://get.maestro.mobile.dev | bash
            maestro test maestro/flows/ --format junit
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: maestro-screenshots
          path: ~/.maestro/tests/
```

---

## 5. Performance Benchmarks

| Operation | Target | Measurement |
|-----------|--------|-------------|
| App cold start | < 3s | Time to first frame |
| Chat response (cloud) | < 5s | First token latency |
| Chat response (local 7B) | < 3s | First token latency |
| Tool execution (web_search) | < 5s | Query to results |
| Tool execution (set_alarm) | < 1s | Intent fired |
| Context compaction | < 2s | Summarization time |
| Model list fetch | < 3s | API call + parse |
| STT transcription (5s audio) | < 2s | Audio to text |
| TTS first audio byte | < 500ms | Text to audio start |
| Wake word detection latency | < 200ms | Keyword to callback |
| Memory retrieval (RAG) | < 100ms | Query to results |
| App memory (idle) | < 150MB | RSS |
| App memory (local model) | < 2GB | RSS (7B model loaded) |

---

## 6. Acceptance Criteria

- [ ] Unit test coverage > 80% for core modules
- [ ] All 40+ Maestro E2E flows pass
- [ ] All resilience tests pass
- [ ] CI pipeline runs on every PR
- [ ] Performance benchmarks within targets
- [ ] No memory leaks (LeakCanary clean)
- [ ] No ANRs in E2E tests
