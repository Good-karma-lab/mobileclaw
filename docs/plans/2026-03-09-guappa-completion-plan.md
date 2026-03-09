# Guappa Completion Plan

**Date**: 2026-03-09
**Status**: Active plan
**Scope**: Finish Guappa as a Kotlin-native Android AI organism, remove all ZeroClaw legacy surfaces, complete backend and mobile product work, integrate World Wide Swarm correctly, and ship with full emulator + real-device + hardware validation.

---

## 1. Decision summary

This plan makes the following product and architecture decisions final:

1. Guappa is no longer a wrapper around ZeroClaw.
2. The production runtime for the mobile product is Kotlin-native Android, with React Native UI.
3. ZeroClaw code, configs, docs, CI, scripts, names, and dead compatibility layers must be fully removed from active repo surfaces.
4. The mobile app is not a utility app with AI screens. It is one living AI entity. Chat, Voice, Command, Config, and the ambient visual system are all parts of Guappa.
5. The dedicated `Swarm` screen is the interface to the World Wide Swarm Protocol connector and global swarm participation.
6. The neural-swarm visual language from `docs/plans/guappa-cosmic-v5.html` and `docs/plans/phase-14-swarm.md` must inform the entire app, but it must not replace the WWSP function of the `Swarm` screen.
7. No feature is done until it is verified on emulator and on real Android hardware when hardware or OEM behavior matters.

---

## 2. What was checked before writing this plan

This plan was grounded in the current code and current plan set, not only old proposals.

### Reviewed plan sources

- `docs/plans/2026-03-06-guappa-master-plan.md`
- `docs/plans/2026-03-07-guappa-implementation-plan.md`
- `docs/plans/2026-03-07-guappa-purge-rust-wire-kotlin.md`
- `docs/plans/android-agent-backend-v2.md`
- `docs/plans/phase-09-testing-qa.md`
- `docs/plans/phase-11-world-wide-swarm.md`
- `docs/plans/phase-12-android-ui.md`
- `docs/plans/guappa-cosmic-v5.html`

### Reviewed code paths

- `mobile-app/android/app/src/main/java/com/guappa/app/MainApplication.kt`
- `mobile-app/android/app/src/main/java/com/guappa/app/swarm/SwarmManager.kt`
- `mobile-app/android/app/src/main/java/com/guappa/app/swarm/SwarmConnectorClient.kt`
- `mobile-app/android/app/src/main/java/com/guappa/app/swarm/SwarmIdentity.kt`
- `mobile-app/android/app/src/main/java/com/guappa/app/swarm/SwarmTaskPoller.kt`
- `mobile-app/android/app/src/main/java/com/guappa/app/swarm/SwarmTaskExecutor.kt`
- `mobile-app/src/screens/tabs/SwarmScreen.tsx`
- `mobile-app/src/native/guappaSwarm.ts`
- `mobile-app/src/screens/tabs/CommandScreen.tsx`
- `mobile-app/package.json`
- `.github/workflows/release.yml`
- legacy repo surfaces under `src/`

### Reviewed external WWSP source

- `https://github.com/Good-karma-lab/World-Wide-Swarm-Protocol`

Key connector facts verified from that repository:

- current release visible: `v0.9.1`
- local connector ports: `9370` JSON-RPC and `9371` HTTP/dashboard
- protocol bootstrap surface: connector serves `SKILL.md`
- connector is the local bridge handling identity, P2P, Kademlia, holons, voting, and security

---

## 3. Current reality audit

### 3.1 What already exists and should be completed, not re-invented

- Kotlin-native agent foundation exists in `mobile-app/android/app/src/main/java/com/guappa/app/agent/`.
- Large native tool surface exists in `mobile-app/android/app/src/main/java/com/guappa/app/tools/`.
- Memory stack exists in `mobile-app/android/app/src/main/java/com/guappa/app/memory/`.
- Proactive notifications/workers exist in `mobile-app/android/app/src/main/java/com/guappa/app/proactive/`.
- Current mobile UI redesign already exists in `mobile-app/src/screens/tabs/` and `mobile-app/src/swarm/`.
- The release workflow already builds the Android app from `mobile-app/` in `.github/workflows/release.yml`.

