# GUAPPA Neural Swarm — React Native Android Implementation Guide

## For React Native Mobile Developers

**Version:** 1.0  
**Target:** Android (React Native + react-native-skia)  
**AI Backend:** Dedicated "Swarm Director" LLM (separate from main agent)

---

## Table of Contents

1. Architecture Overview
2. Technology Stack
3. Project Structure
4. Core Rendering — Skia Canvas Neural Swarm
5. 3D Projection & Accelerometer Integration
6. State Machine — Idle, Listen, Think, Speak
7. Emotion Detection Pipeline
8. Intent Recognition & Shape Formation
9. Text Formation System
10. The "Swarm Director" LLM — Architecture
11. Voice Integration
12. Performance Optimization
13. Full Source Code
14. Debug Controls (HTML Prototype Reference)

---

## 1. Architecture Overview

The GUAPPA Neural Swarm is a living visualization of 400+ nodes rendered on a Skia canvas that reacts to voice input, emotional tone, and user intent in real time. The architecture separates concerns across three layers:

```
┌────────────────────────────────────────────────────┐
│                    UI Layer                         │
│  @shopify/react-native-skia Canvas                 │
│  - 420 neurons with 3D projection                  │
│  - Connections, synaptic fires, data flows         │
│  - Harmonic pulse waves                            │
│  - Shape/text formation animations                 │
└──────────────────────┬─────────────────────────────┘
                       │ State updates at 60fps
┌──────────────────────┴─────────────────────────────┐
│               Swarm Controller                      │
│  React state + Reanimated shared values             │
│  - Current AI state (idle/listen/think/speak)       │
│  - Current emotion (20 emotions)                    │
│  - Current formation (shape/text/scatter)           │
│  - Voice amplitude (from audio stream)              │
│  - Accelerometer data (device orientation)          │
└──────────┬──────────────────────┬──────────────────┘
           │                      │
┌──────────┴──────┐  ┌───────────┴──────────────────┐
│  Voice Pipeline  │  │  Swarm Director LLM           │
│  STT streaming   │  │  (NOT the main agent)         │
│  Audio amplitude │  │  - Emotion classification      │
│  TTS playback    │  │  - Intent → shape mapping      │
│                  │  │  - Text extraction for display  │
│                  │  │  Lightweight, <200ms latency    │
└─────────────────┘  └────────────────────────────────┘
```

**Critical design principle:** The Swarm Director is a *separate* LLM call pipeline from the main GUAPPA agent. The main agent handles reasoning, tool use, and conversation. The Swarm Director only analyzes transcription snippets to produce visualization commands. This keeps visualization responsive (sub-200ms) and decoupled from potentially slow agent reasoning.

---

## 2. Technology Stack

| Component | Technology                                    | Purpose |
|-----------|-----------------------------------------------|---------|
| Rendering | `@shopify/react-native-skia`                  | GPU-accelerated 2D canvas with GLSL shader support |
| Animation | `react-native-reanimated` 3+                  | 60fps shared values on UI thread |
| Accelerometer | `expo-sensors` or `react-native-sensors`      | Device orientation for 3D rotation |
| Audio | `expo-av` or `react-native-live-audio-stream` | Microphone amplitude extraction |
| STT | STT model                                     | Streaming transcription with interim results |
| Swarm Director | Fast model                                    | Emotion + intent classification |
| State | Zustand or Jotai                              | Lightweight reactive state |

---

## 3. Project Structure

```
src/
├── swarm/
│   ├── SwarmCanvas.tsx          # Main Skia canvas component
│   ├── SwarmController.ts       # State machine + orchestration
│   ├── SwarmDirector.ts         # LLM integration for emotion/intent
│   ├── neurons/
│   │   ├── NeuronSystem.ts      # Node physics, positions, energy
│   │   ├── ConnectionSystem.ts  # Edge rendering, data flows
│   │   └── FireSystem.ts        # Synaptic fire particles
│   ├── formations/
│   │   ├── ShapeLibrary.ts      # All shape point generators
│   │   ├── TextRenderer.ts      # Pixel font text → point cloud
│   │   └── FaceLibrary.ts       # Emoji face formations
│   ├── waves/
│   │   └── HarmonicWaves.ts     # Organic pulse wave rings
│   ├── camera/
│   │   ├── Camera3D.ts          # 3D projection math
│   │   └── AccelerometerBridge.ts # Device orientation → rotation
│   ├── emotion/
│   │   ├── EmotionPalette.ts    # 20 emotion color definitions
│   │   └── EmotionBlender.ts    # Smooth HSL transitions
│   └── audio/
│       ├── VoiceAmplitude.ts    # Real-time amplitude extraction
│       └── TranscriptStream.ts  # Interim STT results
```

---

## 4. Core Rendering — Skia Canvas Neural Swarm

The entire swarm renders on a single `<Canvas>` from `@shopify/react-native-skia`. This is non-negotiable — React Native views cannot handle 420 animated elements at 60fps.

### SwarmCanvas.tsx

