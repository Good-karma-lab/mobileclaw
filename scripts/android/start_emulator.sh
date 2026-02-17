#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
EMULATOR_BIN="$SDK_ROOT/emulator/emulator"
ADB_BIN="$SDK_ROOT/platform-tools/adb"
AVD_NAME="${ZEROCLAW_AVD_NAME:-zeroclaw-api35}"
EMULATOR_PORT="${ZEROCLAW_EMULATOR_PORT:-5554}"
TARGET_SERIAL="emulator-${EMULATOR_PORT}"

EXISTING_SERIAL=$(
    "$ADB_BIN" devices | awk '/^emulator-[0-9]+\s+device$/ {print $1; exit}'
)
if [ -n "$EXISTING_SERIAL" ]; then
    echo "Emulator already running: $EXISTING_SERIAL"
    exit 0
fi

if [ ! -x "$EMULATOR_BIN" ] || [ ! -x "$ADB_BIN" ]; then
    echo "Missing emulator or adb binaries. Run scripts/android/setup_sdk.sh first."
    exit 1
fi

"$EMULATOR_BIN" -avd "$AVD_NAME" -port "$EMULATOR_PORT" -no-audio -no-snapshot -no-boot-anim >/tmp/zeroclaw-android-emulator.log 2>&1 &

"$ADB_BIN" -s "$TARGET_SERIAL" wait-for-device

BOOT_TIMEOUT_SECS=240
START_TS=$(date +%s)
while true; do
    BOOT_STATUS=$("$ADB_BIN" -s "$TARGET_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$BOOT_STATUS" = "1" ]; then
        break
    fi
    NOW=$(date +%s)
    if [ $((NOW - START_TS)) -gt "$BOOT_TIMEOUT_SECS" ]; then
        echo "Emulator boot timeout"
        exit 1
    fi
    sleep 2
done

echo "Emulator booted: $AVD_NAME ($TARGET_SERIAL)"
