/**
 * EmotionPalette — 20 emotion HSL definitions.
 * Dark stormy palette: low saturation, low lightness, deep moody tones.
 * The neural swarm should feel like dark storm clouds with subtle color shifts.
 */

export interface HSL {
  h: number;
  s: number;
  l: number;
}

export const EMOTION_PALETTE = {
  neutral:    { h: 200, s: 10, l: 28 },  // Dark steel
  curious:    { h: 195, s: 25, l: 30 },  // Dark ocean teal
  happy:      { h: 42,  s: 20, l: 32 },  // Dim warm amber
  love:       { h: 340, s: 22, l: 28 },  // Dark rose
  focused:    { h: 250, s: 20, l: 26 },  // Deep dark violet
  alert:      { h: 15,  s: 25, l: 28 },  // Dark coral
  calm:       { h: 185, s: 18, l: 30 },  // Dark sage teal
  sad:        { h: 220, s: 18, l: 22 },  // Deep dark indigo
  excited:    { h: 30,  s: 28, l: 30 },  // Dark amber
  mysterious: { h: 270, s: 22, l: 24 },  // Very dark purple
  grateful:   { h: 50,  s: 15, l: 30 },  // Dim gold
  proud:      { h: 40,  s: 22, l: 28 },  // Dark warm amber
  playful:    { h: 300, s: 18, l: 26 },  // Dark magenta
  anxious:    { h: 190, s: 15, l: 24 },  // Very muted teal
  inspired:   { h: 55,  s: 25, l: 32 },  // Dim yellow
  nostalgic:  { h: 25,  s: 15, l: 26 },  // Dark sunset
  angry:      { h: 5,   s: 30, l: 24 },  // Very deep red
  surprised:  { h: 48,  s: 25, l: 30 },  // Dim gold
  confused:   { h: 230, s: 14, l: 24 },  // Dark blue-gray
  sleepy:     { h: 240, s: 10, l: 20 },  // Near-black lavender
} as const;

export type EmotionKey = keyof typeof EMOTION_PALETTE;

export const EMOTION_KEYS = Object.keys(EMOTION_PALETTE) as EmotionKey[];

/** Convert HSL to rgba string for Skia Paint. */
export function hslToRgba(h: number, s: number, l: number, a: number = 1): string {
  const sn = s / 100;
  const ln = l / 100;
  const k = (n: number) => (n + h / 30) % 12;
  const an = sn * Math.min(ln, 1 - ln);
  const f = (n: number) =>
    ln - an * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  const r = Math.round(f(0) * 255);
  const g = Math.round(f(8) * 255);
  const b = Math.round(f(4) * 255);
  return `rgba(${r}, ${g}, ${b}, ${a})`;
}

/** Convert HSL to [r, g, b] array (0-255). */
export function hslToRgb(h: number, s: number, l: number): [number, number, number] {
  const sn = s / 100;
  const ln = l / 100;
  const k = (n: number) => (n + h / 30) % 12;
  const an = sn * Math.min(ln, 1 - ln);
  const f = (n: number) =>
    ln - an * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  return [Math.round(f(0) * 255), Math.round(f(8) * 255), Math.round(f(4) * 255)];
}
