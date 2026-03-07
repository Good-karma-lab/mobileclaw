# Phase 12: GUAPPA Android UI — Ultra-Futuristic Agent Interface

**Date**: 2026-03-07
**Status**: Proposal
**Depends On**: Phase 1 (Foundation), Phase 2 (Provider Router), Phase 3 (Tool Engine), Phase 4 (Proactive Push), Phase 5 (Channel Hub), Phase 6 (Voice Pipeline), Phase 7 (Memory & Context), Phase 10 (Live Config), Phase 11 (World Wide Swarm)
**Scope**: Complete UI redesign of the GUAPPA Android app — 5-screen liquid-futuristic interface with comprehensive Maestro test coverage

---

## 0. Vision

GUAPPA is an autonomous AI agent — like J.A.R.V.I.S. — running on Android, connected to the World Wide Swarm. The UI must feel like an interface from 2035: volumetric liquid glass surfaces, a living plasma orb AI visualization, morphing navigation, and real-time reactive animations. Every pixel communicates intelligence, presence, and power.

The app has 5 screens accessed via a floating morphing dock:
1. **Voice** — fullscreen plasma orb, the primary AI interaction mode
2. **Chat** — streaming conversation with liquid glass bubbles
3. **Command Center** — tasks, schedules, triggers, memory
4. **Swarm** — World Wide Swarm connection, P2P feed, network status
5. **Configuration** — capability-first settings, permissions, debug info download

---

## 1. Visual Identity

### 1.1 Aesthetic Direction

**Liquid futurism.** Every surface is frosted glass with depth, refraction, and subtle luminescence. Dark base theme with iridescent accent lighting. Not flat, not skeuomorphic — *volumetric*.

### 1.2 Color System

```
Base:
  space-black:     #050510
  midnight-blue:   #0A0A2E
  background:      linear-gradient(180deg, #050510, #0A0A2E)

Glass Surfaces:
  glass-fill:      rgba(255, 255, 255, 0.06–0.12)
  glass-border:    rgba(255, 255, 255, 0.15)  /* 1px luminous border */
  glass-blur:      12px backdrop blur

GUAPPA Accent (iridescent, state-dependent):
  idle:            #00F0FF (cyan)
  listening:       #00F0FF → #00D4E0 (cool cyan, brightened)
  processing:      #8B5CF6 (violet)
  speaking:        #FF3366 → #FFAA33 (rose to amber)
  full-cycle:      cyan → violet → rose → cyan (30s idle drift)

User Accent:
  user-warm:       #FFAA33 (amber)
  user-gold:       #FFD700 (gold)

Semantic:
  success:         #14B8A6 (teal)
  error:           #EF4444 (crimson)
  warning:         #F59E0B (amber)
  info:            #3B82F6 (blue)
  /* all semantic colors get glass treatment in cards */

Text:
  primary:         rgba(255, 255, 255, 0.90)
  secondary:       rgba(255, 255, 255, 0.60)
  tertiary:        rgba(255, 255, 255, 0.35)
```

### 1.3 Typography

| Role | Font | Weight | Usage |
|------|------|--------|-------|
| Display | Orbitron | 700 | Screen titles, GUAPPA wordmark, section headers |
| Body | Exo 2 | 400/500/600 | Body text, labels, descriptions, messages |
| Mono | JetBrains Mono | 400 | Code, stats, timestamps, technical data |

### 1.4 Motion Principles

- **Spring physics everywhere** — reanimated shared values, no linear easing
- **Liquid morphing transitions** — shared element transitions between screens
- **Subtle parallax** — gyroscope-driven on glass layers (supported devices)
- **Micro-interactions** — every tap produces a ripple through the glass surface
- **Organic state changes** — animate through intermediate shapes, never snap
- **Performance budget** — 60fps minimum, drop effects before dropping frames

### 1.5 Glass Material Spec

Every glass surface is constructed from these layers (bottom to top):
1. **Backdrop blur** — `expo-blur` BlurView (intensity: 12–20)
2. **Fill** — semi-transparent white (6–12% opacity)
3. **Noise grain** — Skia noise shader (opacity: 0.03, animated)
4. **Border** — 1px rgba(255,255,255,0.12–0.15)
5. **Inner glow** — optional, for active/focused states (accent color at 8%)

---

## 2. Tech Stack — New Dependencies

| Library | Purpose | Justification |
|---------|---------|---------------|
| `@shopify/react-native-skia` | Custom shaders (plasma orb, noise, particles, arc charts, topology graph) | Only library providing GPU-accelerated custom GLSL shaders on Android RN |
| `react-native-glass-effect-view` | Lightweight glass utility for simple cards/buttons | Android fallback sufficient for non-hero glass surfaces |
| `expo-sensors` | Gyroscope data for parallax effects | Already in Expo ecosystem |

**Retained from current stack:**
- `react-native-reanimated` 4.x — spring animations, layout transitions, shared values
- `expo-blur` — backdrop blur layers
- `expo-linear-gradient` — gradient overlays
- `react-native-gesture-handler` — swipe, long-press, tap gestures
- `expo-haptics` — tactile feedback on interactions
- `expo-file-system` — debug info ZIP creation
- `expo-av` — audio playback/recording

---

## 3. Navigation — Floating Morphing Dock

### 3.1 Phone Layout: Floating Pill Dock

```
┌──────────────────────────────────┐
│                                  │
│         [Screen Content]         │
│                                  │
│                                  │
│                                  │
│   ╭─────────────────────────╮    │
│   │  🎙  💬  ⚡  🌐  ⚙️  │    │  ← Floating 16px above safe area
│   ╰─────────────────────────╯    │
└──────────────────────────────────┘
```

**Construction:**
- Skia-rendered capsule with glass shader (blur + noise + luminous border)
- Auto-width: 5 icons × 56px + padding = ~320px, centered
- Absolute positioned, `pointerEvents="box-none"` on container for pass-through
- Semi-transparent — content scrolls behind it with blur

**Active Tab Indicator:**
- Active icon scales to 1.2x with spring physics (damping: 15, stiffness: 150)
- Glow ring pulses in GUAPPA accent color (Skia radial gradient, animated opacity)
- The glow "slides" between icons as a liquid blob — reanimated shared value drives X position, interpolated with spring

**Idle Animation:**
- Subtle breathing: scale oscillates 0.995–1.005, 4s period (imperceptible but alive)

**Tab Icons** (custom, consistent style):
| Tab | Icon | Label |
|-----|------|-------|
| Voice | Waveform / orb glyph | Voice |
| Chat | Chat bubble | Chat |
| Command Center | Lightning bolt / radar | Command |
| Swarm | Network nodes | Swarm |
| Config | Sliders / equalizer | Config |

### 3.2 Tablet/Automotive Layout: Liquid Glass Side Rail

```
┌────┬─────────────────────────────┐
│ 🎙 │                             │
│    │                             │
│ 💬 │      [Screen Content]       │
│    │                             │
│ ⚡ │                             │
│    │                             │
│ 🌐 │                             │
│    │                             │
│ ⚙️ │                             │
└────┴─────────────────────────────┘
  72px         remaining width
```

**Construction:**
- Left edge, full height, 72px wide collapsed
- Same liquid glass material as dock
- Icons vertically stacked, centered in rail

**Expand Behavior:**
- Long-press or swipe right: expands to 240px with spring animation
- Shows icon + label for each tab (Exo 2, 14px)
- Auto-collapses after 3s idle or on tap outside

**Active Indicator:**
- Vertical glowing bar (3px wide) on the left edge of active tab
- Color matches GUAPPA accent state

### 3.3 Screen Transitions

- Horizontal shared-element morph between adjacent tabs
- Plasma orb (Voice screen) scales down into the dock icon when leaving Voice tab
- Glass surfaces cross-fade with slight parallax shift (20ms stagger)
- Duration: 300ms with spring deceleration

---

## 4. Screen 1: Voice — The Centerpiece

### 4.1 Layout

Fullscreen, no header chrome. The entire screen IS the experience.

```
┌──────────────────────────────────┐
│ GUAPPA               ● 12 peers │  ← Fade-in on tap, auto-hide 3s
│                                  │
│                                  │
│                                  │
│          ╭──────────╮            │
│         ╱  ▒▒▒▒▒▒▒▒  ╲          │
│        │  ▒ PLASMA ▒  │         │  ← 45% screen width
│         ╲  ▒▒▒▒▒▒▒▒  ╱          │
│          ╰──────────╯            │
│        ◌ voice waveform ◌        │  ← Circular ring around orb
│                                  │
│         "Listening..."           │  ← State label, fade transitions
│    (interim transcript here)     │  ← Real-time STT, 60% opacity
│                                  │
│   ╭─────────────────────────╮    │
│   │  🎙  💬  ⚡  🌐  ⚙️  │    │
│   ╰─────────────────────────╯    │
└──────────────────────────────────┘
```

### 4.2 Background

- Deep space gradient: `#050510` → `#0A0A2E` (vertical)
- Skia animated noise grain overlay: opacity 0.03, slowly drifting
- Faint radial gradient behind orb: GUAPPA accent color at 5% opacity, radius 60% screen

### 4.3 Plasma Orb — Skia Custom Shader

