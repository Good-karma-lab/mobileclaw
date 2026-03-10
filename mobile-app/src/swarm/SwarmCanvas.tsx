/**
 * SwarmCanvas — GPU-accelerated neural swarm visualization.
 *
 * Faithful port of guappa-cosmic-v5.html to React Native Skia.
 * Uses imperative Skia canvas drawing (createPicture) for 60fps performance
 * with 420 neurons, connections, synaptic fires, data flows, harmonic waves,
 * stars, cosmic dust, nebula background, central glow, and vignette.
 */
import React, { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { useWindowDimensions } from 'react-native';
import {
  Canvas,
  Picture,
  Skia,
  createPicture,
  BlurStyle,
  PaintStyle,
  TileMode,
} from '@shopify/react-native-skia';
import { NeuronSystem, STATE_CONFIGS } from './neurons/NeuronSystem';
import type { SwarmState } from './neurons/NeuronSystem';
import { Camera3D } from './camera/Camera3D';
import { EmotionBlender } from './emotion/EmotionBlender';
import { hslToRgb } from './emotion/EmotionPalette';
import { HarmonicWaveSystem } from './waves/HarmonicWaves';
import { generateFormation } from './formations/ShapeLibrary';
import { generateTextFormation } from './formations/TextRenderer';
import { swarmStore } from './SwarmController';

const NEURON_COUNT = 420;
const CONNECTION_DISTANCE = 52;
const STAR_COUNT = 250;
const DUST_COUNT = 200;

// Pre-generate static stars
interface Star { x: number; y: number; r: number; a: number; twinklePhase: number; twinkleSpeed: number; hue: number }
function generateStars(): Star[] {
  return Array.from({ length: STAR_COUNT }, () => ({
    x: Math.random(),
    y: Math.random(),
    r: 0.2 + Math.random() * 0.6,
    a: 0.04 + Math.random() * 0.15,
    twinklePhase: Math.random() * Math.PI * 2,
    twinkleSpeed: 0.0002 + Math.random() * 0.0008,
    hue: 200 + Math.random() * 60,
  }));
}

// Pre-generate cosmic dust
interface DustMote { x: number; y: number; z: number; size: number; alpha: number; speed: number; phase: number }
function generateDust(): DustMote[] {
  return Array.from({ length: DUST_COUNT }, () => ({
    x: Math.random() * 2 - 1,
    y: Math.random() * 2 - 1,
    z: Math.random() * 2 - 1,
    size: 0.3 + Math.random() * 0.9,
    alpha: 0.05 + Math.random() * 0.12,
    speed: 0.00005 + Math.random() * 0.00015,
    phase: Math.random() * Math.PI * 2,
  }));
}

// HSL to RGB (0-255)
function hsl(h: number, s: number, l: number): [number, number, number] {
  return hslToRgb(h, s, l);
}

export const SwarmCanvas: React.FC = () => {
  const { width, height } = useWindowDimensions();
  const [picture, setPicture] = useState<ReturnType<typeof createPicture> | null>(null);

  const systemRef = useRef<NeuronSystem | null>(null);
  const cameraRef = useRef<Camera3D | null>(null);
  const blenderRef = useRef(new EmotionBlender());
  const wavesRef = useRef(new HarmonicWaveSystem());
  const starsRef = useRef(generateStars());
  const dustRef = useRef(generateDust());
  const lastFormationRef = useRef<string | null>(null);
  const lastTextRef = useRef<string | null>(null);
  const waveSpawnTimerRef = useRef(0);
  const startTimeRef = useRef(Date.now());
  const animFrameRef = useRef(0);

  // Reusable paint objects
  const paintsRef = useRef<{
    fill: ReturnType<typeof Skia.Paint>;
    stroke: ReturnType<typeof Skia.Paint>;
    blurFill: ReturnType<typeof Skia.Paint>;
  } | null>(null);

  if (!paintsRef.current) {
    const fill = Skia.Paint();
    fill.setStyle(PaintStyle.Fill);

    const stroke = Skia.Paint();
    stroke.setStyle(PaintStyle.Stroke);

    const blurFill = Skia.Paint();
    blurFill.setStyle(PaintStyle.Fill);

    paintsRef.current = { fill, stroke, blurFill };
  }

  // Initialize systems
  useEffect(() => {
    systemRef.current = new NeuronSystem(NEURON_COUNT, width, height);
    cameraRef.current = new Camera3D(width, height);

    return () => {
      cameraRef.current?.destroy();
      cancelAnimationFrame(animFrameRef.current);
    };
  }, [width, height]);

  // Listen for formation/text changes
  useEffect(() => {
    return swarmStore.subscribe((state) => {
      const system = systemRef.current;
      if (!system) return;

      if (state.displayText && state.displayText !== lastTextRef.current) {
        lastTextRef.current = state.displayText;
        lastFormationRef.current = null;
        system.setFormation(generateTextFormation(state.displayText, NEURON_COUNT));
      } else if (state.formation && state.formation !== lastFormationRef.current) {
        lastFormationRef.current = state.formation;
        lastTextRef.current = null;
        system.setFormation(generateFormation(state.formation, NEURON_COUNT));
      } else if (!state.formation && !state.displayText && (lastFormationRef.current || lastTextRef.current)) {
        lastFormationRef.current = null;
        lastTextRef.current = null;
        system.scatter();
      }

      cameraRef.current?.setProfile(state.state);
    });
  }, []);

  const canvasRect = useMemo(
    () => Skia.XYWHRect(0, 0, width, height),
    [width, height],
  );

  // Animation loop — draws a new Picture each frame
  const tick = useCallback(() => {
    const system = systemRef.current;
    const camera = cameraRef.current;
    const blender = blenderRef.current;
    const waveSystem = wavesRef.current;
    const paints = paintsRef.current;
    if (!system || !camera || !paints) {
      animFrameRef.current = requestAnimationFrame(tick);
      return;
    }

    const store = swarmStore.state;
    const time = (Date.now() - startTimeRef.current) / 1000;
    const config = STATE_CONFIGS[store.state];

    // Update physics
    system.update(time, store.state, store.amplitude);
    camera.update(time);
    blender.update(store.emotion);

    // Spawn waves in listening/speaking states
    if (config.waveActive) {
      waveSpawnTimerRef.current += 0.016;
      const interval = 1.2 - store.amplitude * 0.6;
      if (waveSpawnTimerRef.current > interval) {
        waveSpawnTimerRef.current = 0;
        waveSystem.spawn(Math.min(width, height) * 0.45, time);
      }
    }

    const waveDrawData = config.waveActive
      ? waveSystem.update(time, store.amplitude)
      : [];

    const globalPulse = Math.sin(time * config.pulseFreq) * config.pulseAmp;
    const emoHsl = blender.current;
    const [er, eg, eb] = hsl(emoHsl.h, emoHsl.s, emoHsl.l);
    const cx = width / 2;
    const cy = height / 2;
    const spread = Math.min(width, height) * 0.36;

    // Project all neurons for 3D
    const projected = system.neurons.map((n, idx) => {
      const p = camera.project(n.x, n.y, n.z);
      return { ...p, idx };
    });
    // Depth sort (back to front)
    projected.sort((a, b) => b.depth - a.depth);

    const W = width;
    const H = height;
    const { fill, stroke, blurFill } = paints;

    // Create picture imperatively
    const pic = createPicture(
      (canvas) => {
        // ── 1. Background ──
        fill.setColor(Skia.Color('#030608'));
        canvas.drawRect(Skia.XYWHRect(0, 0, W, H), fill);

        // ── 2. Nebula clouds ──
        const drawCloud = (x: number, y: number, r: number, a: number, cloudH: number) => {
          const [cr, cg, cb] = hsl(cloudH, 20, 18);
          const shader = Skia.Shader.MakeRadialGradient(
            { x, y }, r,
            [
              Skia.Color(`rgba(${cr}, ${cg}, ${cb}, ${a})`),
              Skia.Color(`rgba(${cr}, ${cg}, ${cb}, ${a * 0.35})`),
              Skia.Color('rgba(0, 0, 0, 0)'),
            ],
            [0, 0.5, 1],
            TileMode.Clamp,
          );
          fill.setShader(shader);
          canvas.drawRect(Skia.XYWHRect(0, 0, W, H), fill);
          fill.setShader(null);
        };
        drawCloud(W * 0.15, H * 0.3, W * 0.38, 0.10, 200);
        drawCloud(W * 0.85, H * 0.2, W * 0.30, 0.07, 220);
        drawCloud(cx, cy, Math.min(W, H) * 0.50, 0.08, 195);

        // ── 3. Emotion tint overlay ──
        const tintA = 0.1 + Math.abs(globalPulse) * 0.05;
        const [tR, tG, tB] = hsl(emoHsl.h, emoHsl.s * 0.4, 7 + globalPulse * 2);
        fill.setColor(Skia.Color(`rgba(${tR}, ${tG}, ${tB}, ${tintA})`));
        canvas.drawRect(Skia.XYWHRect(0, 0, W, H), fill);

        // ── 4. Stars (subtle background) ──
        for (const star of starsRef.current) {
          const twinkle = 0.5 + 0.5 * Math.sin(time * 2 * star.twinkleSpeed + star.twinklePhase);
          const starA = star.a * twinkle;
          if (starA < 0.01) continue;
          const starH = emoHsl.h * 0.2 + star.hue * 0.8;
          const [sR, sG, sB] = hsl(starH, 6 + emoHsl.s * 0.1, 55);
          fill.setColor(Skia.Color(`rgba(${sR}, ${sG}, ${sB}, ${starA})`));
          canvas.drawCircle(star.x * W, star.y * H, star.r, fill);
        }

        // ── 5. Cosmic dust ──
        for (const d of dustRef.current) {
          d.phase += d.speed;
          const dx = cx + (d.x + Math.sin(d.phase) * 0.08) * spread * 1.5;
          const dy = cy + (d.y + Math.cos(d.phase * 0.7) * 0.08) * spread * 1.5;
          const dp = camera.project(dx, dy, d.z * spread);
          if (dp.scale < 0.05) continue;
          const dustA = d.alpha * dp.scale * (1 + globalPulse * 0.3);
          if (dustA < 0.005) continue;
          const [dR, dG, dB] = hsl(emoHsl.h, emoHsl.s * 0.4, emoHsl.l);
          fill.setColor(Skia.Color(`rgba(${dR}, ${dG}, ${dB}, ${dustA})`));
          canvas.drawCircle(dp.sx, dp.sy, d.size * dp.scale, fill);
        }

        // ── 6. Central glow (subtle ambient, not fog) ──
        const glP = 1 + globalPulse * 0.2;
        const glowR = Math.min(W, H) * 0.40 * (0.85 + 0.15 * Math.sin(time * 0.3)) * glP;
        const glowA = (0.03 + config.coreGlow * 0.6) * glP;
        const glowA2 = (0.015 + config.coreGlow * 0.25) * glP;
        const glowShader = Skia.Shader.MakeRadialGradient(
          { x: cx, y: cy }, glowR,
          [
            Skia.Color(`rgba(${er}, ${eg}, ${eb}, ${glowA})`),
            Skia.Color(`rgba(${Math.round(er * 0.7)}, ${Math.round(eg * 0.7)}, ${Math.round(eb * 0.7)}, ${glowA2})`),
            Skia.Color('rgba(0, 0, 0, 0)'),
          ],
          [0, 0.35, 1],
          TileMode.Clamp,
        );
        fill.setShader(glowShader);
        canvas.drawCircle(cx, cy, glowR, fill);
        fill.setShader(null);

        // ── 7. Harmonic Waves ──
        for (const w of waveDrawData) {
          if (w.points.length < 2 || w.alpha < 0.001) continue;
          const waveA = w.alpha * 0.3 * (config.waveActive ? 1 : 0);
          if (waveA < 0.002) continue;

          const wavePath = Skia.Path.Make();
          wavePath.moveTo(cx + w.points[0].x, cy + w.points[0].y);
          for (let i = 1; i < w.points.length; i++) {
            const curr = w.points[i];
            const prev = w.points[i - 1];
            const mx = (prev.x + curr.x) / 2;
            const my = (prev.y + curr.y) / 2;
            wavePath.quadTo(cx + prev.x, cy + prev.y, cx + mx, cy + my);
          }
          wavePath.close();

          // Stroke
          stroke.setColor(Skia.Color(`rgba(${Math.min(255, er + 30)}, ${Math.min(255, eg + 30)}, ${Math.min(255, eb + 30)}, ${waveA})`));
          stroke.setStrokeWidth(1.2 + store.amplitude * 1.5);
          canvas.drawPath(wavePath, stroke);

          // Faint fill
          fill.setColor(Skia.Color(`rgba(${er}, ${eg}, ${eb}, ${waveA * 0.08})`));
          canvas.drawPath(wavePath, fill);
        }

        // ── 8. Thinking rings (processing state) ──
        if (config.thinkConns) {
          const thinkRings = 5;
          for (let r = 0; r < thinkRings; r++) {
            const angle = time * 0.12 + r * Math.PI * 2 / 5;
            const oR = 60 + r * 22;
            const wobble = Math.sin(time * 0.2 + r) * 5;
            const ox = cx + Math.cos(angle) * (oR + wobble);
            const oy = cy + Math.sin(angle) * (oR + wobble);

            const ringShader = Skia.Shader.MakeRadialGradient(
              { x: ox, y: oy }, 18,
              [
                Skia.Color(`rgba(${er}, ${eg}, ${eb}, 0.06)`),
                Skia.Color('rgba(0, 0, 0, 0)'),
              ],
              [0, 1],
              TileMode.Clamp,
            );
            fill.setShader(ringShader);
            canvas.drawCircle(ox, oy, 18, fill);
            fill.setShader(null);
          }

          // Sacred geometry (hexagons)
          for (let s = 0; s < 2; s++) {
            const sr = 70 + s * 40 + globalPulse * 8;
            const hexPath = Skia.Path.Make();
            for (let i = 0; i <= 6; i++) {
              const a = (i / 6) * Math.PI * 2 + time * 0.04 + s * 0.5;
              const hx = cx + Math.cos(a) * sr;
              const hy = cy + Math.sin(a) * sr;
              if (i === 0) hexPath.moveTo(hx, hy);
              else hexPath.lineTo(hx, hy);
            }
            hexPath.close();
            stroke.setColor(Skia.Color(`rgba(${er}, ${eg}, ${eb}, 0.012)`));
            stroke.setStrokeWidth(0.5);
            canvas.drawPath(hexPath, stroke);
          }
        }

        // ── 9. Connections ──
        const connDist = CONNECTION_DISTANCE * (config.thinkConns ? 2.5 : 1);
        const drawnEdges = new Set<string>();
        for (const p of projected) {
          const n = system.neurons[p.idx];
          if (p.scale < 0.1) continue;

          for (const j of n.connections) {
            const key = p.idx < j ? `${p.idx}-${j}` : `${j}-${p.idx}`;
            if (drawnEdges.has(key)) continue;
            drawnEdges.add(key);

            const m = system.neurons[j];
            const mp = camera.project(m.x, m.y, m.z);
            const dx = p.sx - mp.sx;
            const dy = p.sy - mp.sy;
            const dist = Math.sqrt(dx * dx + dy * dy);

            if (dist > connDist * 2.8) continue;
            const strength = Math.max(0, 1 - dist / (connDist * 2.8));
            const avgEnergy = (n.energy + m.energy) / 2;
            const connA = strength * config.connAlpha * (0.25 + avgEnergy * 0.75)
              * Math.min(p.scale, mp.scale) * (1 + globalPulse * 0.15);
            if (connA < 0.003) continue;

            // Traveling light pulse along connection
            const travelT = (time * 0.5 + p.idx * 0.08) % 1;
            const pulseX = p.sx + (mp.sx - p.sx) * travelT;
            const pulseY = p.sy + (mp.sy - p.sy) * travelT;

            // Draw connection line — bright glowing wires
            const lineA = Math.min(1, connA * 2.5);
            stroke.setColor(Skia.Color(`rgba(${Math.min(255, er + 80)}, ${Math.min(255, eg + 80)}, ${Math.min(255, eb + 80)}, ${lineA})`));
            stroke.setStrokeWidth((0.4 + strength * 1.2) * Math.min(p.scale, mp.scale));
            const connPath = Skia.Path.Make();
            const mmx = (p.sx + mp.sx) / 2 + Math.sin(time * 0.6 + p.idx) * 2;
            const mmy = (p.sy + mp.sy) / 2 + Math.cos(time * 0.6 + j) * 2;
            connPath.moveTo(p.sx, p.sy);
            connPath.quadTo(mmx, mmy, mp.sx, mp.sy);
            canvas.drawPath(connPath, stroke);

            // Bright traveling pulse dot
            if (connA > 0.005) {
              const pulseBright = Math.min(1, connA * 4.0);
              fill.setColor(Skia.Color(`rgba(${Math.min(255, er + 100)}, ${Math.min(255, eg + 100)}, ${Math.min(255, eb + 100)}, ${pulseBright})`));
              canvas.drawCircle(pulseX, pulseY, 1.8 * Math.min(p.scale, mp.scale), fill);
            }
          }
        }

        // ── 10. Data flow light streams (processing) ──
        for (const flow of system.dataFlows) {
          if (!flow.active || flow.chain.length < 2) continue;
          const totalSegs = flow.chain.length - 1;
          const globalPos = flow.progress * totalSegs;
          const segIdx = Math.min(totalSegs - 1, Math.floor(globalPos));
          const segT = globalPos - segIdx;
          const from = system.neurons[flow.chain[segIdx]];
          const to = system.neurons[flow.chain[segIdx + 1]];
          const fp = camera.project(from.x, from.y, from.z);
          const tp = camera.project(to.x, to.y, to.z);
          const px = fp.sx + (tp.sx - fp.sx) * segT;
          const py = fp.sy + (tp.sy - fp.sy) * segT;
          const sc = (fp.scale + tp.scale) / 2;
          const flowA = Math.sin(flow.progress * Math.PI) * 0.7;

          // Glow
          const flowGlowR = 10 * sc;
          const flowGlowShader = Skia.Shader.MakeRadialGradient(
            { x: px, y: py }, flowGlowR,
            [
              Skia.Color(`rgba(${Math.min(255, er + 40)}, ${Math.min(255, eg + 40)}, ${Math.min(255, eb + 40)}, ${flowA * 0.4})`),
              Skia.Color('rgba(0, 0, 0, 0)'),
            ],
            [0, 1],
            TileMode.Clamp,
          );
          fill.setShader(flowGlowShader);
          canvas.drawCircle(px, py, flowGlowR, fill);
          fill.setShader(null);

          // Core particle
          fill.setColor(Skia.Color(`rgba(${Math.min(255, er + 50)}, ${Math.min(255, eg + 80)}, 255, ${flowA})`));
          canvas.drawCircle(px, py, (1.2 + flowA * 1.2) * sc * flow.size, fill);

          // Trail line
          const trailLen = 0.08;
          const prevPos = Math.max(0, globalPos - trailLen);
          const prevSeg = Math.min(totalSegs - 1, Math.floor(prevPos));
          const prevT = prevPos - prevSeg;
          const prevFrom = system.neurons[flow.chain[prevSeg]];
          const prevTo = system.neurons[flow.chain[Math.min(flow.chain.length - 1, prevSeg + 1)]];
          const pfp = camera.project(prevFrom.x, prevFrom.y, prevFrom.z);
          const ptp = camera.project(prevTo.x, prevTo.y, prevTo.z);
          const trailX = pfp.sx + (ptp.sx - pfp.sx) * prevT;
          const trailY = pfp.sy + (ptp.sy - pfp.sy) * prevT;

          stroke.setColor(Skia.Color(`rgba(${Math.min(255, er + 30)}, ${Math.min(255, eg + 50)}, 255, ${flowA * 0.25})`));
          stroke.setStrokeWidth(sc * 0.8);
          const trailPath = Skia.Path.Make();
          trailPath.moveTo(trailX, trailY);
          trailPath.lineTo(px, py);
          canvas.drawPath(trailPath, stroke);
        }

        // ── 11. Synaptic fires ──
        for (const fire of system.fires) {
          if (!fire.active) continue;
          const from = system.neurons[fire.from];
          const to = system.neurons[fire.to];
          const fp = camera.project(from.x, from.y, from.z);
          const tp = camera.project(to.x, to.y, to.z);
          const px = fp.sx + (tp.sx - fp.sx) * fire.progress;
          const py = fp.sy + (tp.sy - fp.sy) * fire.progress;
          const fireA = Math.sin(fire.progress * Math.PI) * 1.0;
          const sc = (fp.scale + tp.scale) / 2;

          // Glow halo
          const fireGlowR = 8 * sc;
          const fireGlowShader = Skia.Shader.MakeRadialGradient(
            { x: px, y: py }, fireGlowR,
            [
              Skia.Color(`rgba(${Math.min(255, er + 30)}, ${Math.min(255, eg + 30)}, ${Math.min(255, eb + 30)}, ${fireA * 0.3})`),
              Skia.Color('rgba(0, 0, 0, 0)'),
            ],
            [0, 1],
            TileMode.Clamp,
          );
          fill.setShader(fireGlowShader);
          canvas.drawCircle(px, py, fireGlowR, fill);
          fill.setShader(null);

          // Core
          fill.setColor(Skia.Color(`rgba(${Math.min(255, er + 40)}, ${Math.min(255, eg + 40)}, ${Math.min(255, eb + 40)}, ${fireA})`));
          canvas.drawCircle(px, py, (1 + fireA * 0.8) * sc, fill);
        }

        // ── 12. Neurons (back to front) ──
        // Two-pass: first draw all glow halos with blur, then core circles
        // This is more efficient than per-neuron gradient shaders

        // Pass A: Subtle glow halos (keep tight, not foggy)
        blurFill.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
        for (const p of projected) {
          const n = system.neurons[p.idx];
          const baseR = n.size * p.scale;
          const a = n.brightness * (0.5 + n.energy * 0.5) * Math.min(1, p.scale * 1.4);
          if (a < 0.02 || baseR < 0.8) continue;

          const breathe = 1 + Math.sin(time * 0.5 + n.phase) * 0.06 + globalPulse * 0.06;
          const r = baseR * breathe;
          const glowSize = r * 1.6;
          const glowA = a * 0.18;

          blurFill.setColor(Skia.Color(`rgba(${Math.min(255, er + 50)}, ${Math.min(255, eg + 50)}, ${Math.min(255, eb + 50)}, ${Math.min(0.5, glowA)})`));
          canvas.drawCircle(p.sx, p.sy, glowSize, blurFill);
        }
        blurFill.setMaskFilter(null);

        // Pass B: Core neurons — blazing bright nodes
        for (const p of projected) {
          const n = system.neurons[p.idx];
          const baseR = n.size * p.scale;
          const rawA = n.brightness * (0.5 + n.energy * 0.5) * Math.min(1, p.scale * 1.4);
          if (rawA < 0.005 || baseR < 0.2) continue;
          const a = Math.min(1, rawA * 1.6);

          const breathe = 1 + Math.sin(time * 0.5 + n.phase) * 0.07 + globalPulse * 0.07;
          const r = baseR * breathe;

          if (r > 1.0) {
            // White-hot core → bright cyan → fade
            const coreShader = Skia.Shader.MakeRadialGradient(
              { x: p.sx - r * 0.15, y: p.sy - r * 0.15 }, r,
              [
                Skia.Color(`rgba(${Math.min(255, er + 160)}, ${Math.min(255, eg + 140)}, ${Math.min(255, eb + 130)}, ${a})`),
                Skia.Color(`rgba(${Math.min(255, er + 70)}, ${Math.min(255, eg + 60)}, ${Math.min(255, eb + 50)}, ${a * 0.85})`),
                Skia.Color(`rgba(${er}, ${eg}, ${eb}, ${a * 0.2})`),
              ],
              [0, 0.3, 1],
              TileMode.Clamp,
            );
            fill.setShader(coreShader);
            canvas.drawCircle(p.sx, p.sy, r, fill);
            fill.setShader(null);

            // White specular glint
            if (r > 1.3) {
              const specA = Math.min(1, 0.25 + n.energy * 0.35);
              fill.setColor(Skia.Color(`rgba(230, 245, 255, ${specA})`));
              canvas.drawCircle(p.sx, p.sy, r * 0.35, fill);
            }
          } else {
            // Small neurons — bright points
            fill.setColor(Skia.Color(`rgba(${Math.min(255, er + 120)}, ${Math.min(255, eg + 100)}, ${Math.min(255, eb + 90)}, ${a})`));
            canvas.drawCircle(p.sx, p.sy, r, fill);
          }

          n.energy *= 0.996;
        }

        // ── 13. Vignette (lighter — let the stars shine through) ──
        const vigR = Math.max(W, H) * 0.80;
        const vigShader = Skia.Shader.MakeRadialGradient(
          { x: cx, y: cy }, vigR,
          [
            Skia.Color('rgba(0, 0, 0, 0)'),
            Skia.Color('rgba(2, 3, 8, 0.40)'),
          ],
          [Math.min(W, H) * 0.30 / vigR, 1],
          TileMode.Clamp,
        );
        fill.setShader(vigShader);
        canvas.drawRect(Skia.XYWHRect(0, 0, W, H), fill);
        fill.setShader(null);
      },
      canvasRect,
    );

    setPicture(pic);
    animFrameRef.current = requestAnimationFrame(tick);
  }, [width, height, canvasRect]);

  useEffect(() => {
    animFrameRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(animFrameRef.current);
  }, [tick]);

  if (!picture) {
    return <Canvas style={{ flex: 1, backgroundColor: '#030608' }} />;
  }

  return (
    <Canvas style={{ flex: 1, backgroundColor: '#030608' }}>
      <Picture picture={picture} />
    </Canvas>
  );
};

export default SwarmCanvas;
