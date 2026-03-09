#!/usr/bin/env bash
# ============================================================
# E2E orchestrator: Memory → Call Hook → Call Emulation → Voice STT
# ============================================================
#
# This script runs the Maestro flow AND injects the emulated call
# and TTS audio at the right moments.
#
# Prerequisites:
#   - BlackHole 2ch installed (brew install --cask blackhole-2ch)
#   - Emulator running with mic input set to "BlackHole 2ch"
#   - System audio output set to "BlackHole 2ch" (for TTS routing)
#   - ffmpeg installed (brew install ffmpeg)
#   - Provider API key configured in the app
#   - Deepgram API key configured in the app (for voice STT)
#
# Environment variables (optional):
#   GUAPPA_EMULATOR   — ADB device ID (default: emulator-5554)
#   MAESTRO_DEVICE    — Maestro device specifier
#
# Usage:
#   bash mobile-app/.maestro/e2e_agent_memory_call_voice.sh

set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
EMULATOR="${GUAPPA_EMULATOR:-emulator-5554}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP_DIR="$(mktemp -d)"
CALLER_NUMBER="5551234567"

cleanup() {
    rm -rf "$TMP_DIR"
    # Cancel any lingering emulated call
    "$ADB" -s "$EMULATOR" emu gsm cancel "$CALLER_NUMBER" 2>/dev/null || true
}
trap cleanup EXIT

# ── Helpers ────────────────────────────────────────────────────

check_prereqs() {
    echo "=== Checking prerequisites ==="

    if ! command -v ffmpeg &>/dev/null; then
        echo "❌ ffmpeg not found. Install with: brew install ffmpeg"
        exit 1
    fi

    if ! "$ADB" -s "$EMULATOR" get-state &>/dev/null; then
        echo "❌ Emulator $EMULATOR not reachable via ADB"
        exit 1
    fi

    if ! command -v maestro &>/dev/null && ! command -v ~/.maestro/bin/maestro &>/dev/null; then
        echo "❌ Maestro not found in PATH"
        exit 1
    fi

    # Check BlackHole is available
    if system_profiler SPAudioDataType 2>/dev/null | grep -q "BlackHole"; then
        echo "✅ BlackHole audio driver detected"
    else
        echo "⚠️  BlackHole not detected. Voice STT test may fail."
        echo "   Install: brew install --cask blackhole-2ch"
        echo "   Then set System Output + Emulator Mic to 'BlackHole 2ch'"
    fi

    echo "✅ Prerequisites OK"
    echo ""
}

generate_tts_wav() {
    local text="$1"
    local output="$2"
    echo "  Generating TTS: \"$text\" → $output"
    # macOS 'say' generates AIFF; convert to 16kHz mono WAV for best STT compatibility
    say "$text" -o "$TMP_DIR/tts_raw.aiff"
    ffmpeg -y -i "$TMP_DIR/tts_raw.aiff" \
        -ar 16000 -ac 1 -sample_fmt s16 \
        "$output" \
        -loglevel error
}

play_audio_to_blackhole() {
    local wav_file="$1"
    echo "  Playing audio to BlackHole: $wav_file"
    # afplay routes through system audio output (which should be BlackHole)
    afplay "$wav_file" &
    AFPLAY_PID=$!
    wait $AFPLAY_PID 2>/dev/null || true
}

get_maestro_cmd() {
    if command -v maestro &>/dev/null; then
        echo "maestro"
    else
        echo "$HOME/.maestro/bin/maestro"
    fi
}

# ── Main ───────────────────────────────────────────────────────

check_prereqs

echo "=== Phase 1-2: Starting Maestro flow (memory + call hook setup) ==="
echo "    Maestro will send messages and wait for agent responses."
echo "    Meanwhile, we prepare the call emulation."
echo ""

# Pre-generate voice TTS file while Maestro is running phases 1-2
generate_tts_wav "What is my wife's name?" "$TMP_DIR/voice_query.wav"

# Start Maestro in background
MAESTRO_CMD=$(get_maestro_cmd)
MAESTRO_LOG="$TMP_DIR/maestro.log"