**Construction:**
- Skia `<Canvas>` element, centered
- Custom GLSL fragment shader with 3 layered simplex noise fields
- Each noise layer runs at different frequency and scroll speed
- Output: color = mix(accent_colors, noise_value) with distortion displacement
- Outer edge: Gaussian blur glow (20px radius) in current dominant color

**Orb States:**

| State | Scale | Color Shift | Noise Behavior | Additional |
|-------|-------|-------------|----------------|------------|
| **Idle** | 0.97–1.03 (4s breathe) | Slow iridescent drift (30s cycle) | Slow plasma currents | Ambient glow |
| **Listening** | 0.90 (contracted) | Cool cyan dominant | Frequency increases with voice amplitude | Audio level ring appears |
| **Processing** | 0.95 (held) | Violet dominant | Accelerates into vortex rotation | 3–5 orbiting particle dots |
| **Speaking** | 1.10 (expanded) | Rose/amber warm | Displacement maps to GUAPPA audio amplitude | Ripple shockwaves radiate outward |
| **Error** | 1.0 (rapid shake) | Crimson flash | Momentary turbulence | Single flash, return to idle |

**State transitions:** spring-animated scale + color lerp over 400ms.

### 4.4 Voice Waveform Ring

- Skia `<Path>` — circular waveform wrapping around orb at radius = orb + 20px
- During listening: deforms based on user audio amplitude (polar coordinates, amplitude → radius offset)
- During speaking: shows GUAPPA output amplitude
- Smooth interpolation between sample points (cubic bezier)
- Color: GUAPPA accent at 40% opacity
- Line width: 2px

### 4.5 Status Elements

- **Top-left**: "GUAPPA" in Orbitron 14px, 40% opacity. Fades in on screen tap, auto-hides after 3s.
- **Top-right**: Connection dot (green/amber/red, 8px) + swarm peer count badge if connected (glass pill, JetBrains Mono 11px)
- **Bottom center** (above dock): State label in Exo 2 16px — "Listening...", "Thinking...", "Speaking" — cross-fade transitions (200ms)
- **Below state label**: Interim STT transcript, Exo 2 14px, 60% opacity. Updates real-time. Fades when finalized.

### 4.6 Interactions

| Gesture | Action |
|---------|--------|
| **Tap orb** | Toggle listening on/off (push-to-talk) |
| **Long-press orb** (500ms) | Activate continuous conversation mode — orb gains persistent outer ring, haptic feedback |
| **Swipe up on orb** | Expand last 3 messages as floating glass cards (mini-chat preview) |
| **Swipe down** (on expanded preview) | Dismiss mini-chat |
| **Tap mini-chat card** | Navigate to Chat tab, scroll to that message |

### 4.7 Tablet/Automotive Adaptation

- Orb scales to 35% of the smaller screen dimension
- Transcript text: larger font (18px) for readability at distance
- Side rail visible on left
- Mini-chat preview cards: wider, can show more text

---

## 5. Screen 2: Chat — Streaming Conversation

### 5.1 Layout

```
┌──────────────────────────────────┐
│ ╔══════════════════════════════╗ │
│ ║  Chat                   (●) ║ │  ← Glass header, mini orb right
│ ╚══════════════════════════════╝ │
│                                  │
│  ╭─────────────────────╮        │  ← GUAPPA message (left, cool tint)
│  │ Hello! How can I     │        │
│  │ help you today?      │        │
│  ╰─────────────────────╯        │
│                                  │
│        ╭─────────────────────╮  │  ← User message (right, warm tint)
│        │ Set an alarm for    │  │
│        │ 7am tomorrow        │  │
│        ╰─────────────────────╯  │
│                                  │
│  ╭──────────────────────╮       │  ← GUAPPA streaming (glow cursor)
│  │ Done! I've set your   │       │
│  │ alarm for 7:00 AM ▍   │       │  ← Cursor pulses cyan
│  ╰──────────────────────╯       │
│                                  │
│ ╔══════════════════════════════╗ │
│ ║ 📎  Message GUAPPA...   🎤 ║ │  ← Glass input bar
│ ╚══════════════════════════════╝ │
│   ╭─────────────────────────╮    │
│   │  🎙  💬  ⚡  🌐  ⚙️  │    │
│   ╰─────────────────────────╯    │
└──────────────────────────────────┘
```

### 5.2 Header

- Frosted glass bar, extends into status bar area
- "Chat" in Orbitron 18px (left)
- Mini plasma orb (24px, live Skia shader — same as Voice but miniaturized). Tap → navigate to Voice tab.
- Bottom edge: 1px gradient line (cyan → transparent → cyan), subtle

### 5.3 Message Bubbles

**Glass Material per sender:**

| Property | GUAPPA (left-aligned) | User (right-aligned) |
|----------|-----------------------|----------------------|
| Fill tint | Cyan-violet at 8% opacity | Amber-gold at 10% opacity |
| Border | 1px cool white 12% | 1px warm white 15% |
| Corner radius | 16px all, top-left 4px | 16px all, top-right 4px |
| Blur | backdrop 12px | backdrop 8px |
| Max width | 80% of screen | 80% of screen |

**Entry animation:** SlideInLeft/Right + FadeIn + scale spring from 0.85 (damping: 12, stiffness: 100)

**Voice message badge:**
- Messages originating from voice mode show a small icon badge:
  - User voice: microphone icon (8px, top-right of bubble)
  - GUAPPA voice: speaker icon (8px, top-left of bubble)
- Tapping speaker badge on GUAPPA message replays TTS audio

**Markdown rendering:**
- Headings: Orbitron, scaled sizes
- Code blocks: JetBrains Mono, glass card with slightly darker tint
- Links: cyan underline, tap to open
- Lists, tables, blockquotes: Exo 2 with appropriate indentation
- Images: rounded corners, glass border, tap to fullscreen

### 5.4 Streaming Behavior

```
Timeline:
  t=0     User sends message
  t=0.1   Mini plasma orb typing indicator appears (pulsing, 32px)
  t=T     First token arrives:
          → Typing indicator morphs into glass bubble (spring scale from 32px to bubble min-width)
          → Text begins filling in with glow cursor (cyan, 2px wide, pulsing 0.5s)
  t=T+n   Tokens stream in:
          → Bubble width/height animates with spring physics as content grows
          → reanimated layout animation on bubble container
          → Markdown renders progressively (headings/code materialize as tokens complete them)
  t=END   Stream completes:
          → Glow cursor fades out (200ms)
          → Bubble settles with micro-bounce (spring overshoot: 1.02, settle to 1.0)
```

### 5.5 Input Bar

- Frosted glass strip, full width, 56px height (expands for multiline, max 4 visible lines)
- **Left**: Attachment button (paperclip icon) — opens picker: Image (expo-image-picker), File (SAF)
- **Center**: TextInput, transparent bg, white text, placeholder "Message GUAPPA..." in 35% opacity
- **Right (no text)**: Microphone button — tap to record voice note, waveform replaces text input while recording, release to send
- **Right (has text)**: Send button — glass circle (32px) with arrow icon, pulses once on tap with haptic

### 5.6 Empty State

- Plasma orb centered in chat area, 50% opacity, breathing animation
- "Start a conversation" in Exo 2 16px, 40% opacity
- Tap orb → navigate to Voice tab

### 5.7 Tablet/Automotive Adaptation

- Messages max-width: 65% of screen
- Input bar: inset from edges, floating pill shape (matching dock aesthetic)
- Side rail visible on left

---

## 6. Screen 3: Command Center — Agent Operations

### 6.1 Layout (Phone — Vertical Feed)

```
┌──────────────────────────────────┐
│ ╔══════════════════════════════╗ │
│ ║  Command Center    Active ● ║ │  ← Status pill: "Executing 2 tasks"
│ ╚══════════════════════════════╝ │
│                                  │
│ ▼ Active Tasks              (3) │  ← Collapsible glass section
│ ┌────────────────────────────┐   │
│ │ ◐ Search flights to Tokyo  │   │  ← Progress ring + task name
│ │   Searching web...    1m2s │   │
│ ├────────────────────────────┤   │
│ │ ● Send morning briefing    │   │  ← Completed (teal ring)
│ │   Done               0m8s │   │
│ └────────────────────────────┘   │
│                                  │
│ ▶ Scheduled                 (5) │  ← Collapsed, shows next-fire time
│   Next in 2h 14m                 │
│                                  │
│ ▶ Triggers               4 on   │  ← Collapsed
│                                  │
│ ▶ Memory          142 facts 23MB │  ← Collapsed
│                                  │
│ ▶ Sessions                  (2) │  ← Collapsed
│                                  │
│   ╭─────────────────────────╮    │
│   │  🎙  💬  ⚡  🌐  ⚙️  │    │
│   ╰─────────────────────────╯    │
└──────────────────────────────────┘
```

### 6.2 Header

- Frosted glass bar, "Command Center" in Orbitron 18px
- Right: live status pill — glass capsule with colored dot:
  - Green + "Active" — agent processing
  - Cyan + "Executing N tasks" — concurrent tasks running
  - Dim + "Idle" — no active work

### 6.3 Section: Active Tasks