```tsx
import React, { useEffect, useRef } from 'react';
import { Canvas, useCanvasRef, Path, Circle, 
         Paint, LinearGradient, RadialGradient,
         Skia, useClockValue, useComputedValue } from '@shopify/react-native-skia';
import { useSharedValue, useDerivedValue, 
         withSpring, withTiming } from 'react-native-reanimated';
import { useWindowDimensions } from 'react-native';
import { useSwarmStore } from './SwarmController';
import { NeuronSystem } from './neurons/NeuronSystem';
import { Camera3D } from './camera/Camera3D';

/**
 * Main swarm canvas — renders the entire neural visualization.
 * This replaces the HTML <canvas> from the prototype.
 * 
 * NOTE: All buttons, labels, emotion bar, volume slider in the HTML
 * prototype are DEBUG CONTROLS ONLY. They are not part of the mobile
 * app UI. In production, state changes come from:
 *   - Voice pipeline (idle ↔ listening ↔ speaking)
 *   - Main agent (→ processing when thinking)
 *   - Swarm Director LLM (emotion, formations)
 */
export const SwarmCanvas: React.FC = () => {
  const { width, height } = useWindowDimensions();
  const cx = width / 2;
  const cy = height / 2;

  // Shared values driven by SwarmController
  const state = useSwarmStore(s => s.state);
  const emotion = useSwarmStore(s => s.emotion);
  const formation = useSwarmStore(s => s.formation);
  const voiceAmplitude = useSwarmStore(s => s.amplitude);

  // The neuron system manages all 420 nodes
  const system = useRef(new NeuronSystem(420, width, height)).current;
  const camera = useRef(new Camera3D(width, height)).current;

  // Clock for animation
  const clock = useClockValue();

  // On every frame, update physics and get draw commands
  const frame = useComputedValue(() => {
    const t = clock.current / 1000;
    system.update(t, state, emotion, voiceAmplitude);
    camera.update(t);
    return system.getDrawData(camera);
  }, [clock]);

  return (
    <Canvas style={{ flex: 1, backgroundColor: '#020206' }}>
      {/* Nebula background — static, rendered once */}
      <NebulaBackground width={width} height={height} emotion={emotion} />

      {/* Stars */}
      <StarField width={width} height={height} clock={clock} />

      {/* Main neural network rendering happens in a custom drawing hook */}
      <SwarmRenderer frame={frame} emotion={emotion} />
    </Canvas>
  );
};
```

### NeuronSystem.ts — The Brain

```typescript
interface Neuron {
  // 3D position
  x: number; y: number; z: number;
  // Target position (for formation morphing)
  tx: number; ty: number; tz: number;
  // Velocity
  vx: number; vy: number; vz: number;
  // Visual properties
  size: number;
  brightness: number;
  phase: number;
  energy: number;
  targetEnergy: number;
  // Drift (organic floating motion)
  driftFreqX: number; driftFreqY: number;
  driftAmpX: number; driftAmpY: number;
  // Connections
  connections: number[];
}

interface SynapticFire {
  from: number;
  to: number;
  progress: number; // 0..1
}

interface DataFlow {
  chain: number[];   // node indices for multi-hop path
  progress: number;  // 0..1
  speed: number;
  size: number;
}

export class NeuronSystem {
  neurons: Neuron[] = [];
  fires: SynapticFire[] = [];
  dataFlows: DataFlow[] = [];
  
  private spread: number;
  private cx: number;
  private cy: number;

  constructor(count: number, width: number, height: number) {
    this.cx = width / 2;
    this.cy = height / 2;
    this.spread = Math.min(width, height) * 0.36;
    this.initializeNeurons(count);
    this.buildConnections();
  }

  private initializeNeurons(count: number) {
    for (let i = 0; i < count; i++) {
      const angle = Math.random() * Math.PI * 2;
      const r = 0.15 + Math.random() * 0.65;
      const x = Math.cos(angle) * r;
      const y = Math.sin(angle) * r;
      const z = (Math.random() - 0.5) * 0.5;

      this.neurons.push({
        x: this.cx + x * this.spread,
        y: this.cy + y * this.spread,
        z: z * this.spread * 0.5,
        tx: this.cx + x * this.spread,
        ty: this.cy + y * this.spread,
        tz: z * this.spread * 0.5,
        vx: 0, vy: 0, vz: 0,
        size: 1 + Math.random() * 2.8,
        brightness: 0.35 + Math.random() * 0.55,
        phase: Math.random() * Math.PI * 2,
        energy: 0.12 + Math.random() * 0.12,
        targetEnergy: 0.12,
        driftFreqX: 0.04 + Math.random() * 0.06,
        driftFreqY: 0.03 + Math.random() * 0.05,
        driftAmpX: 1 + Math.random() * 3,
        driftAmpY: 1 + Math.random() * 3,
        connections: [],
      });
    }
  }

  private buildConnections() {
    const count = this.neurons.length;
    for (let i = 0; i < count; i++) {
      const distances: { j: number; d: number }[] = [];
      for (let j = 0; j < count; j++) {
        if (j === i) continue;
        const dx = this.neurons[j].x - this.neurons[i].x;
        const dy = this.neurons[j].y - this.neurons[i].y;
        const dz = this.neurons[j].z - this.neurons[i].z;
        distances.push({ j, d: Math.sqrt(dx*dx + dy*dy + dz*dz) });
      }
      distances.sort((a, b) => a.d - b.d);
      this.neurons[i].connections = distances
        .slice(0, 2 + Math.floor(Math.random() * 2))
        .map(d => d.j);
    }
  }

  /**
   * Set formation targets — nodes will spring-animate toward these positions.
   * Called by SwarmController when Swarm Director issues a formation command.
   */
  setFormation(points: { x: number; y: number; z: number }[]) {
    for (let i = 0; i < this.neurons.length; i++) {
      const p = points[i % points.length];
      this.neurons[i].tx = this.cx + p.x * this.spread;
      this.neurons[i].ty = this.cy + p.y * this.spread;
      this.neurons[i].tz = p.z * this.spread * 0.5;
    }
  }

  /**
   * Main physics update — called every frame (~16ms).
   * All state-dependent behavior is parameterized through the state config.
   */
  update(
    time: number,
    state: SwarmState,
    emotion: EmotionState,
    amplitude: number,
  ) {
    const config = STATE_CONFIGS[state];
    const globalPulse = Math.sin(time * config.pulseFreq) * config.pulseAmp;

    // Update each neuron
    for (const n of this.neurons) {
      // Organic drift
      const driftX = Math.sin(time * n.driftFreqX + n.phase) 
                   * n.driftAmpX * (1 + globalPulse * 0.5);
      const driftY = Math.cos(time * n.driftFreqY + n.phase * 1.3) 
                   * n.driftAmpY * (1 + globalPulse * 0.5);

      // Collective breathing
      const breatheScale = 1 + Math.sin(time * config.pulseFreq * 0.4) 
                         * config.breatheAmp;
      const btx = this.cx + (n.tx - this.cx) * breatheScale;
      const bty = this.cy + (n.ty - this.cy) * breatheScale;

      // Spring physics toward target
      const springK = 0.012;
      const ax = (btx + driftX - n.x) * springK;
      const ay = (bty + driftY - n.y) * springK;
      const az = (n.tz - n.z) * springK;

      // Gravitational pull toward center (state-dependent)
      const gx = (this.cx - n.x) * config.gravity;
      const gy = (this.cy - n.y) * config.gravity;

      // Damped velocity integration
      n.vx = (n.vx + ax + gx) * 0.92;
      n.vy = (n.vy + ay + gy) * 0.92;
      n.vz = (n.vz + az) * 0.92;

      n.x += n.vx * config.speed;
      n.y += n.vy * config.speed;
      n.z += n.vz * config.speed;

      // Energy decay + pulse
      n.targetEnergy = 0.1 + config.pulseAmp * 0.3 
                     + Math.sin(time + n.phase) * 0.04 * config.pulseAmp;
      n.energy += (n.targetEnergy - n.energy) * 0.02;
    }

    // Synaptic fires
    this.updateFires(time, config, state);

    // Data flow particles (thinking mode)
    if (state === 'processing') {
      this.updateDataFlows(time, config);
    }
  }

  private updateFires(time: number, config: StateConfig, state: SwarmState) {
    // Spawn new fires
    if (Math.random() < config.fireRate) {
      const src = Math.floor(Math.random() * this.neurons.length);
      const conns = this.neurons[src].connections;
      if (conns.length > 0) {
        this.fires.push({
          from: src,
          to: conns[Math.floor(Math.random() * conns.length)],
          progress: 0,
        });
      }
    }

    // Update existing fires
    for (let i = this.fires.length - 1; i >= 0; i--) {
      this.fires[i].progress += 0.014 * 1.6 * config.speed;
      
      if (this.fires[i].progress >= 1) {
        // Light up target neuron
        const target = this.neurons[this.fires[i].to];
        target.energy = Math.min(1, target.energy + 0.25);

        // Chain reaction in processing mode (55% chance)
        if (state === 'processing' && Math.random() < 0.55) {
          const nextConns = target.connections;
          if (nextConns.length > 0) {
            this.fires.push({
              from: this.fires[i].to,
              to: nextConns[Math.floor(Math.random() * nextConns.length)],
              progress: 0,
            });
          }
        }

        this.fires.splice(i, 1);
      }
    }
  }

  private updateDataFlows(time: number, config: StateConfig) {
    // Spawn data flows — multi-hop light streams
    if (Math.random() < 0.15 && this.dataFlows.length < 120) {
      const src = Math.floor(Math.random() * this.neurons.length);
      const chain = [src];
      let current = src;
      const hops = 2 + Math.floor(Math.random() * 3);

      for (let h = 0; h < hops; h++) {
        const conns = this.neurons[current].connections;
        if (conns.length > 0) {
          current = conns[Math.floor(Math.random() * conns.length)];
          chain.push(current);
        }
      }

      if (chain.length > 1) {
        this.dataFlows.push({
          chain,
          progress: 0,
          speed: 0.003 + Math.random() * 0.004,
          size: 1 + Math.random() * 1.5,
        });
      }
    }

    // Update existing flows
    for (let i = this.dataFlows.length - 1; i >= 0; i--) {
      this.dataFlows[i].progress += this.dataFlows[i].speed * (config.speed + 0.1);
      if (this.dataFlows[i].progress >= 1) {
        this.dataFlows.splice(i, 1);
      }
    }
  }
}
```

