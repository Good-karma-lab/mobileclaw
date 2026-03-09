# Guappa Implementation Backlog

**Date**: 2026-03-09
**Status**: Active backlog
**Source plan**: `docs/plans/2026-03-09-guappa-completion-plan.md`

This backlog converts the completion plan into execution milestones with dependency order, concrete outputs, and acceptance gates.

---

## 1. Milestone map

| Milestone | Name | Depends on | Primary outcome |
|---|---|---|---|
| B0 | Audit and board setup | — | One execution board and traceability map |
| B1 | ZeroClaw purge inventory | B0 | Full deletion map for code, docs, CI, config, names |
| B2 | ZeroClaw purge execution | B1 | Active repo surfaces become Guappa-only |
| B3 | Bridge alignment | B0 | Swarm/config/agent native bridges are real and consistent |
| B4 | Compliance action layer | B3 | Accessibility-first autonomy removed from ship path |
| B5 | Provider router completion | B3 | Capability-based provider/model routing works |
| B6 | Tool engine hardening | B4, B5 | Shipped tools have safety, approval, audit, tests |
| B7 | Memory and cognition completion | B3 | 5-tier memory and summarization behave correctly |
| B8 | Proactive engine and channels | B6, B7 | Notifications, triggers, and launch channels work |
| B9 | Live config and hot reload | B3, B5, B6, B7, B8 | Config changes apply without restart |
| B10 | WWSP connector integration | B3, B7, B8 | `Swarm` screen becomes real WWSP surface |
| B11 | Voice and presence architecture | B5, B7, B9 | Stable voice-first Guappa |
| B12 | AI organism redesign | B9, B10, B11 | Product feels like one living AI |
| B13 | Test matrix build-out | B4-B12 | Automated and real-device validation pipeline |
| B14 | Docs and release readiness | B2, B10, B12, B13 | Guappa docs and launch operations complete |

---

## 2. Milestone backlog

## B0 - Audit and board setup

**Goal**: turn plan sprawl into an executable delivery system.

### Tasks

- B0.1 Create a requirement traceability table from old plan docs to current code paths.
- B0.2 Tag every requirement as `implemented`, `partial`, `missing`, `obsolete`, or `replaced`.
- B0.3 Create a delivery board grouped by subsystem: runtime, automation, providers, tools, memory, proactive, channels, config, WWSP, voice, UI, testing, docs.
- B0.4 Assign risk tags: `policy`, `security`, `hardware`, `performance`, `migration`, `release`.

### Outputs

- traceability sheet
- execution board
- dependency graph

### Acceptance gate

- every old plan section is either mapped into backlog work or explicitly retired

## B1 - ZeroClaw purge inventory

**Goal**: identify every active legacy surface before deletion.

### Tasks

- B1.1 Search repo for `ZeroClaw`, `MobileClaw`, `zeroclaw`, `mobileclaw`, old package IDs, old bridge names, old config names.
- B1.2 Classify each hit as code, config, docs, CI/CD, test, asset/string, or archive.
- B1.3 Build a delete/rename/archive matrix.
- B1.4 Identify blockers where deletion requires replacement first.
- B1.5 Define banned identifiers for CI.

### Outputs

- purge inventory document
- banned-identifier list
- delete order

### Acceptance gate

- nothing legacy remains unclassified

## B2 - ZeroClaw purge execution

**Goal**: remove the old wrapped architecture from active repo surfaces.

### Tasks

- B2.1 Remove legacy runtime ownership from `src/` and related mobile-coupled Rust surfaces.
- B2.2 Remove obsolete Cargo/JNI/mobile wrapper build paths.
- B2.3 Remove old config files, keys, scripts, bootstrap paths, and naming.
- B2.4 Replace active docs entry points with Guappa-native docs.
- B2.5 Remove or archive obsolete workflows and release steps.
- B2.6 Rename remaining strings, assets, package names, and artifact names.
- B2.7 Add CI grep guard for banned legacy identifiers.

### Outputs

- Guappa-only active repo surfaces
- banned-name CI check

### Acceptance gate

- build, install, docs navigation, and active workflows run without ZeroClaw dependencies

## B3 - Bridge alignment

**Goal**: eliminate dead or divergent JS/native interfaces.

### Tasks

- B3.1 Inventory all current native modules used by Chat, Command, Config, Voice, Swarm.
- B3.2 Choose canonical module names and remove duplicates.
- B3.3 Implement missing Android package/module registration in `MainApplication.kt`.
- B3.4 Align `SwarmScreen.tsx` and `guappaSwarm.ts` to one native module contract.
- B3.5 Audit config and proactive bridge surfaces for missing methods and dead calls.
- B3.6 Add bridge contract tests for method presence and event emission.

### Outputs

- bridge contract spec
- registered native packages
- bridge smoke tests

### Acceptance gate

- no core screen depends on a missing native module

## B4 - Compliance action layer

**Goal**: replace policy-risky action execution with compliant architecture.

### Tasks

