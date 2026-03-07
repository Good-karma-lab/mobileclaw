# Phase 12: Documentation — Complete Guappa Docs

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: Phase 1-7
**Blocks**: —

---

## 1. Objective

Delete all existing ZeroClaw/MobileClaw docs (167 markdown files) and create new Guappa-branded documentation from scratch. Single language: English.

---

## 2. Scope

### 2.1 DELETE

- All files in `docs/` (except `docs/plans/` — keep phase docs)
- All `README*.md` in root (except main `README.md` — rewrite)
- `CLAUDE.md` — rewrite for Guappa
- `CONTRIBUTING.md` — rewrite

### 2.2 CREATE

```
README.md                               — Guappa product overview, install, quick start
CLAUDE.md                               — Guappa agent engineering protocol
CONTRIBUTING.md                         — contribution guidelines

docs/
├── README.md                           — docs hub
├── SUMMARY.md                          — table of contents
│
├── getting-started/
│   ├── README.md                       — getting started overview
│   ├── installation.md                 — install (Play Store / APK / build from source)
│   ├── first-setup.md                  — permissions, provider API key, first message
│   ├── quick-tour.md                   — 5-minute tour of all features
│   └── faq.md                          — frequently asked questions
│
├── guides/
│   ├── README.md                       — guides overview
│   ├── choosing-a-model.md             — decision matrix for model selection
│   ├── capability-settings.md          — configuring models per capability type
│   ├── voice-setup.md                  — STT/TTS configuration
│   ├── wake-word.md                    — wake word setup ("Hey Guappa")
│   ├── controlling-apps.md             — app control via intents + AppFunctions
│   ├── telegram-setup.md              — connect Telegram bot step-by-step
│   ├── discord-setup.md               — connect Discord bot
│   ├── messenger-integration.md       — all messenger channels
│   ├── memory-management.md           — memory, summarization, long-term facts
│   ├── proactive-agent.md             — proactive behavior configuration
│   ├── local-inference.md             — on-device model setup + download
│   ├── web-tools.md                   — web_fetch, web_search, web_scrape setup
│   ├── privacy-security.md            — security model, data handling, encryption
│   ├── battery-optimization.md        — battery tips, OEM-specific settings
│   └── troubleshooting.md             — common issues and fixes
│
├── reference/
│   ├── README.md                       — reference overview
│   ├── providers.md                    — all LLM providers (dynamic model fetching)
│   ├── capability-types.md             — text/vision/image/video/audio/embedding/code
│   ├── tools.md                        — all 69 tools with JSON schemas
│   ├── channels.md                     — all messenger channels
│   ├── app-control.md                  — Intent-based app control reference
│   ├── automation.md                   — AppFunctions + UI Automation Framework
│   ├── voice.md                        — STT/TTS/wake word options matrix
│   ├── hardware-acceleration.md        — device SoC/NPU/GPU compatibility
│   ├── notifications.md               — push notification types and channels
│   ├── permissions.md                  — Android permissions per feature
│   ├── memory.md                       — memory architecture (5 tiers)
│   ├── context-management.md           — auto-summarization, recursive LLM, budgets
│   └── config.md                       — all configuration options
│
├── architecture/
│   ├── README.md                       — architecture overview
│   ├── agent-core.md                   — orchestrator, ReAct loop, sessions
│   ├── message-bus.md                  — event system (SharedFlow)
│   ├── provider-router.md              — provider routing, capability-based selection
│   ├── tool-engine.md                  — tool execution pipeline
│   ├── memory-system.md               — multi-tier memory, RAG, consolidation
│   ├── event-system.md                — proactive triggers, device events
│   ├── voice-pipeline.md              — STT → Agent → TTS flow
│   └── live-config.md                 — reactive config, TurboModules bridge
│
├── development/
│   ├── README.md                       — development overview
│   ├── setup.md                        — dev environment setup
│   ├── building.md                     — build instructions (debug/release)
│   ├── testing.md                      — test guide (JUnit, Espresso, Maestro)
│   ├── maestro-setup.md               — Maestro installation and usage
│   └── contributing.md                 — contribution flow
│
└── plans/                              — KEEP existing phase documents
    ├── 2026-03-06-guappa-master-plan.md
    ├── phase-01-foundation.md
    ├── phase-02-provider-router.md
    ├── ... (all phase docs)
```

---

## 3. Documentation Rules

- All docs in English (single language for v1)
- Use "Guappa" consistently (never ZeroClaw, MobileClaw)
- Reference "она" (she) in Russian UI strings context
- Concise, actionable, example-driven
- Every reference doc: complete lists (all providers, tools, etc.)
- Every guide: step-by-step with code examples
- All code examples must compile
- Keep navigation: README → docs hub → SUMMARY → category index → specific doc

---

## 4. Test Plan

- Markdown lint (`markdownlint`)
- Link integrity check (`markdown-link-check`)
- All code examples compile
- No references to "ZeroClaw", "MobileClaw", "OpenClaw"
- All navigation links resolve

---

## 5. Acceptance Criteria

- [ ] All 167 old docs deleted
- [ ] New docs structure created (40+ files)
- [ ] README.md rewrites completed
- [ ] CLAUDE.md rewritten for Guappa
- [ ] All links resolve
- [ ] No legacy branding references
- [ ] Markdown lint passes