---

## 5. 3D Projection & Accelerometer Integration

### Camera3D.ts

Each state has a unique organic rotation profile. The accelerometer provides additional user-driven rotation layered on top.

```typescript
import { Accelerometer } from 'expo-sensors';

interface RotationProfile {
  rotXamp: number; rotXfreq: number;
  rotYamp: number; rotYfreq: number;
  rotZamp: number; rotZfreq: number;
}

// Each state feels different in 3D space
const ROTATION_PROFILES: Record<string, RotationProfile> = {
  idle: {
    // Gentle slow orbit — like breathing in space
    rotXamp: 0.08, rotXfreq: 0.07,
    rotYamp: 0.12, rotYfreq: 0.05,
    rotZamp: 0.03, rotZfreq: 0.03,
  },
  listening: {
    // Tilts toward user, slight nodding — attentive
    rotXamp: 0.15, rotXfreq: 0.2,
    rotYamp: 0.06, rotYfreq: 0.12,
    rotZamp: 0.04, rotZfreq: 0.15,
  },
  processing: {
    // Slow deliberate rotation — examining all angles of a thought
    rotXamp: 0.10, rotXfreq: 0.04,
    rotYamp: 0.20, rotYfreq: 0.06,
    rotZamp: 0.06, rotZfreq: 0.025,
  },
  speaking: {
    // Expressive swaying — rhythmic, like gesturing
    rotXamp: 0.06, rotXfreq: 0.1,
    rotYamp: 0.10, rotYfreq: 0.15,
    rotZamp: 0.05, rotZfreq: 0.08,
  },
};

export class Camera3D {
  // Current rotation angles
  rotX = 0; rotY = 0; rotZ = 0;

  // User input (accelerometer)
  private userRotX = 0;
  private userRotY = 0;

  // Auto-rotation from state
  private autoRotX = 0;
  private autoRotY = 0;
  private autoRotZ = 0;

  private perspective = 600;
  private cx: number;
  private cy: number;
  private currentProfile: RotationProfile = ROTATION_PROFILES.idle;

  private accelSubscription: any = null;

  constructor(width: number, height: number) {
    this.cx = width / 2;
    this.cy = height / 2;
    this.initAccelerometer();
  }

  private initAccelerometer() {
    Accelerometer.setUpdateInterval(16); // 60fps

    this.accelSubscription = Accelerometer.addListener(({ x, y, z }) => {
      // Map device tilt to rotation
      // x: roll (left-right tilt)
      // y: pitch (forward-backward tilt)
      this.userRotY = x * 0.4;           // tilt left/right → rotate Y
      this.userRotX = (y - 0.5) * 0.35;  // tilt forward/back → rotate X
    });
  }

  setProfile(state: string) {
    this.currentProfile = ROTATION_PROFILES[state] || ROTATION_PROFILES.idle;
  }

  update(time: number) {
    const p = this.currentProfile;

    // State-driven organic auto-rotation
    this.autoRotX = Math.sin(time * p.rotXfreq) * p.rotXamp;
    this.autoRotY = Math.sin(time * p.rotYfreq + 0.7) * p.rotYamp;
    this.autoRotZ = Math.sin(time * p.rotZfreq + 1.4) * p.rotZamp;

    // Blend user input + auto rotation (very smooth lerp)
    this.rotX += (this.userRotX + this.autoRotX - this.rotX) * 0.015;
    this.rotY += (this.userRotY + this.autoRotY - this.rotY) * 0.015;
    this.rotZ += (this.autoRotZ - this.rotZ) * 0.01;
  }

  /**
   * Project a 3D point to 2D screen coordinates.
   * Full XYZ rotation → perspective projection.
   */
  project(x: number, y: number, z: number): {
    sx: number; sy: number; scale: number; depth: number;
  } {
    let dx = x - this.cx;
    let dy = y - this.cy;
    let dz = z;

    // Rotate Z
    let c = Math.cos(this.rotZ), s = Math.sin(this.rotZ);
    let rx = dx * c - dy * s;
    let ry = dx * s + dy * c;
    dx = rx; dy = ry;

    // Rotate Y
    c = Math.cos(this.rotY); s = Math.sin(this.rotY);
    rx = dx * c - dz * s;
    let rz = dx * s + dz * c;
    dx = rx; dz = rz;

    // Rotate X
    c = Math.cos(this.rotX); s = Math.sin(this.rotX);
    ry = dy * c - dz * s;
    rz = dy * s + dz * c;
    dy = ry; dz = rz;

    // Perspective projection
    const scale = this.perspective / (this.perspective + dz);
    return {
      sx: this.cx + dx * scale,
      sy: this.cy + dy * scale,
      scale,
      depth: dz,
    };
  }

  destroy() {
    this.accelSubscription?.remove();
  }
}
```

