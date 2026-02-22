#!/bin/bash
# End-to-end test: simulate incoming call → verify Telegram notification arrives
#
# Required environment variables:
#   ZEROCLAW_TG_BOT_TOKEN      — Telegram bot token for polling updates
#   ZEROCLAW_TEST_CALLER_NUMBER — Phone number to simulate the incoming call from
#
# Optional environment variables:
#   ZEROCLAW_EMULATOR — ADB device serial (default: emulator-5554)
#
# Usage:
#   export ZEROCLAW_TG_BOT_TOKEN=123:ABC
#   export ZEROCLAW_TEST_CALLER_NUMBER=+341234567890
#   bash mobile-app/.maestro/e2e_call_telegram.sh

set -e

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
EMULATOR="${ZEROCLAW_EMULATOR:-emulator-5554}"
BOT_TOKEN="${ZEROCLAW_TG_BOT_TOKEN:?Need ZEROCLAW_TG_BOT_TOKEN}"
TEST_NUMBER="${ZEROCLAW_TEST_CALLER_NUMBER:?Need ZEROCLAW_TEST_CALLER_NUMBER}"

echo "==> Running Maestro scenario on $EMULATOR..."
maestro test --device "$EMULATOR" mobile-app/.maestro/test_scenario_incoming_call_telegram.yaml

echo "==> Waiting 30s for agent to index the rule..."
sleep 30

echo "==> Recording baseline Telegram update offset..."
BEFORE=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates?limit=1" | \
  python3 -c "
import sys, json
d = json.load(sys.stdin)
results = d.get('result', [])
print(results[-1]['update_id'] if results else 0)
")
echo "    Baseline update_id: $BEFORE"

echo "==> Simulating incoming call from $TEST_NUMBER..."
"$ADB" -s "$EMULATOR" emu gsm call "$TEST_NUMBER"
sleep 15
"$ADB" -s "$EMULATOR" emu gsm cancel "$TEST_NUMBER"
echo "    Call ended."

echo "==> Polling for Telegram message (up to 60s)..."
for i in $(seq 1 12); do
  MSG=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates?limit=5&offset=$((BEFORE + 1))" | \
    python3 -c "
import sys, json
d = json.load(sys.stdin)
msgs = [r.get('message', {}).get('text', '') for r in d.get('result', [])]
print(' | '.join(m for m in msgs if m))
")
  if [ -n "$MSG" ]; then
    echo "✅ PASS: Telegram message received: $MSG"
    exit 0
  fi
  echo "    Attempt $i/12 — no message yet, waiting 5s..."
  sleep 5
done

echo "❌ FAIL: No Telegram message received within 60s"
exit 1