### 3.2 What is incomplete or misaligned right now

#### ZeroClaw legacy still exists

- Legacy Rust runtime still dominates `src/`.
- Old docs and repo structure still present under `docs/`.
- Old naming/history still influences plans and repo layout.

#### Swarm integration is incomplete and currently mismatched

- `mobile-app/src/screens/tabs/SwarmScreen.tsx` expects `NativeModules.SwarmManager`.
- `mobile-app/src/native/guappaSwarm.ts` expects `NativeModules.GuappaSwarm`.
- `mobile-app/android/app/src/main/java/com/guappa/app/MainApplication.kt` does not register a swarm package/module at all.
- `mobile-app/android/app/src/main/java/com/guappa/app/swarm/SwarmManager.kt` is not a React Native module.

#### Current swarm implementation is only a partial HTTP client, not a full WWSP-aligned integration

- `mobile-app/android/app/src/main/java/com/guappa/app/swarm/SwarmConnectorClient.kt` currently assumes simplified REST endpoints like `/api/status`, `/api/peers`, `/api/messages`, `/api/register`, and SSE `/api/events`.
- The phase 11 plans and WWSP repo describe a richer connector contract centered on JSON-RPC on `9370`, HTTP on `9371`, and connector-served `SKILL.md`.
- Current code defaults to `http://127.0.0.1:9371`, which is only one deployment mode.

#### Current identity implementation does not yet match the plan-level WWSP expectations

- `mobile-app/android/app/src/main/java/com/guappa/app/swarm/SwarmIdentity.kt` uses Android Keystore EC `secp256r1` and a short derived `did:swarm:` string.
- Phase 11 expects Ed25519-first semantics, challenge/verification flow, rotation/revocation, and stronger protocol parity.

#### Command surface still relies on mock-first behavior

- `mobile-app/src/screens/tabs/CommandScreen.tsx` initializes from `MOCK_*` data and only overlays live data if native calls succeed.

#### Policy-risky automation still exists

- Accessibility-based autonomous automation still exists in `mobile-app/android/app/src/main/java/com/guappa/app/AgentAccessibilityService.kt` and related tools.
- This conflicts with the March 2026 Android/Play direction described in the master plan and Android policy materials.

#### Test architecture is unbalanced

- Strong Maestro asset count exists in `mobile-app/.maestro/`.
- `mobile-app/package.json` does not define a test script.
- No Android unit or instrumented test tree was found under `mobile-app/android/app/src/test` or `mobile-app/android/app/src/androidTest`.
- Current CI/release is build-oriented, not full validation-oriented.

---

## 4. Product model

### 4.1 Guappa as one organism

The app must feel like one AI being, not five tools.

- `Chat` is Guappa's conversational cortex.
- `Voice` is Guappa's embodied listening/speaking presence.
- `Command` is Guappa's agency ledger: tasks, triggers, schedules, memory workload, and side effects.
- `Config` is Guappa's self-description, boundary control, capability routing, privacy policy, and repair surface.
- The ambient swarm/neural visual system is Guappa's visible mind and state language across the entire app.

### 4.2 Dedicated role of the `Swarm` screen

The `Swarm` tab is not the same thing as the ambient neural mind visualization.

The `Swarm` tab specifically represents:

- WWSP connector status
- Guappa's identity in the global swarm
- peers, reputation, tasks, holons, and events
- local connector vs remote connector configuration
- network feed and protocol-level participation

The ambient AI-mind visuals from `guappa-cosmic-v5.html` and `phase-14-swarm.md` should appear app-wide, while the `Swarm` screen remains the WWSP interface.

---

## 5. Non-negotiable constraints

### 5.1 ZeroClaw eradication

- Remove all active ZeroClaw code.
- Remove all Rust/JNI paths that only existed for the wrapped architecture.
- Remove all ZeroClaw/MobileClaw names from code, config, docs, CI, scripts, package names, assets, strings, env vars, and comments where they still imply active ownership.
- Keep historical references only if they are clearly archival and outside active runtime, build, release, and product docs.
- Add CI guardrails so banned legacy identifiers cannot be reintroduced.

### 5.2 Kotlin-native ownership