**Collapsed:** "Active Tasks" + count badge (glowing glass pill with number)

**Expanded:** List of task cards:

| Element | Rendering |
|---------|-----------|
| Status ring | Skia animated arc — RUNNING: cyan rotating, PENDING: amber static, COMPLETED: teal full + checkmark, FAILED: crimson broken + X, CANCELLED: gray dashed |
| Task name | Exo 2 16px bold |
| Current step | Exo 2 14px, 60% opacity ("Searching web...", "SMS sent") |
| Elapsed time | JetBrains Mono 12px, 35% opacity, live counter |
| Tap to expand | Full detail: step list, tool calls with results, token usage |
| Swipe left | Cancel task (glass confirmation dialog) |

### 6.4 Section: Scheduled Tasks & Cron Jobs

**Collapsed:** "Scheduled" + count + next-fire countdown in JetBrains Mono ("Next in 2h 14m")

**Expanded:** Vertical timeline:
- Left edge: vertical line (1px, glass white 20%)
- Nodes along timeline for each scheduled item:
  - Cron → human-readable: "Every day at 8:00 AM"
  - Last run: success (teal dot) / fail (crimson dot) + timestamp
  - Next run: relative time + absolute time
  - Enable/disable: glass pill toggle switch
  - Tap: edit schedule in a glass bottom sheet

### 6.5 Section: Triggers & Hooks

**Collapsed:** "Triggers" + active count ("4 active")

**Expanded:** Grid of trigger cards:
- **Phone:** 2 columns
- **Tablet:** 4 columns

Each card:
- Glass card, 1px border
- Icon (relevant to trigger type) + name
- Toggle switch (glass pill)
- Active triggers: icon has subtle pulse animation (scale 0.95–1.05, 2s)
- Tap card (not toggle): configure parameters (delay, quiet hours, custom prompt) in bottom sheet

**Built-in triggers:**
| Trigger | Icon | Default |
|---------|------|---------|
| Incoming SMS | Message icon | ON |
| Missed Call | Phone-missed icon | ON |
| Calendar Event | Calendar icon | ON |
| New Email | Mail icon | OFF |
| Low Battery | Battery icon | ON |
| Geofence | Map-pin icon | OFF |
| Morning Briefing | Sun icon | ON |
| Evening Summary | Moon icon | ON |

### 6.6 Section: Memory

**Collapsed:** "Memory" + fact count + storage size ("142 facts, 23 MB")

**Expanded:**
- **Search bar:** Glass input with magnifying glass icon
- **Category filter pills:** horizontal scroll — All | Preferences | Facts | Relationships | Routines | Dates. Glass pills, active one glows with accent color.
- **Memory cards** (flat list):
  - Fact text (Exo 2 14px)
  - Category badge (colored glass pill, small)
  - Confidence bar (thin Skia bar, color: green >0.8, amber >0.5, red <0.5)
  - Last accessed (JetBrains Mono 11px, 35% opacity)
  - Swipe left: delete with confirmation
- **Footer:** Storage stats row (total facts, embedding count, DB size in JetBrains Mono)
- **Action buttons:** "Export Memory" | "Import Memory" — glass outline buttons

### 6.7 Section: Sessions

**Collapsed:** "Sessions" + active count

**Expanded:**
- Active sessions list, each row:
  - Type badge: CHAT (cyan) / BACKGROUND_TASK (violet) / TRIGGER (amber) / SYSTEM (gray) — glass pill
  - Duration (live counter)
  - Token usage bar (Skia, filled proportionally)
- **Context budget visualization:** Skia arc chart showing allocation:
  - System prompt (~2K): blue
  - Tool schemas (~3K): violet
  - Memories (~5K): teal
  - Conversation (~remaining): cyan
  - Reserve (~4K): gray
- **Compaction status:** "Context at 64%" progress bar with threshold marker at 80%

### 6.8 Section Collapse/Expand Animation

- Spring-driven height animation (damping: 20, stiffness: 180)
- Content fades in after 100ms delay (prevents flash of unstyled content during expand)
- Chevron icon rotates 180 degrees with spring
- Glass surface brightens slightly when expanded (fill opacity 6% → 10%)

### 6.9 Tablet/Automotive — Mission Control Grid

```
┌────┬───────────────┬───────────────┐
│    │ Session Info Bar (horizontal) │
│    ├───────────────┬───────────────┤
│ Rail│ Active Tasks  │  Scheduled    │
│    │ (scrollable)  │  (scrollable) │
│    │               │               │
│    ├───────────────┬───────────────┤
│    │ Triggers      │  Memory       │
│    │ (scrollable)  │  (scrollable) │
│    │               │               │
└────┴───────────────┴───────────────┘
```

- 2×2 grid, all sections always expanded (no collapse toggles)
- Each card independently scrollable
- Session info as horizontal bar below header
- Real-time updates without user interaction

---

## 7. Screen 4: Swarm — World Wide Swarm Network

### 7.1 Layout

```
┌──────────────────────────────────┐
│ ╔══════════════════════════════╗ │
│ ║  Swarm                Stats ║ │  ← Header + stats button
│ ╚══════════════════════════════╝ │
│ ┌────────────────────────────────┐│
│ │ ● Connected  │ 12 │EXEC│ 847 ││  ← Status bar: toggle, peers, tier, rep
│ └────────────────────────────────┘│
│ ┌────────────────────────────────┐│
│ │  [Embedded ◉] [Remote ○]      ││  ← Connection mode (collapsible)
│ └────────────────────────────────┘│
│                                   │
│ Filter: [All][Tasks][Msgs][Rep]   │  ← Glass filter pills
│                                   │
│ ┃ ⎯ Incoming Task ───────────── │  ← Cyan left edge
│ ┃ "Translate document EN→RU"     │
│ ┃ Caps: text, translation        │
│ ┃ [Accept] [Decline]             │
│ ┃                                │
│ ┃ ⎯ Peer Message ────────────── │  ← Violet left edge
│ ┃ did:swarm:z6Mk...a3f           │
│ ┃ "Task result ready for review" │
│ ┃                                │
│ ┃ ⎯ Reputation ─────────────── │  ← Gold left edge
│ ┃ +5 rep: task completed          │
│ ┃ Source: did:swarm:z6Mk...b7c   │
│                                   │
│   ╭─────────────────────────╮    │
│   │  🎙  💬  ⚡  🌐  ⚙️  │    │
│   ╰─────────────────────────╯    │
└──────────────────────────────────┘
```

### 7.2 Header

- Frosted glass bar, "Swarm" in Orbitron 18px
- Right: "Stats" button (glass outline, tap opens statistics modal)

### 7.3 Status Bar (fixed, glass strip)

| Element | Rendering |
|---------|-----------|
| Connection toggle | Large glass pill switch. ON: fills with flowing cyan energy animation (Skia shader). OFF: energy drains out. |
| Peer count | Network icon + number (JetBrains Mono 14px), subtle scale pulse on change |
| Tier badge | Glass pill: EXECUTOR (gray) / TIER1 (cyan) / TIER2 (violet) / GUARDIAN (gold) |
| Reputation | Skia mini arc gauge (40px), score number in center (JetBrains Mono), color: gray→teal→gold |
| Uptime | JetBrains Mono 11px, 35% opacity ("Up 14d 7h") |

### 7.4 Connection Mode Selector

- Glass segmented control below status bar
- **Embedded**: Local connector status, port info (9370/9371), running indicator
- **Remote**: Connector URL input (glass text field), latency ping (green <100ms, amber <500ms, red >500ms)
- Collapsible — tap chevron to show/hide

### 7.5 Live Feed

**Filter pills** (horizontal scroll, top of feed): All | Tasks | Messages | Reputation | Network — glass pills, multi-select supported, active pills glow

**Event cards** — glass cards with colored left edge (3px):

| Event Type | Left Edge | Icon | Content | Actions |
|------------|-----------|------|---------|---------|
| Incoming Task | Cyan | Target | Title, required capabilities, reputation reward | Accept / Decline (glass buttons) |
| Task Update | Teal | Gear | Task name, new status, result preview | — |
| Peer Message | Violet | Chat bubble | Sender DID (truncated to 8+...+4), message, timestamp | Reply (glass button) |
| Reputation Event | Gold | Star | Change description, source peer DID | — |
| Holon Formed | Rose | Network graph | Purpose, member count, your role | View Details |
| Connection Event | Amber | Link chain | "Peer joined"/"Peer left", peer DID | — |
| Deliberation | Violet | Vote box | Proposal summary, phase, action needed | Submit / Vote (glass buttons) |

**Feed behavior:**
- New events: slide in from top with spring + brief glow flash on left edge (400ms)
- Events >24h old: auto-collapse to single-line summary (icon + title + time)
- Pull-to-refresh with glass loading indicator
- Infinite scroll with pagination

**Inline actions:**
- Accept/Decline buttons on task cards
- Submit Proposal / Vote buttons on deliberation cards
- Inline text input for clarification requests
- All action buttons: glass style, haptic on tap

### 7.6 Empty State (Disconnected)

- Skia rendered: stylized network graph wireframe (dim white lines, slowly rotating, 8-12 nodes)
- "Connect to the Swarm" — Exo 2 18px, 50% opacity
- "Enable" glass button (cyan accent) → triggers connection toggle

