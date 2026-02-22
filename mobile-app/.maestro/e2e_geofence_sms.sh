#!/usr/bin/env bash
# E2E test: geofence SMS — set GPS near home, verify SMS is sent
#
# Required env vars:
#   ZEROCLAW_HOME_LAT   — home latitude (e.g. 37.7749)
#   ZEROCLAW_HOME_LON   — home longitude (e.g. -122.4194)
#   ZEROCLAW_WIFE_NUMBER — wife's phone number
#
# Optional env vars:
#   ZEROCLAW_EMULATOR — ADB device ID (default: emulator-5554)
#
# What this tests (end-to-end):
#   1. Maestro: ask agent to set up home-arrival SMS trigger
#   2. Set emulator GPS coordinates to within 100m of home
#   3. Wait for the agent's geofence cron job to poll and detect arrival
#   4. Verify SMS was sent via ADB read_sms / call log

set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
EMULATOR="${ZEROCLAW_EMULATOR:-emulator-5554}"
HOME_LAT="${ZEROCLAW_HOME_LAT:-37.7749}"
HOME_LON="${ZEROCLAW_HOME_LON:--122.4194}"
WIFE_NUMBER="${ZEROCLAW_WIFE_NUMBER:?Need ZEROCLAW_WIFE_NUMBER}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Step 1: Run Maestro scenario — set up geofence SMS cron job ==="
maestro test --device "$EMULATOR" "$SCRIPT_DIR/test_scenario_geofence_sms.yaml"

echo "=== Step 2: Wait 30s for agent to register the cron job ==="
sleep 30

echo "=== Step 3: Set emulator GPS to home coordinates (${HOME_LAT}, ${HOME_LON}) ==="
# adb emu geo fix <longitude> <latitude>
"$ADB" -s "$EMULATOR" emu geo fix "$HOME_LON" "$HOME_LAT"
echo "GPS set to home: lat=$HOME_LAT lon=$HOME_LON"

echo "=== Step 4: Wait for geofence cron job to fire (polls every 5 min max, check every 30s) ==="
for i in $(seq 1 20); do
  sleep 30
  # Check SMS outbox for a message to the wife's number
  SMS_COUNT=$("$ADB" -s "$EMULATOR" shell \
    "content query --uri content://sms/sent --where \"address='${WIFE_NUMBER}'\" --projection body" \
    2>/dev/null | grep -c "body=" || echo "0")
  if [ "$SMS_COUNT" -gt "0" ] 2>/dev/null; then
    SMS_BODY=$("$ADB" -s "$EMULATOR" shell \
      "content query --uri content://sms/sent --where \"address='${WIFE_NUMBER}'\" --projection body" \
      2>/dev/null | grep "body=" | tail -1 || echo "")
    echo "✅ PASS: SMS sent to $WIFE_NUMBER — $SMS_BODY"
    # Reset GPS to avoid re-triggering
    "$ADB" -s "$EMULATOR" emu geo fix 0 0
    exit 0
  fi
  echo "  Attempt $i/20 — no SMS yet ($((i*30))s elapsed)"
done

echo "❌ FAIL: No SMS detected in sent box within 10 minutes"
echo "    Possible causes:"
echo "    1. Agent created a cron job with longer interval"
echo "    2. SMS permission not granted on emulator"
echo "    3. Cron job uses agent's android_device send_sms but emulator blocks it"
"$ADB" -s "$EMULATOR" emu geo fix 0 0
exit 1
