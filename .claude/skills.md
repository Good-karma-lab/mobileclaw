# MobileClaw Android Testing Protocol for Claude Code

This document defines the correct procedure for building, deploying, and
testing the MobileClaw Android app. Follow this exactly — shortcuts lead to
silent failures that look like passes in Maestro but are broken in reality.

---

## 1. Full Build Pipeline

Every time Rust source changes, run all four steps. Skip any step only if you
are certain nothing upstream changed.

```bash
# Step 1: Compile Rust JNI library (cross-compiles for ARM64 Android)
bash /Users/aostapenko/Work/mobileclaw/build_android_jni.sh

# Step 2: Bundle the React Native JS (must be in mobile-app directory)
cd /Users/aostapenko/Work/mobileclaw/mobile-app
npx expo export:embed \
  --platform android \
  --bundle-output android/app/src/main/assets/index.android.bundle \
  --assets-dest android/app/src/main/res \
  --dev false

# Step 3: Build APK (must be in android directory)
cd /Users/aostapenko/Work/mobileclaw/mobile-app/android
./gradlew assembleDebug

# Step 4: Install on emulator
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

If only JS/React Native changed (no Rust), skip Steps 1 and start from Step 2.
If only Android Kotlin changed, skip Step 1 and Step 2 (run `./gradlew assembleDebug` directly).

---

## 2. Emulator Reference

| Device   | Emulator ID     | ABI   | Use for                     |
|----------|-----------------|-------|-----------------------------|
| Phone    | emulator-5554   | arm64 | All phone Maestro tests     |
| AAOS Car | emulator-5556   | arm64 | Car variant tests (note: Maestro port 7001 may not be exposed — infra issue) |

Check connected emulators:
```bash
~/Library/Android/sdk/platform-tools/adb devices
```

---

## 3. Maestro Tests

### Running a test
```bash
maestro test --device emulator-5554 \
  /Users/aostapenko/Work/mobileclaw/mobile-app/.maestro/test_scenario_memory_persistence.yaml
```

### Test scenarios (phone variants)
| File | What it tests |
|------|---------------|
| `test_scenario_incoming_call_telegram.yaml` | Register incoming call hook for wife's number, send via Telegram |
| `test_scenario_twitter_reply.yaml` | Open Twitter and reply to recent posts |
| `test_scenario_geofence_sms.yaml` | Create home-arrival SMS trigger with geofence |
| `test_scenario_memory_persistence.yaml` | Agent remembers name/wife across messages |

Car variants are the same with `_car` suffix and an extra `hideKeyboard` step.

### CRITICAL: Always read the agent response in chat after a test

**Maestro PASS does not mean correct behavior.** Hard assertions only check
for absence of error text. Optional assertions only warn, never fail. After
every Maestro run, take a screenshot and read what the agent actually said:

```bash
# Take screenshot
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  exec-out screencap -p > /tmp/test_result.png
open /tmp/test_result.png
```

Or use the Claude-in-Chrome browser automation tools to view the emulator
screen if Android Studio is open.

Look for these patterns in the agent response:
- ✅ **Good**: agent acknowledges the task, mentions specific values from the prompt, reports action taken
- ❌ **Bad**: "capability disabled", "not available", "I don't have a tool for that", "Gateway error"
- ⚠️ **Investigate**: agent gives a generic response without mentioning task-specific values

### Timing behavior

The agent uses the minimax model via OpenRouter which can take 3–5 minutes
for complex tool-using turns. The app retries once after 180 s timeout. Maestro
`waitForAnimationToEnd: timeout: 360000` (6 min) covers this, but optional
assertions often warn because the response arrives after the animation check.

This is expected. The PASS is real; the warning is a timing artifact.

---

## 4. E2E Test Scripts (ADB Call Simulation)

These scripts simulate real Android events (incoming calls, SMS) and verify
the agent's response end-to-end via Telegram.

### Prerequisites
```bash
export ZEROCLAW_TG_BOT_TOKEN="<bot token>"       # Telegram bot token
export ZEROCLAW_TEST_CALLER_NUMBER="+341234567890" # Number that triggers the hook
export ZEROCLAW_EMULATOR="emulator-5554"           # Target emulator
```

The user must configure `telegramChatId` in app Settings → Integrations by:
1. Messaging `@mobileclawofficialbot` on Telegram
2. Running `/start` to get their chat ID
3. Entering that chat ID in the app settings

### Run E2E call test
```bash
bash /Users/aostapenko/Work/mobileclaw/e2e_test_incoming_call_telegram.sh
```

Or the Maestro-wrapped version:
```bash
bash /Users/aostapenko/Work/mobileclaw/mobile-app/.maestro/e2e_call_telegram.sh
```

---

## 5. Rust Unit Tests

Run before any build to catch regressions:
```bash
cd /Users/aostapenko/Work/mobileclaw
~/.cargo/bin/cargo test --lib 2>&1 | tail -20
```

Known pre-existing failure (NOT introduced by this project's changes):
- `memory::lucid::tests::failure_cooldown_avoids_repeated_lucid_calls` — flaky timing test on macOS, tracked upstream

Expected: 1639 tests pass, 1 pre-existing failure.

---

## 6. Daemon Health Check

The Rust daemon runs at `http://127.0.0.1:8000` inside the app. Check health:
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell curl -s http://127.0.0.1:8000/health
```

If this fails, the daemon crashed. Look for exit code 139 (segfault) in logcat:
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 logcat \
  -s "ZeroClaw:*" "AndroidRuntime:E" "*:F" | head -50
```