- Kotlin is the production backend for Guappa mobile.
- React Native remains the UI shell.
- Native bridges must be typed, consistently named, and eventually Codegen/TurboModule-backed where the New Architecture path is required.

### 5.3 Compliance-first automation

- AccessibilityService cannot remain the production backbone for autonomous AI control.
- Shipped automation must route through compliant abstractions:
  - AppFunctions where available
  - sanctioned Android UI automation preview paths where available
  - deterministic intent-based actions where available
  - explicit unsupported-state handling where not available
- Sensitive actions must require confirmation.
- User visibility, takeover, cancel, and live progress are required.

### 5.4 Quality bar

- Unit/integration evidence for every core subsystem.
- Emulator E2E for every user-visible feature.
- Real-device E2E for hardware-sensitive and OEM-sensitive flows.
- Benchmark and resilience evidence for long-running agent behavior.

---

## 6. March 2026 research program

Research is a delivery track with explicit outputs.

### 6.1 Required research tracks

#### A. Android agent platform and policy

Questions:

- current AppFunctions maturity and supported device/OEM matrix
- sanctioned UI automation preview constraints and rollout limits
- Android 16/17 foreground/background restrictions
- Play policy boundaries for agentic automation
- notification/live-view/takeover patterns for background actions

Outputs:

- compliance ADR
- device/OEM capability matrix
- ship-blocker list for unsupported surfaces

#### B. Provider/model ecosystem

Questions:

- March 2026 provider shortlist
- structured output reliability
- tool use quality
- multimodal quality
- streaming quality
- cost, rate limits, latency, and fallback quality

Outputs:

- provider scorecard
- routing matrix by capability
- budget policy

#### C. Local inference

Questions:

- LiteRT-LM, llama.cpp Android, ONNX Runtime Mobile, Qualcomm/MediaTek/Samsung acceleration
- RAM/thermal budgets by device class
- quantization formats and download/storage policy

Outputs:

- benchmark report
- local-model support matrix by device tier
- storage/download lifecycle spec

#### D. Voice stack

Questions:

- STT engine shortlist
- VAD shortlist
- wake word shortlist
- TTS shortlist
- Bluetooth/headset routing, echo cancellation, interruption, and offline fallback

Outputs:

- voice architecture ADR
- latency and quality targets
- device routing policy

#### E. Agent cognition and memory

Questions:

- context budget allocation
- summarization policies
- recursive decomposition and reflection loops
- long-horizon recall and memory conflict handling

Outputs:

- cognition design note
- memory evaluation dataset
- summarization quality rubric

#### F. WWSP integration

Questions:

- exact connector contract to use for launch
- local connector packaging strategy vs remote connector strategy
- JSON-RPC method parity vs mobile launch subset
- identity/recovery and secure storage requirements
- holon/reputation/task UX exposure level for mobile users

Outputs:

- WWSP integration ADR
- launch-minimum method list
- connector mode matrix: local, LAN, remote, future embedded

#### G. Mobile design system

Questions:

- how to apply `guappa-cosmic-v5.html` app-wide without sacrificing readability
- how to distinguish Guappa's local mind from WWSP network state
- motion budgets and degraded visual modes

Outputs:

- design system brief
- interaction grammar
- reduced-motion and low-end fallback spec

#### H. Test architecture

Questions:

- Maestro organization and smoke/regression/resilience split
- Robolectric and instrumented scope
- Firebase Test Lab matrix
- real-device hardware lab procedures

Outputs:

- test architecture ADR
- device matrix
- release-gate checklist

### 6.2 Research deliverables that block implementation

The following ADRs must exist before the corresponding implementation phase is closed:

- runtime ownership ADR
- ZeroClaw purge ADR
- automation compliance ADR
- provider routing ADR
- voice stack ADR
- WWSP integration ADR
- test architecture ADR

---

## 7. Execution roadmap

## Phase 0 - Audit freeze and execution board

**Goal:** convert plan sprawl into one execution system.

Tasks:

- Freeze this document as the master delivery plan.
- Build a plan-to-code traceability table for every old phase document.
- Tag every requirement as one of: `implemented`, `partial`, `missing`, `obsolete`, `replaced`.
- Create delivery owners for: runtime, tools, providers, memory, voice, WWSP, UI, tests, docs.
- Create risk tags: `policy`, `security`, `hardware`, `performance`, `release`, `migration`.

Deliverables:

- execution board
- phase dependency graph
- repo audit log

Exit criteria:

- every tracked requirement from old plans maps to a current implementation task or an explicit deprecation decision

## Phase 1 - ZeroClaw purge map and repo cleanup

**Goal:** remove active legacy ownership cleanly and irreversibly.

### 1.1 Inventory and classification

- search for all `ZeroClaw`, `MobileClaw`, `zeroclaw`, `mobileclaw`, old package IDs, old bridge names, old configs, old scripts
- classify each hit as:
  - runtime code
  - config/build
  - docs
  - tests
  - CI/CD
  - assets/strings
  - historical archive

### 1.2 Code/runtime removal

- remove legacy Rust `src/` from active Guappa ownership scope
- remove Cargo-based mobile runtime ownership
- remove JNI bridge leftovers and `libzeroclaw` assumptions
- remove old bridge modules/packages/services if still present in mobile code

### 1.3 Config/build removal

- remove obsolete configs, schemas, sample files, migration helpers
- remove old environment variable names and build hooks
- remove scripts that bootstrap or release obsolete runtime targets

### 1.4 Docs removal/replacement

- replace legacy root README and active docs entry points with Guappa-native docs
- retire or archive obsolete docs under a clearly non-active history area if needed
- remove stale navigation that still leads engineers into ZeroClaw paths

### 1.5 CI/CD cleanup

- remove workflows/jobs that lint, test, build, or package obsolete ZeroClaw targets
- keep only Guappa-active mobile pipelines and any intentionally retained separate products

### 1.6 Guardrails

- add banned-identifier CI checks for legacy names in active code/docs/build surfaces
- add docs/link audit after deletion
- add smoke build after purge

Exit criteria:

- no active runtime/build/doc/release path depends on ZeroClaw
- banned-identifier CI gate exists
- Guappa builds and installs without legacy dependencies

## Phase 2 - Native bridge and protocol alignment

**Goal:** remove current bridge drift before adding more features.

### 2.1 Swarm bridge alignment

Current problem:

- JS expects both `SwarmManager` and `GuappaSwarm`
- Android registers neither

Required action:

- choose one canonical bridge name, preferably `GuappaSwarm`
- implement actual React Native package/module for swarm
- register it in `MainApplication.kt`
- make `SwarmScreen.tsx` and `guappaSwarm.ts` consume the same surface
- define the typed contract once and reuse it everywhere

### 2.2 Config bridge alignment

- inventory current config bridge methods and actual call sites
- replace ad hoc patterns with one typed config surface
- define event emission for config changes and state refresh

### 2.3 Agent/proactive/command bridge alignment

- inventory all native modules needed by `Command`, `Chat`, `Voice`, and `Config`
- remove dead or duplicate interfaces
- ensure no UI screen depends on absent native modules or mocks for core state

Exit criteria:

- all active JS-native interfaces are implemented, registered, typed, and used consistently

## Phase 3 - Compliance-first action layer

**Goal:** make Guappa's ability to act policy-safe and shippable.

### 3.1 Replace accessibility-first autonomous architecture

- introduce `DeviceActionGateway` abstraction with backends:
  - `AppFunctionsBackend`
  - `UiAutomationPreviewBackend`
  - `IntentBackend`
  - `UnsupportedBackend`
- route every high-level action through capability discovery and policy checks

### 3.2 Action categories

- app-to-app structured actions
- navigation/system intents
- media/camera/file/share actions
- controlled form-fill/navigation actions when sanctioned preview path exists

### 3.3 Consent and safety

- define approval policy per tool/action
- define sensitive-action categories: payment, messages, account changes, purchases, system settings, destructive file actions
- add explicit confirmation flows, cancel/takeover, and action history

### 3.4 Observability

- every action should emit:
  - request source
  - backend selected
  - permission state
  - confirmation state
  - outcome
  - user-visible log line

Exit criteria:

