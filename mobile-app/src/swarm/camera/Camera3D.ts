/**
 * Camera3D — 3D projection with accelerometer integration.
 *
 * Each AI state has a unique organic rotation profile. The accelerometer
 * provides additional user-driven rotation layered on top for spatial presence.
 */
import { Accelerometer } from 'expo-sensors';
import type { SwarmState } from '../neurons/NeuronSystem';

interface RotationProfile {
  rotXamp: number; rotXfreq: number;
  rotYamp: number; rotYfreq: number;
  rotZamp: number; rotZfreq: number;
}

const ROTATION_PROFILES: Record<SwarmState, RotationProfile> = {
  idle: {
    rotXamp: 0.08, rotXfreq: 0.07,
    rotYamp: 0.12, rotYfreq: 0.05,
    rotZamp: 0.03, rotZfreq: 0.03,
  },
  listening: {
    rotXamp: 0.15, rotXfreq: 0.2,
    rotYamp: 0.06, rotYfreq: 0.12,
    rotZamp: 0.04, rotZfreq: 0.15,
  },
  processing: {
    rotXamp: 0.10, rotXfreq: 0.04,
    rotYamp: 0.20, rotYfreq: 0.06,
    rotZamp: 0.06, rotZfreq: 0.025,
  },
  speaking: {
    rotXamp: 0.06, rotXfreq: 0.1,
    rotYamp: 0.10, rotYfreq: 0.15,
    rotZamp: 0.05, rotZfreq: 0.08,
  },
};

export interface Projected {
  sx: number;
  sy: number;
  scale: number;
  depth: number;
}

export class Camera3D {
  rotX = 0;
  rotY = 0;
  rotZ = 0;

  private userRotX = 0;
  private userRotY = 0;
  private autoRotX = 0;
  private autoRotY = 0;
  private autoRotZ = 0;

  private perspective = 600;
  private cx: number;
  private cy: number;
  private currentProfile: RotationProfile = ROTATION_PROFILES.idle;
  private accelSubscription: ReturnType<typeof Accelerometer.addListener> | null = null;

  constructor(width: number, height: number) {
    this.cx = width / 2;
    this.cy = height / 2;
    this.initAccelerometer();
  }

  private initAccelerometer() {
    Accelerometer.setUpdateInterval(16);

    this.accelSubscription = Accelerometer.addListener(({ x, y }) => {
      this.userRotY = x * 0.4;
      this.userRotX = (y - 0.5) * 0.35;
    });
  }

  setProfile(state: SwarmState) {
    this.currentProfile = ROTATION_PROFILES[state] || ROTATION_PROFILES.idle;
  }

  update(time: number) {
    const p = this.currentProfile;

    this.autoRotX = Math.sin(time * p.rotXfreq) * p.rotXamp;
    this.autoRotY = Math.sin(time * p.rotYfreq + 0.7) * p.rotYamp;
    this.autoRotZ = Math.sin(time * p.rotZfreq + 1.4) * p.rotZamp;

    // Blend user input + auto rotation (smooth lerp)
    this.rotX += (this.userRotX + this.autoRotX - this.rotX) * 0.015;
    this.rotY += (this.userRotY + this.autoRotY - this.rotY) * 0.015;
    this.rotZ += (this.autoRotZ - this.rotZ) * 0.01;
  }

  project(x: number, y: number, z: number): Projected {
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
