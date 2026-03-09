import { Easing } from "react-native-reanimated";

// =============================================================================
// 1. Spring Configs (withSpring options)
// =============================================================================

export const springs = {
  /** Smooth UI transitions — cards, modals, overlays */
  gentle: { damping: 20, stiffness: 150 },
  /** Interactive feedback — buttons, toggles, tab switches */
  snappy: { damping: 15, stiffness: 300 },
  /** Playful elements — badges, notifications, onboarding */
  bouncy: { damping: 10, stiffness: 200 },
  /** Fast precise motion — icon swaps, layout snaps */
  stiff: { damping: 25, stiffness: 400 },
  /** Attention-grabbing — alerts, errors, shake effects */
  wobbly: { damping: 8, stiffness: 150 },
} as const;

// =============================================================================
// 2. Timing Configs (withTiming options)
// =============================================================================

const BEZIER_STANDARD = Easing.bezier(0.25, 0.1, 0.25, 1.0);
const BEZIER_DECELERATE = Easing.bezier(0.0, 0.0, 0.2, 1.0);
const BEZIER_ACCELERATE = Easing.bezier(0.4, 0.0, 1.0, 1.0);

export const timing = {
  /** Quick micro-interactions — 150ms */
  fast: { duration: 150, easing: BEZIER_STANDARD },
  /** Standard transitions — 300ms */
  normal: { duration: 300, easing: BEZIER_STANDARD },
  /** Deliberate transitions — 500ms */
  slow: { duration: 500, easing: BEZIER_DECELERATE },
  /** Page-level transitions — 800ms */
  verySlow: { duration: 800, easing: BEZIER_DECELERATE },
  /** Fade out / exit — accelerate curve */
  exit: { duration: 200, easing: BEZIER_ACCELERATE },
} as const;

// =============================================================================
// 3. Glass Morphism Presets
// =============================================================================

export const glass = {
  /** Opacity 0 -> 1 with blur intensity ramp */
  fadeIn: {
    from: { opacity: 0, blurIntensity: 0 },
    to: { opacity: 1, blurIntensity: 20 },
    timing: { duration: 400, easing: BEZIER_DECELERATE },
  },
  /** Slide up from 20px below with opacity fade */
  slideUp: {
    from: { translateY: 20, opacity: 0 },
    to: { translateY: 0, opacity: 1 },
    spring: springs.gentle,
  },
  /** Slide down from 20px above with opacity fade */
  slideDown: {
    from: { translateY: -20, opacity: 0 },
    to: { translateY: 0, opacity: 1 },
    spring: springs.gentle,
  },
  /** Scale from 0.95 -> 1 with opacity */
  scaleIn: {
    from: { scale: 0.95, opacity: 0 },
    to: { scale: 1, opacity: 1 },
    spring: springs.snappy,
  },
  /** Width/height expansion with border radius adjustment */
  morphExpand: {
    from: { scaleX: 0.8, scaleY: 0.8, borderRadius: 24, opacity: 0 },
    to: { scaleX: 1, scaleY: 1, borderRadius: 20, opacity: 1 },
    spring: springs.gentle,
  },
} as const;

// =============================================================================
// 4. Plasma Orb Animation Presets
// =============================================================================

export const plasma = {
  /** Slow breathing — idle state, 3s cycle */
  idle: {
    scale: { min: 0.95, max: 1.05 },
    duration: 3000,
    easing: Easing.inOut(Easing.sin),
    glowOpacity: { min: 0.3, max: 0.6 },
  },
  /** Faster pulse — active listening, amplitude-reactive */
  listening: {
    scale: { min: 0.9, max: 1.1 },
    duration: 1200,
    easing: Easing.inOut(Easing.sin),
    glowOpacity: { min: 0.5, max: 0.85 },
  },
  /** Rotation + color shift — processing state */
  thinking: {
    rotation: { speed: 2000, easing: Easing.inOut(Easing.quad) },
    colorShift: { duration: 1500, easing: Easing.linear },
    scale: { min: 0.98, max: 1.02 },
    duration: 1500,
  },
  /** Scale + glow tied to output amplitude */
  speaking: {
    scale: { min: 0.92, max: 1.12 },
    duration: 800,
    easing: Easing.inOut(Easing.sin),
    glowOpacity: { min: 0.6, max: 1.0 },
    glowRadius: { min: 12, max: 28 },
  },
} as const;

