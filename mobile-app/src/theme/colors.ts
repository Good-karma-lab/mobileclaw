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
    fill: 'rgba(22, 48, 65, 0.38)',           // brighter tint, more see-through — dark glass
    fillActive: 'rgba(30, 62, 82, 0.48)',
    border: 'rgba(110, 200, 230, 0.24)',      // brighter cyan glass edge
    borderSubtle: 'rgba(90, 180, 210, 0.16)',
  },
  accent: {
    cyan: '#5DD4E8',         // bright cyan — primary accent, clearly visible
    cyanMuted: '#4DB8D0',    // medium cyan for secondary elements — brighter
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
    secondary: 'rgba(185, 210, 225, 0.88)', // brighter muted blue — clearly visible
    tertiary: 'rgba(140, 175, 200, 0.72)',  // brighter for icons and labels
  },
} as const;