After a daemon crash, restart the app to respawn it:
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell am force-stop com.mobileclaw.app
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell am start -n com.mobileclaw.app/.MainActivity
```

---

## 7. Key Configuration Locations

### App settings (AsyncStorage inside the APK sandbox)
The app stores all settings in React Native AsyncStorage. To read them:
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell \
  "run-as com.mobileclaw.app cat databases/RKStorage 2>/dev/null | strings | grep -A2 'apiKey\|model\|telegram'"
```

### Daemon config (config.toml)
Written to the app's files directory on daemon start:
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell \
  "run-as com.mobileclaw.app find files -name 'config.toml' -exec cat {} \;"
```

### SharedPreferences (hook registration)
Call and SMS hooks are stored here:
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell \
  "run-as com.mobileclaw.app cat shared_prefs/RuntimeBridgePrefs.xml 2>/dev/null"
```
Key names: `incoming_call_hooks` (boolean), `incoming_sms_hooks` (boolean).

---

## 8. Common Pitfalls

| Pitfall | Symptom | Fix |
|---------|---------|-----|
| `assertVisible: text: "+341234567890"` | Test warns even when text is visible | Maestro interprets `+` as a regex operator. Use `341234567890` (without `+`) |
| Daemon not starting | Chat shows "Gateway error" on first message | Check daemon health; check if JNI library is arm64 and properly copied to jniLibs |
| Agent says "capability disabled" | All 4 tests fail hard assertions | Ensure `jni_bridge.rs` sets `distribution = Full` and all capabilities to `true` |
| Timing race on optional assertions | All optional assertions warn | Expected behavior with slow minimax model. Screenshot and verify agent response manually |
| Kotlin compile error after adding constants | `Unresolved reference 'INCOMING_CALL_HOOKS'` | `INCOMING_CALL_HOOKS` is `private const val` in `RuntimeBridge` object. Use string literals `"incoming_call_hooks"` directly in `AndroidActionBridgeServer` |
| Telegram notify tool not available | Agent says "I don't have a Telegram tool" | User must configure `telegramChatId` in app settings. Tool only registers when both `bot_token` and `notify_chat_id` are non-empty |
| http_request tool error | "no allowed_domains configured" | `jni_bridge.rs` now sets `allowed_domains`; rebuild and reinstall APK |
| Browser tool not working on emulator | "browser not available" | Emulator has no Brave browser; BrowserOpenTool targets Brave specifically. `http_request` tool is the alternative for web fetching |

---

## 9. Integration Requirements

| Integration | What's needed | Where to configure |
|-------------|---------------|-------------------|
| Telegram bot | `telegramToken` (bot token) | App Settings → Integrations |
| Telegram notify | `telegramChatId` (your chat ID) | App Settings → Integrations; get ID by messaging @mobileclawofficialbot |
| Discord | `discordBotToken` | App Settings → Integrations |
| Slack | `slackBotToken` | App Settings → Integrations |
| Composio | `composioApiKey` | App Settings → Integrations |
| Deepgram (voice) | `deepgramApiKey` | App Settings → Agent Config |

---

## 10. Quick Sanity Check (5-minute verify)

After installing a new APK, do this before running full test suite:

1. Open app → verify no crash on launch
2. Send message: `"list your available tools"` → agent should respond with tool list (not "Gateway error")
3. Send message: `"what is my battery level"` → should return a percentage (not "disabled")
4. Check `http://127.0.0.1:8000/health` via adb → should return 200 OK
5. Open Tasks & Hooks screen → gateway status badge should show "Healthy"

If all 5 pass, the daemon is running with full capabilities and the basic tool
bridge is operational.
