#!/usr/bin/env python3
"""
Generate TTS audio and play it into the virtual audio cable.
The emulator picks this up via "Virtual microphone uses host audio input".

Usage:
    python3 generate_and_play.py "Hello Guappa"
    python3 generate_and_play.py "Find recipes for chocolate cake" --lang es
"""
import sys
import os
import platform
import subprocess

def main():
    if len(sys.argv) < 2:
        print("Usage: generate_and_play.py <text> [--lang <code>]")
        sys.exit(1)

    text = sys.argv[1]
    lang = "en"

    if "--lang" in sys.argv:
        idx = sys.argv.index("--lang")
        if idx + 1 < len(sys.argv):
            lang = sys.argv[idx + 1]

    try:
        from gtts import gTTS
    except ImportError:
        print("ERROR: gTTS not installed. Run: pip install gTTS")
        sys.exit(1)

    output_path = "/tmp/guappa_speech.mp3"
    tts = gTTS(text=text, lang=lang)
    tts.save(output_path)
    print(f"Generated TTS: '{text}' -> {output_path}")

    # Play audio non-blocking so Maestro can continue
    if platform.system() == "Darwin":
        subprocess.Popen(["afplay", output_path])
    elif platform.system() == "Windows":
        os.system(f'start /B ffplay -nodisp -autoexit "{output_path}"')
    else:
        # Linux
        subprocess.Popen(["aplay", output_path])

    print("Audio playback started (non-blocking)")

if __name__ == "__main__":
    main()
