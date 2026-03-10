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
    fill: 'rgba(14, 32, 48, 0.55)',
    fillActive: 'rgba(22, 51, 74, 0.65)',
    border: 'rgba(60, 140, 170, 0.25)',      // brighter cyan-tinted borders
    borderSubtle: 'rgba(50, 120, 150, 0.15)',
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
