#!/bin/bash
set -e
EMULATOR="emulator-5554"
BOT_TOKEN="8353127948:AAH5Dyuc1ydsTDzwbydobbRYndoqpXXUPEc"

echo "=== Step 1: Schedule a task via chat ==="
cat > /tmp/maestro_schedule.yaml << 'EOF'
appId: com.mobileclaw.app
---
- launchApp:
    clearState: false
- waitForAnimationToEnd:
    timeout: 25000
- assertVisible:
    id: chat-input
    timeout: 20000
- tapOn:
    id: chat-input
- inputText: "In 3 minutes, send me a Telegram message saying: ZeroClaw schedule test passed"
- tapOn:
    id: chat-send-or-voice
- waitForAnimationToEnd:
    timeout: 60000
- assertNotVisible:
    text: "Gateway error"
EOF
maestro test --device $EMULATOR /tmp/maestro_schedule.yaml

echo "=== Step 2: Wait 4 minutes for scheduler to trigger ==="
sleep 240

echo "=== Step 3: Verify Telegram message ==="
MSG=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates?limit=5" | python3 -c "
import sys, json
d = json.load(sys.stdin)
for r in reversed(d.get('result', [])):
    text = r.get('message', {}).get('text', '')
    if 'schedule test passed' in text.lower():
        print(text)
        break
")
if [ -n "$MSG" ]; then
  echo "✅ PASS: Scheduled Telegram message received: $MSG"
else
  echo "❌ FAIL: Scheduled Telegram message not received"
  exit 1
fi