- no production-critical autonomous action path requires `AccessibilityService`
- unsupported devices fail explicitly and safely

## Phase 4 - Provider router completion

**Goal:** finish the provider stack described by the plans.

### 4.1 Provider surface

- unify cloud and local provider contract
- support capability-scoped routing for:
  - text chat
  - reasoning
  - code
  - vision
  - image generation
  - video generation if shipped
  - STT
  - TTS
  - embeddings
  - swarm-director micro-model tasks

### 4.2 Dynamic discovery

- model fetch from provider APIs where available
- metadata enrichment cache for capability/context/pricing when APIs are incomplete
- cache TTL and stale-cache behavior
- refresh on app start, provider change, and manual request

### 4.3 Routing policy

- primary provider per capability
- fallback chain per capability
- auto mode with health + cost + latency weighting
- hard errors for unsupported capability paths
- offline/local fallback when allowed by policy

### 4.4 Accounting

- token counting families
- cost estimation
- budget warning thresholds
- quota exhaustion handling

### 4.5 Local inference track

- device capability probing
- model download and storage policy
- load/unload lifecycle
- thermal/memory fallback behavior

Exit criteria:

- Config can select providers/models by capability
- next request uses updated routing without restart
- provider failures degrade gracefully

## Phase 5 - Tool engine completion

**Goal:** finish the native capability surface with safety and testability.

### 5.1 Tool contract

- stable JSON-schema-like declaration
- availability checks
- permission checks
- risk level
- approval requirement
- structured result and error contract

### 5.2 Mandatory tool groups

- web: `web_fetch`, `web_search`, `web_scrape`, `browser_session`
- device: notifications, alarms, timers, media, files, clipboard, sensors, camera, location, battery
- app actions: launch, deep links, intents, app status
- communication: SMS/email/share/channel reply surfaces as permitted
- AI tools: translate, image analyze, TTS, STT, memory store/search/forget

### 5.3 Tool governance

- no hidden side effects
- audit log for every side-effecting tool call
- rate limits and cooldowns where needed
- dry-run mode for sensitive tools where possible

### 5.4 Tool testing

- unit tests for argument validation and result shaping
- integration tests for permission denial and success paths
- E2E tests for each shipped side-effect category

Exit criteria:

- all shipped tools declare risk, approval, and observability behavior

## Phase 6 - Voice and presence completion

**Goal:** ship Guappa as a stable voice-first entity.

### 6.1 Engine selection

- choose STT, TTS, VAD, and wake-word engines from March 2026 research
- define online/offline/fallback matrix
- define language support and voice persona policy

### 6.2 Native audio pipeline

- move latency-critical pieces out of JS where needed
- support AudioRecord/AudioTrack or equivalent native routing
- handle audio focus, ducking, call interruption, headset/Bluetooth handoff, and background lifecycle

### 6.3 Conversation state machine

- states: idle, listening, transcribing, reasoning, speaking, interrupted, muted, error
- sync these states to chat, ambient visuals, notifications, and command ledger

### 6.4 Voice UX

- full-screen presence mode
- barge-in handling
- interim transcript rendering
- speaking indicator tied to audio output amplitude
- privacy/mic disabled indicators

### 6.5 Acceptance thresholds

- STT first partial latency target
- TTS first audio byte target
- wake-word false positive/false negative targets
- session stability targets for long voice sessions

Exit criteria:

- voice survives app backgrounding, interruptions, and long sessions

## Phase 7 - Memory and cognition completion

**Goal:** finish Guappa's long-horizon mind.

### 7.1 Five-tier memory implementation contract

- working memory
- short-term memory
- long-term facts
- episodic memory
- semantic/vector memory

### 7.2 Retention and consolidation

- retention rules by memory type
- promotion/demotion rules
- dedupe/conflict resolution
- scheduled consolidation
- safe export/import/wipe

### 7.3 Context management

- token budget allocator
- compaction policy
- recursive summarization
- map-reduce import for long histories

### 7.4 Retrieval quality

- benchmark dataset
- relevance scoring evaluation
- hallucination/contradiction spot checks

### 7.5 User control

- inspect memory summaries
- forget specific items
- export memory
- secure reset

Exit criteria:

- Guappa remembers correctly across restarts and long sessions with measurable retrieval quality

## Phase 8 - Proactive engine and channel hub

**Goal:** complete outward communication and autonomous follow-up.

### 8.1 Proactive engine

- built-in triggers
- custom triggers/rules
- schedule support
- quiet hours / DND policy
- dedupe/history
- escalation and cooldowns

### 8.2 Notification behavior

- channel definitions
- inline reply
- approve/deny actions
- snooze
- open-chat continuity
- morning briefing / summary surfaces

### 8.3 Channel launch scope

Define `launch-minimum`, `post-launch`, and `deferred`.

For each shipped channel specify:

- auth/setup
- health check
- inbound mapping
- outbound formatting
- reconnect behavior
- media support
- allowlist/safety rules

### 8.4 Native/mobile boundaries

- decide which channels are local-mobile only vs server-assisted
- avoid pretending unsupported channels are complete

Exit criteria:

- proactive events and shipped channels are live, observable, and recoverable

## Phase 9 - Live config and hot reload

**Goal:** make `Config` truly part of Guappa, not a restart screen.

### 9.1 Config store

- single canonical persisted store
- schema versioning and migrations
- validation and defaults

### 9.2 Hot-swap domains

- provider/model capability routing
- tool enablement
- voice engine selection
- proactive rules
- channel state
- swarm settings
- memory policies where safe

### 9.3 Bridge architecture

- typed native interface
- event emission for live changes
- UI refresh without full app restart

### 9.4 Config UX

- capability-first provider selection
- permission and health views
- privacy boundaries
- debug/export logs and repair tools

Exit criteria:

- changed config applies on the next relevant operation or immediately, according to documented semantics

## Phase 10 - WWSP connector integration and `Swarm` screen completion

**Goal:** integrate Guappa properly with the World Wide Swarm Protocol connector.

### 10.1 Required product interpretation

The `Swarm` screen is the WWSP control and status surface.

It is responsible for:

- connector mode and URL/local node status
- Guappa identity and reputation
- peer list and peer health
- message feed and swarm events
- task offers and delegated work
- holon membership and participation state
- network and uptime metrics

### 10.2 Connector modes

Launch must explicitly support named modes:

- `Local connector`: connector reachable on the device or local host boundary
- `Remote connector`: remote HTTP endpoint over LAN/VPN/server
- `Disabled`: zero swarm traffic, zero background polling

Future mode:

- `Embedded connector`: packaged or sidecar local connector on Android, only when technically and operationally justified

### 10.3 WWSP contract alignment

The integration must stop being an ad hoc REST approximation and become an intentional WWSP client.

Tasks:

- define launch-minimum method surface from WWSP
- define whether mobile uses:
  - JSON-RPC directly on `9370`
  - HTTP plus event stream on `9371`
  - hybrid transport
- fetch and pin connector protocol/version metadata
- add protocol compatibility checks
- handle connector unavailability, version mismatch, auth/registration issues, and reconnect logic

### 10.4 Identity and registration

Tasks:

- align mobile identity strategy with WWSP expectations
- decide authoritative key format and compatibility behavior
- implement registration challenge flow, verification, and failure UX
- implement key rotation/revocation/recovery roadmap, even if parts are deferred
- store identity and connector state securely on device

### 10.5 Task and holon execution

Tasks:

- route swarm tasks into agent sessions cleanly
- enforce capability matching and max concurrency
- support accept/reject/report lifecycle
- track reputation-affecting outcomes
- define what mobile supports for holons at launch:
  - observe only
  - join and vote
  - propose and critique

### 10.6 Current code gaps that must be explicitly fixed

- `SwarmScreen.tsx` / `guappaSwarm.ts` bridge naming mismatch
- missing native module registration in `MainApplication.kt`
- simplified connector endpoints in `SwarmConnectorClient.kt`
- identity mismatch in `SwarmIdentity.kt`
- incomplete event/task/holon contract in current swarm classes

### 10.7 `Swarm` screen acceptance checklist

- connect/disconnect toggle
- connector mode selector
- connector URL editor and health state
- identity create/view/reset flow
- peer list with status and capability chips
- reputation badge and score history surface
- event feed with message/task/holon/reputation filters
- active tasks and actions
- stats card: uptime, peers, traffic, tasks, holons
- clear disabled-state UX when swarm is turned off