### 7.7 Statistics Modal

- Glass bottom sheet (slides up, 80% screen height)
- Content:
  - Tasks completed: all time / 30d / 7d (JetBrains Mono, large numbers)
  - Success rate: Skia arc chart with percentage
  - Total reputation earned: number + trend arrow
  - Messages sent/received: two-column stat
  - Average task completion time
  - Most used capabilities: horizontal bar chart (Skia)
  - Top interacted peers: list with DID + interaction count

### 7.8 Tablet/Automotive Adaptation

```
┌────┬────────────────────┬──────────────────┐
│    │ Status bar (full width)               │
│    ├────────────────────┬──────────────────┤
│Rail│                    │                  │
│    │   Live Feed        │  Topology Graph  │
│    │   (60% width)      │  (40% width)     │
│    │                    │  Skia animated   │
│    │                    │  node network    │
│    │                    │                  │
└────┴────────────────────┴──────────────────┘
```

- **Left 60%**: Live feed (wider cards)
- **Right 40%**: Live topology graph:
  - GUAPPA as center node (GUAPPA accent glow)
  - Connected peers as orbiting nodes (sized by reputation)
  - Connection lines pulse with data flow (animated dash-offset)
  - Tap a node: glass tooltip with peer details (DID, tier, rep, capabilities)
  - Skia Canvas with spring-physics node positioning

---

## 8. Screen 5: Configuration — Capability-First Settings

### 8.1 Layout

```
┌──────────────────────────────────┐
│ ╔══════════════════════════════╗ │
│ ║  Configuration          🔍  ║ │  ← Search icon expands to input
│ ╚══════════════════════════════╝ │
│                                  │
│ ▼ How GUAPPA Thinks              │  ← Capability section
│ ┌────────────────────────────┐   │
│ │ Text Provider: [Anthropic▼]│   │
│ │ Text Model:  [Sonnet 4.6▼]│   │
│ │ Fallback:    [OpenAI ▼   ]│   │
│ │ Temperature: ═══●══ 0.7    │   │
│ │ Budget: $5/day  [$2.3 used]│   │
│ │ 🟢 Internet               │   │  ← Permission status
│ └────────────────────────────┘   │
│                                  │
│ ▶ How GUAPPA Sees                │
│ ▶ How GUAPPA Speaks & Listens    │
│ ▶ How GUAPPA Connects            │
│ ▶ What GUAPPA Can Do             │
│ ▶ What GUAPPA Remembers          │
│ ▶ How GUAPPA Acts on Her Own     │
│ ▶ Local Intelligence             │
│                                  │
│ ▶ Permissions Summary            │
│                                  │
│ ┌────────────────────────────┐   │
│ │ ⬇🐛 Download Debug Info   │   │  ← Distinct action card
│ │ Logs, version, device info │   │
│ └────────────────────────────┘   │
│                                  │
│   ╭─────────────────────────╮    │
│   │  🎙  💬  ⚡  🌐  ⚙️  │    │
│   ╰─────────────────────────╯    │
└──────────────────────────────────┘
```

### 8.2 Header

- Frosted glass bar, "Configuration" in Orbitron 18px
- Right: search icon — tap to expand glass search input (searches across all settings live, filters sections to show only matches)

### 8.3 Capability Sections

All sections are collapsible liquid glass cards with the same expand/collapse animation as Command Center (section 6.8).

#### 8.3a "How GUAPPA Thinks" — Text & Reasoning

| Control | Type | Details |
|---------|------|---------|
| Provider | Glass dropdown | Current provider icon + name, list of all providers with icons |
| Model | Glass dropdown, searchable | Model name + context window + price/1M tokens |
| Fallback provider/model | Secondary dropdowns, dimmer styling | Labeled "Fallback" |
| Temperature | Glass slider | Track with glowing thumb, value in JetBrains Mono |
| Max tokens | Glass slider | Same treatment |
| Daily budget | Glass number input | $ value, current spend progress bar below |
| Monthly display | Read-only stat | Total cost this month |
| Permission: Internet | Status pill | Green "Granted" / Red tap-to-grant |

**Provider/Model selection is per-capability-type.** Each capability section below has its own provider+model selectors. This maps to Phase 2's `setCapabilityProvider(capability, providerId, modelId)` TurboModule API.

#### 8.3b "How GUAPPA Sees" — Vision & Image

| Control | Type |
|---------|------|
| Vision provider + model | Paired glass dropdowns |
| Image generation provider + model | Paired glass dropdowns |
| Video generation provider + model | Paired glass dropdowns |
| Permission: Camera | Status pill with grant flow |
| Permission: Media access | Status pill with grant flow |

**Permission grant flow:** Red "Not Granted" pill → tap → Android permission dialog → on grant: pill animates to green "Granted" with checkmark scale-in + haptic. On deny: pill shakes briefly, stays red.

#### 8.3c "How GUAPPA Speaks & Listens" — Voice

| Control | Type |
|---------|------|
| STT engine | Glass dropdown (ranked: WhisperKit, Google Cloud, Whisper API, ML Kit, Deepgram, Vosk) |
| STT model | Glass dropdown + download button (for on-device). Progress bar with speed + ETA. |
| TTS engine | Glass dropdown (ranked: Picovoice Orca, Kokoro, Piper, ElevenLabs, OpenAI, Google, Android) |
| Voice | Glass dropdown + preview button (play 3s sample, speaker icon pulses during playback) |
| Wake word toggle | Glass pill switch — enable "Hey GUAPPA" |
| Wake word sensitivity | Glass slider (0.0–1.0) |
| Continuous mode default | Glass pill switch |
| Permission: Microphone | Status pill with grant flow |

#### 8.3d "How GUAPPA Connects" — Channels & Integrations

**Channel grid** (2 columns on phone):

Each tile: glass card with:
- Channel icon (Telegram, Discord, Slack, WhatsApp, Signal, Matrix, Email, SMS)
- Channel name (Exo 2 14px)
- Status dot (green connected / red disconnected / amber connecting)
- Enable toggle (glass pill)

**Tap tile → inline expand** (tile grows to show config):
- Token / API key: glass secret input (dots, tap eye icon to reveal)
- Channel-specific config: chat IDs, allowed senders, webhook URL
- Health check button: glass outline, shows latency on completion
- Setup guide link: cyan text link
- Permissions where relevant: SMS send/read, call log (status pills)

#### 8.3e "What GUAPPA Can Do" — Tools & Automation

**Tool category sub-sections** (expandable within this section):
- Device (14 tools) | App Control (12) | Social (5) | Automation (9) | File (6) | Web (4) | AI-Powered (9) | System (4)

Each category expands to show tool list:
- Tool name + toggle switch (glass pill)
- Tool description (Exo 2 12px, 50% opacity)
- High-risk tools: amber warning icon
- Approval-required tools: shield icon
- Permission needed: inline status pill next to tools that require specific permissions

**UI Automation setup card:** Status indicator + setup guide link

#### 8.3f "What GUAPPA Remembers" — Memory Config

| Control | Type |
|---------|------|
| Auto-summarization | Glass pill toggle |
| Summarization threshold | Glass slider (60–90% context usage) |
| Long-term extraction | Glass pill toggle |
| Embedding model | Glass dropdown with download status per model |
| Short-term TTL | Glass number input (days) |
| Episodic TTL | Glass number input (days) |
| Storage usage | Skia bar: DB size / vector size / available. Color-coded. |

#### 8.3g "How GUAPPA Acts on Her Own" — Proactive Behavior

| Control | Type |
|---------|------|
| Proactive mode | Master glass pill toggle |
| Quiet hours start/end | Glass time pickers (scroll wheel, glass overlay) |
| Notification cooldown | Glass slider (1–10 minutes) |
| Morning briefing | Glass pill toggle + time picker |
| Evening summary | Glass pill toggle + time picker |
| Individual triggers | Toggle list (same as Command Center but with deeper config) |
| Permission: Notifications | Status pill |
| Permission: Exact Alarms | Status pill |
| Permission: Battery Optimization | Status pill |

#### 8.3h "Local Intelligence" — On-Device Models

- **Downloaded models list:** Model name, size, backend badge (llama.cpp / LiteRT / ONNX), delete button (glass outline, crimson on hover)
- **Available models:** Browsable list with download button, size, performance tier badge
- **Hardware info card:** Glass card showing SoC name, NPU (yes/no), GPU type, available RAM — all JetBrains Mono
- **Auto-select backend:** Glass pill toggle (CPU/GPU/NPU auto-selection)
- **Download progress:** Glass progress bar with speed (MB/s) + ETA, cancelable

### 8.4 Permissions Summary Card

- Separate collapsible section between last capability section and debug button
- Shows ALL Android permissions in unified view:
  - Permission name + status (Green "Granted" / Red "Denied")
  - "Grant" button for each denied permission (glass outline)
  - Denied permissions show which features are blocked (Exo 2 12px, 50% opacity)
- **"Grant All Required"** button at bottom: glass button, amber accent, requests all missing permissions in sequence

### 8.5 Download Debug Info

**Visually distinct card** at the very bottom, separated by 24px extra spacing:

- Glass card with slightly different tint (amber/warm at 4%) to visually separate from settings
- Icon: combined download + bug icon (custom)
- Label: "Download Debug Info" in Exo 2 16px medium
- Subtitle: "Logs, app version, device info, configs" in Exo 2 13px, 50% opacity

**Tap behavior:**
1. Glass loading overlay appears over the card: "Collecting debug data..." with spinner
2. Collects:
   - App version + build number
   - Device model + manufacturer
   - Android OS version + API level
   - SoC / NPU / GPU / RAM info
   - All application logs (last 7 days)
   - Current configuration snapshot (API keys REDACTED — replaced with `***REDACTED***`)
   - Provider health check results
   - Crash logs (if any)
   - Memory store statistics
   - Swarm connection state + last 100 events
   - Active sessions and task states
   - Granted/denied permissions list
3. Packages as ZIP file: `guappa-debug-YYYY-MM-DD-HHmmss.zip`
4. Opens Android share sheet (save to files, send via email/Telegram/etc.)
5. On completion: success toast — "Debug info saved (2.3 MB)" with glass toast style
6. On error: error toast with retry suggestion

**Security:** API keys, tokens, and secrets are always redacted before packaging. No raw credentials ever leave the device in debug output.

### 8.6 Tablet/Automotive Adaptation

- Two-column masonry layout: capability sections distributed across columns
- More settings visible without scrolling
- Permissions summary as persistent sidebar widget
- Debug info card spans full width at bottom

---

## 9. Shared Components Library

### 9.1 GlassCard

Base container for all glass surfaces.

```typescript
interface GlassCardProps {
  tint?: 'cool' | 'warm' | 'neutral' | 'accent';  // Default: 'neutral'
  intensity?: number;        // Blur intensity 0-25, default 12
  borderGlow?: boolean;      // Luminous border, default true
  pressable?: boolean;       // Ripple on press, default false
  expanded?: boolean;        // For collapsible cards
  children: ReactNode;
}
```

### 9.2 GlassToggle

Pill-shaped toggle switch with glass material.

```typescript
interface GlassToggleProps {
  value: boolean;
  onValueChange: (value: boolean) => void;
  activeColor?: string;       // Default: GUAPPA cyan
  disabled?: boolean;
  testID?: string;
}
```

### 9.3 GlassDropdown

Searchable dropdown with glass material.

```typescript
interface GlassDropdownProps {
  items: Array<{ id: string; label: string; icon?: ImageSource; subtitle?: string }>;
  selected: string;
  onSelect: (id: string) => void;
  searchable?: boolean;       // Default: false
  placeholder?: string;
  testID?: string;
}
```

### 9.4 GlassSlider

Slider with glass track and glowing thumb.

```typescript
interface GlassSliderProps {
  value: number;
  min: number;
  max: number;
  step?: number;
  onValueChange: (value: number) => void;
  label?: string;
  valueFormat?: (v: number) => string;  // e.g., (v) => `${v}°`
  testID?: string;
}
```

### 9.5 GlassInput

Text input with glass background.

```typescript
interface GlassInputProps {
  value: string;
  onChangeText: (text: string) => void;
  placeholder?: string;
  secret?: boolean;           // Dots + reveal toggle
  multiline?: boolean;
  testID?: string;
}
```

### 9.6 GlassButton

Action button with glass material.

```typescript
interface GlassButtonProps {
  label: string;
  onPress: () => void;
  variant?: 'filled' | 'outline';   // Default: 'filled'
  accent?: string;                    // Default: GUAPPA cyan
  icon?: string;
  loading?: boolean;
  disabled?: boolean;
  testID?: string;
}
```

### 9.7 PermissionPill

Status pill for Android permissions.

```typescript
interface PermissionPillProps {
  permission: string;         // Android permission string
  granted: boolean;
  onGrant: () => void;        // Triggers permission request
  blockedFeatures?: string[]; // Shown on tap when denied
  testID?: string;
}
```

### 9.8 PlasmaOrb

The Skia plasma orb component.

```typescript
interface PlasmaOrbProps {
  size: number;
  state: 'idle' | 'listening' | 'processing' | 'speaking' | 'error';
  audioLevel?: SharedValue<number>;  // 0-1 reanimated shared value
  interactive?: boolean;      // Tap/long-press handlers
  onTap?: () => void;
  onLongPress?: () => void;
  testID?: string;
}
```

### 9.9 StatusRing

Skia arc progress ring for task status.

```typescript
interface StatusRingProps {
  status: 'running' | 'pending' | 'completed' | 'failed' | 'cancelled';
  progress?: number;          // 0-1 for running tasks
  size?: number;              // Default: 32
  testID?: string;
}
```

---

## 10. Backend Integration Points

### 10.1 TurboModule API (Phase 10 Bridge)

The UI communicates with the Kotlin backend via TurboModules. Key API calls per screen:

**Voice Screen:**
- `startListening()` → activates STT pipeline
- `stopListening()` → returns final transcript
- `getVoiceState(): Flow<VoiceState>` → IDLE/LISTENING/PROCESSING/SPEAKING
- `getAudioLevel(): Flow<Float>` → real-time amplitude 0-1
- `interruptSpeaking()` → stop current TTS

**Chat Screen:**
- `sendMessage(text: String): Flow<StreamToken>` → streaming response
- `getConversationHistory(): List<Message>` → load persisted messages
- `replayTTS(messageId: String)` → replay voice for a message

**Command Center:**
- `getTasks(): Flow<List<Task>>` → real-time task list
- `cancelTask(taskId: String)` → cancel running task
- `getScheduledTasks(): List<ScheduledTask>` → cron jobs
- `setScheduleEnabled(id: String, enabled: Boolean)`
- `getTriggers(): List<Trigger>` → trigger configs
- `setTriggerEnabled(id: String, enabled: Boolean)`
- `configureTrigger(id: String, config: TriggerConfig)`
- `getMemoryFacts(query: String?, category: String?): List<MemoryFact>`
- `deleteMemoryFact(id: String)`
- `exportMemory(): String` → file path
- `importMemory(path: String)`
- `getSessionInfo(): List<SessionInfo>` → active sessions + context budgets

**Swarm Screen:**
- `getSwarmStatus(): Flow<SwarmStatus>` → connection, peers, tier, rep
- `setSwarmEnabled(enabled: Boolean)`
- `setSwarmMode(mode: 'embedded' | 'remote')`
- `setConnectorUrl(url: String)`
- `getSwarmEvents(): Flow<SwarmEvent>` → live feed
- `acceptTask(taskId: String)`
- `declineTask(taskId: String)`
- `sendPeerMessage(peerId: String, message: String)`
- `getSwarmStats(): SwarmStats`
- `getNetworkTopology(): List<PeerNode>` → for topology graph

**Configuration:**
- `setCapabilityProvider(capability: String, providerId: String, modelId: String)`
- `setProviderKey(providerId: String, apiKey: String): Boolean` → validates via health check
- `refreshModels(providerId: String): List<Model>`
- `getModelsForCapability(capability: String): List<Model>`
- `setToolEnabled(toolName: String, enabled: Boolean)`
- `setChannelConfig(channelId: String, config: ChannelConfig)`
- `setVoiceConfig(config: VoiceConfig)`
- `setMemoryConfig(config: MemoryConfig)`
- `setProactiveConfig(config: ProactiveConfig)`
- `downloadModel(modelId: String): Flow<DownloadProgress>`
- `deleteModel(modelId: String)`
- `getHardwareInfo(): HardwareInfo`
- `getPermissionStatus(permission: String): Boolean`
- `requestPermission(permission: String): Boolean`
- `collectDebugInfo(): String` → ZIP file path
- Config change events flow back via `onConfigChanged: Flow<ConfigEvent>`

### 10.2 State Flow Architecture

```
TurboModule (Kotlin) ←→ React Native (TypeScript)
         │                        │
    StateFlow emissions    →    useEffect listeners
    suspend functions     ←    async calls
    Flow<T> streams       →    event subscriptions
```

- All config changes propagate via StateFlow → subscribers → immediate effect
- UI receives confirmation events back through TurboModule event emitters
- No app restart required for any setting change

---

## 11. Responsive Design Contract

### 11.1 Device Detection

Extend existing `LayoutProvider` with refined breakpoints:

| Device Type | Detection | Dock | Layout |
|-------------|-----------|------|--------|
| Phone (portrait) | width < 600dp | Floating pill (bottom) | Single column |
| Phone (landscape) | width ≥ 600dp, height < 600dp | Floating pill (bottom, compact) | Single column, compressed |
| Tablet | width ≥ 600dp, height ≥ 600dp | Side rail (left, 72px) | Multi-column / grid |
| Automotive | `useSidebar` + landscape + large | Side rail (left, 72px) | Multi-column / grid, larger touch targets (min 56px) |

### 11.2 Per-Screen Adaptations

| Screen | Phone | Tablet/Automotive |
|--------|-------|-------------------|
| Voice | Orb 45% width, bottom transcript | Orb 35% min-dimension, larger text |
| Chat | Full-width bubbles (80% max) | 65% max-width bubbles, pill input bar |
| Command Center | Vertical feed, collapsible | 2×2 mission control grid, always expanded |
| Swarm | Status bar + feed | Status bar + feed (60%) + topology graph (40%) |
| Config | Single column sections | Two-column masonry |

