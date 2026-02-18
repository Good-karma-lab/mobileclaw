#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
CMDLINE_TOOLS_DIR="$SDK_ROOT/cmdline-tools/latest"
AVD_NAME="${ZEROCLAW_AVD_NAME:-zeroclaw-api35}"

if [ "$(uname -m)" = "arm64" ]; then
    DEFAULT_IMAGE="system-images;android-35;google_apis_playstore;arm64-v8a"
else
    DEFAULT_IMAGE="system-images;android-35;google_apis;x86_64"
fi

IMAGE="${ZEROCLAW_ANDROID_IMAGE:-$DEFAULT_IMAGE}"

AVDMANAGER="$CMDLINE_TOOLS_DIR/bin/avdmanager"

if [ ! -x "$AVDMANAGER" ]; then
    echo "avdmanager not found: $AVDMANAGER"
    exit 1
fi

if "$AVDMANAGER" list avd | grep -q "Name: $AVD_NAME"; then
    echo "AVD already exists: $AVD_NAME"
    exit 0
fi

echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$IMAGE" --force
echo "Created AVD: $AVD_NAME"
