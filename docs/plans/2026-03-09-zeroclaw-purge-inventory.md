# ZeroClaw Purge Inventory

**Date**: 2026-03-09
**Status**: Active implementation artifact
**Backlog milestone**: `B1 - ZeroClaw purge inventory`
**Source backlog**: `docs/plans/2026-03-09-guappa-implementation-backlog.md`

This document is the first execution artifact for the backlog. It identifies the currently visible ZeroClaw and MobileClaw legacy surfaces that must be removed, replaced, or explicitly archived before Guappa can be considered a Kotlin-native product.

---

## 1. Audit method

The inventory was built from current repository searches for these legacy identifiers:

- `ZeroClaw`
- `MobileClaw`
- `zeroclaw`
- `mobileclaw`
- `com.mobileclaw.app`
- `libzeroclaw`
- `ZeroClawDaemon`
- `zeroClawDaemon`

The current audit found legacy matches in **332 files outside `.git/` and excluding `docs/plans/**`**.

Top-level spread from the current search:

| Area | Files with legacy matches | Notes |
|---|---:|---|
| `docs/` | 128 | Active docs are still heavily legacy-branded |
| `src/` | 88 | Legacy Rust runtime remains the largest active code surface |
| `firmware/` | 17 | Legacy hardware/bridge naming remains |
| `tests/` | 15 | Rust tests still import `zeroclaw::*` |
| `web/` | 12 | Legacy web/dashboard surfaces remain |
| `python/` | 11 | `zeroclaw-tools` and related docs remain |
| `crates/` | 10 | Legacy Rust workspace subcrates remain |
| `scripts/` | 8 | Installer/bootstrap/E2E scripts still point to old architecture |
| `.github/` | 5 | Active workflows still include Rust-era repo assumptions |
| `mobile-app/` | 1 | Main app code is mostly renamed, but active support/test surfaces still leak old IDs elsewhere |

---

## 2. Classification model

Each hit belongs to one of these purge classes:

| Class | Meaning | Action |
|---|---|---|
| `delete-now` | Obsolete active surface with no place in Guappa | Delete |
| `replace-now` | Active surface must remain but be rewritten for Guappa | Rewrite |
| `rename-now` | Active asset or identifier should remain but legacy naming must change | Rename/update |
| `archive-only` | Historical material that may be preserved outside active product paths | Archive or move out of active navigation |
| `decision-needed` | Removal depends on explicit product boundary decision | Resolve in architecture/doc ADR |

---

## 3. Highest-priority active legacy surfaces

These are the most important blockers because they still affect active product identity, build flow, or engineering workflow.

### 3.1 Root product identity

| Path | Evidence | Class | Why it matters |
|---|---|---|---|
| `README.md:3` | `MobileClaw turns an old Android phone...` | `replace-now` | Root repo still presents old product identity |
| `README.md:12` | `MobileClaw uses ZeroClaw as its runtime foundation` | `replace-now` | Contradicts Kotlin-native direction |
| `NOTICE:1` | `ZeroClaw` | `decision-needed` | Legal/notice file must be reviewed, not blindly deleted |
| `Cargo.toml:6` | package name `zeroclaw` | `delete-now` | Core sign that repo still treats Rust runtime as active product |
| `ZEROCLAW_FEATURE_COMPARISON.md:1` | comparison report centered on old architecture | `archive-only` | Historical analysis, not active product doc |

### 3.2 Active install/bootstrap flow

| Path | Evidence | Class | Why it matters |
|---|---|---|---|
| `bootstrap.sh:5` | delegates to `zeroclaw_install.sh` | `delete-now` |
| `scripts/bootstrap.sh:21` | `./zeroclaw_install.sh [options]` | `delete-now` |
| `scripts/bootstrap.sh:724` | repo URL `zeroclaw-labs/zeroclaw.git` | `delete-now` |
| `scripts/install.sh:5` | installer path `zeroclaw_install.sh` | `delete-now` |
| `scripts/install.sh:7` | repo URL `zeroclaw-labs/zeroclaw.git` | `delete-now` |

### 3.3 Active Android/E2E support surfaces

| Path | Evidence | Class | Why it matters |
|---|---|---|---|
| `scripts/android/run_e2e.sh:36` | starts `com.mobileclaw.app` | `replace-now` | Active E2E script points to wrong package |
| `scripts/android/run_e2e.sh:40` | sends `ZeroClaw E2E inbound SMS` | `replace-now` | Active test data still legacy |
| `e2e_test_scheduling.sh:8` | `appId: com.mobileclaw.app` | `replace-now` | Old Maestro/E2E artifact still active |
| `.claude/skills/test-mobileclaw/SKILL.md:8` | `MobileClaw Android Testing Protocol` | `replace-now` | Local testing skill still assumes Rust daemon architecture |
| `.claude/skills/test-mobileclaw/SKILL.md:15` | `must go through the MobileClaw embedded agent daemon (Rust runtime)` | `replace-now` | Directly contradicts target architecture |