### 11.3 Font Scaling

| Element | Phone | Tablet/Automotive |
|---------|-------|-------------------|
| Screen titles | 18px | 22px |
| Body text | 14px | 16px |
| Labels | 12px | 14px |
| Stats/Mono | 11px | 13px |

---

## 12. Accessibility

- All interactive elements have `testID` for Maestro and `accessibilityLabel` for screen readers
- Minimum touch target: 44px (phone), 56px (automotive)
- Color is never the sole indicator — always paired with icons or labels
- Glass surfaces maintain WCAG AA contrast ratio for text on glass (white text on dark glass ≥ 4.5:1)
- Reduced motion preference: disable plasma orb shader animation, disable parallax, use simple fade transitions
- Screen reader: plasma orb announces state ("GUAPPA is listening", "GUAPPA is speaking")

---

## 13. Performance Budgets

| Metric | Target | Measurement |
|--------|--------|-------------|
| App cold start → Voice screen interactive | < 2s | Maestro timing |
| Tab switch animation | < 300ms, 60fps | Reanimated profiler |
| Plasma orb shader | 60fps sustained | Skia profiler, no GPU frame drops |
| Chat bubble entry animation | < 200ms to visible | Reanimated profiler |
| Streaming first token → visible in bubble | < 100ms | Custom timing |
| Config section expand/collapse | < 250ms | Reanimated profiler |
| Debug info collection | < 10s | Maestro timing |
| Memory usage (idle, no local model) | < 180MB | Android Profiler |
| Memory usage (voice active, Skia rendering) | < 300MB | Android Profiler |
| Battery (screen on, voice idle) | < 5%/hour | Battery Historian |

---

## 14. Maestro E2E Test Plan

### 14.1 Test Infrastructure

**Framework:** Maestro v2.2
**Device targets:** API 34 emulator (primary), API 30 emulator (compat), Firebase Test Lab (10 configs)
**Test file location:** `maestro/flows/`
**Naming convention:** `<screen>-<feature>-<scenario>.yaml`

### 14.2 Navigation Tests (6 flows)

```
maestro/flows/navigation/
├── nav-dock-tab-switch.yaml          — Tap each dock tab, verify screen loads
├── nav-dock-active-indicator.yaml    — Verify active tab glow moves correctly
├── nav-dock-scroll-behind.yaml       — Scroll content, verify dock stays floating
├── nav-transition-animation.yaml     — Switch tabs, verify no blank frames
├── nav-tablet-side-rail.yaml         — Tablet layout: verify side rail renders
├── nav-tablet-rail-expand.yaml       — Long-press rail, verify expand to labels
```

**Flow: nav-dock-tab-switch.yaml**
```yaml
appId: com.guappa.app
---
- launchApp
- assertVisible: "voice-screen"
- tapOn:
    id: "dock-tab-chat"
- assertVisible: "chat-screen"
- tapOn:
    id: "dock-tab-command"
- assertVisible: "command-screen"
- tapOn:
    id: "dock-tab-swarm"
- assertVisible: "swarm-screen"
- tapOn:
    id: "dock-tab-config"
- assertVisible: "config-screen"
- tapOn:
    id: "dock-tab-voice"
- assertVisible: "voice-screen"
```

### 14.3 Voice Screen Tests (12 flows)

```
maestro/flows/voice/
├── voice-orb-idle-render.yaml        — Verify orb renders on launch
├── voice-tap-to-listen.yaml          — Tap orb, verify state → LISTENING
├── voice-tap-to-stop.yaml            — Tap orb while listening, verify → IDLE
├── voice-long-press-continuous.yaml  — Long-press orb, verify continuous mode ring
├── voice-listening-animation.yaml    — Speak, verify orb contracts + waveform ring
├── voice-processing-state.yaml       — After speech, verify → PROCESSING (violet)
├── voice-speaking-state.yaml         — Verify orb expands during TTS response
├── voice-transcript-display.yaml     — Speak, verify interim transcript appears
├── voice-swipe-up-preview.yaml       — Swipe up on orb, verify mini-chat cards
├── voice-swipe-down-dismiss.yaml     — Swipe down on preview, verify dismissal
├── voice-mini-chat-navigate.yaml     — Tap mini-chat card, verify Chat tab opens
├── voice-error-state.yaml            — Trigger error, verify orb flash + recovery
```

**Flow: voice-tap-to-listen.yaml**
```yaml
appId: com.guappa.app
---
- launchApp
- assertVisible:
    id: "plasma-orb"
- assertVisible:
    id: "voice-state-label"
    text: ".*"  # Any idle state text
- tapOn:
    id: "plasma-orb"
- assertVisible:
    id: "voice-state-label"
    text: "Listening..."
- waitForAnimationToEnd
- tapOn:
    id: "plasma-orb"
- assertNotVisible:
    id: "voice-state-label"
    text: "Listening..."
```

### 14.4 Chat Screen Tests (16 flows)

```
maestro/flows/chat/
├── chat-empty-state.yaml             — Verify orb + "Start a conversation" visible
├── chat-send-text-message.yaml       — Type + send, verify user bubble appears
├── chat-streaming-response.yaml      — Send message, verify typing indicator → streaming bubble
├── chat-streaming-cursor.yaml        — During stream, verify glow cursor visible
├── chat-bubble-alignment.yaml        — Verify user=right, GUAPPA=left alignment
├── chat-markdown-rendering.yaml      — Send message triggering markdown, verify rendering
├── chat-code-block.yaml              — Verify code block renders in JetBrains Mono
├── chat-voice-badge.yaml             — Send via voice, verify microphone badge on message
├── chat-replay-tts.yaml              — Tap speaker badge on GUAPPA message, verify audio plays
├── chat-scroll-to-bottom.yaml        — Many messages, verify auto-scroll on new message
├── chat-input-multiline.yaml         — Enter multiline text, verify input expands (max 4 lines)
├── chat-input-voice-record.yaml      — Tap mic button, verify waveform appears, release to send
├── chat-attachment-button.yaml       — Tap paperclip, verify picker options appear
├── chat-mini-orb-navigate.yaml       — Tap mini orb in header, verify Voice tab opens
├── chat-voice-sync.yaml              — Voice conversation in Voice tab → switch to Chat → verify messages appear
├── chat-long-conversation.yaml       — Send 50+ messages, verify scroll performance + no crashes
```

**Flow: chat-streaming-response.yaml**
```yaml
appId: com.guappa.app
---
- launchApp
- tapOn:
    id: "dock-tab-chat"
- tapOn:
    id: "chat-input"
- inputText: "What is 2 + 2?"
- tapOn:
    id: "chat-send-button"
- assertVisible:
    id: "chat-typing-indicator"
- waitForAnimationToEnd
- assertVisible:
    id: "chat-message-assistant-0"
- assertNotVisible:
    id: "chat-typing-indicator"
```

### 14.5 Command Center Tests (18 flows)

```
maestro/flows/command/
├── command-header-status.yaml        — Verify status pill shows correct agent state
├── command-tasks-expand.yaml         — Tap Active Tasks, verify section expands
├── command-tasks-progress-ring.yaml  — Running task visible, verify animated ring
├── command-tasks-completed.yaml      — Completed task, verify teal ring + checkmark
├── command-tasks-cancel.yaml         — Swipe left on task, verify cancel dialog
├── command-tasks-detail.yaml         — Tap task, verify detail view (steps, tools, tokens)
├── command-schedule-expand.yaml      — Expand Scheduled, verify timeline + items
├── command-schedule-toggle.yaml      — Toggle schedule on/off, verify state change
├── command-schedule-next-fire.yaml   — Verify next-fire countdown displays correctly
├── command-triggers-expand.yaml      — Expand Triggers, verify grid of trigger cards
├── command-triggers-toggle.yaml      — Toggle trigger, verify state persists
├── command-triggers-configure.yaml   — Tap trigger card, verify config bottom sheet
├── command-memory-expand.yaml        — Expand Memory, verify facts list
├── command-memory-search.yaml        — Type in search, verify filtered results
├── command-memory-filter.yaml        — Tap category pill, verify filtered by category
├── command-memory-delete.yaml        — Swipe left on fact, verify delete confirmation
├── command-memory-export.yaml        — Tap Export, verify file created
├── command-sessions-expand.yaml      — Expand Sessions, verify context budget chart
```

**Flow: command-triggers-toggle.yaml**
```yaml
appId: com.guappa.app
---
- launchApp
- tapOn:
    id: "dock-tab-command"
- tapOn:
    id: "section-triggers"
- assertVisible:
    id: "trigger-incoming-sms"
- tapOn:
    id: "trigger-toggle-incoming-sms"
# Verify toggle state changed
- assertVisible:
    id: "trigger-toggle-incoming-sms"
    # Toggle should now be off (was default on)
```

### 14.6 Swarm Screen Tests (14 flows)