$MAESTRO_CMD test \
    ${MAESTRO_DEVICE:+--device "$MAESTRO_DEVICE"} \
    "$SCRIPT_DIR/e2e_agent_memory_call_voice.yaml" \
    >"$MAESTRO_LOG" 2>&1 &
MAESTRO_PID=$!

echo "  Maestro started (PID: $MAESTRO_PID)"
echo ""

# ── Wait for Phase 2 to complete (call hook setup) ────────────
# We poll the Maestro log for evidence that the second message was sent.
# Heuristic: wait for the agent to process both messages (~60-90s)
echo "=== Waiting for Phases 1-2 to complete (memory + hook setup) ==="

HOOK_READY=false
for i in $(seq 1 60); do
    sleep 3
    # Check if Maestro is still running
    if ! kill -0 $MAESTRO_PID 2>/dev/null; then
        echo "  Maestro exited early — checking result..."
        wait $MAESTRO_PID
        MAESTRO_EXIT=$?
        if [ $MAESTRO_EXIT -ne 0 ]; then
            echo "❌ Maestro failed (exit $MAESTRO_EXIT)"
            cat "$MAESTRO_LOG"
            exit 1
        fi
        # If Maestro finished successfully before we even triggered the call,
        # that means it didn't wait for the call (unexpected).
        echo "⚠️  Maestro finished before call emulation — flow may have changed"
        break
    fi
    # Check log for progress — once we see the hook-related input sent,
    # give the agent time to respond and register the hook
    if grep -q "incoming call" "$MAESTRO_LOG" 2>/dev/null; then
        echo "  Hook setup message detected in Maestro log"
        HOOK_READY=true
        # Give agent extra time to register the hook
        echo "  Waiting 30s for hook registration..."
        sleep 30
        break
    fi
    echo "  [$((i*3))s] Waiting for Maestro to reach Phase 2..."
done

if [ "$HOOK_READY" = false ] && kill -0 $MAESTRO_PID 2>/dev/null; then
    echo "  Timeout waiting for Phase 2 — proceeding with call anyway"
fi

# ── Phase 3: Emulate incoming call ────────────────────────────
echo ""
echo "=== Phase 3: Emulating incoming call from $CALLER_NUMBER ==="
"$ADB" -s "$EMULATOR" emu gsm call "$CALLER_NUMBER"
echo "  📞 Call started"
sleep 10
"$ADB" -s "$EMULATOR" emu gsm cancel "$CALLER_NUMBER"
echo "  📞 Call ended"
echo ""

# ── Wait for Phase 4 to complete (memory recall) ──────────────
echo "=== Waiting for Phases 3-4 (call notification + memory recall) ==="
for i in $(seq 1 30); do
    sleep 3
    if ! kill -0 $MAESTRO_PID 2>/dev/null; then
        break
    fi
    echo "  [$((i*3))s] Waiting for Maestro to reach Phase 5 (voice)..."
done

# ── Phase 5: Play TTS audio for voice STT ─────────────────────
if kill -0 $MAESTRO_PID 2>/dev/null; then
    echo ""
    echo "=== Phase 5: Playing TTS audio for voice mode STT ==="
    echo "  Waiting 5s for mic button to be tapped by Maestro..."
    sleep 5
    play_audio_to_blackhole "$TMP_DIR/voice_query.wav"
    echo "  🎤 TTS audio played"
    echo ""
fi

# ── Wait for Maestro to finish ─────────────────────────────────
echo "=== Waiting for Maestro to complete ==="
wait $MAESTRO_PID
MAESTRO_EXIT=$?

echo ""
echo "=============================================="
if [ $MAESTRO_EXIT -eq 0 ]; then
    echo "✅ E2E TEST PASSED"
    echo "   ✓ Memory stored (wife = Kate)"
    echo "   ✓ Incoming call hook registered"
    echo "   ✓ Call emulated, agent notified caller number"
    echo "   ✓ Memory recalled (Kate)"
    echo "   ✓ Voice STT recognized speech via virtual audio"
    echo "   ✓ Agent responded via voice with correct answer"
else
    echo "❌ E2E TEST FAILED (exit code: $MAESTRO_EXIT)"
    echo ""
    echo "Maestro log:"
    cat "$MAESTRO_LOG"
fi
echo "=============================================="
exit $MAESTRO_EXIT
