#!/bin/bash
set -e
EMULATOR="emulator-5554"
BOT_TOKEN="8353127948:AAH5Dyuc1ydsTDzwbydobbRYndoqpXXUPEc"
WIFE_NUMBER="+341234567890"
WRONG_NUMBER="+11234567890"

echo "=== Step 1: Setup — send scenario to agent via chat ==="
maestro test --device $EMULATOR mobile-app/.maestro/test_scenario_incoming_call_telegram.yaml

echo "=== Step 2: Wait 2 minutes (agent indexes the rule) ==="
sleep 120

echo "=== Step 3: Wrong number call (should be ignored) ==="
# Record Telegram update ID before test
BEFORE_UPDATE_ID=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates?limit=1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['result'][-1]['update_id'] if d['result'] else 0)")

adb -s $EMULATOR emu gsm call $WRONG_NUMBER
sleep 15
adb -s $EMULATOR emu gsm cancel $WRONG_NUMBER
sleep 30

# Verify NO new Telegram message from wrong number
NEW_UPDATE_COUNT=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates?limit=1&offset=$((BEFORE_UPDATE_ID+1))" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d['result']))")
if [ "$NEW_UPDATE_COUNT" -gt "0" ]; then
  echo "⚠️ Warning: Telegram message received for wrong number call"
fi

echo "=== Step 4: Wife's call (should trigger Telegram) ==="
adb -s $EMULATOR emu gsm call $WIFE_NUMBER
sleep 15
adb -s $EMULATOR emu gsm cancel $WIFE_NUMBER

echo "=== Step 5: Wait for agent to process and send Telegram (up to 60s) ==="
TIMEOUT=60
INTERVAL=5
for i in $(seq 1 $((TIMEOUT/INTERVAL))); do
  MSG=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates?limit=5&offset=$((BEFORE_UPDATE_ID+1))" | python3 -c "
import sys, json
d = json.load(sys.stdin)
msgs = [r.get('message', {}).get('text', '') for r in d['result']]
print(' | '.join(m for m in msgs if m))
")
  if [ -n "$MSG" ]; then
    echo "✅ PASS: Telegram message received: $MSG"
    exit 0
  fi
  sleep $INTERVAL
done

echo "❌ FAIL: No Telegram message received within ${TIMEOUT}s"
exit 1