Exit criteria:

- `Swarm` tab is a real WWSP surface, not a partially mocked dashboard

## Phase 11 - AI organism redesign and app-wide visual system

**Goal:** finish the product-level redesign according to the Guappa philosophy.

### 11.1 App-wide design contract

- Guappa feels alive at rest
- every screen uses the same state language
- ambient neural visuals communicate present/listening/reasoning/speaking/alert/sleeping
- glass/cosmic design remains readable and fast

### 11.2 Distinguish local mind vs global swarm

- ambient neural field = Guappa's local living mind
- `Swarm` screen = WWSP network participation
- do not collapse these two ideas into one ambiguous screen

### 11.3 Per-screen requirements

#### Chat

- streaming conversation
- tool traces
- memory references
- proactive initiations
- voice handoff
- emotional tone/state continuity

#### Voice

- full-screen presence mode
- orb/neural state tied to real audio and AI state
- transcript and interruption handling
- connection and task visibility

#### Command

- remove mock-first boot path
- show active tasks, schedules, triggers, sessions, and memory state from native sources
- expose side effects and recent actions

#### Swarm

- WWSP connector surface as defined in Phase 10

#### Config

- capability-first routing
- privacy boundaries
- permissions and health
- swarm/voice/provider/memory settings
- repair/export/reset flows

### 11.4 Shared components

- glass cards, inputs, buttons, toggles, chips, pills
- status indicators
- ambient background system
- state ring/orb/neural overlays
- reduced-motion fallbacks

### 11.5 Performance budgets

- preserve 60fps where possible
- define degraded visual modes for weak devices
- drop expensive effects before dropping usability

Exit criteria:

- the product feels like one AI organism with a dedicated WWSP screen, not a collection of generic tabs

## Phase 12 - Test architecture and validation

**Goal:** prove the app works in reality.

### 12.1 Test pyramid

- TS/JS unit tests for state, hooks, adapters, formatting, reducers
- Kotlin unit tests for agent, providers, tools, memory, proactive, config, swarm
- Robolectric for service, alarms, notifications, permission-dependent logic, DB migrations
- instrumented Android tests for native bridges, service lifecycle, notifications, boot flows, storage, permission UX
- Maestro smoke/regression/resilience/hardware suites
- cloud device matrix via Firebase Test Lab or equivalent
- manual real-device signoff for hardware/OEM/policy-sensitive flows

### 12.2 Maestro organization

Required suites:

- smoke: install, onboarding, chat, voice, command, swarm, config
- regression: provider switch, long chat, memory, proactive, channels, local model flows
- resilience: app kill, reboot, network loss, provider outage, permission revoke, battery saver, migration
- hardware: camera, mic, speaker, vibration, sensors, alarms, notifications, Bluetooth/headset

### 12.3 Real-device matrix

- recent Pixel
- recent Samsung flagship
- one mid-range Android phone
- one constrained-RAM or battery-sensitive device

### 12.4 Benchmark gates

- cold start
- first token latency
- STT partial latency
- TTS first audio byte
- memory retrieval latency
- long session stability
- battery drain and ANR/jank checks

### 12.5 Subsystem-specific must-pass validations

#### Automation

- unsupported-device fallback
- sensitive-action confirmation
- live-view/takeover flow

#### Providers

- dynamic model refresh
- fallback route correctness
- quota exhaustion and network failure

#### Voice

- Bluetooth routing
- interruption
- background survival

#### Memory

- retention across restart
- export/import integrity
- forget/reset behavior

#### WWSP

- identity creation and reconnect
- connector down/version mismatch
- task accept/reject/report
- disabled mode produces no background poll traffic

Exit criteria:

- release gate is automated as far as practical and manually verified where hardware/policy requires it

## Phase 13 - Documentation, operations, and release readiness

**Goal:** make Guappa operable by engineers and understandable to users.

### 13.1 Docs replacement

- new root README
- mobile architecture docs
- runtime docs
- permissions/privacy docs
- provider and model routing docs
- WWSP connector setup docs
- voice and hardware support docs
- testing docs
- troubleshooting and recovery docs