---

## 6. State Machine — Idle, Listen, Think, Speak

States are driven by the voice pipeline and main agent, NOT by user buttons (those are debug only).

```typescript
// SwarmController.ts
import { create } from 'zustand';

type SwarmState = 'idle' | 'listening' | 'processing' | 'speaking';
type EmotionKey = keyof typeof EMOTION_PALETTE;

interface SwarmStore {
  state: SwarmState;
  emotion: EmotionKey;
  formation: string | null;    // current shape/text formation
  amplitude: number;           // voice amplitude 0..1
  
  setState: (s: SwarmState) => void;
  setEmotion: (e: EmotionKey) => void;
  setFormation: (f: string | null) => void;
  setAmplitude: (a: number) => void;
}

export const useSwarmStore = create<SwarmStore>((set) => ({
  state: 'idle',
  emotion: 'neutral',
  formation: null,
  amplitude: 0,
  
  setState: (state) => set({ state }),
  setEmotion: (emotion) => set({ emotion }),
  setFormation: (formation) => set({ formation }),
  setAmplitude: (amplitude) => set({ amplitude }),
}));

/**
 * State transition rules:
 * 
 * User taps mic / starts speaking   → 'listening'
 * User stops speaking               → 'idle' (or 'processing' if agent is thinking)
 * Agent starts reasoning             → 'processing'
 * Agent starts TTS output            → 'speaking'
 * Agent finishes speaking            → 'idle'
 * 
 * These transitions happen automatically through the voice pipeline.
 * The HTML prototype buttons are DEBUG CONTROLS ONLY.
 */
```

### State Configuration (matches HTML prototype parameters)

```typescript
export const STATE_CONFIGS = {
  idle: {
    speed: 0.25, connAlpha: 0.05, fireRate: 0.005,
    gravity: 0, spread: 1.0,
    pulseAmp: 0.1, pulseFreq: 0.5,
    breatheAmp: 0.025, coreGlow: 0.03,
    waveActive: false, thinkConns: false,
  },
  listening: {
    speed: 0.45, connAlpha: 0.14, fireRate: 0.03,
    gravity: 0.001, spread: 0.55,
    pulseAmp: 0.22, pulseFreq: 1.2,
    breatheAmp: 0.05, coreGlow: 0.06,
    waveActive: true, thinkConns: false,
  },
  processing: {
    speed: 0.55, connAlpha: 0.28, fireRate: 0.06,
    gravity: 0.0003, spread: 0.72,
    pulseAmp: 0.16, pulseFreq: 0.5,
    breatheAmp: 0.035, coreGlow: 0.09,
    waveActive: false, thinkConns: true,
    // ↑ thinkConns: true → increased connection density + data flow streams
  },
  speaking: {
    speed: 0.35, connAlpha: 0.12, fireRate: 0.018,
    gravity: -0.0004, spread: 1.15,
    pulseAmp: 0.13, pulseFreq: 0.7,
    breatheAmp: 0.03, coreGlow: 0.05,
    waveActive: true, thinkConns: false,
  },
};
```

---

## 7. Emotion Detection Pipeline

Emotion is detected by the Swarm Director LLM from streaming transcription. The swarm color shifts smoothly between any of 20 emotion palettes.

### EmotionPalette.ts — 20 Emotions

