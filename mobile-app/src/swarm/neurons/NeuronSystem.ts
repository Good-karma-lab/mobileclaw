/**
 * NeuronSystem — 420-node physics engine for the GUAPPA Neural Swarm.
 *
 * Each neuron has a 3D position, velocity, target position (for formations),
 * energy level, and connections to nearby neurons. Spring physics animate
 * neurons toward targets with organic drift and collective breathing.
 */

export interface Neuron {
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

export interface SynapticFire {
  from: number;
  to: number;
  progress: number; // 0..1
  active: boolean;
}

export interface DataFlow {
  chain: number[];   // node indices for multi-hop path
  progress: number;  // 0..1
  speed: number;
  size: number;
  active: boolean;
}

export type SwarmState = 'idle' | 'listening' | 'processing' | 'speaking';

export interface StateConfig {
  speed: number;
  connAlpha: number;
  fireRate: number;
  gravity: number;
  spread: number;
  pulseAmp: number;
  pulseFreq: number;
  breatheAmp: number;
  coreGlow: number;
  waveActive: boolean;
  thinkConns: boolean;
}

export const STATE_CONFIGS: Record<SwarmState, StateConfig> = {
  idle: {
    speed: 0.25, connAlpha: 0.18, fireRate: 0.012,
    gravity: 0, spread: 1.0,
    pulseAmp: 0.15, pulseFreq: 0.5,
    breatheAmp: 0.04, coreGlow: 0.12,
    waveActive: false, thinkConns: false,
  },
  listening: {
    speed: 0.45, connAlpha: 0.35, fireRate: 0.06,
    gravity: 0.001, spread: 0.55,
    pulseAmp: 0.30, pulseFreq: 1.2,
    breatheAmp: 0.07, coreGlow: 0.22,
    waveActive: true, thinkConns: false,
  },
  processing: {
    speed: 0.55, connAlpha: 0.50, fireRate: 0.10,
    gravity: 0.0003, spread: 0.72,
    pulseAmp: 0.22, pulseFreq: 0.5,
    breatheAmp: 0.05, coreGlow: 0.30,
    waveActive: false, thinkConns: true,
  },
  speaking: {
    speed: 0.35, connAlpha: 0.30, fireRate: 0.04,
    gravity: -0.0004, spread: 1.15,
    pulseAmp: 0.18, pulseFreq: 0.7,
    breatheAmp: 0.04, coreGlow: 0.18,
    waveActive: true, thinkConns: false,
  },
};

const MAX_FIRES = 200;
const MAX_DATA_FLOWS = 120;

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
    this.initPools();
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
        size: 2.5 + Math.random() * 5.5,
        brightness: 0.6 + Math.random() * 0.4,
        phase: Math.random() * Math.PI * 2,
        energy: 0.25 + Math.random() * 0.20,
        targetEnergy: 0.25,
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
        distances.push({ j, d: Math.sqrt(dx * dx + dy * dy + dz * dz) });
      }
      distances.sort((a, b) => a.d - b.d);
      this.neurons[i].connections = distances
        .slice(0, 2 + Math.floor(Math.random() * 2))
        .map(d => d.j);
    }
  }

  private initPools() {
    for (let i = 0; i < MAX_FIRES; i++) {
      this.fires.push({ from: 0, to: 0, progress: 0, active: false });
    }
    for (let i = 0; i < MAX_DATA_FLOWS; i++) {
      this.dataFlows.push({ chain: [], progress: 0, speed: 0, size: 0, active: false });
    }
  }

  setFormation(points: { x: number; y: number; z: number }[]) {
    for (let i = 0; i < this.neurons.length; i++) {
      const p = points[i % points.length];
      this.neurons[i].tx = this.cx + p.x * this.spread;
      this.neurons[i].ty = this.cy + p.y * this.spread;
      this.neurons[i].tz = p.z * this.spread * 0.5;
    }
  }

  scatter() {
    for (let i = 0; i < this.neurons.length; i++) {
      const angle = Math.random() * Math.PI * 2;
      const r = 0.15 + Math.random() * 0.65;
      this.neurons[i].tx = this.cx + Math.cos(angle) * r * this.spread;
      this.neurons[i].ty = this.cy + Math.sin(angle) * r * this.spread;
      this.neurons[i].tz = (Math.random() - 0.5) * 0.5 * this.spread * 0.5;
    }
  }

  update(
    time: number,
    state: SwarmState,
    amplitude: number,
  ) {
    const config = STATE_CONFIGS[state];
    const globalPulse = Math.sin(time * config.pulseFreq) * config.pulseAmp;

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

      // Gravitational pull toward center
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

    // Voice amplitude boost
    if (amplitude > 0.05) {
      const boost = amplitude * 0.15;
      for (const n of this.neurons) {
        n.energy = Math.min(1, n.energy + boost * Math.random());
      }
    }

    this.updateFires(config, state);

    if (state === 'processing') {
      this.updateDataFlows(config);
    }
  }

  private updateFires(config: StateConfig, state: SwarmState) {
    // Spawn new fires
    if (Math.random() < config.fireRate) {
      const src = Math.floor(Math.random() * this.neurons.length);
      const conns = this.neurons[src].connections;
      if (conns.length > 0) {
        const slot = this.fires.find(f => !f.active);
        if (slot) {
          slot.from = src;
          slot.to = conns[Math.floor(Math.random() * conns.length)];
          slot.progress = 0;
          slot.active = true;
        }
      }
    }

    for (const fire of this.fires) {
      if (!fire.active) continue;
      fire.progress += 0.014 * 1.6 * config.speed;

      if (fire.progress >= 1) {
        const target = this.neurons[fire.to];
        target.energy = Math.min(1, target.energy + 0.25);

        // Chain reaction in processing mode (55% chance)
        if (state === 'processing' && Math.random() < 0.55) {
          const nextConns = target.connections;
          if (nextConns.length > 0) {
            const chainSlot = this.fires.find(f => !f.active);
            if (chainSlot) {
              chainSlot.from = fire.to;
              chainSlot.to = nextConns[Math.floor(Math.random() * nextConns.length)];
              chainSlot.progress = 0;
              chainSlot.active = true;
            }
          }
        }

        fire.active = false;
      }
    }
  }

  private updateDataFlows(config: StateConfig) {
    // Spawn data flows
    const activeFlows = this.dataFlows.filter(f => f.active).length;
    if (Math.random() < 0.15 && activeFlows < MAX_DATA_FLOWS) {
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
        const slot = this.dataFlows.find(f => !f.active);
        if (slot) {
          slot.chain = chain;
          slot.progress = 0;
          slot.speed = 0.003 + Math.random() * 0.004;
          slot.size = 1 + Math.random() * 1.5;
          slot.active = true;
        }
      }
    }

    for (const flow of this.dataFlows) {
      if (!flow.active) continue;
      flow.progress += flow.speed * (config.speed + 0.1);
      if (flow.progress >= 1) {
        flow.active = false;
      }
    }
  }
}