### 3.4 Active engineering docs that still point to old repo/product

| Path | Evidence | Class | Why it matters |
|---|---|---|---|
| `docs/getting-started/installation.md:45` | clone `ArtificialGuardianAngel/mobileclaw.git` | `replace-now` | Setup docs still point to old repo name |
| `docs/getting-started/installation.md:103` | `cd mobileclaw` | `replace-now` | Old workspace naming remains in active docs |
| `docs/android-setup.md:3` | `ZeroClaw provides prebuilt binaries for Android devices` | `delete-now` or `archive-only` | This is explicitly old-distribution guidance |
| `docs/config-reference.md:1` | `ZeroClaw Config Reference` | `replace-now` | Active runtime/config doc is still old product doc |
| `docs/channels-reference.md:3` | canonical reference for channels in ZeroClaw | `replace-now` | Active channel docs still legacy |

---

## 4. Legacy runtime and code surfaces

These are the main old-architecture surfaces the product no longer wants to own.

### 4.1 Root Rust runtime

Representative evidence:

- `Cargo.toml:6` - crate name `zeroclaw`
- `src/jni_bridge.rs:57` - `Java_com_mobileclaw_app_ZeroClawBackend_startAgent`
- `src/mobile_bridge.rs:98` - `mobileclaw_chat_json`
- `src/gateway/mod.rs:635` - `ZeroClaw Gateway listening`
- `src/channels/mod.rs:2491` - `You are ZeroClaw...`
- `src/onboard/wizard.rs:81` - `Welcome to ZeroClaw`
- `src/doctor/mod.rs:96` - `ZeroClaw Doctor`

Classification: `delete-now`

Why:

- This is the old runtime that the user explicitly wants removed.
- It still dominates the repository's code and docs surface.
- It continues to leak old product language, old config locations, old services, old metrics, and old onboarding assumptions.

### 4.2 Rust workspace and support packages

Representative evidence:

- `crates/` contains legacy Rust subcrates with identifier matches
- `tests/` imports `zeroclaw::*`
- `python/README.md:1` - `zeroclaw-tools`
- `web/src/pages/AgentChat.tsx:167` - `ZeroClaw Agent`
- `Dockerfile:56` - copies `/app/zeroclaw`

Classification: `delete-now` or `decision-needed`

Decision note:

- If these are not part of a separately maintained non-Guappa product, they should be removed.
- If they are kept as a separate product, they must be moved out of Guappa's active repo identity and release path.

### 4.3 Firmware and peripheral naming

Representative evidence:

- `src/peripherals/uno_q_setup.rs:6` - `zeroclaw-uno-q-bridge`
- `src/peripherals/uno_q_setup.rs:77` - `ZeroClaw Bridge app started`
- `firmware/zeroclaw-uno-q-bridge/**` implied by include paths

Classification: `decision-needed`

Why:

- Hardware support may remain conceptually useful, but current names are legacy.
- If hardware support stays in Guappa, these surfaces need Guappa naming and product framing.

---

## 5. Legacy config, storage, and observability identifiers

These identifiers are especially important because they can leak into user state, metrics, file paths, and compatibility behavior.

Representative evidence:

- `src/config/schema.rs:64` - default path `~/.zeroclaw/config.toml`
- `src/config/schema.rs:1692` - default collection `zeroclaw_memories`
- `src/config/schema.rs:1883` - default OTel service `zeroclaw`
- `src/config/schema.rs:3578` - estop state `~/.zeroclaw/estop-state.json`
- `src/observability/prometheus.rs:36` - metrics like `zeroclaw_agent_starts_total`
- `src/observability/otel.rs:84` - meter `zeroclaw`
- `Dockerfile:94` - `ZEROCLAW_WORKSPACE=/zeroclaw-data/workspace`

Classification: `delete-now`

Why:

- These identifiers keep the old runtime contract alive.
- They create migration confusion and invisible brand/runtime leakage even if UI text is renamed.

---

## 6. Legacy docs and navigation surfaces

This is the largest visible legacy area.

Representative active-doc evidence:

- `docs/docs-inventory.md:1` - `ZeroClaw Documentation Inventory`
- `docs/config-reference.md:1` - `ZeroClaw Config Reference`
- `docs/channels-reference.md:3` - `ZeroClaw`
- `docs/nextcloud-talk-setup.md:3` - `ZeroClaw`
- `docs/android-setup.md:3` - `ZeroClaw provides prebuilt binaries`
- `README.md:3` - `MobileClaw`

Classification:

- active entry/docs: `replace-now`
- clearly historical comparison material: `archive-only`

Why:

- A new engineer or user still lands in a ZeroClaw/MobileClaw repo story.
- This directly blocks the Guappa-only product identity requirement.

---

## 7. CI/CD and workflow surfaces

The release workflow is already mobile-app-centric, but the repo still carries Rust-era CI and tooling assumptions.

Representative evidence:

- `scripts/ci/rust_quality_gate.sh:10` - `cargo fmt`
- `scripts/ci/rust_quality_gate.sh:14` - `cargo clippy`
- `scripts/bootstrap.sh` - Rust install/build/install flow
- `scripts/install.sh` - ZeroClaw installer wrapper
- `scripts/release/cut_release_tag.sh:73` - release message `zeroclaw $TAG`

Classification:

- Rust-only mobile-obsolete tooling: `delete-now`
- generic repo automation that remains useful but mentions legacy names: `replace-now`

---

## 8. Mobile-app-specific residual issues

The main app has been renamed more thoroughly than the rest of the repo, but residual legacy surfaces still matter.

Current evidence found outside app source proper:

- `scripts/android/run_e2e.sh:36`
- `e2e_test_scheduling.sh:8`
- `.claude/skills/test-mobileclaw/SKILL.md:63`

Classification: `replace-now`

Why:

- These files still target `com.mobileclaw.app` and the Rust daemon architecture.
- They will break or mislead validation work during backlog execution.

---

## 9. Archive-only candidates

These should not remain in active navigation, but they may be preserved if explicitly moved into a historical/archive area.

- `ZEROCLAW_FEATURE_COMPARISON.md`
- old implementation plan docs describing the wrapped architecture
- old migration notes that are useful only for historical context

Condition:

- archived files must not be linked from active README, docs hubs, onboarding, CI, or release workflows

---

## 10. Banned identifier list for CI

Initial banned list for active Guappa surfaces:

- `ZeroClaw`
- `MobileClaw`
- `zeroclaw`
- `mobileclaw`
- `com.mobileclaw.app`
- `ZeroClawDaemon`
- `zeroClawDaemon`
- `libzeroclaw`
- `zeroclaw_install.sh`
- `ZEROCLAW_WORKSPACE`

Allowlist exceptions to review before enforcement:

- archival docs directory if one is created
- third-party attributions where legally required
- migration notes that are explicitly marked historical and excluded from active product checks

---

## 11. Delete order

Recommended order so the repo stays understandable while being cleaned:

1. **active docs and setup surfaces**
   - root `README.md`
   - install/setup/testing docs
   - testing skill and E2E helper scripts
2. **active validation/support surfaces**
   - package IDs in scripts
   - old app/test IDs and strings
3. **active CI/install/bootstrap surfaces**
   - old installer/bootstrap flows
   - Rust-era mobile helper scripts
4. **runtime/config leftovers**
   - Cargo/JNI/root runtime ownership
   - old observability/config identifiers
5. **archive decision surfaces**
   - comparison docs
   - old migration notes
6. **guardrails**
   - banned-name CI rule
   - docs navigation audit
   - build/install smoke test

---

## 12. Blockers and dependencies

### Immediate blockers

- Root README and active docs still describe MobileClaw/ZeroClaw.
- Testing helpers still assume `com.mobileclaw.app` and embedded Rust daemon flows.
- Root build/install scripts still assume Rust/ZeroClaw ownership.

### Dependency-sensitive removals

- Deleting `src/` and Cargo surfaces should happen only after confirming they are fully out of Guappa's active release/build path.
- Legal or attribution files such as `NOTICE` require review, not blind deletion.
- Hardware/firmware surfaces need a keep-or-drop decision before mass rename or delete.

---

## 13. Recommended next implementation steps

1. Rewrite `README.md` as Guappa-only.
2. Fix active testing surfaces that still target `com.mobileclaw.app` and Rust daemon assumptions.
3. Remove or replace `bootstrap.sh`, `scripts/bootstrap.sh`, and `scripts/install.sh`.
4. Add banned-name CI enforcement with an archive allowlist.
5. Decide the fate of root Rust runtime, `crates/`, `python/`, `web/`, and hardware surfaces.