// =============================================================================
// 5. Dock Animation Presets
// =============================================================================

export const dock = {
  /** Spring transition for the active tab indicator */
  tabSwitch: {
    spring: springs.snappy,
    scaleActive: 1.1,
    scaleInactive: 1.0,
  },
  /** Small scale bounce on tap */
  iconBounce: {
    spring: springs.bouncy,
    scaleDown: 0.85,
    scaleUp: 1.0,
  },
  /** Width morph for active tab pill background */
  morphPill: {
    spring: springs.gentle,
    widthActive: 64,
    widthInactive: 40,
    borderRadiusActive: 16,
    borderRadiusInactive: 12,
  },
  /** Breathing animation on the dock container */
  breathe: {
    scaleMin: 0.995,
    scaleMax: 1.005,
    duration: 4000,
    easing: Easing.inOut(Easing.sin),
  },
} as const;

// =============================================================================
// 6. Swarm Animation Values
// =============================================================================

export const swarm = {
  /** Glow oscillation for swarm neuron nodes */
  neuronPulse: {
    glowMin: 0.2,
    glowMax: 0.8,
    duration: 2000,
    easing: Easing.inOut(Easing.sin),
  },
  /** Connection line alpha fade timing */
  connectionFade: {
    alphaMin: 0.05,
    alphaMax: 0.4,
    duration: 1500,
    easing: Easing.inOut(Easing.quad),
  },
  /** Spring config for formation transitions (grid <-> cluster <-> ring) */
  formationMorph: {
    spring: { damping: 18, stiffness: 120 },
    duration: 600,
  },
  /** Data flow particle animation along connections */
  dataFlow: {
    speed: 1000,
    particleSize: 3,
    easing: Easing.linear,
  },
} as const;

// =============================================================================
// 7. Page Transitions
// =============================================================================

export const transitions = {
  /** Fade + slide for screen navigation */
  fadeSlide: {
    entering: {
      from: { opacity: 0, translateX: 30 },
      to: { opacity: 1, translateX: 0 },
      timing: { duration: 350, easing: BEZIER_DECELERATE },
    },
    exiting: {
      from: { opacity: 1, translateX: 0 },
      to: { opacity: 0, translateX: -30 },
      timing: { duration: 250, easing: BEZIER_ACCELERATE },
    },
  },
  /** Shared element morph timing */
  sharedElement: {
    timing: { duration: 400, easing: BEZIER_STANDARD },
    spring: springs.gentle,
  },
  /** Simple opacity crossfade */
  crossFade: {
    entering: {
      from: { opacity: 0 },
      to: { opacity: 1 },
      timing: { duration: 300, easing: BEZIER_DECELERATE },
    },
    exiting: {
      from: { opacity: 1 },
      to: { opacity: 0 },
      timing: { duration: 200, easing: BEZIER_ACCELERATE },
    },
  },
  /** Scale-based modal entrance */
  modal: {
    entering: {
      from: { opacity: 0, scale: 0.9 },
      to: { opacity: 1, scale: 1 },
      spring: springs.snappy,
    },
    exiting: {
      from: { opacity: 1, scale: 1 },
      to: { opacity: 0, scale: 0.95 },
      timing: { duration: 200, easing: BEZIER_ACCELERATE },
    },
  },
} as const;

// =============================================================================
// 8. Micro-interactions
// =============================================================================

