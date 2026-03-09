#!/usr/bin/env bash
# ============================================================
# play_tts.sh — Generate TTS and play via system output
# ============================================================
# Used with BlackHole virtual audio cable to inject speech
# into the Android emulator's microphone input.
#
# Prerequisites:
#   - macOS with 'say' command
#   - ffmpeg installed
#   - System audio output set to "BlackHole 2ch"
#   - Emulator mic input set to "BlackHole 2ch"
#
# Usage:
#   bash play_tts.sh "Hello, this is a test"
#   bash play_tts.sh "What is my wife's name?" --wait
#
# Options:
#   --wait    Block until playback finishes (default: background)
#   --rate N  Speech rate in words per minute (default: 180)
#   --voice V macOS voice name (default: system default)

set -euo pipefail

TEXT="${1:?Usage: play_tts.sh \"text to speak\" [--wait] [--rate N] [--voice V]}"
shift

# Parse options
WAIT=false
RATE=180
VOICE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --wait) WAIT=true; shift ;;
        --rate) RATE="$2"; shift 2 ;;
        --voice) VOICE="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

TMP_DIR="$(mktemp -d)"
trap "rm -rf $TMP_DIR" EXIT

AIFF="$TMP_DIR/tts.aiff"
WAV="$TMP_DIR/tts.wav"

# Generate with macOS say
SAY_ARGS=(-r "$RATE" -o "$AIFF")
if [ -n "$VOICE" ]; then
    SAY_ARGS=(-v "$VOICE" "${SAY_ARGS[@]}")
fi
say "${SAY_ARGS[@]}" "$TEXT"

# Convert to 16kHz mono WAV (optimal for Google Speech / Deepgram)
ffmpeg -y -i "$AIFF" \
    -ar 16000 -ac 1 -sample_fmt s16 \
    "$WAV" \
    -loglevel error

# Play through system output (routed to BlackHole → emulator mic)
if [ "$WAIT" = true ]; then
    afplay "$WAV"
else
    afplay "$WAV" &
    echo $!
fi
