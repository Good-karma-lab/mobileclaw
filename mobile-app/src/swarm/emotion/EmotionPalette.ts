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
  neutral:    { h: 195, s: 20, l: 40 },  // Storm steel
  curious:    { h: 190, s: 40, l: 45 },  // Ocean teal
  happy:      { h: 42,  s: 40, l: 48 },  // Warm amber
  love:       { h: 340, s: 35, l: 42 },  // Rose
  focused:    { h: 250, s: 30, l: 40 },  // Deep violet
  alert:      { h: 15,  s: 40, l: 42 },  // Coral
  calm:       { h: 185, s: 30, l: 44 },  // Sage teal
  sad:        { h: 220, s: 25, l: 32 },  // Deep indigo
  excited:    { h: 30,  s: 45, l: 48 },  // Amber
  mysterious: { h: 270, s: 35, l: 38 },  // Purple
  grateful:   { h: 50,  s: 30, l: 44 },  // Gold
  proud:      { h: 40,  s: 35, l: 42 },  // Warm amber
  playful:    { h: 300, s: 30, l: 40 },  // Magenta
  anxious:    { h: 190, s: 20, l: 35 },  // Muted teal
  inspired:   { h: 55,  s: 40, l: 48 },  // Yellow
  nostalgic:  { h: 25,  s: 25, l: 38 },  // Sunset
  angry:      { h: 5,   s: 45, l: 38 },  // Deep red
  surprised:  { h: 48,  s: 40, l: 46 },  // Gold
  confused:   { h: 230, s: 20, l: 35 },  // Blue-gray
  sleepy:     { h: 240, s: 15, l: 28 },  // Dark lavender
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