- B4.1 Define `DeviceActionGateway` abstraction.
- B4.2 Implement `AppFunctionsBackend` where available.
- B4.3 Implement sanctioned `UiAutomationPreviewBackend` abstraction where available.
- B4.4 Implement deterministic `IntentBackend` for supported standard actions.
- B4.5 Implement `UnsupportedBackend` for unsupported devices and actions.
- B4.6 Re-route tool and action paths away from accessibility-first autonomy.
- B4.7 Add approval and sensitive-action confirmation UX.
- B4.8 Add action history, live progress, takeover, and cancel behavior.

### Outputs

- compliance ADR
- action backend matrix
- user-visible action controls

### Acceptance gate

- production path no longer depends on accessibility-first autonomous action execution

## B5 - Provider router completion

**Goal**: finish provider/model capability routing.

### Tasks

- B5.1 Finalize provider shortlist for March 2026 launch.
- B5.2 Add dynamic model discovery with TTL and stale-cache handling.
- B5.3 Add capability-scoped routing for text, reasoning, code, vision, image, STT, TTS, embeddings, swarm-director.
- B5.4 Implement fallback chains and health scoring.
- B5.5 Add token counting and cost estimation.
- B5.6 Add budget and quota exhaustion behavior.
- B5.7 Implement local-model probe, download, and load/unload policy.

### Outputs

- provider scorecard
- routing matrix
- local inference support matrix

### Acceptance gate

- Config can switch provider/model by capability and next request reflects it without restart

## B6 - Tool engine hardening

**Goal**: productionize tools with safety and observability.

### Tasks

- B6.1 Inventory shipped vs partial vs deferred tools.
- B6.2 Add tool metadata: schema, availability, permission, risk, approval, audit tags.
- B6.3 Complete mandatory web tools.
- B6.4 Complete device/app/system tools used by Guappa launch flows.
- B6.5 Add per-tool denial, timeout, and side-effect handling.
- B6.6 Add audit log and user-visible task/action traces.
- B6.7 Add tool-specific unit and integration tests.

### Outputs

- tool registry contract
- launch tool catalog
- tool validation suite

### Acceptance gate

- all shipped tools declare and enforce safety, approval, and audit behavior

## B7 - Memory and cognition completion

**Goal**: finish Guappa's long-horizon internal model.

### Tasks

- B7.1 Validate and complete all 5 memory tiers.
- B7.2 Implement retention, promotion, decay, and dedupe rules.
- B7.3 Complete summarization and compaction pipeline.
- B7.4 Add retrieval evaluation fixtures and benchmarks.
- B7.5 Implement user-facing export/import/forget/reset behavior.
- B7.6 Validate restart persistence and long-session quality.

### Outputs

- memory behavior spec
- retrieval benchmark set
- persistence tests

### Acceptance gate

- memory survives restart and produces acceptable retrieval quality under test scenarios

## B8 - Proactive engine and channels

**Goal**: finish Guappa's autonomous follow-up and communications.

### Tasks

- B8.1 Finalize trigger catalog and custom rule model.
- B8.2 Implement quiet hours, cooldowns, dedupe, escalation.
- B8.3 Finish notification action handling: reply, approve/deny, snooze, open chat.
- B8.4 Define launch-minimum channels vs deferred channels.
- B8.5 Implement setup, auth, health checks, inbound/outbound flows for launch channels.
- B8.6 Add OEM background reliability guidance and health surfaces.

### Outputs

- proactive rules spec
- launch channel matrix
- notification validation suite

### Acceptance gate

- proactive events and launch channels behave reliably on supported devices

## B9 - Live config and hot reload

**Goal**: make Config truly live.

### Tasks

- B9.1 Define canonical config schema and versioning.
- B9.2 Add typed bridge API for config read/write and change events.
- B9.3 Implement subsystem-specific hot-swap handlers.
- B9.4 Define and test timing semantics for each config domain.
- B9.5 Add Config health, debug export, and reset tooling.

### Outputs

- config schema/migration spec
- hot-reload contract
- config bridge tests

### Acceptance gate

- config changes apply at documented boundaries without restart

## B10 - WWSP connector integration

**Goal**: make the `Swarm` screen and backend a real World Wide Swarm Protocol client.

### Tasks

- B10.1 Define launch connector modes: local, remote, disabled.
- B10.2 Choose launch transport path: JSON-RPC, HTTP, or hybrid.
- B10.3 Align `SwarmConnectorClient.kt` with actual WWSP contract and version checks.
- B10.4 Implement canonical swarm native module and register it.
- B10.5 Align `SwarmScreen.tsx` and `guappaSwarm.ts` to the same API.
- B10.6 Implement identity creation, secure storage, and registration flow.
- B10.7 Implement connector health, reconnect, and disabled-mode no-traffic behavior.
- B10.8 Implement task accept/reject/report flow and agent-session routing.
- B10.9 Define launch scope for holons and reputation surfaces.
- B10.10 Replace placeholders with real peer, event, stats, task, and identity data.

### Outputs

- WWSP integration ADR
- native bridge contract
- `Swarm` screen backed by real connector state

### Acceptance gate

- `Swarm` tab can connect to a real WWSP connector, show identity and peers, and process task lifecycle correctly