```typescript
export const EMOTION_PALETTE = {
  neutral:    { h: 220, s: 12, l: 76 },  // Platinum
  curious:    { h: 200, s: 45, l: 72 },  // Ocean blue
  happy:      { h: 42,  s: 50, l: 78 },  // Warm gold
  love:       { h: 338, s: 55, l: 74 },  // Rose
  focused:    { h: 265, s: 48, l: 70 },  // Deep violet
  alert:      { h: 15,  s: 58, l: 70 },  // Coral
  calm:       { h: 170, s: 32, l: 73 },  // Sage teal
  sad:        { h: 225, s: 28, l: 56 },  // Deep indigo (dimmer)
  excited:    { h: 30,  s: 65, l: 74 },  // Bright amber
  mysterious: { h: 280, s: 45, l: 65 },  // Deep purple
  grateful:   { h: 50,  s: 35, l: 75 },  // Soft gold
  proud:      { h: 40,  s: 55, l: 72 },  // Warm amber
  playful:    { h: 310, s: 50, l: 75 },  // Magenta
  anxious:    { h: 190, s: 30, l: 60 },  // Muted teal
  inspired:   { h: 55,  s: 60, l: 80 },  // Bright yellow
  nostalgic:  { h: 25,  s: 35, l: 68 },  // Warm sunset
  angry:      { h: 5,   s: 65, l: 58 },  // Deep red
  surprised:  { h: 48,  s: 60, l: 78 },  // Bright gold
  confused:   { h: 240, s: 25, l: 62 },  // Blue-gray
  sleepy:     { h: 250, s: 15, l: 50 },  // Dim lavender
} as const;
```

### EmotionBlender.ts — Smooth Transitions

```typescript
/**
 * Blends between emotion colors at 0.6% per frame.
 * A full transition takes ~2-3 seconds — deliberate and organic.
 */
export class EmotionBlender {
  current = { h: 220, s: 12, l: 76 };
  
  update(targetEmotion: keyof typeof EMOTION_PALETTE) {
    const target = EMOTION_PALETTE[targetEmotion];
    const rate = 0.006; // Very slow blend
    
    this.current.h += (target.h - this.current.h) * rate;
    this.current.s += (target.s - this.current.s) * rate;
    this.current.l += (target.l - this.current.l) * rate;
  }

  /** Get current HSL as CSS string for Skia Paint */
  getColor(alpha: number = 1): string {
    return `hsla(${this.current.h}, ${this.current.s}%, ${this.current.l}%, ${alpha})`;
  }
}
```

---

## 8. Intent Recognition & Shape Formation

The Swarm Director analyzes streaming transcript fragments and issues formation commands.

### How Intent → Shape Works

```
Agent says: "I love you"
  ↓ Swarm Director classifies:
      emotion: "love"
      formation: "heart"       ← shape
      display_text: null
  ↓ SwarmController:
      setEmotion('love')       → color shifts to rose
      setFormation('heart')    → nodes spring-animate into heart shape

User says: "Say hello to everyone"
  ↓ STT interim: "Say hello"
  ↓ Swarm Director:
      emotion: "happy"
      formation: null
      display_text: "HELLO"    ← text formation
  ↓ SwarmController:
      setEmotion('happy')
      neuronSystem.setFormation(TextRenderer.generate("HELLO", 420))

User asks a complex question
  ↓ Main agent starts processing
  ↓ State → 'processing'
  ↓ No formation change, but:
      - Connection density increases 2.5×
      - Data flow light streams appear (120 particles)
      - Synaptic chain reactions cascade at 55%
      - 3D rotation shifts to slow deliberate examination
```

### ShapeLibrary.ts — All Formations

```typescript
/**
 * Generates normalized point clouds for each shape.
 * Points are in [-1, 1] range, the NeuronSystem scales them to screen.
 * 
 * Port these directly from the HTML prototype's sP() function.
 * Each returns {x, y, z}[] of length `count`.
 */
export function generateFormation(
  shape: string, 
  count: number
): { x: number; y: number; z: number }[] {
  switch (shape) {
    case 'heart':       return heartPoints(count);
    case 'star':        return starPoints(count);
    case 'brain':       return brainPoints(count);
    case 'eye':         return eyePoints(count);
    case 'infinity':    return infinityPoints(count);
    case 'dna':         return dnaPoints(count);
    case 'atom':        return atomPoints(count);
    case 'spiral':      return spiralPoints(count);
    case 'lightning':   return lightningPoints(count);
    case 'diamond':     return diamondPoints(count);
    case 'crown':       return crownPoints(count);
    case 'moon':        return moonPoints(count);
    case 'fire':        return firePoints(count);
    case 'music':       return musicPoints(count);
    case 'tree':        return treePoints(count);
    // Emoji faces
    case 'face_happy':  return faceHappyPoints(count);
    case 'face_sad':    return faceSadPoints(count);
    case 'face_love':   return faceLovePoints(count);
    case 'face_angry':  return faceAngryPoints(count);
    case 'face_surprise': return faceSurprisePoints(count);
    case 'face_think':  return faceThinkPoints(count);
    case 'face_wink':   return faceWinkPoints(count);
    case 'face_cool':   return faceCoolPoints(count);
    case 'face_cry':    return faceCryPoints(count);
    case 'face_laugh':  return faceLaughPoints(count);
    case 'face_sleep':  return faceSleepPoints(count);
    case 'face_scared': return faceScaredPoints(count);
    case 'scatter':
    default:            return scatterPoints(count);
  }
}

// Example: Heart formation (ported from HTML prototype)
function heartPoints(count: number) {
  const points = [];
  const jitter = (v = 0.06) => (Math.random() - 0.5) * v;
  for (let i = 0; i < count; i++) {
    const t = (i / count) * Math.PI * 2;
    const x = 16 * Math.pow(Math.sin(t), 3) / 18 + jitter();
    const y = -(13 * Math.cos(t) - 5 * Math.cos(2*t) 
              - 2 * Math.cos(3*t) - Math.cos(4*t)) / 18 + jitter();
    points.push({ x, y, z: (Math.random() - 0.5) * 0.2 });
  }
  return points;
}

// ... port all other shapes from the HTML prototype's sP() function
```

