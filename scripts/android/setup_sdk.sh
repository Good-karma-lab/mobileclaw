#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
CMDLINE_TOOLS_DIR="$SDK_ROOT/cmdline-tools/latest"

if [ "$(uname -m)" = "arm64" ]; then
    DEFAULT_IMAGE="system-images;android-35;google_apis_playstore;arm64-v8a"
else
    DEFAULT_IMAGE="system-images;android-35;google_apis;x86_64"
fi

IMAGE="${ZEROCLAW_ANDROID_IMAGE:-$DEFAULT_IMAGE}"

mkdir -p "$SDK_ROOT"

if [ ! -x "$CMDLINE_TOOLS_DIR/bin/sdkmanager" ]; then
    echo "Android cmdline-tools not found at $CMDLINE_TOOLS_DIR"
    echo "Install Android command-line tools and set ANDROID_SDK_ROOT before running emulator automation."
    exit 1
fi

yes | "$CMDLINE_TOOLS_DIR/bin/sdkmanager" --licenses >/dev/null

"$CMDLINE_TOOLS_DIR/bin/sdkmanager" \
    "platform-tools" \
    "emulator" \
    "platforms;android-35" \
    "$IMAGE" \
    "build-tools;35.0.0"

echo "Android SDK components installed under $SDK_ROOT"
