---
name: test-mobileclaw
description: Complete testing workflow for MobileClaw Android app. Use when running tests, verifying agent behavior, checking integrations, or troubleshooting test failures. Includes build pipeline, Maestro testing, E2E verification, and side effect validation.
disable-model-invocation: false
allowed-tools: Bash, Read, Grep, Glob, Write, Edit
---

# MobileClaw Android Testing Protocol

Complete workflow for testing the MobileClaw Android app. This protocol ensures
agent behavior is verified end-to-end, not just through Maestro pass/fail.

## ARCHITECTURE PRINCIPLE

**Applications must NEVER make direct calls to LLM APIs.** All LLM interactions
MUST go through the ZeroClaw agent (Rust daemon) embedded in the APK. The
React Native app communicates with the daemon via HTTP at `http://127.0.0.1:8000`.

This ensures:
- Consistent tool availability and security policies
- Proper memory/context management
- Unified observability and error handling
- Resilience and retry logic at the daemon layer

## 1. Full Build Pipeline

Every time Rust source changes, run all four steps:

```bash
# Step 1: Compile Rust JNI library
bash build_android_jni.sh

# Step 2: Bundle React Native JS
cd mobile-app
npx expo export:embed \
  --platform android \
  --bundle-output android/app/src/main/assets/index.android.bundle \
  --assets-dest android/app/src/main/res \
  --dev false

# Step 3: Build APK
cd android
./gradlew assembleDebug

# Step 4: Install on emulator
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

**Shortcuts:**
- If only JS/React Native changed: skip Step 1
- If only Android Kotlin changed: skip Steps 1 and 2

## 2. Complete Testing Workflow (REQUIRED)

**DO NOT just run Maestro and check pass/fail.** Follow this workflow:

### Step 1: Clean the chat history

```bash
# Stop the app
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell am force-stop com.mobileclaw.app

# Delete chat database
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell "run-as com.mobileclaw.app rm -f databases/RKStorage"

# Restart app
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell am start -n com.mobileclaw.app/.MainActivity
```

### Step 2: Restore required configuration

After cleaning chat, restore Telegram configuration (if testing Telegram features):

```bash
# Pull current AsyncStorage DB
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell "run-as com.mobileclaw.app cat databases/RKStorage" | base64 > /tmp/storage.db.b64

# Decode
base64 -d -i /tmp/storage.db.b64 > /tmp/RKStorage_local.db

# Update telegramChatId (replace 530732407 with actual chat ID)
sqlite3 /tmp/RKStorage_local.db <<SQL
UPDATE catalystLocalStorage
SET value = replace(value, '"telegramChatId":""', '"telegramChatId":"530732407"')
WHERE key = 'mobileclaw:integrations-config:v2';
SQL

# Push back
cat /tmp/RKStorage_local.db | base64 | \
  ~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell "run-as com.mobileclaw.app sh -c 'base64 -d > databases/RKStorage'"

# Restart app
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell am force-stop com.mobileclaw.app
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell am start -n com.mobileclaw.app/.MainActivity
```

### Step 3: Run the Maestro test

```bash
maestro test --device emulator-5554 \
  mobile-app/.maestro/test_scenario_incoming_call_telegram.yaml
```

**Available test scenarios:**
- `test_scenario_incoming_call_telegram.yaml` — Register call hook, verify via Telegram
- `test_scenario_twitter_reply.yaml` — Reply to Twitter posts (username: @QML_RU)
- `test_scenario_geofence_sms.yaml` — Create geofence SMS trigger
- `test_scenario_memory_persistence.yaml` — Agent remembers context across messages

Car variants: add `_car` suffix (includes `hideKeyboard` step).

### Step 4: Check agent's actual response (CRITICAL)

**Maestro PASS ≠ correct behavior.** Always screenshot and read the chat:

```bash
# Take screenshot
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  exec-out screencap -p > /tmp/test_result.png
open /tmp/test_result.png
```

**Look for:**
- ✅ **Good**: agent acknowledges task, mentions specific values (phone number, username, coordinates), reports action taken (e.g., "I've registered a hook")
- ❌ **Bad**: "capability disabled", "not available", "I don't have a tool for that", "Gateway error"
- ⚠️ **Investigate**: Generic response without task-specific values; agent created a cron job when it should have used a hook; agent didn't mention completing the action

### Step 5: Verify actual side effects

**Don't trust agent's claims — verify the action happened.**

**For incoming call hook:**
```bash
# Check SharedPreferences shows hook is ON
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell "run-as com.mobileclaw.app cat shared_prefs/RuntimeBridgePrefs.xml" | \
  grep incoming_call_hooks

# Navigate to Tasks & Hooks screen in app and verify:
# - "Incoming Call Hook" shows as ON
# - "No scheduled tasks" (agent should NOT have created a cron job)

# Simulate a real call
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  emu gsm call +341234567890

# Wait 15 seconds, then cancel
sleep 15
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  emu gsm cancel +341234567890

# Check Telegram app to verify message was sent
```

**For geofence SMS task:**
```bash
# Check cron job was created via gateway API
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell curl -s http://127.0.0.1:8000/cron/jobs | jq .

# Navigate to Tasks & Hooks screen and verify scheduled task appears

# Simulate GPS at target location (San Francisco)
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  emu geo fix -122.4194 37.7749

# Wait for cron job to fire, then check Android SMS sent box
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell "content query --uri content://sms/sent --projection address,body,date" | \
  grep "wife\|+34987654321"