## B11 - Voice and presence architecture

**Goal**: make Guappa stable and compelling in voice mode.

### Tasks

- B11.1 Finalize STT/TTS/VAD/wake-word engine decisions.
- B11.2 Move latency-critical audio pipeline pieces native where needed.
- B11.3 Add audio routing, focus, interruption, and Bluetooth/headset handling.
- B11.4 Implement synchronized voice state machine across native and UI.
- B11.5 Add offline/degraded mode behavior.
- B11.6 Validate long-session voice stability and barge-in behavior.

### Outputs

- voice stack ADR
- audio routing matrix
- voice regression suite

### Acceptance gate

- voice works reliably across interruptions, backgrounding, and route changes

## B12 - AI organism redesign

**Goal**: make the product feel like one living AI entity.

### Tasks

- B12.1 Define shared state language across all screens.
- B12.2 Apply ambient neural/cosmic system app-wide.
- B12.3 Redesign Chat around streaming, memory, tool traces, and emotional continuity.
- B12.4 Replace mock-first Command behavior with real operational ledger UX.
- B12.5 Keep `Swarm` as WWSP screen while integrating ambient state visuals around it.
- B12.6 Redesign Config as self-description, trust, and repair surface.
- B12.7 Add low-end and reduced-motion fallback modes.

### Outputs

- design system brief
- screen acceptance checklist
- performance fallback matrix

### Acceptance gate

- product reads as one AI organism, not separate utility tabs

## B13 - Test matrix build-out

**Goal**: create real proof of correctness.

### Tasks

- B13.1 Add JS/TS test runner and baseline suites.
- B13.2 Add Kotlin unit test suites.
- B13.3 Add Robolectric coverage for Android-dependent logic.
- B13.4 Add instrumented tests for service, notifications, migrations, and bridges.
- B13.5 Reorganize Maestro into smoke, regression, resilience, and hardware suites.
- B13.6 Add CI gates for PR, nightly, and release candidate levels.
- B13.7 Define real-device matrix and hardware signoff scripts.
- B13.8 Add benchmark harnesses and thresholds.

### Outputs

- test architecture ADR
- CI gating matrix
- device signoff checklist

### Acceptance gate

- launch gates run reproducibly and cover emulator, cloud, and real-device needs

## B14 - Docs and release readiness

**Goal**: finish operational maturity.

### Tasks

- B14.1 Replace root and active docs entry points with Guappa-native docs.
- B14.2 Write architecture, setup, provider, voice, WWSP, privacy, troubleshooting, and testing docs.
- B14.3 Add runbooks for outages, stuck tasks, connector failures, and local-model issues.
- B14.4 Define release checklist, rollback plan, and crash triage workflow.
- B14.5 Validate docs links, commands, screenshots, and branding.

### Outputs

- Guappa doc set
- operations runbooks
- release readiness checklist

### Acceptance gate

- a new engineer can build, test, debug, and release Guappa without old plan context

---

## 3. Critical path

The critical path is:

`B0 -> B1 -> B2 -> B3 -> B4 -> (B5 + B7) -> B6 -> B8 -> B9 -> B10 -> B11 -> B12 -> B13 -> B14`

Notes:

- `B10` cannot close before `B3` because swarm bridge mismatch is a hard blocker.
- `B12` should not be considered done before `B10`, because the `Swarm` screen role must be correct before final UX polish.
- `B13` starts earlier in parallel, but release gating only closes after B4-B12 stabilize.

---

## 4. Release-blocking backlog items

These items block any serious release candidate:

- RB1: ZeroClaw active surfaces still present
- RB2: accessibility-first autonomous action path still required for core features
- RB3: swarm native module not registered or contract mismatch remains
- RB4: `Command` still mock-first for core state
- RB5: no Android unit/instrumented test layers
- RB6: no real-device hardware validation
- RB7: `Swarm` screen not connected to a real WWSP connector
- RB8: docs still route engineers/users into obsolete product surfaces

---

## 5. Suggested execution slices

To keep work reversible, implement in these slice groups:

### Slice S1 - Repo ownership reset

- B0, B1, first half of B2

### Slice S2 - Native truth

- second half of B2, B3, first half of B9

### Slice S3 - Policy-safe action core

- B4, B6 core, B13 baseline

### Slice S4 - Mind and memory

- B5, B7, B9 completion

### Slice S5 - Swarm truth

- B10 and related tests

### Slice S6 - Presence and organism UI

- B11, B12

### Slice S7 - Finish and ship

- B8 completion, B13 completion, B14

---

## 6. First tickets to create now

- T1: Build ZeroClaw purge inventory and banned-name list
- T2: Specify canonical native module names and register missing packages
- T3: Replace `SwarmManager`/`GuappaSwarm` split with one real bridge contract
- T4: Replace `CommandScreen` mock-first boot with explicit loading/empty/live states
- T5: Draft automation compliance ADR and `DeviceActionGateway` API
- T6: Draft WWSP integration ADR with launch transport and connector-mode decisions
- T7: Add baseline JS/TS and Kotlin test scaffolding
