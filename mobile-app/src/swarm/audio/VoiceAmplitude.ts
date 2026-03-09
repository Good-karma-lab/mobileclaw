/**
 * VoiceAmplitude — real-time microphone amplitude extraction.
 *
 * Drives the harmonic wave deformation intensity:
 *   quiet voice → barely visible trembling
 *   loud voice → dramatic organic shapes (exponential scaling)
 */
import { Audio } from 'expo-av';
import { swarmStore } from '../SwarmController';

export class VoiceAmplitude {
  private recording: Audio.Recording | null = null;
  private interval: ReturnType<typeof setInterval> | null = null;
  private smoothedAmplitude = 0;

  async start() {
    try {
      const { granted } = await Audio.requestPermissionsAsync();
      if (!granted) return;

      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
      });

      this.recording = new Audio.Recording();
      await this.recording.prepareToRecordAsync(
        Audio.RecordingOptionsPresets.HIGH_QUALITY
      );
      await this.recording.startAsync();

      // Poll amplitude at ~60fps
      this.interval = setInterval(async () => {
        if (!this.recording) return;
        try {
          const status = await this.recording.getStatusAsync();
          if (status.isRecording && status.metering != null) {
            // Convert dB to 0..1 range
            // metering is typically -160 (silence) to 0 (max)
            const db = status.metering;
            const raw = Math.max(0, Math.min(1, (db + 60) / 60));
            // Smooth with exponential moving average
            this.smoothedAmplitude += (raw - this.smoothedAmplitude) * 0.3;
            swarmStore.setAmplitude(this.smoothedAmplitude);
          }
        } catch {
          // Ignore polling errors
        }
      }, 16);
    } catch {
      // Audio permission denied or device not available
    }
  }

  async stop() {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
    if (this.recording) {
      try {
        await this.recording.stopAndUnloadAsync();
      } catch {
        // Ignore cleanup errors
      }
      this.recording = null;
    }
    this.smoothedAmplitude = 0;
    swarmStore.setAmplitude(0);
  }

  get isActive(): boolean {
    return this.recording !== null;
  }
}

// Singleton
export const voiceAmplitude = new VoiceAmplitude();
