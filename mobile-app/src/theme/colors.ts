export const colors = {
  base: {
    spaceBlack: '#030608',
    midnightBlue: '#06101A',
  },
  glass: {
    fill: 'rgba(255, 255, 255, 0.05)',
    fillActive: 'rgba(255, 255, 255, 0.08)',
    border: 'rgba(255, 255, 255, 0.08)',
    borderSubtle: 'rgba(255, 255, 255, 0.05)',
  },
  accent: {
    cyan: '#1A5C6A',       // dark stormy teal — the primary accent
    cyanGlow: '#2A8090',   // slightly brighter for subtle glow moments
    cyanBright: '#4AA0B0',  // brightest teal, used very sparingly
    violet: '#5A3A8A',     // muted dark violet
    rose: '#8A2040',       // dark muted rose
    amber: '#8A6A20',      // muted amber
    gold: '#8A7A30',       // muted gold
    lime: '#607840',       // muted lime
  },
  semantic: {
    success: '#1A7A6A',    // dark teal-green
    error: '#8A2020',      // dark muted red
    warning: '#7A6020',    // dark amber
    info: '#2A5080',       // dark blue
  },
  text: {
    primary: 'rgba(180, 200, 210, 0.85)',     // cool blue-white, not pure white
    secondary: 'rgba(140, 165, 180, 0.55)',   // muted steel blue
    tertiary: 'rgba(100, 130, 150, 0.35)',    // very subdued
  },
} as const;
