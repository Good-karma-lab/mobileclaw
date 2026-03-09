/**
 * ShapeLibrary — 30+ formation point generators.
 * Each returns normalized {x, y, z}[] in [-1, 1] range.
 * NeuronSystem scales them to screen coordinates.
 */

type Point3D = { x: number; y: number; z: number };

const jitter = (v = 0.06) => (Math.random() - 0.5) * v;

export function generateFormation(shape: string, count: number): Point3D[] {
  switch (shape) {
    case 'heart':       return heartPoints(count);
    case 'star':        return starPoints(count);
    case 'brain':       return brainPoints(count);
    case 'eye':         return eyePoints(count);
    case 'infinity':    return infinityPoints(count);
    case 'dna':         return dnaPoints(count);
    case 'atom':        return atomPoints(count);
    case 'spiral':      return spiralPoints(count);
    case 'lightning':   return lightningPoints(count);
    case 'diamond':     return diamondPoints(count);
    case 'crown':       return crownPoints(count);
    case 'moon':        return moonPoints(count);
    case 'fire':        return firePoints(count);
    case 'music':       return musicPoints(count);
    case 'tree':        return treePoints(count);
    case 'wave':        return wavePoints(count);
    case 'check':       return checkPoints(count);
    // Emoji faces
    case 'face_happy':    return faceHappyPoints(count);
    case 'face_sad':      return faceSadPoints(count);
    case 'face_love':     return faceLovePoints(count);
    case 'face_angry':    return faceAngryPoints(count);
    case 'face_surprise': return faceSurprisePoints(count);
    case 'face_think':    return faceThinkPoints(count);
    case 'face_wink':     return faceWinkPoints(count);
    case 'face_cool':     return faceCoolPoints(count);
    case 'face_cry':      return faceCryPoints(count);
    case 'face_laugh':    return faceLaughPoints(count);
    case 'face_sleep':    return faceSleepPoints(count);
    case 'face_scared':   return faceScaredPoints(count);
    case 'scatter':
    default:              return scatterPoints(count);
  }
}

function scatterPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const a = Math.random() * Math.PI * 2;
    const r = 0.15 + Math.random() * 0.65;
    pts.push({
      x: Math.cos(a) * r,
      y: Math.sin(a) * r,
      z: (Math.random() - 0.5) * 0.5,
    });
  }
  return pts;
}

function heartPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const t = (i / count) * Math.PI * 2;
    const x = 16 * Math.pow(Math.sin(t), 3) / 18 + jitter();
    const y = -(13 * Math.cos(t) - 5 * Math.cos(2 * t)
      - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 18 + jitter();
    pts.push({ x, y, z: (Math.random() - 0.5) * 0.2 });
  }
  return pts;
}

function starPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const angle = (i / count) * Math.PI * 2;
    const spike = i % 2 === 0 ? 0.7 : 0.3;
    pts.push({
      x: Math.cos(angle * 5 / (count / (count / 5))) * spike * (i % 2 === 0 ? 1 : 0.4) + jitter(0.04),
      y: Math.sin(angle * 5 / (count / (count / 5))) * spike * (i % 2 === 0 ? 1 : 0.4) + jitter(0.04),
      z: (Math.random() - 0.5) * 0.15,
    });
  }
  // Correct star: 5 outer + 5 inner points
  const corrected: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const a = (i / count) * Math.PI * 2 - Math.PI / 2;
    const r = (i % 2 === 0) ? 0.7 : 0.3;
    corrected.push({
      x: Math.cos(a) * r + jitter(0.04),
      y: Math.sin(a) * r + jitter(0.04),
      z: (Math.random() - 0.5) * 0.15,
    });
  }
  return corrected;
}

function brainPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const t = (i / count) * Math.PI * 2;
    // Two lobes
    const lobe = i < count / 2 ? -1 : 1;
    const r = 0.45 + Math.sin(t * 3) * 0.15;
    pts.push({
      x: (Math.cos(t) * r * 0.5 + lobe * 0.25) + jitter(0.05),
      y: Math.sin(t) * r * 0.6 + jitter(0.05),
      z: Math.cos(t * 2) * 0.2 + jitter(0.05),
    });
  }
  return pts;
}

function eyePoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  const pupilCount = Math.floor(count * 0.3);
  // Iris/outer
  for (let i = 0; i < count - pupilCount; i++) {
    const t = (i / (count - pupilCount)) * Math.PI * 2;
    const rx = 0.65, ry = 0.35;
    pts.push({
      x: Math.cos(t) * rx + jitter(0.04),
      y: Math.sin(t) * ry * Math.cos(t * 0.5) + jitter(0.04),
      z: (Math.random() - 0.5) * 0.1,
    });
  }
  // Pupil
  for (let i = 0; i < pupilCount; i++) {
    const t = (i / pupilCount) * Math.PI * 2;
    const r = 0.15 + Math.random() * 0.05;
    pts.push({
      x: Math.cos(t) * r + jitter(0.02),
      y: Math.sin(t) * r + jitter(0.02),
      z: 0.05 + (Math.random() - 0.5) * 0.05,
    });
  }
  return pts;
}

function infinityPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const t = (i / count) * Math.PI * 2;
    const scale = 0.55;
    pts.push({
      x: scale * Math.cos(t) / (1 + Math.sin(t) * Math.sin(t)) + jitter(0.03),
      y: scale * Math.sin(t) * Math.cos(t) / (1 + Math.sin(t) * Math.sin(t)) + jitter(0.03),
      z: Math.sin(t * 2) * 0.15,
    });
  }
  return pts;
}

function dnaPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const t = (i / count) * 4 * Math.PI;
    const y = (i / count - 0.5) * 1.4;
    const strand = i % 2 === 0 ? 1 : -1;
    pts.push({
      x: Math.cos(t) * 0.3 * strand + jitter(0.03),
      y: y + jitter(0.02),
      z: Math.sin(t) * 0.3 * strand,
    });
  }
  return pts;
}

function atomPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  const orbits = 3;
  const nucleus = Math.floor(count * 0.15);
  // Nucleus
  for (let i = 0; i < nucleus; i++) {
    const a = Math.random() * Math.PI * 2;
    const r = Math.random() * 0.1;
    pts.push({
      x: Math.cos(a) * r, y: Math.sin(a) * r,
      z: (Math.random() - 0.5) * 0.1,
    });
  }
  // Orbits
  const perOrbit = Math.floor((count - nucleus) / orbits);
  for (let o = 0; o < orbits; o++) {
    const tilt = (o / orbits) * Math.PI;
    for (let i = 0; i < perOrbit; i++) {
      const t = (i / perOrbit) * Math.PI * 2;
      const r = 0.55 + jitter(0.03);
      const x = Math.cos(t) * r;
      const y = Math.sin(t) * r * Math.cos(tilt);
      const z = Math.sin(t) * r * Math.sin(tilt);
      pts.push({ x: x + jitter(0.02), y: y + jitter(0.02), z });
    }
  }
  while (pts.length < count) pts.push({ x: jitter(0.1), y: jitter(0.1), z: jitter(0.1) });
  return pts;
}

function spiralPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const t = (i / count) * 6 * Math.PI;
    const r = (i / count) * 0.65;
    pts.push({
      x: Math.cos(t) * r + jitter(0.03),
      y: Math.sin(t) * r + jitter(0.03),
      z: (i / count - 0.5) * 0.4,
    });
  }
  return pts;
}

function lightningPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  const segments = 8;
  const perSeg = Math.floor(count / segments);
  let x = 0, y = -0.6;
  for (let s = 0; s < segments; s++) {
    const nx = x + (Math.random() - 0.5) * 0.4;
    const ny = y + 1.2 / segments;
    for (let i = 0; i < perSeg; i++) {
      const t = i / perSeg;
      pts.push({
        x: x + (nx - x) * t + jitter(0.04),
        y: y + (ny - y) * t + jitter(0.02),
        z: (Math.random() - 0.5) * 0.1,
      });
    }
    x = nx; y = ny;
  }
  while (pts.length < count) pts.push({ x: jitter(0.1), y: jitter(0.1), z: 0 });
  return pts;
}

function diamondPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const t = (i / count) * Math.PI * 2;
    const r = 0.5;
    // Diamond: 4 vertices compressed vertically
    const x = Math.cos(t) * r * 0.6 + jitter(0.03);
    const y = Math.sin(t) * r + jitter(0.03);
    pts.push({ x, y, z: Math.cos(t * 2) * 0.15 });
  }
  return pts;
}

function crownPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  const peaks = 5;
  for (let i = 0; i < count; i++) {
    const t = (i / count) * Math.PI * 2;
    const spike = Math.abs(Math.sin(t * peaks / 2)) * 0.3;
    pts.push({
      x: Math.cos(t) * 0.5 + jitter(0.03),
      y: -0.1 + spike + (Math.sin(t) > 0 ? 0 : -0.3) + jitter(0.03),
      z: (Math.random() - 0.5) * 0.15,
    });
  }
  return pts;
}

function moonPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const t = (i / count) * Math.PI * 2;
    const outerR = 0.5;
    const innerR = 0.4;
    const outerX = Math.cos(t) * outerR;
    const outerY = Math.sin(t) * outerR;
    const innerX = Math.cos(t) * innerR + 0.2;
    const innerY = Math.sin(t) * innerR;
    // Only keep points outside the inner circle
    const dx = outerX - 0.2, dy = outerY;
    if (dx * dx + dy * dy > innerR * innerR * 0.9) {
      pts.push({ x: outerX + jitter(0.03), y: outerY + jitter(0.03), z: (Math.random() - 0.5) * 0.1 });
    } else {
      // Redistribute to crescent edge
      const a = Math.random() * Math.PI + Math.PI / 2;
      pts.push({
        x: Math.cos(a) * outerR * 0.8 - 0.15 + jitter(0.04),
        y: Math.sin(a) * outerR + jitter(0.04),
        z: (Math.random() - 0.5) * 0.1,
      });
    }
  }
  return pts;
}

function firePoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const t = (i / count);
    const height = t * 1.2 - 0.4;
    const width = Math.max(0, 0.4 * (1 - t * 0.8));
    pts.push({
      x: (Math.random() - 0.5) * width * 2 + jitter(0.03),
      y: -height + jitter(0.03),
      z: (Math.random() - 0.5) * width * 0.5,
    });
  }
  return pts;
}

function musicPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  // Two music notes
  for (let i = 0; i < count; i++) {
    const note = i < count / 2 ? 0 : 1;
    const offset = note * 0.4 - 0.2;
    const t = (i % (count / 2)) / (count / 2);
    if (t < 0.3) {
      // Note head (ellipse)
      const a = (t / 0.3) * Math.PI * 2;
      pts.push({
        x: Math.cos(a) * 0.12 + offset + jitter(0.02),
        y: 0.25 + Math.sin(a) * 0.08 + jitter(0.02),
        z: (Math.random() - 0.5) * 0.05,
      });
    } else {
      // Stem
      const stemT = (t - 0.3) / 0.7;
      pts.push({
        x: offset + 0.12 + jitter(0.01),
        y: 0.25 - stemT * 0.6 + jitter(0.02),
        z: (Math.random() - 0.5) * 0.05,
      });
    }
  }
  return pts;
}

function treePoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  const trunkCount = Math.floor(count * 0.2);
  // Trunk
  for (let i = 0; i < trunkCount; i++) {
    pts.push({
      x: (Math.random() - 0.5) * 0.08,
      y: 0.2 + (i / trunkCount) * 0.5,
      z: (Math.random() - 0.5) * 0.05,
    });
  }
  // Canopy (triangle layers)
  for (let i = 0; i < count - trunkCount; i++) {
    const t = i / (count - trunkCount);
    const width = 0.5 * (1 - t * 0.7);
    pts.push({
      x: (Math.random() - 0.5) * width + jitter(0.03),
      y: 0.2 - t * 0.8 + jitter(0.03),
      z: (Math.random() - 0.5) * width * 0.4,
    });
  }
  return pts;
}

function wavePoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  for (let i = 0; i < count; i++) {
    const x = (i / count - 0.5) * 1.6;
    const y = Math.sin(x * 4) * 0.25 + jitter(0.03);
    pts.push({ x, y, z: (Math.random() - 0.5) * 0.15 });
  }
  return pts;
}

function checkPoints(count: number): Point3D[] {
  const pts: Point3D[] = [];
  const mid = Math.floor(count * 0.35);
  // Short stroke (down-left to bottom)
  for (let i = 0; i < mid; i++) {
    const t = i / mid;
    pts.push({
      x: -0.3 + t * 0.3 + jitter(0.03),
      y: -0.1 + t * 0.4 + jitter(0.03),
      z: (Math.random() - 0.5) * 0.1,
    });
  }
  // Long stroke (bottom to top-right)
  for (let i = 0; i < count - mid; i++) {
    const t = i / (count - mid);
    pts.push({
      x: 0.0 + t * 0.5 + jitter(0.03),
      y: 0.3 - t * 0.7 + jitter(0.03),
      z: (Math.random() - 0.5) * 0.1,
    });
  }
  return pts;
}

// --- Emoji Faces ---

function faceCircle(count: number): Point3D[] {
  const pts: Point3D[] = [];
  const outlineCount = Math.floor(count * 0.4);
  for (let i = 0; i < outlineCount; i++) {
    const a = (i / outlineCount) * Math.PI * 2;
    pts.push({
      x: Math.cos(a) * 0.55 + jitter(0.02),
      y: Math.sin(a) * 0.55 + jitter(0.02),
      z: (Math.random() - 0.5) * 0.08,
    });
  }
  return pts;
}

function addEyes(pts: Point3D[], count: number, style: 'normal' | 'hearts' | 'angry' | 'wide' | 'wink' | 'cool' | 'closed' | 'x') {
  const eyeCount = Math.floor(count * 0.1);
  for (let i = 0; i < eyeCount; i++) {
    const side = i < eyeCount / 2 ? -1 : 1;
    const a = (i % (eyeCount / 2)) / (eyeCount / 2) * Math.PI * 2;
    const r = style === 'wide' ? 0.08 : 0.06;

    if (style === 'hearts') {
      const t = a;
      const hx = 0.04 * Math.pow(Math.sin(t), 3);
      const hy = -(0.035 * Math.cos(t) - 0.012 * Math.cos(2 * t));
      pts.push({ x: side * 0.2 + hx, y: -0.12 + hy, z: 0.05 });
    } else if (style === 'cool') {
      // Sunglasses line
      pts.push({
        x: side * 0.2 + (i % (eyeCount / 2)) / (eyeCount / 2) * 0.15 - 0.075,
        y: -0.15 + jitter(0.01),
        z: 0.05,
      });
    } else if (style === 'closed' || style === 'wink') {
      if (style === 'wink' && side === 1) {
        pts.push({ x: side * 0.2 + jitter(0.02), y: -0.15 + jitter(0.01), z: 0.05 });
      } else {
        // Closed eye: horizontal line
        pts.push({
          x: side * 0.2 + ((i % (eyeCount / 2)) / (eyeCount / 2) - 0.5) * 0.1,
          y: -0.15 + jitter(0.01),
          z: 0.05,
        });
      }
    } else if (style === 'x') {
      pts.push({
        x: side * 0.2 + jitter(0.04),
        y: -0.15 + jitter(0.04),
        z: 0.05,
      });
    } else {
      pts.push({
        x: side * 0.2 + Math.cos(a) * r + jitter(0.01),
        y: -0.15 + Math.sin(a) * r * (style === 'angry' ? 0.5 : 1) + jitter(0.01),
        z: 0.05,
      });
    }
  }
}