```
maestro/flows/swarm/
├── swarm-disconnected-empty.yaml     — Verify empty state with network wireframe
├── swarm-connect-toggle.yaml         — Toggle connection, verify status change animation
├── swarm-status-bar-elements.yaml    — Verify peer count, tier badge, reputation gauge
├── swarm-mode-switch.yaml            — Switch embedded/remote, verify config appears
├── swarm-remote-url-input.yaml       — Enter connector URL, verify latency ping
├── swarm-feed-incoming-task.yaml     — Receive task, verify card with Accept/Decline
├── swarm-feed-accept-task.yaml       — Tap Accept on task, verify state change
├── swarm-feed-decline-task.yaml      — Tap Decline on task, verify card dismissed
├── swarm-feed-peer-message.yaml      — Receive message, verify card with sender DID
├── swarm-feed-reputation.yaml        — Reputation event, verify gold card + score update
├── swarm-feed-filter.yaml            — Tap filter pills, verify feed filtered
├── swarm-feed-old-collapse.yaml      — Events >24h, verify collapsed to single line
├── swarm-stats-modal.yaml            — Tap Stats, verify modal with charts
├── swarm-tablet-topology.yaml        — Tablet: verify topology graph renders
```

**Flow: swarm-connect-toggle.yaml**
```yaml
appId: com.guappa.app
---
- launchApp
- tapOn:
    id: "dock-tab-swarm"
- assertVisible:
    id: "swarm-status-disconnected"
- tapOn:
    id: "swarm-connection-toggle"
- waitForAnimationToEnd
- assertVisible:
    id: "swarm-status-connected"
- assertVisible:
    id: "swarm-peer-count"
```

### 14.7 Configuration Screen Tests (22 flows)

```
maestro/flows/config/
├── config-search-filter.yaml         — Type in search, verify sections filter
├── config-section-expand.yaml        — Tap each section header, verify expand/collapse
│
│ # How GUAPPA Thinks
├── config-text-provider-select.yaml  — Select text provider from dropdown
├── config-text-model-select.yaml     — Select model, verify context window + price shown
├── config-text-fallback.yaml         — Set fallback provider/model
├── config-temperature-slider.yaml    — Adjust temperature, verify value updates
├── config-budget-set.yaml            — Set daily budget, verify enforcement
│
│ # How GUAPPA Sees
├── config-vision-provider.yaml       — Select vision provider + model
├── config-image-gen-provider.yaml    — Select image generation provider + model
├── config-camera-permission.yaml     — Tap camera permission pill, verify grant flow
│
│ # How GUAPPA Speaks & Listens
├── config-stt-engine-select.yaml     — Select STT engine from ranked list
├── config-stt-model-download.yaml    — Download on-device STT model, verify progress
├── config-tts-engine-select.yaml     — Select TTS engine
├── config-tts-voice-preview.yaml     — Tap preview button, verify audio plays
├── config-wake-word-toggle.yaml      — Toggle wake word, verify setting persists
│
│ # How GUAPPA Connects
├── config-channel-toggle.yaml        — Toggle Telegram on/off, verify status dot
├── config-channel-config.yaml        — Tap channel tile, verify config expands
├── config-channel-health.yaml        — Tap health check, verify latency result
│
│ # What GUAPPA Can Do
├── config-tool-toggle.yaml           — Toggle tool on/off, verify persists
├── config-tool-categories.yaml       — Expand each tool category, verify tool list
│
│ # Permissions & Debug
├── config-permissions-summary.yaml   — Expand Permissions Summary, verify all listed
├── config-debug-download.yaml        — Tap Download Debug Info, verify ZIP + share sheet
```

**Flow: config-debug-download.yaml**
```yaml
appId: com.guappa.app
---
- launchApp
- tapOn:
    id: "dock-tab-config"
- scrollUntilVisible:
    element:
      id: "debug-download-button"
    direction: DOWN
- tapOn:
    id: "debug-download-button"
- assertVisible:
    id: "debug-collecting-overlay"
    text: "Collecting debug data..."
- waitForAnimationToEnd:
    timeout: 15000
# Verify success toast appears
- assertVisible:
    id: "toast-success"
```

**Flow: config-text-provider-select.yaml**
```yaml
appId: com.guappa.app
---
- launchApp
- tapOn:
    id: "dock-tab-config"
- tapOn:
    id: "section-how-guappa-thinks"
- assertVisible:
    id: "text-provider-dropdown"
- tapOn:
    id: "text-provider-dropdown"
- assertVisible:
    id: "dropdown-item-anthropic"
- tapOn:
    id: "dropdown-item-anthropic"
- assertVisible:
    id: "text-provider-dropdown"
    text: "Anthropic"
# Verify models refresh
- assertVisible:
    id: "text-model-dropdown"
```

### 14.8 Backend Integration Tests (12 flows)

```
maestro/flows/backend/
├── backend-agent-startup.yaml        — Launch app, verify agent service starts
├── backend-provider-health.yaml      — Set provider key, verify health check passes
├── backend-tool-execution.yaml       — Send "set alarm for 7am", verify tool call + result
├── backend-streaming-tokens.yaml     — Send message, verify tokens stream (not batch)
├── backend-context-compaction.yaml   — Fill context to 80%, verify summarization triggers
├── backend-session-persistence.yaml  — Send messages, kill app, relaunch, verify history
├── backend-config-hot-reload.yaml    — Change provider in config, verify next request uses new provider
├── backend-channel-reconnect.yaml    — Enable Telegram, verify connection, disable, verify disconnect
├── backend-proactive-push.yaml       — Trigger proactive event, verify notification appears
├── backend-memory-extraction.yaml    — Tell agent a fact, verify it appears in Memory section
├── backend-swarm-registration.yaml   — Enable swarm, verify registration + challenge solved
├── backend-cost-tracking.yaml        — Send multiple messages, verify cost accumulates in config
```

**Flow: backend-tool-execution.yaml**
```yaml
appId: com.guappa.app
---
- launchApp
- tapOn:
    id: "dock-tab-chat"
- tapOn:
    id: "chat-input"
- inputText: "Set an alarm for 7:00 AM tomorrow"
- tapOn:
    id: "chat-send-button"
- waitForAnimationToEnd:
    timeout: 30000
# Verify GUAPPA responded with tool execution result
- assertVisible:
    id: "chat-message-assistant-0"
- assertTrue:
    # Response should mention alarm being set
    visible:
      id: "chat-message-assistant-0"
      text: ".*alarm.*7.*AM.*"
```

### 14.9 Resilience Tests (10 flows)

```
maestro/flows/resilience/
├── resilience-app-restart.yaml       — Kill app, relaunch, verify state preserved
├── resilience-network-loss.yaml      — Disable network, send message, verify graceful error
├── resilience-network-restore.yaml   — Disable then enable network, verify recovery
├── resilience-provider-timeout.yaml  — Set invalid endpoint, send message, verify fallback
├── resilience-rapid-tab-switch.yaml  — Switch tabs 20x rapidly, verify no crash
├── resilience-rapid-messages.yaml    — Send 10 messages in 2 seconds, verify all processed
├── resilience-memory-pressure.yaml   — Fill memory section, verify no OOM
├── resilience-permission-revoke.yaml — Revoke mic permission, tap voice orb, verify graceful error
├── resilience-battery-saver.yaml     — Enable battery saver, verify reduced animations
├── resilience-orientation-change.yaml — Rotate device, verify layout adapts without crash
```

**Flow: resilience-app-restart.yaml**
```yaml
appId: com.guappa.app
---
- launchApp
- tapOn:
    id: "dock-tab-chat"
- tapOn:
    id: "chat-input"
- inputText: "Hello GUAPPA"
- tapOn:
    id: "chat-send-button"
- waitForAnimationToEnd:
    timeout: 15000
- assertVisible:
    id: "chat-message-user-0"
# Kill and relaunch
- stopApp
- launchApp
- tapOn:
    id: "dock-tab-chat"
# Verify previous messages are still there
- assertVisible:
    id: "chat-message-user-0"
    text: "Hello GUAPPA"
```

### 14.10 Performance Tests (6 flows)

```
maestro/flows/performance/
├── perf-cold-start.yaml              — Measure launch to Voice screen interactive (< 2s)
├── perf-tab-switch-timing.yaml       — Measure tab switch duration (< 300ms)
├── perf-chat-scroll.yaml             — 100 messages, scroll top-to-bottom, verify 60fps
├── perf-voice-orb-fps.yaml           — Voice screen idle 30s, verify no dropped frames
├── perf-config-section-expand.yaml   — Expand/collapse rapidly, verify smooth (< 250ms)
├── perf-debug-collection.yaml        — Download debug info, verify < 10s
```

### 14.11 Tablet/Automotive Tests (6 flows)

```
maestro/flows/tablet/
├── tablet-side-rail-render.yaml      — Wide screen, verify side rail visible (no dock)
├── tablet-command-grid.yaml          — Verify 2x2 mission control grid layout
├── tablet-swarm-topology.yaml        — Verify topology graph in right panel
├── tablet-config-two-column.yaml     — Verify two-column masonry layout
├── tablet-chat-max-width.yaml        — Verify bubbles max 65% width
├── tablet-voice-orb-scale.yaml       — Verify orb scales to 35% of min dimension
```

### 14.12 Test Summary

