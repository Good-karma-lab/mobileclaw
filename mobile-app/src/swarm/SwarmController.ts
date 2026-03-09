/**
 * SwarmController — reactive state store for the neural swarm.
 *
 * Manages: AI state, emotion, formation, voice amplitude.
 * Uses a simple observer pattern (no external deps).
 */
import type { SwarmState } from './neurons/NeuronSystem';
import type { EmotionKey } from './emotion/EmotionPalette';

export interface SwarmStoreState {
  state: SwarmState;
  emotion: EmotionKey;
  formation: string | null;
  displayText: string | null;
  amplitude: number;
}

type Listener = (state: SwarmStoreState) => void;

class SwarmStore {
  private _state: SwarmStoreState = {
    state: 'idle',
    emotion: 'neutral',
    formation: null,
    displayText: null,
    amplitude: 0,
  };

  private listeners: Set<Listener> = new Set();

  get state(): SwarmStoreState {
    return this._state;
  }

  setState(state: SwarmState) {
    this._state = { ...this._state, state };
    this.notify();
  }

  setEmotion(emotion: EmotionKey) {
    this._state = { ...this._state, emotion };
    this.notify();
  }

  setFormation(formation: string | null) {
    this._state = { ...this._state, formation };
    this.notify();
  }

  setDisplayText(text: string | null) {
    this._state = { ...this._state, displayText: text };
    this.notify();
  }

  setAmplitude(amplitude: number) {
    this._state = { ...this._state, amplitude };
    // No notify for amplitude — polled per frame for perf
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    for (const listener of this.listeners) {
      listener(this._state);
    }
  }
}

// Singleton
export const swarmStore = new SwarmStore();
