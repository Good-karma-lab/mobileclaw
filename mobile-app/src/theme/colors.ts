/**
 * Storm Palette — "Dramatic Night Sky"
 *
 * Dark moody backgrounds with bright cool-cyan foreground elements.
 * The storm is the background — text/icons must be bright enough to read.
 */
export const colors = {
  base: {
    // ─── Backward-compat aliases ───
    spaceBlack: '#020408',
    midnightBlue: '#060D14',
    // ─── Storm backgrounds ───
    abyss: '#020408',
    stormBlack: '#060D14',
    deepNight: '#0B1A26',
    midnightStorm: '#102638',
    darkCyanStorm: '#16334A',
  },
  glass: {
    fill: 'rgba(18, 40, 55, 0.45)',           // brighter tint, more see-through
    fillActive: 'rgba(25, 55, 75, 0.55)',
    border: 'rgba(100, 190, 220, 0.20)',      // visible cyan glass edge
    borderSubtle: 'rgba(80, 170, 200, 0.12)',
  },
  accent: {
    cyan: '#5DD4E8',         // bright cyan — primary accent, clearly visible
    cyanMuted: '#3A9AB0',    // medium cyan for secondary elements
    cyanGlow: '#80E8F8',     // very bright glow
    cyanBright: '#A0F0FF',   // ultra-bright, sparingly
    violet: '#A880D0',       // bright violet
    rose: '#D06080',         // bright rose
    amber: '#D4A840',        // bright amber
    gold: '#D4B850',         // bright gold
    lime: '#A0C860',         // bright lime
  },
  semantic: {
    success: '#50D0A0',      // bright teal-green
    error: '#E06060',        // bright red
    warning: '#E0B040',      // bright amber
    info: '#60A0D0',         // bright blue
  },
  text: {
    primary: 'rgba(210, 230, 240, 0.95)',   // near-white cool blue — highly readable
    secondary: 'rgba(160, 190, 210, 0.80)', // clearly visible muted blue
    tertiary: 'rgba(120, 160, 185, 0.65)',  // visible for icons and labels
  },
} as const;