### 13.2 Internal runbooks

- provider outage
- stuck task
- notification failure
- voice engine failure
- connector unreachable
- swarm identity repair
- local model corruption or storage pressure

### 13.3 Release operations

- release checklist
- rollback plan
- telemetry/log capture guidance
- crash triage flow
- support boundaries by device class

Exit criteria:

- a new engineer can build, run, test, debug, and release Guappa without reading the old plan sprawl

---

## 8. Detailed subsystem completion checklists

## 8.1 ZeroClaw purge checklist

### Code and runtime

- remove legacy Rust runtime from active product ownership
- remove JNI wrappers and obsolete native libs
- remove old daemon/service/module naming where still present

### Config and build

- remove old config names and schema references
- remove old env vars and scripts
- remove old package IDs and artifact names

### Docs and branding

- remove or archive obsolete docs
- rewrite active docs as Guappa-only
- remove stale screenshots and product terminology

### CI/CD

- remove obsolete jobs
- keep Guappa mobile build/test/release only
- add banned-name grep checks

### Validation

- repo-wide banned-name search
- build dry run
- docs navigation audit
- install/run smoke test

## 8.2 WWSP swarm checklist

### Bridge

- one canonical native module name
- package registration in Android app
- TS type contract shared with UI

### Transport

- decide launch transport mode
- connector health/version checks
- reconnect/backoff policy
- disabled mode with zero traffic

### Identity

- secure key storage
- registration flow
- display name update
- recovery/rotation roadmap

### Network participation

- peer inventory
- event feed
- task accept/reject/report
- holon support scope
- reputation sync

### UI

- status cards
- feed filters
- peer list
- tasks and actions
- connector configuration

## 8.3 Command checklist

- remove mock boot defaults for core state
- show native task/job/trigger/session/memory data
- define empty/loading/error states
- expose recent side effects and task details

## 8.4 Config checklist

- provider/model routing by capability
- voice settings
- swarm settings
- privacy and permissions
- debug/export/reset tools
- hot-reload semantics documented in UI

## 8.5 Voice checklist

- stable wake/listen/reason/speak loop
- transcript persistence
- audio route awareness
- background behavior
- offline/degraded mode

## 8.6 Memory checklist

- five tiers
- summarization
- retrieval evaluation
- export/import
- forget/reset

## 8.7 Testing checklist

- unit tests added to JS and Kotlin layers
- Android test directories exist and run
- Maestro suites categorized and runnable
- cloud device matrix configured
- manual hardware scripts documented

---

## 9. Done means done

Guappa is complete only when all of the following are true:

- ZeroClaw has been removed from active code, configs, docs, CI, scripts, and product identity.
- Kotlin-native Android is the unambiguous mobile runtime.
- no major screen depends on dead bridges or mock-first state for core behavior.
- automation is policy-compliant for the intended distribution path.
- providers, tools, memory, proactive, channels, voice, and config are complete enough for real use.
- the `Swarm` screen is a real WWSP connector interface, not a partial placeholder.
- Guappa's ambient visual system expresses a living AI across the app, without confusing that with WWSP network state.
- emulator E2E, real-device E2E, and hardware-sensitive validations pass.
- docs match the actual shipped product.

---

## 10. Recommended implementation order

1. freeze audit and build the execution board
2. perform ZeroClaw purge mapping and active-surface deletion
3. align native bridges and remove dead interfaces
4. replace accessibility-first automation architecture
5. finish provider router and tool engine safety contracts
6. finish memory, proactive, channels, and live config
7. complete WWSP connector integration and `Swarm` screen
8. complete voice/presence architecture
9. finish AI-organism redesign across all screens
10. build the full test matrix and device validation pipeline
11. rewrite docs and finalize release readiness

---

## 11. Immediate next actions

- create the repo-wide ZeroClaw deletion inventory
- create the swarm bridge/protocol alignment task list from the current code mismatch
- create ADRs for runtime ownership, ZeroClaw purge, automation compliance, WWSP integration, voice stack, and test architecture
- convert this plan into milestone tickets with owners, dependencies, and acceptance tests