function addMouth(pts: Point3D[], count: number, style: 'smile' | 'frown' | 'open' | 'straight' | 'wide' | 'laugh' | 'wavy') {
  const mouthCount = Math.floor(count * 0.12);
  for (let i = 0; i < mouthCount; i++) {
    const t = (i / mouthCount - 0.5) * 2;
    let x = t * 0.2;
    let y = 0.2;

    switch (style) {
      case 'smile': y += t * t * 0.08; break;
      case 'frown': y -= t * t * 0.08; break;
      case 'open': {
        const a = (i / mouthCount) * Math.PI * 2;
        x = Math.cos(a) * 0.1;
        y = 0.2 + Math.sin(a) * 0.08;
        break;
      }
      case 'wide': {
        const a = (i / mouthCount) * Math.PI * 2;
        x = Math.cos(a) * 0.15;
        y = 0.2 + Math.sin(a) * 0.1;
        break;
      }
      case 'laugh': y += t * t * 0.12; break;
      case 'wavy': y += Math.sin(t * 4) * 0.04; break;
      case 'straight': break;
    }
    pts.push({ x: x + jitter(0.01), y: y + jitter(0.01), z: 0.05 });
  }
}

function faceHappyPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'normal');
  addMouth(pts, count, 'smile');
  while (pts.length < count) {
    const a = Math.random() * Math.PI * 2;
    const r = Math.random() * 0.5;
    pts.push({ x: Math.cos(a) * r, y: Math.sin(a) * r, z: jitter(0.05) });
  }
  return pts.slice(0, count);
}

function faceSadPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'normal');
  addMouth(pts, count, 'frown');
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceLovePoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'hearts');
  addMouth(pts, count, 'smile');
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceAngryPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'angry');
  addMouth(pts, count, 'straight');
  // Angry eyebrows
  const browCount = Math.floor(count * 0.06);
  for (let i = 0; i < browCount; i++) {
    const side = i < browCount / 2 ? -1 : 1;
    const t = (i % (browCount / 2)) / (browCount / 2);
    pts.push({
      x: side * (0.12 + t * 0.12),
      y: -0.28 + t * side * 0.05,
      z: 0.06,
    });
  }
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceSurprisePoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'wide');
  addMouth(pts, count, 'open');
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceThinkPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'normal');
  addMouth(pts, count, 'straight');
  // Thinking hand/chin dot
  const dotCount = Math.floor(count * 0.05);
  for (let i = 0; i < dotCount; i++) {
    const a = (i / dotCount) * Math.PI * 2;
    pts.push({ x: 0.35 + Math.cos(a) * 0.06, y: 0.3 + Math.sin(a) * 0.06, z: 0.07 });
  }
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceWinkPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'wink');
  addMouth(pts, count, 'smile');
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceCoolPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'cool');
  addMouth(pts, count, 'smile');
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceCryPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'normal');
  addMouth(pts, count, 'frown');
  // Tears
  const tearCount = Math.floor(count * 0.06);
  for (let i = 0; i < tearCount; i++) {
    const side = i < tearCount / 2 ? -1 : 1;
    const t = (i % (tearCount / 2)) / (tearCount / 2);
    pts.push({
      x: side * 0.2 + jitter(0.02),
      y: -0.05 + t * 0.2,
      z: 0.06,
    });
  }
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceLaughPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'closed');
  addMouth(pts, count, 'laugh');
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceSleepPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'closed');
  addMouth(pts, count, 'straight');
  // Zzz
  const zCount = Math.floor(count * 0.06);
  for (let i = 0; i < zCount; i++) {
    const t = i / zCount;
    pts.push({
      x: 0.35 + t * 0.15,
      y: -0.35 - t * 0.15,
      z: 0.08 + t * 0.05,
    });
  }
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}

function faceScaredPoints(count: number): Point3D[] {
  const pts = faceCircle(count);
  addEyes(pts, count, 'wide');
  addMouth(pts, count, 'wavy');
  while (pts.length < count) pts.push({ x: jitter(0.4), y: jitter(0.4), z: jitter(0.05) });
  return pts.slice(0, count);
}
