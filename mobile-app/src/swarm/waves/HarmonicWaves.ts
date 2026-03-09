/**
 * HarmonicWaves — voice-reactive pulse wave rings.
 *
 * Rings of connected node-points deformed by 5 sinusoidal harmonics.
 * Voice amplitude controls deformation intensity exponentially:
 *   distortion = volume^2.5 × baseAmplitude
 */

const HARMONICS = [
  { freq: 3,  weight: 1.0,  phaseSpeed: 0.5  },
  { freq: 5,  weight: 0.6,  phaseSpeed: -0.8 },
  { freq: 8,  weight: 0.35, phaseSpeed: 1.2  },
  { freq: 13, weight: 0.2,  phaseSpeed: -1.7 },
  { freq: 21, weight: 0.12, phaseSpeed: 2.3  },
];

export interface WaveDrawData {
  points: { x: number; y: number }[];
  alpha: number;
  progress: number;
}

interface HarmonicWave {
  nodes: { angle: number; energy: number }[];
  radius: number;
  maxRadius: number;
  bornAt: number;
  harmonicPhases: number[];
  baseAmplitude: number;
  active: boolean;
}

const MAX_WAVES = 6;
const NODES_PER_WAVE = 80;

export class HarmonicWaveSystem {
  private waves: HarmonicWave[] = [];

  constructor() {
    // Pre-allocate pool
    for (let i = 0; i < MAX_WAVES; i++) {
      this.waves.push({
        nodes: Array.from({ length: NODES_PER_WAVE }, (_, j) => ({
          angle: (j / NODES_PER_WAVE) * Math.PI * 2,
          energy: 0.5 + Math.random() * 0.5,
        })),
        radius: 0,
        maxRadius: 0,
        bornAt: 0,
        harmonicPhases: HARMONICS.map(() => 0),
        baseAmplitude: 0,
        active: false,
      });
    }
  }

  spawn(maxRadius: number, time: number) {
    const slot = this.waves.find(w => !w.active);
    if (!slot) {
      // Reuse oldest
      const oldest = this.waves.reduce((a, b) =>
        a.bornAt < b.bornAt ? a : b
      );
      this.activateWave(oldest, maxRadius, time);
    } else {
      this.activateWave(slot, maxRadius, time);
    }
  }

  private activateWave(wave: HarmonicWave, maxRadius: number, time: number) {
    wave.radius = 5;
    wave.maxRadius = maxRadius;
    wave.bornAt = time;
    wave.baseAmplitude = 8 + Math.random() * 6;
    wave.active = true;
    for (let i = 0; i < HARMONICS.length; i++) {
      wave.harmonicPhases[i] = Math.random() * Math.PI * 2;
    }
    for (let i = 0; i < NODES_PER_WAVE; i++) {
      wave.nodes[i].energy = 0.5 + Math.random() * 0.5;
    }
  }

  update(time: number, voiceAmplitude: number): WaveDrawData[] {
    const drawData: WaveDrawData[] = [];

    for (const w of this.waves) {
      if (!w.active) continue;

      w.radius += (0.3 + voiceAmplitude * 0.5) * 1.8;

      if (w.radius > w.maxRadius) {
        w.active = false;
        continue;
      }

      const progress = w.radius / w.maxRadius;
      const fade = progress < 0.04
        ? progress / 0.04
        : Math.pow(1 - progress, 2.0);

      // Exponential voice scaling
      const distortionScale = Math.pow(voiceAmplitude, 2.5)
        * w.baseAmplitude * (1 + progress * 2);

      // Advance harmonic phases
      for (let h = 0; h < HARMONICS.length; h++) {
        w.harmonicPhases[h] += HARMONICS[h].phaseSpeed * 0.016;
      }

      // Compute deformed ring positions
      const points: { x: number; y: number }[] = [];
      for (const node of w.nodes) {
        let displacement = 0;
        for (let h = 0; h < HARMONICS.length; h++) {
          displacement += Math.sin(
            node.angle * HARMONICS[h].freq + w.harmonicPhases[h]
          ) * HARMONICS[h].weight;
        }
        displacement *= distortionScale;

        const r = w.radius + displacement;
        points.push({
          x: Math.cos(node.angle) * r,
          y: Math.sin(node.angle) * r,
        });
      }

      drawData.push({ points, alpha: fade, progress });
    }

    return drawData;
  }

  get activeCount(): number {
    return this.waves.filter(w => w.active).length;
  }
}
