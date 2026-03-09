/**
 * EmotionBlender — smooth HSL transitions between emotion colors.
 * Blends at 0.6% per frame (~2-3 seconds for a full transition).
 */
import { EMOTION_PALETTE, type EmotionKey, type HSL, hslToRgba, hslToRgb } from './EmotionPalette';

export class EmotionBlender {
  current: HSL = { ...EMOTION_PALETTE.neutral };

  update(targetEmotion: EmotionKey) {
    const target = EMOTION_PALETTE[targetEmotion];
    const rate = 0.006; // Very slow blend

    // Handle hue wrapping (shortest path around the circle)
    let dh = target.h - this.current.h;
    if (dh > 180) dh -= 360;
    if (dh < -180) dh += 360;

    this.current.h = (this.current.h + dh * rate + 360) % 360;
    this.current.s += (target.s - this.current.s) * rate;
    this.current.l += (target.l - this.current.l) * rate;
  }

  getColor(alpha: number = 1): string {
    return hslToRgba(this.current.h, this.current.s, this.current.l, alpha);
  }

  getRgb(): [number, number, number] {
    return hslToRgb(this.current.h, this.current.s, this.current.l);
  }

  /** Get a brighter variant for glows/highlights. */
  getGlow(alpha: number = 0.6): string {
    return hslToRgba(this.current.h, this.current.s + 10, this.current.l + 15, alpha);
  }

  /** Get a dimmer variant for connections/backgrounds. */
  getDim(alpha: number = 0.3): string {
    return hslToRgba(this.current.h, this.current.s - 5, this.current.l - 10, alpha);
  }
}