| Category | Flow Count | Coverage Area |
|----------|------------|---------------|
| Navigation | 6 | Dock, side rail, transitions |
| Voice | 12 | Orb states, gestures, transcript, preview |
| Chat | 16 | Bubbles, streaming, input, voice sync, markdown |
| Command Center | 18 | Tasks, schedules, triggers, memory, sessions |
| Swarm | 14 | Connection, feed, filters, actions, topology |
| Configuration | 22 | All capability sections, permissions, debug download |
| Backend | 12 | Agent core, providers, tools, streaming, config hot-reload |
| Resilience | 10 | Restart, network, crashes, permissions, battery |
| Performance | 6 | Cold start, fps, timing budgets |
| Tablet/Automotive | 6 | Adaptive layouts |
| **Total** | **122** | **Full product coverage** |

### 14.13 CI Integration

```yaml
# .github/workflows/maestro-e2e.yml
name: Maestro E2E
on:
  pull_request:
    paths:
      - 'mobile-app/**'
  push:
    branches: [main]

jobs:
  maestro:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - name: Install dependencies
        run: cd mobile-app && npm ci
      - name: Build debug APK
        run: cd mobile-app/android && ./gradlew assembleDebug
      - name: Start emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          script: |
            curl -Ls "https://get.maestro.mobile.dev" | bash
            export PATH="$PATH":"$HOME/.maestro/bin"
            maestro test maestro/flows/ --format junit --output maestro-results.xml
      - name: Upload results
        uses: actions/upload-artifact@v4
        with:
          name: maestro-results
          path: maestro-results.xml
```

---

## 15. File Structure

New and modified files for Phase 12:

```
mobile-app/
├── src/
│   ├── components/
│   │   ├── glass/                    # NEW — Glass design system
│   │   │   ├── GlassCard.tsx
│   │   │   ├── GlassToggle.tsx
│   │   │   ├── GlassDropdown.tsx
│   │   │   ├── GlassSlider.tsx
│   │   │   ├── GlassInput.tsx
│   │   │   ├── GlassButton.tsx
│   │   │   └── PermissionPill.tsx
│   │   ├── plasma/                   # NEW — Skia shader components
│   │   │   ├── PlasmaOrb.tsx
│   │   │   ├── PlasmaOrbMini.tsx
│   │   │   ├── VoiceWaveformRing.tsx
│   │   │   ├── StatusRing.tsx
│   │   │   ├── TopologyGraph.tsx
│   │   │   └── shaders/
│   │   │       ├── plasma.glsl
│   │   │       ├── glass-noise.glsl
│   │   │       └── ripple.glsl
│   │   ├── dock/                     # NEW — Navigation
│   │   │   ├── FloatingDock.tsx
│   │   │   ├── SideRail.tsx
│   │   │   └── DockGlob.tsx          # Animated glow blob
│   │   ├── chat/                     # NEW — Chat components
│   │   │   ├── MessageBubble.tsx
│   │   │   ├── StreamingBubble.tsx
│   │   │   ├── TypingIndicator.tsx
│   │   │   ├── ChatInputBar.tsx
│   │   │   └── VoiceBadge.tsx
│   │   └── command/                  # NEW — Command Center components
│   │       ├── CollapsibleSection.tsx
│   │       ├── TaskCard.tsx
│   │       ├── ScheduleTimeline.tsx
│   │       ├── TriggerGrid.tsx
│   │       ├── MemoryList.tsx
│   │       ├── SessionInfo.tsx
│   │       └── ContextBudgetChart.tsx
│   ├── screens/
│   │   └── tabs/                     # MODIFIED — Replace all screens
│   │       ├── VoiceScreen.tsx       # NEW (replaces old structure)
│   │       ├── ChatScreen.tsx        # REWRITE
│   │       ├── CommandScreen.tsx     # NEW (replaces Activity + ScheduledTasks + Memory)
│   │       ├── SwarmScreen.tsx       # NEW
│   │       └── ConfigScreen.tsx      # REWRITE (replaces Settings + Integrations + Device + Security)
│   ├── navigation/
│   │   └── RootNavigator.tsx         # REWRITE — Dock-based navigation
│   ├── theme/                        # NEW — Design system tokens
│   │   ├── colors.ts
│   │   ├── typography.ts
│   │   ├── spacing.ts
│   │   └── animations.ts
│   ├── hooks/                        # NEW — Shared hooks
│   │   ├── useGlassMaterial.ts
│   │   ├── usePlasmaState.ts
│   │   ├── useSwarmEvents.ts
│   │   └── useDebugInfo.ts
│   └── state/
│       ├── layout.tsx                # MODIFIED — Enhanced breakpoints
│       └── toast.tsx                 # MODIFIED — Glass toast styling
├── maestro/
│   └── flows/                        # NEW — 122 E2E test flows
│       ├── navigation/               # 6 flows
│       ├── voice/                    # 12 flows
│       ├── chat/                     # 16 flows
│       ├── command/                  # 18 flows
│       ├── swarm/                    # 14 flows
│       ├── config/                   # 22 flows
│       ├── backend/                  # 12 flows
│       ├── resilience/               # 10 flows
│       ├── performance/              # 6 flows
│       └── tablet/                   # 6 flows
└── package.json                      # MODIFIED — New dependencies
```

---

## 16. New Dependencies

```json
{
  "dependencies": {
    "@shopify/react-native-skia": "^1.5.0",
    "react-native-glass-effect-view": "^0.3.0",
    "expo-sensors": "~14.0.0"
  }
}
```

**Dependency justification:**
- `@shopify/react-native-skia` — Required for plasma orb GLSL shaders, topology graph, arc charts, noise overlays. No alternative provides GPU-accelerated custom shaders on Android RN.
- `react-native-glass-effect-view` — Lightweight utility for simple glass surfaces where full Skia is overkill. Android fallback is acceptable for cards/buttons.
- `expo-sensors` — Gyroscope data for parallax effects on glass layers. Already in Expo ecosystem, minimal footprint.

---

## 17. Migration Plan

### 17.1 Screen Consolidation

| Old Screen | New Screen | Migration |
|------------|------------|-----------|
| ChatScreen | ChatScreen | Rewrite with glass bubbles + streaming |
| SettingsScreen | ConfigScreen | Rewrite with capability-first layout |
| ActivityScreen | CommandScreen | Absorb into Command Center |
| ScheduledTasksScreen | CommandScreen | Absorb into Command Center (Scheduled section) |
| MemoryScreen | CommandScreen | Absorb into Command Center (Memory section) |
| IntegrationsScreen | ConfigScreen | Absorb into "How GUAPPA Connects" section |
| DeviceScreen | ConfigScreen | Absorb into "What GUAPPA Can Do" section |
| SecurityScreen | ConfigScreen | Absorb permissions + policies into relevant capability sections |
| _(new)_ | VoiceScreen | New screen |
| _(new)_ | SwarmScreen | New screen |

### 17.2 Navigation Consolidation

**Old:** 5 tabs (Chat, Activity, Settings, Integrations, Device) + 3 modal stacks (Security, Tasks, Memory)

**New:** 5 tabs (Voice, Chat, Command, Swarm, Config) + 0 modal stacks (everything inline)

### 17.3 Rollback Strategy

- All old screens preserved in `src/screens/tabs/_deprecated/` during development
- Feature flag `USE_NEW_UI=true` in DataStore to toggle between old and new navigation
- Rollback: set feature flag to false, old navigation + screens render

---

## 18. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Skia shader performance on low-end devices | Plasma orb drops below 60fps | Quality tiers: high (full shader), medium (simplified noise), low (static gradient + scale animation) |
| Glass blur performance on older Android | UI jank | Fallback to semi-transparent solid fills on API <30 |
| Large bundle size from Skia + fonts | Slow download, storage | Skia is ~4MB. Fonts loaded async, only used weights included. Total delta: ~6MB acceptable. |
| 122 Maestro tests slow CI | PR merge velocity drops | Parallel execution on 4 emulators. Critical path (30 tests) runs on every PR, full suite nightly. |
| Streaming bubble resize jank | Chat feels broken | Pre-allocate minimum bubble height, use `LayoutAnimation` with spring, debounce resize to 16ms frames |
| Wake word battery drain | User complaints | Default OFF, clear battery impact warning in settings |

---

## 19. Definition of Done

Phase 12 is complete when:

1. All 5 screens render correctly on phone and tablet/automotive layouts
2. Floating dock (phone) and side rail (tablet) navigate between all screens
3. Plasma orb renders at 60fps with all 5 state transitions
4. Chat streaming displays tokens progressively with glow cursor
5. Command Center shows live task/schedule/trigger/memory data from backend
6. Swarm screen connects/disconnects and displays live event feed
7. Config screen allows per-capability provider/model selection with hot-reload
8. All Android permissions grantable from Config screen
9. Download Debug Info produces valid ZIP and opens share sheet
10. Glass design system components (GlassCard, GlassToggle, GlassDropdown, GlassSlider, GlassInput, GlassButton, PermissionPill) are reusable and documented
11. All 122 Maestro E2E flows pass on API 34 emulator
12. Performance budgets met (cold start <2s, tab switch <300ms, orb 60fps)
13. No regressions in existing backend functionality