---

## 9. Text Formation System

### TextRenderer.ts

```typescript
/**
 * Pixel font: each character is a 5×5 grid.
 * Text is converted to point positions that neurons target.
 * 
 * Ported directly from the HTML prototype's FN object and tP() function.
 */
const PIXEL_FONT: Record<string, string[]> = {
  A: ['01110','10001','11111','10001','10001'],
  B: ['11110','10001','11110','10001','11110'],
  // ... full alphabet from HTML prototype
  ' ': ['000','000','000','000','000'],
};

export function generateTextFormation(
  text: string, 
  nodeCount: number
): { x: number; y: number; z: number }[] {
  const chars = text.toUpperCase().split('');
  const pixels: { x: number; y: number }[] = [];
  
  chars.forEach((char, charIndex) => {
    const glyph = PIXEL_FONT[char] || PIXEL_FONT[' '];
    glyph.forEach((row, rowY) => {
      row.split('').forEach((pixel, colX) => {
        if (pixel === '1') {
          pixels.push({ x: charIndex * 6 + colX, y: rowY });
        }
      });
    });
  });

  if (pixels.length === 0) return scatterPoints(nodeCount);

  const totalWidth = chars.length * 6;
  const totalHeight = 5;
  const points = [];

  for (let i = 0; i < nodeCount; i++) {
    const p = pixels[i % pixels.length];
    points.push({
      x: (p.x / totalWidth - 0.5) * 1.6 + (Math.random() - 0.5) * (1.2 / totalWidth),
      y: (p.y / totalHeight - 0.5) * 0.9 + (Math.random() - 0.5) * (1.2 / totalHeight),
      z: (Math.random() - 0.5) * 0.12,
    });
  }

  return points;
}
```

---

## 10. The "Swarm Director" LLM — Architecture

This is the most important architectural decision: **the Swarm Director is a completely separate LLM pipeline from the main GUAPPA agent.**

### Why Separate?

The main agent may take 2-10+ seconds to reason, call tools, and compose a response. The swarm visualization must react in under 200ms to feel alive. A separate, fast, lightweight LLM call handles only visualization decisions.

### SwarmDirector.ts

```typescript
import Anthropic from '@anthropic-ai/sdk';

interface SwarmDirective {
  emotion: string;           // one of 20 emotion keys
  formation: string | null;  // shape name or null
  display_text: string | null; // text to form, or null
}

const SWARM_DIRECTOR_PROMPT = `You are the Swarm Director for GUAPPA, an AI visualization system.
You analyze conversation transcript fragments and output ONLY a JSON object 
that controls the neural swarm visualization.

Your job: detect emotion, and when appropriate, suggest a visual formation 
(shape or text) the swarm should form.

RULES:
- Respond ONLY with valid JSON. No markdown, no explanation.
- emotion: one of: neutral, curious, happy, love, focused, alert, calm, sad, 
  excited, mysterious, grateful, proud, playful, anxious, inspired, nostalgic,
  angry, surprised, confused, sleepy
- formation: one of: heart, star, brain, eye, infinity, dna, atom, spiral,
  lightning, diamond, crown, moon, fire, music, tree, wave, check,
  face_happy, face_sad, face_love, face_angry, face_surprise, face_think,
  face_wink, face_cool, face_cry, face_laugh, face_sleep, face_scared,
  scatter, OR null (no change)
- display_text: short text (1-6 chars) to form with nodes, or null
- Only suggest formation/display_text when strongly relevant to what was said.
  Most of the time, just set emotion and leave formation/display_text null.

Examples:
User: "I love this!"        → {"emotion":"love","formation":"heart","display_text":null}
User: "What time is it?"    → {"emotion":"curious","formation":null,"display_text":null}
User: "Say hi to everyone"  → {"emotion":"happy","formation":null,"display_text":"HI"}
User: "I'm so angry"       → {"emotion":"angry","formation":"face_angry","display_text":null}
User: "Let me think..."    → {"emotion":"focused","formation":"brain","display_text":null}
User: "Good night"         → {"emotion":"calm","formation":"moon","display_text":null}
User: "YES!"               → {"emotion":"excited","formation":null,"display_text":"YES"}`;

export class SwarmDirector {
  private client: Anthropic;
  private debounceTimer: NodeJS.Timeout | null = null;
  private lastTranscript = '';

  constructor(apiKey: string) {
    this.client = new Anthropic({ apiKey });
  }

  /**
   * Called with every interim STT transcript update.
   * Debounced to max 3 calls/second to avoid API spam.
   */
  async analyzeTranscript(transcript: string): Promise<SwarmDirective | null> {
    // Skip if transcript hasn't meaningfully changed
    if (transcript === this.lastTranscript) return null;
    if (transcript.length < 3) return null;
    
    this.lastTranscript = transcript;

    // Debounce: wait 300ms of no new input before calling
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    
    return new Promise((resolve) => {
      this.debounceTimer = setTimeout(async () => {
        try {
          const response = await this.client.messages.create({
            model: 'claude-haiku-4-5-20251001', // Fast model for <200ms
            max_tokens: 100,
            system: SWARM_DIRECTOR_PROMPT,
            messages: [{ role: 'user', content: transcript }],
          });

          const text = response.content[0].type === 'text' 
            ? response.content[0].text : '';
          const directive = JSON.parse(text) as SwarmDirective;
          resolve(directive);
        } catch (err) {
          console.warn('SwarmDirector error:', err);
          resolve(null);
        }
      }, 300);
    });
  }

  /**
   * Called when the main agent starts/stops speaking.
   * Analyzes the agent's response for emotion.
   */
  async analyzeAgentResponse(text: string): Promise<SwarmDirective | null> {
    try {
      const response = await this.client.messages.create({
        model: '<fast model>',
        max_tokens: 100,
        system: SWARM_DIRECTOR_PROMPT,
        messages: [{ 
          role: 'user', 
          content: `[GUAPPA is saying this to the user]: ${text.slice(0, 200)}` 
        }],
      });

      const parsed = response.content[0].type === 'text'
        ? response.content[0].text : '{}';
      return JSON.parse(parsed) as SwarmDirective;
    } catch {
      return null;
    }
  }
}
```