```

### Step 6: Analyze and fix

- If agent response is wrong → update tool descriptions, agent config, or test assertions
- If side effect didn't happen → check daemon logs, permissions, integration config
- If test assertions are wrong → fix the YAML file

### Step 7: Repeat until correct

Re-run from Step 1 (clean chat) after each fix to ensure consistent behavior.

## 3. Simulating Android Events

### Incoming phone call
```bash
# Start call
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  emu gsm call +341234567890

# Let it ring for 15 seconds
sleep 15

# Cancel call
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  emu gsm cancel +341234567890
```

### Incoming SMS
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  emu sms send +341234567890 "Test message"
```

### GPS location change
```bash
# Set GPS (longitude, latitude)
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  emu geo fix -122.4194 37.7749
```

### Check SMS sent box
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell "content query --uri content://sms/sent --projection address,body,date"
```

## 4. Verifying Telegram Bot Messages

Telegram `getUpdates` API shows messages TO the bot, not FROM it.

**Best method:** Check Telegram app directly to see if message arrived.

**Indirect verification:**
```bash
# Record baseline update_id before test
BASELINE=$(curl -s "https://api.telegram.org/bot${TOKEN}/getUpdates" | \
  jq -r '.result[-1].update_id // 0')

# After test: check for new updates
curl -s "https://api.telegram.org/bot${TOKEN}/getUpdates" | \
  jq ".result[] | select(.update_id > $BASELINE)"
```

**Check daemon logs:**
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 logcat -d | \
  grep -i "telegram_notify\|TelegramNotifyTool"
```

## 5. Checking Scheduled Tasks and Hooks

### Via Mobile App UI
1. Navigate to Tasks & Hooks tab (bottom navigation)
2. Check "Active Hooks" section: Incoming Call Hook, Incoming SMS Hook, Notifications Hook (ON/OFF)
3. Check "Scheduled Tasks" section: lists all cron jobs with schedule, next run time, Delete button

### Via ADB (SharedPreferences for hooks)
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell "run-as com.mobileclaw.app cat shared_prefs/RuntimeBridgePrefs.xml"
```

### Via Gateway API (cron jobs)
```bash
# List all cron jobs
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell curl -s http://127.0.0.1:8000/cron/jobs | jq .

# Delete a job by ID
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell curl -s -X DELETE http://127.0.0.1:8000/cron/jobs/<job_id>
```

### Common mistake: cron job instead of hook
If agent creates a cron job for incoming calls/SMS monitoring, it will MISS short calls/messages.

- ✅ **Use hooks**: `hook_incoming_call`, `hook_incoming_sms` — fires immediately via BroadcastReceiver
- ❌ **Don't use cron jobs**: polling will miss events

## 6. Daemon Health Check

The Rust daemon runs at `http://127.0.0.1:8000` inside the app:

```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 \
  shell curl -s http://127.0.0.1:8000/health
```

If this fails, check logcat for crashes:
```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 logcat \
  -s "ZeroClaw:*" "AndroidRuntime:E" "*:F" | head -50
```

## 7. Quick Sanity Check

After installing APK, verify:

1. Open app → no crash on launch
2. Send: `"list your available tools"` → should return tool list (not "Gateway error")
3. Send: `"what is my battery level"` → should return percentage (not "disabled")
4. Check `http://127.0.0.1:8000/health` via adb → should return 200 OK
5. Open Tasks & Hooks screen → gateway status badge should show "Healthy"

If all pass, daemon is running with full capabilities.

## 8. Common Pitfalls

| Pitfall | Symptom | Fix |
|---------|---------|-----|
| `assertVisible: text: "+341234567890"` | Test warns despite visible text | Use `341234567890` (Maestro interprets `+` as regex) |
| Daemon not starting | "Gateway error" on first message | Check daemon health; verify JNI library is arm64 |
| Agent says "capability disabled" | All tests fail | Ensure `jni_bridge.rs` sets `distribution = Full` |
| Agent creates cron job instead of hook | Misses short calls | Update tool description to warn against polling |
| Telegram notify not available | "I don't have a Telegram tool" | Configure `telegramChatId` in app settings |
| http_request tool error | "no allowed_domains configured" | `jni_bridge.rs` sets `allowed_domains`; rebuild APK |

## 9. Integration Setup

### Telegram integration
1. Open app → Settings → Integrations
2. Enter Telegram bot token (from @BotFather)
3. Enter chat ID:
   ```bash
   curl "https://api.telegram.org/bot<token>/getUpdates" | \
     jq '.result[-1].message.chat.id'
   ```

### Other integrations
| Integration | Config key | Where to get it |
|-------------|------------|-----------------|
| Discord | `discordBotToken` | Discord Developer Portal |
| Slack | `slackBotToken` | Slack API → OAuth & Permissions |
| Composio | `composioApiKey` | Composio dashboard |
| Deepgram (voice) | `deepgramApiKey` | Deepgram console |

## 10. Testing Summary (TL;DR)

For every test cycle:

1. **Clean chat**: `adb shell am force-stop com.mobileclaw.app && adb shell "run-as com.mobileclaw.app rm -f databases/RKStorage"`
2. **Restore config**: Use sqlite3 to set telegramChatId
3. **Run Maestro test**: `maestro test --device emulator-5554 test_scenario_*.yaml`
4. **Screenshot chat**: `adb exec-out screencap -p > /tmp/test.png && open /tmp/test.png`
5. **Analyze response**: Check if agent mentioned task-specific values
6. **Verify side effects**: Check hooks/cron jobs/SMS/Telegram
7. **Fix and repeat**: Update code/tests as needed

**Key principles:**
- Never trust Maestro PASS alone — always read actual agent response
- Never trust agent's claims — always verify side effects
- Clean chat between tests for consistent behavior
- Agent must use hooks (not cron jobs) for call/SMS monitoring
- All LLM interactions go through ZeroClaw daemon, never direct API calls