export const micro = {
  /** Scale down on press for tactile feedback */
  buttonPress: {
    scaleDown: 0.97,
    scaleUp: 1.0,
    spring: springs.snappy,
  },
  /** TranslateX spring for toggle switch thumb */
  toggleSwitch: {
    spring: springs.snappy,
  },
  /** Scale + background color for chip selection */
  chipSelect: {
    scaleSelected: 1.05,
    scaleDeselected: 1.0,
    spring: springs.snappy,
  },
  /** Touch ripple — opacity + scale expansion */
  ripple: {
    scaleFrom: 0.0,
    scaleTo: 1.0,
    opacityFrom: 0.35,
    opacityTo: 0.0,
    duration: 400,
    easing: BEZIER_DECELERATE,
  },
  /** Long-press scale for contextual actions */
  longPress: {
    scaleDown: 0.94,
    spring: springs.gentle,
    holdDuration: 500,
  },
  /** Input field focus glow */
  inputFocus: {
    borderOpacityFrom: 0.1,
    borderOpacityTo: 0.35,
    timing: { duration: 200, easing: BEZIER_STANDARD },
  },
} as const;

// =============================================================================
// 9. Helper Functions
// =============================================================================

/**
 * Creates a pulse animation loop configuration.
 * Use with withRepeat(withSequence(withTiming(...), withTiming(...)), -1, true).
 */
export function createPulse(
  minScale: number,
  maxScale: number,
  durationMs: number,
) {
  return {
    minScale,
    maxScale,
    halfDuration: durationMs / 2,
    easing: Easing.inOut(Easing.sin),
  };
}

/**
 * Creates a breathing animation configuration.
 * @param intensity - 0..1 how pronounced the breathing is (affects scale range)
 * @param speed - 0..1 how fast (maps to 1500ms..5000ms cycle)
 */
export function createBreathing(intensity: number = 0.5, speed: number = 0.5) {
  const clampedIntensity = Math.max(0, Math.min(1, intensity));
  const clampedSpeed = Math.max(0, Math.min(1, speed));
  const scaleRange = 0.02 + clampedIntensity * 0.08; // 0.02..0.10
  const duration = 5000 - clampedSpeed * 3500; // 5000ms..1500ms

  return {
    minScale: 1 - scaleRange,
    maxScale: 1 + scaleRange,
    halfDuration: duration / 2,
    easing: Easing.inOut(Easing.sin),
  };
}

/**
 * Creates a shimmer / skeleton loading animation configuration.
 * Returns translateX range and timing for a shimmer sweep.
 * @param width - width of the element being shimmered
 */
export function createShimmer(width: number) {
  return {
    translateXFrom: -width,
    translateXTo: width,
    duration: 1200,
    easing: Easing.inOut(Easing.quad),
    shimmerWidth: width * 0.6,
    gradientColors: [
      "rgba(255, 255, 255, 0)",
      "rgba(255, 255, 255, 0.08)",
      "rgba(255, 255, 255, 0)",
    ] as const,
  };
}

/**
 * Linear interpolation between two values.
 */
export function lerp(start: number, end: number, progress: number): number {
  return start + (end - start) * progress;
}

/**
 * Clamps a value within min/max bounds. Useful for constraining
 * animated values before passing to withSpring.
 */
export function clampSpring(
  value: number,
  min: number,
  max: number,
): number {
  "worklet";
  return Math.max(min, Math.min(max, value));
}

/**
 * Maps an input range to an output range (simple version of interpolate).
 * Clamps output to the output range bounds.
 */
export function mapRange(
  value: number,
  inMin: number,
  inMax: number,
  outMin: number,
  outMax: number,
): number {
  "worklet";
  const clamped = Math.max(inMin, Math.min(inMax, value));
  const normalized = (clamped - inMin) / (inMax - inMin);
  return outMin + normalized * (outMax - outMin);
}

/**
 * Converts an amplitude (0..1) into a spring config with dynamic stiffness.
 * Higher amplitude = stiffer, faster spring response.
 */
export function amplitudeToSpring(
  amplitude: number,
  base: typeof springs.gentle = springs.gentle,
) {
  const clamped = Math.max(0, Math.min(1, amplitude));
  return {
    damping: base.damping + clamped * 5,
    stiffness: base.stiffness + clamped * 200,
  };
}

// =============================================================================
// Default export — aggregate object
// =============================================================================

export const animations = {
  springs,
  timing,
  glass,
  plasma,
  dock,
  swarm,
  transitions,
  micro,
  // Helpers
  createPulse,
  createBreathing,
  createShimmer,
  lerp,
  clampSpring,
  mapRange,
  amplitudeToSpring,
} as const;

export default animations;