### Integration in Voice Pipeline

```typescript
// In your main voice handling component:

const swarmDirector = new SwarmDirector(ANTHROPIC_API_KEY);
const swarmStore = useSwarmStore;

// When STT provides interim results:
sttEngine.onInterimResult = async (transcript: string) => {
  // Update swarm visualization (fast, independent of main agent)
  const directive = await swarmDirector.analyzeTranscript(transcript);
  if (directive) {
    swarmStore.getState().setEmotion(directive.emotion);
    
    if (directive.formation) {
      const points = generateFormation(directive.formation, 420);
      neuronSystem.setFormation(points);
    }
    
    if (directive.display_text) {
      const points = generateTextFormation(directive.display_text, 420);
      neuronSystem.setFormation(points);
    }
  }
};

// When main agent starts reasoning:
mainAgent.onProcessingStart = () => {
  swarmStore.getState().setState('processing');
  // Formation stays as-is; processing state triggers:
  // - Denser connections
  // - Data flow light streams
  // - Chain-reaction synaptic fires
  // - Deliberate 3D rotation
};

// When main agent starts speaking (TTS):
mainAgent.onSpeakingStart = async (responseText: string) => {
  swarmStore.getState().setState('speaking');
  
  // Analyze what the agent is saying for emotion
  const directive = await swarmDirector.analyzeAgentResponse(responseText);
  if (directive) {
    swarmStore.getState().setEmotion(directive.emotion);
    if (directive.formation) {
      neuronSystem.setFormation(generateFormation(directive.formation, 420));
    }
  }
};

// When agent finishes speaking:
mainAgent.onSpeakingEnd = () => {
  swarmStore.getState().setState('idle');
  // Return to scatter formation after 3 seconds
  setTimeout(() => {
    neuronSystem.setFormation(generateFormation('scatter', 420));
  }, 3000);
};
```

---

## 11. Voice Integration — Amplitude Extraction

```typescript
// VoiceAmplitude.ts
import { Audio } from 'expo-av';

/**
 * Extracts real-time amplitude from microphone input.
 * This drives the harmonic wave deformation intensity.
 * 
 * Quiet voice → barely visible wave deformation
 * Loud voice → dramatic organic shapes (exponential scaling)
 */
export class VoiceAmplitude {
  private recording: Audio.Recording | null = null;
  private onAmplitude: (value: number) => void;
  private interval: NodeJS.Timeout | null = null;

  constructor(onAmplitude: (value: number) => void) {
    this.onAmplitude = onAmplitude;
  }

  async start() {
    await Audio.requestPermissionsAsync();
    this.recording = new Audio.Recording();
    
    await this.recording.prepareToRecordAsync(
      Audio.RecordingOptionsPresets.HIGH_QUALITY
    );
    await this.recording.startAsync();

    // Poll amplitude at 60fps
    this.interval = setInterval(async () => {
      if (!this.recording) return;
      const status = await this.recording.getStatusAsync();
      if (status.isRecording && status.metering != null) {
        // Convert dB to 0..1 range
        // metering is typically -160 (silence) to 0 (max)
        const db = status.metering;
        const normalized = Math.max(0, Math.min(1, (db + 60) / 60));
        this.onAmplitude(normalized);
      }
    }, 16);
  }

  async stop() {
    if (this.interval) clearInterval(this.interval);
    if (this.recording) {
      await this.recording.stopAndUnloadAsync();
      this.recording = null;
    }
  }
}
```

---

## 12. Performance Optimization

### Critical Rules for 60fps on Android

1. **Single Skia Canvas** — Never use individual React Native `<View>` elements for neurons. Everything draws on one `<Canvas>`.

2. **Avoid JavaScript thread** — All animation math should run on the UI thread via Reanimated worklets where possible. For the complex physics in `NeuronSystem`, use `requestAnimationFrame` on a web worker or accept JS thread usage with the understanding that Skia rendering is GPU-accelerated.

3. **Spatial indexing** — For connection distance checks across 420 nodes, use a simple grid hash instead of O(n²) comparisons:

```typescript
class SpatialGrid {
  private cellSize: number;
  private grid: Map<string, number[]> = new Map();

  constructor(cellSize: number) {
    this.cellSize = cellSize;
  }

  clear() { this.grid.clear(); }

  insert(index: number, x: number, y: number) {
    const key = `${Math.floor(x / this.cellSize)},${Math.floor(y / this.cellSize)}`;
    if (!this.grid.has(key)) this.grid.set(key, []);
    this.grid.get(key)!.push(index);
  }

  getNearby(x: number, y: number): number[] {
    const result: number[] = [];
    const gx = Math.floor(x / this.cellSize);
    const gy = Math.floor(y / this.cellSize);
    for (let dx = -1; dx <= 1; dx++) {
      for (let dy = -1; dy <= 1; dy++) {
        const cell = this.grid.get(`${gx + dx},${gy + dy}`);
        if (cell) result.push(...cell);
      }
    }
    return result;
  }
}
```

4. **Object pooling** — Pre-allocate fire and dataFlow arrays. Never create objects in the render loop:

```typescript
// Pre-allocate pool
const firePool: SynapticFire[] = Array.from({ length: 200 }, () => ({
  from: 0, to: 0, progress: 0, active: false,
}));
```

5. **Reduce draw calls** — Batch connections into a single `Path` instead of individual `drawLine` calls:

```typescript
// BAD: 800 individual draw calls
for (const edge of edges) {
  canvas.drawLine(edge.x1, edge.y1, edge.x2, edge.y2, paint);
}

// GOOD: single path with all segments
const path = Skia.Path.Make();
for (const edge of edges) {
  path.moveTo(edge.x1, edge.y1);
  path.quadTo(edge.mx, edge.my, edge.x2, edge.y2);
}
canvas.drawPath(path, paint);
```

6. **Frame budget** — Target 12ms for physics + 4ms for draw = 16ms total. If you exceed budget, reduce node count (350→250) before reducing visual quality.

---

## 13. Harmonic Pulse Waves — Implementation

The waves are rings of connected node-points deformed by 5 sinusoidal harmonics. Voice amplitude controls deformation intensity exponentially.

```typescript
// HarmonicWaves.ts

const HARMONICS = [
  { freq: 3,  weight: 1.0,  phaseSpeed: 0.5  },
  { freq: 5,  weight: 0.6,  phaseSpeed: -0.8 },
  { freq: 8,  weight: 0.35, phaseSpeed: 1.2  },
  { freq: 13, weight: 0.2,  phaseSpeed: -1.7 },
  { freq: 21, weight: 0.12, phaseSpeed: 2.3  },
];

interface HarmonicWave {
  nodes: { angle: number; energy: number }[];
  radius: number;
  maxRadius: number;
  bornAt: number;
  harmonicPhases: number[];
  baseAmplitude: number;
}

export class HarmonicWaveSystem {
  waves: HarmonicWave[] = [];
  private maxWaves = 6;
  private nodesPerWave = 80;

  spawn(maxRadius: number, time: number) {
    const nodes = Array.from({ length: this.nodesPerWave }, (_, i) => ({
      angle: (i / this.nodesPerWave) * Math.PI * 2,
      energy: 0.5 + Math.random() * 0.5,
    }));

    this.waves.push({
      nodes,
      radius: 5,
      maxRadius,
      bornAt: time,
      harmonicPhases: HARMONICS.map(() => Math.random() * Math.PI * 2),
      baseAmplitude: 8 + Math.random() * 6,
    });

    if (this.waves.length > this.maxWaves) this.waves.shift();
  }

  /**
   * Returns drawable wave data for the current frame.
   * voiceAmplitude controls deformation intensity EXPONENTIALLY:
   *   distortion = volume^2.5 × baseAmplitude
   * 
   * This means:
   *   volume=0.1 → distortion ≈ 0.003 × base (barely visible trembling)
   *   volume=0.5 → distortion ≈ 0.177 × base (gentle organic ripples)
   *   volume=1.0 → distortion ≈ 1.000 × base (dramatic wild shapes)
   */
  update(time: number, voiceAmplitude: number): WaveDrawData[] {
    const drawData: WaveDrawData[] = [];

    for (let i = this.waves.length - 1; i >= 0; i--) {
      const w = this.waves[i];
      w.radius += (0.3 + voiceAmplitude * 0.5) * 1.8;

      if (w.radius > w.maxRadius) {
        this.waves.splice(i, 1);
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
          x: Math.cos(node.angle) * r,  // relative to center
          y: Math.sin(node.angle) * r,
        });
      }

      drawData.push({ points, alpha: fade, progress });
    }

    return drawData;
  }
}
```

---

## 14. Debug Controls — HTML Prototype Reference

**All buttons, text labels, emotion bars, volume sliders, and state controls visible in the HTML prototype (`guappa-cosmic-v5.html`) exist solely for debug and demonstration purposes.**

In the production mobile app:

| HTML Debug Element | Production Equivalent |
|---|---|
| State buttons (Idle/Listen/Think/Speak) | Driven automatically by voice pipeline |
| Emotion buttons | Set by Swarm Director LLM analyzing conversation |
| Shape/emoji buttons | Triggered by Swarm Director intent detection |
| Text buttons (HELLO/GUAPPA/AI) | Triggered by Swarm Director display_text |
| Volume slider | Real microphone amplitude from `expo-av` |
| State label + subtitle | Not shown on Voice screen (swarm IS the UI) |
| Transcript text | Replaced by actual STT interim results overlay |
| Reset button | Automatic — formations revert to scatter after timeout |

The HTML prototype serves as an interactive specification. Every visual behavior, timing constant, and physics parameter has been tuned in the browser and should be ported 1:1 to the React Native implementation.

### Running the Prototype

```bash
# Open in any browser to test all features:
open guappa-cosmic-v5.html

# The prototype includes:
# - 420 neurons with full 3D projection
# - Mouse movement → accelerometer simulation
# - All 20 emotions with smooth color blending
# - All 18 shapes + 12 emoji faces + text formation
# - 4 states with distinct behaviors
# - Harmonic pulse waves with volume slider
# - Data flow light streams in thinking mode
# - State-specific 3D rotation profiles
```

---

## Summary

The GUAPPA Neural Swarm is a living, breathing neural network visualization that communicates the AI's internal state through organic motion, color, and form. The key principles:

1. **Separation of concerns** — The Swarm Director LLM handles only visualization commands. The main agent handles intelligence. Neither blocks the other.

2. **Physics-based animation** — All motion uses spring physics with damping (k=0.012, damp=0.92). Nodes never teleport; they always animate organically toward their targets.

3. **Smooth everything** — State blend rate 0.7%/frame, emotion blend rate 0.6%/frame, camera lerp 1.5%/frame. Full transitions take 2-3 seconds, which creates the feeling of a deliberate, living mind.

4. **Voice-reactive** — Amplitude drives harmonic wave deformation exponentially. The swarm literally vibrates with the user's voice, creating an intimate connection between human speech and AI visualization.

5. **3D as presence** — The accelerometer makes the swarm feel spatially real. Different states have different rotation personalities. The swarm moves like it's thinking, not like it's running code.
