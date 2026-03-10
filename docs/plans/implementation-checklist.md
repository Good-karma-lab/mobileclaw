# Guappa Implementation Checklist — Plan vs Reality

**Date**: 2026-03-10
**Purpose**: Map every planned feature to actual implementation status, identify gaps, define E2E test coverage, and track icon/branding and palette conformance.

Legend:
- ✅ Implemented — code exists and appears functional
- ⚠️ Partial — code exists but incomplete or missing key features
- ❌ Missing — no implementation found
- 🔧 Stub — file exists but minimal/skeleton implementation
- 🧪 E2E test exists (Maestro or UI Automator)
- 🚫 No E2E test

---

## 🎨 Storm Palette — "Dramatic Night Sky"

Derived from reference: dark cyan/blue dramatic storm clouds over night sky.

### Palette Definition

| Token | Hex | Usage |
|-------|-----|-------|
| **abyss** | `#020408` | Deepest background, status bar |
| **stormBlack** | `#060D14` | Primary background |
| **deepNight** | `#0B1A26` | Secondary background, screen fills |
| **midnightStorm** | `#102638` | Card backgrounds, glass fill |
| **darkCyanStorm** | `#16334A` | Elevated surfaces, active areas |
| **stormCyan** | `#1C4A5E` | **Primary accent** — buttons, active indicators, links |
| **cyanGlow** | `#247080` | Bright accent — selected state, glow |
| **lightningCyan** | `#2E90A5` | Highlight, sparingly — important CTAs |
| **cloudShadow** | `#0E2030` | Glass fill, overlays |
| **rainBlue** | `#1A3040` | Borders, dividers |
| **paleStorm** | `#8AA0B0` | **Primary text** |
| **mist** | `#5A7585` | Secondary text |
| **distantStorm** | `#3A5060` | Tertiary text, disabled |
| **thunderGray** | `#1A2A35` | Inactive elements, dock bg |
| **stormWarning** | `#6A2030` | Error / danger |
| **moss** | `#2A5A4A` | Success |
| **amberStorm** | `#6A5020` | Warning |
| **deepViolet** | `#3A2A5A` | Violet accent (swarm, identity) |

### Current `colors.ts` vs Storm Palette — Delta

| Current Token | Current Value | Storm Replacement | Delta |
|---------------|---------------|-------------------|-------|
| `base.spaceBlack` | `#030608` | `#020408` (abyss) | Minor — slightly cooler |
| `base.midnightBlue` | `#06101A` | `#060D14` (stormBlack) | Minor — less blue, more neutral |
| `glass.fill` | `rgba(255,255,255,0.05)` | `rgba(14,32,48,0.65)` (cloudShadow-based) | **Major** — switch from white-alpha to storm blue-alpha |
| `glass.fillActive` | `rgba(255,255,255,0.08)` | `rgba(22,51,74,0.70)` (darkCyanStorm-based) | **Major** |
| `glass.border` | `rgba(255,255,255,0.08)` | `rgba(26,48,64,0.50)` (rainBlue-based) | **Major** — cyan-tinted borders |
| `glass.borderSubtle` | `rgba(255,255,255,0.05)` | `rgba(26,48,64,0.30)` | **Major** |
| `accent.cyan` | `#1A5C6A` | `#1C4A5E` (stormCyan) | Moderate — darker, stormier |
| `accent.cyanGlow` | `#2A8090` | `#247080` (cyanGlow) | Moderate — slightly darker |
| `accent.cyanBright` | `#4AA0B0` | `#2E90A5` (lightningCyan) | Moderate — darker, more saturated |
| `accent.violet` | `#5A3A8A` | `#3A2A5A` (deepViolet) | Moderate — darker |
| `accent.rose` | `#8A2040` | `#6A2030` (stormWarning) | Moderate — darker |
| `accent.amber` | `#8A6A20` | `#6A5020` (amberStorm) | Moderate — darker |
| `semantic.success` | `#1A7A6A` | `#2A5A4A` (moss) | Moderate — mossier |
| `semantic.error` | `#8A2020` | `#6A2030` (stormWarning) | Moderate — bluer red |
| `semantic.warning` | `#7A6020` | `#6A5020` (amberStorm) | Minor |
| `semantic.info` | `#2A5080` | `#1C4A5E` (stormCyan) | Minor |
| `text.primary` | `rgba(180,200,210,0.85)` | `rgba(138,160,176,0.90)` (paleStorm) | **Major** — cooler, less white |
| `text.secondary` | `rgba(140,165,180,0.55)` | `rgba(90,117,133,0.70)` (mist) | Major — darker, more opaque |
| `text.tertiary` | `rgba(100,130,150,0.35)` | `rgba(58,80,96,0.50)` (distantStorm) | Major — darker |

### Hardcoded Colors in Components — Must Update

| File | Current | Storm Replacement |
|------|---------|-------------------|
| `FloatingDock.tsx` line 91 | `color="#D0E8F5"` (active icon) | `#8AA0B0` (paleStorm) — active icons should be muted, not bright white |
| `FloatingDock.tsx` line 91 | `color="rgba(160,190,210,0.75)"` (idle icon) | `rgba(90,117,133,0.60)` (mist) |
| `FloatingDock.tsx` line 159 | `backgroundColor: "rgba(10,22,35,0.92)"` (dock bg) | `rgba(6,13,20,0.94)` (stormBlack) |
| `FloatingDock.tsx` line 162 | `borderColor: "rgba(80,130,160,0.2)"` | `rgba(26,48,64,0.35)` (rainBlue) |
| `FloatingDock.tsx` line 182 | `backgroundColor: "rgba(50,100,130,0.3)"` (active glow) | `rgba(28,74,94,0.25)` (stormCyan glow) |
| `RootNavigator.tsx` line 130 | `backgroundColor: "#020206"` | `#020408` (abyss) |
| `SideRail.tsx` line 97 | `backgroundColor: "rgba(10,10,30,0.65)"` | `rgba(6,13,20,0.80)` (stormBlack) |
| `SideRail.tsx` line 100 | `borderRightColor: "rgba(255,255,255,0.12)"` | `rgba(26,48,64,0.40)` (rainBlue) |

### Implementation Task

| # | Task | File(s) | Priority |
|---|------|---------|----------|
| P.1 | Update `colors.ts` with storm palette tokens | `src/theme/colors.ts` | P0 |
| P.2 | Replace all hardcoded hex/rgba in FloatingDock | `src/components/dock/FloatingDock.tsx` | P0 |
| P.3 | Replace all hardcoded hex/rgba in SideRail | `src/components/dock/SideRail.tsx` | P0 |
| P.4 | Replace hardcoded color in RootNavigator | `src/navigation/RootNavigator.tsx` | P0 |
| P.5 | Audit & update all glass components | `src/components/glass/*.tsx` | P1 |
| P.6 | Audit & update all screen files | `src/screens/tabs/*.tsx` | P1 |
| P.7 | Update SwarmCanvas / EmotionPalette | `src/swarm/emotion/EmotionPalette.ts` | P1 |
| P.8 | Update onboarding components | `src/components/onboarding/*.tsx` | P1 |

---

## 🖼️ In-App Icons Audit — BROKEN / INVISIBLE

### Root Cause: Dual Theme System Conflict

The app has **two competing theme systems** that are out of sync. This likely causes icons to render with wrong/invisible colors:

| Theme System | File | Used By | Icon Colors |
|-------------|------|---------|-------------|
| **Config theme** (`ui/theme.ts`) | `src/config.ts` → `ui/theme.ts` | DeviceScreen, MemoryScreen, SettingsScreen, IntegrationsScreen, glass components | `theme.colors.base.textMuted` = `#A3A3B2`, `theme.colors.base.accent` = `#8B5CF6`, `theme.colors.overlay.dockIconIdle` = `rgba(245,240,230,0.65)` |
| **Neural swarm theme** (`src/theme/colors.ts`) | `src/theme/colors.ts` | FloatingDock, SideRail, ChatScreen, ChatInputBar, CommandScreen, ConfigScreen, SwarmScreen | `colors.accent.cyan` = `#1A5C6A`, `colors.text.secondary` = `rgba(140,165,180,0.55)`, `colors.text.tertiary` = `rgba(100,130,150,0.35)` |

**The config theme defaults** (`src/config.ts`):
```
primary:    #D4F49C   (lime green)
secondary:  #C69CF4   (lavender)
accent:     #8B5CF6   (violet)
background: #05050A   (near-black)
text:       #F5F0E6   (warm cream)
border:     #FFFFFF   (pure white)
textMuted:  #A3A3B2   (gray)
```

**The neural swarm theme** (`src/theme/colors.ts`):
```
accent.cyan:      #1A5C6A   (dark teal)
accent.cyanGlow:  #2A8090   (brighter teal)
text.primary:     rgba(180,200,210,0.85)  (cool blue-gray)
text.secondary:   rgba(140,165,180,0.55)  (muted steel)
text.tertiary:    rgba(100,130,150,0.35)  (very dim)
```

**Conflict impact on icons**:
- Components using `colors.text.tertiary` = `rgba(100,130,150,0.35)` → **35% opacity** — nearly invisible on dark backgrounds
- Components using `colors.accent.cyan` = `#1A5C6A` → very dark teal, borderline visible on `#030608` background
- `FloatingDock` idle icons use `rgba(160,190,210,0.75)` — should be visible
- `FloatingDock` active icons use `#D0E8F5` — bright, should be visible
- Some glass components use `theme.colors.overlay.dockIconIdle` = `rgba(245,240,230,0.65)` — warm cream

### Complete In-App Icon Inventory

**22 files** use Ionicons. **35 unique static icon names** + **12 dynamic icon references** (from config maps).

#### Dock Navigation Icons (MOST VISIBLE)

| Location | Icon (active) | Icon (inactive) | Active Color | Inactive Color | Visibility Issue |
|----------|--------------|-----------------|-------------|----------------|-----------------|
| FloatingDock → Voice | `mic` | `mic-outline` | `#D0E8F5` | `rgba(160,190,210,0.75)` | Should be OK ✅ |
| FloatingDock → Chat | `chatbubble` | `chatbubble-outline` | `#D0E8F5` | `rgba(160,190,210,0.75)` | Should be OK ✅ |
| FloatingDock → Command | `flash` | `flash-outline` | `#D0E8F5` | `rgba(160,190,210,0.75)` | Should be OK ✅ |
| FloatingDock → Swarm | `globe` | `globe-outline` | `#D0E8F5` | `rgba(160,190,210,0.75)` | Should be OK ✅ |
| FloatingDock → Config | `options` | `options-outline` | `#D0E8F5` | `rgba(160,190,210,0.75)` | Should be OK ✅ |
| SideRail → all tabs | Same as above | Same | `colors.accent.cyan` = `#1A5C6A` | `colors.text.secondary` = `rgba(140,165,180,0.55)` | ⚠️ Active = very dark teal on dark bg, could be hard to see |

#### Chat Input Bar Icons

| Location | Icon | Color | Size | Visibility Issue |
|----------|------|-------|------|-----------------|
| `ChatInputBar.tsx` → clear attachment | `close-circle` | `rgba(255,100,100,0.9)` | 20 | OK ✅ |
| `ChatInputBar.tsx` → image picker | `image-outline` | `colors.accent.cyan` = `#1A5C6A` | 22 | ⚠️ Dark teal on dark bg |
| `ChatInputBar.tsx` → camera | `camera-outline` | `colors.accent.cyan` = `#1A5C6A` | 22 | ⚠️ Dark teal on dark bg |
| `ChatInputBar.tsx` → document picker | `document-outline` | `colors.accent.cyan` = `#1A5C6A` | 20 | ⚠️ Dark teal on dark bg |
| `ChatInputBar.tsx` → send button | `arrow-up` | `colors.base.spaceBlack` = `#030608` | 20 | ⚠️ Near-black icon — only visible if button has contrasting bg |
| `ChatInputBar.tsx` → mic button | `mic-outline` | `colors.accent.cyan` = `#1A5C6A` | 22 | ⚠️ Dark teal on dark bg |

#### Command Screen Icons

| Location | Icon | Color | Size | Visibility Issue |
|----------|------|-------|------|-----------------|
| Task status `running` | `play-circle` | `colors.accent.cyan` `#1A5C6A` | 12 | ⚠️ 12px dark teal — very hard to see |
| Task status `queued` | `time-outline` | `colors.semantic.warning` `#7A6020` | 12 | ⚠️ 12px dark amber |
| Task status `completed` | `checkmark-circle` | `colors.semantic.success` `#1A7A6A` | 12 | ⚠️ 12px dark teal |
| Task status `failed` | `close-circle` | `colors.semantic.error` `#8A2020` | 12 | ⚠️ 12px dark red |
| Trigger `event` | `flash-outline` | `colors.accent.cyan` | 12 | ⚠️ Same |
| Trigger `condition` | `git-branch-outline` | `colors.semantic.warning` | 12 | ⚠️ Same |
| Trigger `schedule` | `calendar-outline` | `colors.accent.violet` `#5A3A8A` | 12 | ⚠️ 12px dark violet |
| Trigger `webhook` | `globe-outline` | `colors.accent.rose` `#8A2040` | 12 | ⚠️ 12px dark rose |
| Empty state (tasks) | `list-outline` | `colors.text.tertiary` = 35% opacity | 36 | ❌ **Nearly invisible** |
| Empty state (schedules) | `calendar-outline` | `colors.text.tertiary` | 36 | ❌ **Nearly invisible** |
| Empty state (triggers) | `flash-outline` | `colors.text.tertiary` | 36 | ❌ **Nearly invisible** |
| Section headers | Various `-outline` icons | `colors.accent.cyan` | 14 | ⚠️ Small + dark |
| Memory stats | `document-text-outline`, `library-outline`, `film-outline`, `trash-outline` | Various accent colors | 14 | ⚠️ Small + dark |
| Session `time-outline` | `time-outline` | `colors.text.tertiary` | — | ❌ **Nearly invisible** |
| Session `chatbubble-outline` | `chatbubble-outline` | `colors.text.tertiary` | — | ❌ **Nearly invisible** |
| `hardware-chip-outline` | `hardware-chip-outline` | `colors.text.tertiary` | — | ❌ **Nearly invisible** |

#### Config Screen Icons (CollapsibleSection headers)

| Section Title | Icon | Color | Visibility Issue |
|---------------|------|-------|-----------------|
| "How GUAPPA Thinks" | `sparkles-outline` | `colors.accent.cyan` | ⚠️ Dark teal |
| "How GUAPPA Sees" | `eye-outline` | `colors.accent.cyan` | ⚠️ Dark teal |
| "How GUAPPA Speaks & Listens" | `mic-outline` | `colors.accent.cyan` | ⚠️ Dark teal |
| "How GUAPPA Connects" | `globe-outline` | `colors.accent.cyan` | ⚠️ Dark teal |
| "What GUAPPA Can Do" | `build-outline` | `colors.accent.cyan` | ⚠️ Dark teal |
| "What GUAPPA Remembers" | `server-outline` | `colors.accent.cyan` | ⚠️ Dark teal |
| "How GUAPPA Acts on Her Own" | `flash-outline` | `colors.accent.cyan` | ⚠️ Dark teal |
| "Local Intelligence" | `hardware-chip-outline` | `colors.accent.cyan` | ⚠️ Dark teal |
| "Permissions" | `shield-checkmark-outline` | `colors.accent.cyan` | ⚠️ Dark teal |

#### Swarm Screen Icons

| Location | Icon | Color | Size | Visibility Issue |
|----------|------|-------|------|-----------------|
| Empty state globe | `globe-outline` | `colors.accent.violet` `#5A3A8A` | 56 | ⚠️ Dark violet on dark bg |
| Tab badges | Various (`chatbubble`, `briefcase`, `people`, `star`) | Per-tab accent colors | 14 | ⚠️ Small + dark |
| Peer tier icons | `leaf-outline`, `shield-checkmark-outline`, `ribbon-outline`, `diamond-outline` | Per-tier colors | 14 | ⚠️ Small |
| Connection status | `checkmark-outline`, `close-outline` | accent colors | — | ⚠️ |
| Identity | `globe-outline`, `finger-print-outline`, `key-outline` | `colors.accent.cyan` | — | ⚠️ Dark teal |
| Peer details | `star`, `link-outline`, `people-outline`, `time-outline`, `radio-outline` | accent colors | — | ⚠️ |

#### Device Screen Icons (uses config theme)

| Location | Icon | Color | Visibility Issue |
|----------|------|-------|-----------------|
| Categories (active) | `accessibility`, `camera`, `call`, `radio`, `person` | `theme.colors.base.secondary` = `#C69CF4` | OK ✅ (lavender is visible) |
| Categories (inactive) | Same | `theme.colors.overlay.dockIconIdle` = `rgba(245,240,230,0.65)` | OK ✅ |
| Warning | `warning` | `theme.colors.base.primary` = `#D4F49C` | OK ✅ (lime green) |
| Close button | `close` | `theme.colors.base.textMuted` = `#A3A3B2` | OK ✅ |

#### Memory Screen Icons (uses config theme)

| Location | Icon | Color | Size | Visibility Issue |
|----------|------|-------|------|-----------------|
| Delete action | `trash-outline` | `theme.colors.base.accent` = `#8B5CF6` | 18 | OK ✅ |
| Empty state | `cube-outline` | `theme.colors.base.textMuted` = `#A3A3B2` | 48 | OK ✅ |

#### Settings/Integrations Screen Icons (uses config theme)

| Location | Icon | Color | Visibility Issue |
|----------|------|-------|-----------------|
| Dropdowns | `chevron-down` | `theme.colors.base.textMuted` = `#A3A3B2` | OK ✅ |
| Integration enabled | `checkmark-circle` | `theme.colors.base.secondary` = `#C69CF4` | OK ✅ |
| Integration toggle | `checkmark-circle` / `radio-button-off` | `theme.colors.base.secondary` | OK ✅ |
| Close modal | `close` | `theme.colors.base.textMuted` | OK ✅ |

#### Glass Components Icons

| Component | Icon(s) | Color Source | Visibility Issue |
|-----------|---------|-------------|-----------------|
| `CollapsibleSection.tsx` | Dynamic `icon` prop + `chevron-up`/`chevron-down` | Varies by caller | Depends on which theme the parent uses |
| `GlassButton.tsx` | Optional icon prop | Varies | Depends on parent |
| `GlassChip.tsx` | Optional icon prop | Varies | Depends on parent |
| `GlassTabBar.tsx` | Tab icon prop | Varies | Depends on parent |
| `GlassDropdown.tsx` | `chevron-down` | `theme.colors.base.textMuted` | OK ✅ |
| `GlassModal.tsx` | `close` | `theme.colors.base.textMuted` | OK ✅ |

### Diagnosis: Why Icons May Be Invisible

| Severity | Issue | Affected Components | Fix |
|----------|-------|---------------------|-----|
| 🔴 **Critical** | `colors.text.tertiary` = `rgba(100,130,150,0.35)` used for empty state icons (36px) and inline metadata icons — **35% opacity on near-black bg is invisible** | CommandScreen empty states, session metadata, hardware-chip | Increase to `rgba(100,130,150,0.60)` minimum, or use storm palette `distantStorm` at higher alpha |
| 🔴 **Critical** | `colors.accent.cyan` = `#1A5C6A` is very dark teal — poor contrast ratio (~1.5:1) against `#030608` background, especially at 12-14px sizes | All CollapsibleSection headers in ConfigScreen, status badges in CommandScreen, ChatInputBar action buttons, SideRail active icons | Brighten to `#247080` (cyanGlow) or `#2E90A5` (lightningCyan) for icons |
| 🟡 **Major** | All `colors.semantic.*` values are very dark (`#1A7A6A`, `#8A2020`, `#7A6020`) — used for 12px status badges | CommandScreen task/trigger status indicators | Brighten semantic colors or add glow/bg behind tiny icons |
| 🟡 **Major** | `colors.accent.violet` = `#5A3A8A` at 12-14px — dark violet on dark bg | SwarmScreen tier badges, trigger indicators | Brighten to `#7A5AAA` |
| 🟢 **OK** | Config theme icons (`theme.colors.base.*`) use bright colors (`#D4F49C`, `#C69CF4`, `#A3A3B2`) | DeviceScreen, MemoryScreen, SettingsScreen, IntegrationsScreen | Visible but **wrong palette** — lime/lavender doesn't match storm aesthetic |
| 🟢 **OK** | FloatingDock uses hardcoded bright colors (`#D0E8F5`, `rgba(160,190,210,0.75)`) | FloatingDock (phone layout) | Visible, but should use storm palette tokens |

### Icon Fixes — Implementation Plan

| # | Task | Files | Priority |
|---|------|-------|----------|
| I.1 | **Unify theme systems** — merge `src/config.ts` defaults + `src/theme/colors.ts` into one storm palette; or make config defaults match storm palette | `src/config.ts`, `src/theme/colors.ts`, `ui/theme.ts` | **P0** |
| I.2 | **Fix `colors.text.tertiary` opacity** — raise from 0.35 to 0.55+ for all icon usages | `src/theme/colors.ts` | **P0** |
| I.3 | **Brighten `colors.accent.cyan` for icon use** — either brighten the token or create `colors.accent.cyanIcon` at `#2E90A5` for small icon contexts | `src/theme/colors.ts` + all icon color references | **P0** |
| I.4 | **Brighten semantic colors** for small icon contexts — success, error, warning all need more luminance at 12px | `src/theme/colors.ts` | **P0** |
| I.5 | **Fix ChatInputBar icon colors** — `image-outline`, `camera-outline`, `document-outline`, `mic-outline` should use brighter accent | `src/components/chat/ChatInputBar.tsx` | **P0** |
| I.6 | **Fix CommandScreen empty state icons** — 36px at 35% opacity is invisible | `src/screens/tabs/CommandScreen.tsx` | **P0** |
| I.7 | **Fix ConfigScreen section header icons** — all CollapsibleSection icons use dark cyan | `src/screens/tabs/ConfigScreen.tsx` | **P1** |
| I.8 | **Fix SwarmScreen icon colors** — globe empty state, tier badges, connection status | `src/screens/tabs/SwarmScreen.tsx` | **P1** |
| I.9 | **Align config theme defaults to storm palette** — change `EXPO_PUBLIC_THEME_*` defaults from lime/lavender to storm colors | `src/config.ts` | **P1** |
| I.10 | **Replace hardcoded colors in FloatingDock/SideRail** with theme tokens | `FloatingDock.tsx`, `SideRail.tsx` | **P1** |

### Android-Side Icons (Launcher, Notification, Splash)

| Asset | Location | Current State | Issue |
|-------|----------|---------------|-------|
| **App launcher icon** | `res/mipmap-*/ic_launcher.webp` | ❌ **Default Android robot** (green droid) | No Guappa branding |
| **Round launcher icon** | `res/mipmap-*/ic_launcher_round.webp` | ❌ **Default Android robot** | Same |
| **Splash screen logo** | `res/drawable-*/splashscreen_logo.png` | ❌ **Generic concentric circles placeholder** (Expo default) | No branding |
| **Adaptive icon** | `res/drawable/ic_launcher_foreground.xml` | ❌ **Does not exist** | No adaptive icon |
| **Monochrome icon** (Android 13+) | — | ❌ **Missing** | Themed icons won't work |
| **Notification icons** | 14 Kotlin files | ❌ All use `android.R.drawable.ic_dialog_info` etc. | System defaults |

#### Notification Icons — Per-File Inventory

| File | Line(s) | Current Icon | Should Be |
|------|---------|-------------|-----------|
| `GeofenceTool.kt` | 224 | `android.R.drawable.ic_dialog_map` | Custom `R.drawable.ic_notif_location` |
| `CronJobTool.kt` | 235 | `android.R.drawable.ic_dialog_info` | Custom `R.drawable.ic_notif_schedule` |
| `ReminderTool.kt` | 193 | `android.R.drawable.ic_dialog_info` | Custom `R.drawable.ic_notif_reminder` |
| `RuntimeBridge.kt` | 973 | `android.R.drawable.ic_dialog_info` | Custom `R.drawable.ic_notif_agent` |
| `GuappaAgentService.kt` | 141 | `R.mipmap.ic_launcher` | Custom `R.drawable.ic_notif_agent` |
| `GuappaNotificationManager.kt` | 90,101 | `ic_menu_send` / `ic_dialog_info` | Custom `R.drawable.ic_notif_message` |
| `GuappaNotificationManager.kt` | 138 | `ic_menu_manage` | Custom `R.drawable.ic_notif_task` |
| `GuappaNotificationManager.kt` | 186,197 | `ic_menu_send` / `ic_menu_help` | Custom `R.drawable.ic_notif_action` |
| `GuappaNotificationManager.kt` | 229 | `ic_dialog_alert` | Custom `R.drawable.ic_notif_alert` |
| `GuappaNotificationManager.kt` | 266 | `ic_popup_reminder` | Custom `R.drawable.ic_notif_reminder` |
| `GuappaNotificationManager.kt` | 308 | `ic_menu_info_details` | Custom `R.drawable.ic_notif_info` |
| `NotificationActionReceiver.kt` | 175 | `ic_dialog_info` | Custom `R.drawable.ic_notif_reply` |
| `AndroidAgentToolsModule.kt` | 415 | `R.mipmap.ic_launcher` | Custom `R.drawable.ic_notif_agent` |
| `RuntimeAlwaysOnService.kt` | 43 | `R.mipmap.ic_launcher` | Custom `R.drawable.ic_notif_service` |

#### Android Icon Implementation Plan

| # | Task | Deliverables | Priority |
|---|------|-------------|----------|
| I.11 | **Design Guappa app icon** — storm-themed, neural swarm motif | Adaptive icon XML + all density webps | **P0** |
| I.12 | **Create notification icon set** — 24dp white-on-transparent vector drawables | 11 `ic_notif_*.xml` files | **P0** |
| I.13 | **Replace all system icon references** in Kotlin | Update 14 `.setSmallIcon()` calls | **P0** |
| I.14 | **Design splash screen logo** | Replace `splashscreen_logo.png` in all densities | **P0** |
| I.15 | **Add monochrome icon** (Android 13+) | `<monochrome>` in adaptive icon XML | P1 |
| I.16 | **Update Expo config** (`app.json`) | Set `icon`, `splash.image`, `android.adaptiveIcon` | P1 |
| I.17 | **Add app shortcuts** | `res/xml/shortcuts.xml` + manifest | P2 |

### E2E Tests for Icons

| Test | What It Validates | Type |
|------|-------------------|------|
| `e2e_dock_icons_visible.yaml` | Screenshot dock → verify all 5 tab icons render (not blank squares) | Maestro |
| `e2e_config_section_icons.yaml` | Open Config screen → screenshot → verify section header icons visible | Maestro |
| `e2e_command_empty_states.yaml` | Open Command screen with no tasks → screenshot → verify empty state icons visible | Maestro |
| `e2e_chat_input_icons.yaml` | Open Chat screen → screenshot input bar → verify attachment/send/mic icons | Maestro |
| `e2e_icon_launcher_not_default.yaml` | Screenshot launcher → verify not default Android robot | Maestro |
| `e2e_notification_icon_check.yaml` | Trigger notification → screenshot shade → verify custom icon | Maestro |
| `e2e_splash_screen_check.yaml` | Cold launch → screenshot splash → verify branded logo | Maestro |
| `IconRegressionTest.kt` | Verify `R.drawable.ic_notif_*` resources exist | UI Automator |

---

## Phase 1: Foundation — Agent Core

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 1.1 | GuappaOrchestrator (ReAct loop) | ✅ | `agent/GuappaOrchestrator.kt` (516 lines) | — | 🧪 `e2e_full_agent_scenario.yaml` |
| 1.2 | GuappaSession (conversation state) | ⚠️ | `agent/GuappaSession.kt` (67 lines) | Very thin — no session type enum, no TTL, no checkpoint/recovery | 🚫 Needs: `e2e_session_persistence.yaml` — send message → kill app → reopen → verify conversation intact |
| 1.3 | GuappaPlanner (ReAct, task decomposition) | ✅ | `agent/GuappaPlanner.kt` (356 lines) | — | 🧪 `e2e_full_agent_scenario.yaml` (indirect) |
| 1.4 | MessageBus (SharedFlow pub/sub) | ✅ | `agent/MessageBus.kt` (74 lines) | No priority queue for urgent events | 🚫 Internal — no direct E2E needed |
| 1.5 | TaskManager | ✅ | `agent/TaskManager.kt` (262 lines) | — | 🧪 `e2e_phase2_call_hook_setup.yaml` (creates task) |
| 1.6 | GuappaConfig | ✅ | `agent/GuappaConfig.kt` | — | 🧪 Config screen tests |
| 1.7 | GuappaPersona (system prompt, personality) | ✅ | `agent/GuappaPersona.kt` | — | 🚫 Needs: `e2e_persona_tone.yaml` — verify agent responds with persona traits |
| 1.8 | Foreground service (DATA_SYNC) | ✅ | `RuntimeAlwaysOnService.kt` + `GuappaAgentService.kt` | Verify no duplication | 🚫 Needs: `e2e_service_survives_background.yaml` — minimize app → wait 60s → send intent → verify response |
| 1.9 | Boot receiver (auto-start) | ✅ | `RuntimeBootReceiver.kt` (24 lines) | — | 🚫 Needs: `BootReceiverTest.kt` (UI Automator — reboot emulator) |
| 1.10 | Room database (sessions, messages, tasks) | ✅ | `memory/GuappaDatabase.kt`, `Entities.kt`, `Daos.kt` | — | 🧪 `test_scenario_memory_persistence.yaml` |
| 1.11 | Context Manager / budget allocation | ✅ | `memory/ContextCompactor.kt` (343 lines) | — | 🚫 Needs: `e2e_long_conversation_context.yaml` — 50+ messages then recall early details |
| 1.12 | Streaming responses | ✅ | All providers implement `streamChat()` | — | 🧪 `live_openrouter_chat.yaml` |
| 1.13 | Retry with exponential backoff | ⚠️ | Logic in orchestrator | Verify fallback provider chain | 🚫 Needs: `e2e_provider_failover.yaml` — configure bad key → verify fallback |
| 1.14 | Multi-session concurrency | ⚠️ | Session entity exists | Unclear if concurrent sessions tested | 🚫 Needs: `MultiSessionTest.kt` (UI Automator) |
| 1.15 | Session encryption (SQLCipher / Keystore) | ❌ | No encryption layer found | Plain Room database | 🚫 Needs: `e2e_encrypted_db_check.yaml` — verify DB file not plaintext readable |
| 1.16 | Dependency injection (Hilt/Koin) | ❌ | No DI framework | Manual wiring | 🚫 N/A — architectural, no E2E |

---

## Phase 2: Provider Router — Dynamic Model Discovery

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 2.1 | ProviderRouter (capability-based routing) | ✅ | `providers/ProviderRouter.kt` (114 lines) | — | 🧪 `live_openrouter_chat.yaml` (indirect) |
| 2.2 | Provider interface | ✅ | `providers/Provider.kt` | `chat()`, `streamChat()`, `listModels()` | 🚫 N/A — interface |
| 2.3 | AnthropicProvider | ✅ | `providers/AnthropicProvider.kt` (310 lines) | — | 🚫 Needs: `e2e_anthropic_chat.yaml` |
| 2.4 | OpenAI-compatible provider (base) | ✅ | `providers/OpenAICompatibleProvider.kt` (307 lines) | — | 🧪 `live_openrouter_chat.yaml` |
| 2.5 | Google Gemini provider | ✅ | `providers/GoogleGeminiProvider.kt` | — | 🚫 Needs: `e2e_gemini_chat.yaml` |
| 2.6 | Dynamic model fetching | ✅ | `listModels()` in all providers | — | 🚫 Needs: `e2e_model_list_fetch.yaml` — open config → verify model dropdown populates |
| 2.7 | CapabilityInferrer | ✅ | `providers/CapabilityInferrer.kt` | — | 🚫 Unit test only |
| 2.8 | CapabilityType enum | ✅ | `providers/CapabilityType.kt` | — | 🚫 N/A — enum |
| 2.9 | CostTracker | ✅ | `providers/CostTracker.kt` | — | 🚫 Needs: `e2e_cost_display.yaml` — send messages → verify cost shown in UI |
| 2.10 | ProviderFactory | ✅ | `providers/ProviderFactory.kt` | — | 🚫 N/A — factory |
| 2.11 | Local inference (llama.rn GGUF) | ✅ | `localLlmServer.ts` + NanoHTTPD | — | 🚫 Needs: `e2e_local_llm_chat.yaml` — download GGUF → chat → verify response |
| 2.12 | LiteRT-LM (Gemini Nano) | ❌ | Not found | — | 🚫 |
| 2.13 | Qualcomm GENIE (NPU) | ❌ | Not found | — | 🚫 |
| 2.14 | ONNX Runtime Mobile | ❌ | Not found | — | 🚫 |
| 2.15 | HardwareProbe (SoC/NPU detection) | ❌ | Not found | — | 🚫 |
| 2.16 | ModelDownloadManager | ✅ | `ModelDownloaderModule.kt` + `modelDownloader.ts` | — | 🚫 Needs: `e2e_model_download.yaml` — trigger download → verify progress → verify usable |
| 2.17 | Token counter (tiktoken) | ❌ | Not found | — | 🚫 |
| 2.18 | Separate model per capability | ⚠️ | ConfigStore has fields | UI supports but routing unverified | 🚫 Needs: `e2e_vision_model_routing.yaml` — send image → verify vision model used |
| 2.19 | OAuth — OpenAI Codex | ❌ | Only API key auth | — | 🚫 Needs: `e2e_openai_oauth.yaml` |
| 2.20 | OAuth — Anthropic | ❌ | Only API key auth | — | 🚫 Needs: `e2e_anthropic_oauth.yaml` |
| 2.21 | OAuth — GitHub Copilot | ❌ | Endpoint exists but API key won't work | — | 🚫 Needs: `e2e_copilot_oauth_flow.yaml` |
| 2.22 | OAuth — Google Gemini CLI | ❌ | Only API key auth | — | 🚫 Needs: `e2e_google_oauth.yaml` |
| 2.23 | OAuth infrastructure (PKCE, token refresh) | ❌ | No OAuth code anywhere | — | 🚫 |

---

## Phase 3: Tool Engine — 65+ Tools

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 3.1 | ToolEngine (registry, dispatch) | ✅ | `tools/ToolEngine.kt` (62 lines) | Thin dispatcher | 🧪 All tool tests exercise this |
| 3.2 | ToolRegistry | ✅ | `tools/ToolRegistry.kt` (153 lines) | — | 🚫 N/A — internal |
| 3.3 | ToolResult | ✅ | `tools/ToolResult.kt` | — | 🚫 N/A — data class |
| 3.4 | ToolPermissions | ✅ | `tools/ToolPermissions.kt` | — | 🚫 Needs: `PermissionFlowTest.kt` (UI Automator — `watchFor(PermissionDialog)`) |
| 3.5 | ToolRateLimiter | ✅ | `tools/ToolRateLimiter.kt` | — | 🚫 Needs: `e2e_rate_limit_feedback.yaml` — rapid tool calls → verify rate limit message |
| 3.6 | ToolAuditLog | ✅ | `tools/ToolAuditLog.kt` | — | 🚫 Needs: `e2e_audit_log_visible.yaml` — execute tool → verify log entry in Command screen |
| 3.7 | Tool implementations (78 tools) | ✅ | `tools/impl/` | Exceeds 65 target | 🧪 Multiple Maestro flows |
| 3.8 | Device tools | ✅ | SMS, call, contacts, calendar, camera, location, sensors | — | 🧪 `test_scenario_incoming_call_telegram.yaml`, `e2e_phase2_call_hook_setup.yaml` |
| 3.9 | App tools | ✅ | Launch, list, alarm, timer, reminder, email, browser, maps, music | — | 🚫 Needs: `e2e_set_alarm.yaml`, `e2e_launch_app.yaml` |
| 3.10 | Web tools | ✅ | WebFetch, WebSearch, WebScrape, WebApi | — | 🚫 Needs: `e2e_brave_web_search.yaml` |
| 3.11 | File tools | ✅ | Read, write, search, list, delete | — | 🚫 Needs: `e2e_file_read_write.yaml` |
| 3.12 | Social tools | ✅ | Twitter, Instagram, Telegram, WhatsApp, SocialShare | — | 🧪 `e2e_call_telegram.sh` (Telegram) |
| 3.13 | AI tools | ✅ | ImageAnalyze, OCR, CodeInterpreter, Calculator, Translation | — | 🚫 Needs: `e2e_calculator_tool.yaml`, `e2e_image_analyze.yaml` |
| 3.14 | System tools | ✅ | Shell, PackageInfo, SystemInfo, ProcessList | — | 🚫 Needs: `e2e_system_info.yaml` |
| 3.15 | Android AppFunctions API | ❌ | Not implemented | `androidx.appfunctions:0.1.0-alpha01` now available | 🚫 Needs: `e2e_appfunctions_control.yaml` |
| 3.15b | Android UI Automation Framework | ❌ | `AgentAccessibilityService.kt` exists but uses accessibility | — | 🚫 |
| 3.16 | ScreenshotTool (MediaProjection) | ✅ | `ScreenshotTool.kt` | — | 🚫 Needs: `e2e_screenshot_tool.yaml` |
| 3.17 | CronJobTool | ✅ | `CronJobTool.kt` (241 lines) | — | 🚫 Needs: `e2e_cron_schedule.yaml` — schedule task → wait → verify execution |
| 3.18 | GeofenceTool | ✅ | `GeofenceTool.kt` (230 lines) | — | 🚫 Needs: `e2e_geofence_setup.yaml` |
| 3.19 | Additional tools (NFC, BT, QR, PDF, RSS) | ✅ | 6+ tools | — | 🚫 Needs: `e2e_qr_scan.yaml` |
| 3.20 | Hook incoming call tool | ✅ | `RuntimeBridge.kt` → `hook_incoming_call` | — | 🧪 `e2e_phase2_call_hook_setup.yaml` + `e2e_phase3_call_emulate_verify.yaml` |
| 3.21 | Browser session tool | ⚠️ | `AgentBrowserActivity.kt` | Not headless as planned | 🚫 Needs: `e2e_browser_session.yaml` |

---

## Phase 4: Proactive Agent & Push Notifications

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 4.1 | ProactiveEngine | ✅ | `proactive/ProactiveEngine.kt` (118 lines) | — | 🚫 Needs: `e2e_proactive_trigger.yaml` — trigger event → verify agent proactively responds |
| 4.2 | GuappaNotificationManager | ✅ | `proactive/GuappaNotificationManager.kt` | — | 🚫 Needs: `NotificationTest.kt` (UI Automator — reads notification shade) |
| 4.3 | NotificationChannels | ✅ | `proactive/NotificationChannels.kt` | — | 🚫 N/A — config |
| 4.4 | TriggerManager | ✅ | `proactive/TriggerManager.kt` (274 lines) | — | 🧪 `e2e_phase2_call_hook_setup.yaml` |
| 4.5 | IncomingCallReceiver | ✅ | `IncomingCallReceiver.kt` | Emits to RN + MessageBus | 🧪 `test_scenario_incoming_call_telegram.yaml` |
| 4.6 | IncomingSmsReceiver | ✅ | `IncomingSmsReceiver.kt` | — | 🚫 Needs: `e2e_incoming_sms_trigger.yaml` — send SMS via ADB → verify agent reacts |
| 4.7 | BatteryReceiver | ✅ | `proactive/BatteryReceiver.kt` | — | 🚫 Needs: `e2e_battery_low_trigger.yaml` — ADB set battery low → verify notification |
| 4.8 | CalendarObserver | ✅ | `proactive/CalendarObserver.kt` | — | 🚫 Needs: `e2e_calendar_reminder.yaml` |
| 4.9 | EventReactor | ✅ | `proactive/EventReactor.kt` | — | 🚫 Tested via trigger tests |
| 4.10 | SmartTiming | ✅ | `proactive/SmartTiming.kt` | — | 🚫 Needs: `e2e_dnd_respects_timing.yaml` — set DND → verify no notification |
| 4.11 | ProactiveRules | ✅ | `proactive/ProactiveRules.kt` | — | 🚫 Needs: `e2e_proactive_rules_config.yaml` |
| 4.12 | TaskCompletionReporter | ✅ | `proactive/TaskCompletionReporter.kt` | — | 🚫 Covered by task completion tests |
| 4.13 | NotificationActionReceiver | ✅ | `proactive/NotificationActionReceiver.kt` | — | 🚫 Needs: `NotificationActionTest.kt` (UI Automator — tap notification button) |
| 4.14 | NotificationDeduplicator | ✅ | `proactive/NotificationDeduplicator.kt` | — | 🚫 Unit test sufficient |
| 4.15 | NotificationHistory | ✅ | `proactive/NotificationHistory.kt` | — | 🚫 Needs: `e2e_notification_history.yaml` |
| 4.16 | MorningBriefingWorker | ✅ | `proactive/MorningBriefingWorker.kt` | — | 🚫 Needs: `e2e_morning_briefing.yaml` (hard to time — use WorkManager test utils) |
| 4.17 | DailySummaryWorker | ✅ | `proactive/DailySummaryWorker.kt` | — | 🚫 Same as above |
| 4.18 | MessagingStyle notifications | ⚠️ | NotificationManager exists | Verify MessagingStyle used | 🚫 Needs: `NotificationStyleTest.kt` (UI Automator) |
| 4.19 | Inline reply from notification | ⚠️ | NotificationActionReceiver exists | Verify direct reply input | 🚫 Needs: `NotificationReplyTest.kt` (UI Automator — type in notification) |
| 4.20 | LocationGeofenceReceiver | ⚠️ | GeofenceTool exists | No dedicated broadcast receiver | 🚫 Needs: `e2e_geofence_transition.yaml` |
| 4.21 | Network state receiver | ❌ | Not found | — | 🚫 |
| 4.22 | Screen state receiver | ❌ | Not found | — | 🚫 |

---

## Phase 5: Channel Hub — Messenger Integrations

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 5.1 | Channel interface | ✅ | `channels/Channel.kt` | — | 🚫 N/A — interface |
| 5.2 | ChannelHub | ✅ | `channels/ChannelHub.kt` (153 lines) | — | 🚫 N/A — manager |
| 5.3 | ChannelFactory | ✅ | `channels/ChannelFactory.kt` (52 lines) | — | 🚫 N/A — factory |
| 5.4 | Telegram (send via Bot API) | ✅ | `channels/TelegramChannel.kt` (49 lines) | Send-only | 🧪 `e2e_call_telegram.sh` |
| 5.5 | Telegram incoming (long polling) | ❌ | No `getUpdates` or webhook | Can't receive FROM Telegram | 🚫 Needs: `e2e_telegram_receive.yaml` |
| 5.6 | Discord (webhook send) | 🔧 | `channels/DiscordChannel.kt` (31 lines) | Webhook-only, no incoming | 🚫 Needs: `e2e_discord_send.yaml` |
| 5.7 | Discord gateway (incoming) | ❌ | Not found | — | 🚫 |
| 5.8 | Slack (webhook send) | 🔧 | `channels/SlackChannel.kt` (31 lines) | Webhook-only | 🚫 Needs: `e2e_slack_send.yaml` |
| 5.9 | WhatsApp | ⚠️ | `channels/WhatsAppChannel.kt` (92 lines) | Uses deep links, not Cloud API | 🚫 Needs: `e2e_whatsapp_send.yaml` |
| 5.10 | Signal | 🔧 | `channels/SignalChannel.kt` (59 lines) | Verify signald integration | 🚫 |
| 5.11 | Matrix | ⚠️ | `channels/MatrixChannel.kt` (75 lines) | No E2EE, no sync loop | 🚫 |
| 5.12 | Email (IMAP/SMTP) | 🔧 | `channels/EmailChannel.kt` (26 lines) | Intent-based only | 🚫 Needs: `e2e_email_compose.yaml` |
| 5.13 | SMS channel | ✅ | `channels/SmsChannel.kt` (47 lines) | — | 🚫 Needs: `e2e_sms_send.yaml` |
| 5.14 | GuappaChannelsModule (RN bridge) | ✅ | `channels/GuappaChannelsModule.kt` (240 lines) | — | 🧪 Tested via channel-specific tests |
| 5.15 | Channel `incoming()` Flow | ❌ | Not in Channel interface | No bidirectional | 🚫 |
| 5.16 | ChannelHealthMonitor | ❌ | Not found | — | 🚫 |
| 5.17 | Channel formatters | ❌ | Not found | Raw text to all channels | 🚫 |

---

## Phase 6: Voice Pipeline — STT, TTS, Wake Word

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 6.1 | useVoiceRecording (STT hook) | ✅ | `hooks/useVoiceRecording.ts` | — | 🧪 `guappa_voice_full_flow.yaml` |
| 6.2 | Deepgram STT (cloud, streaming) | ✅ | WebSocket to `api.deepgram.com` | Uses nova-2 — upgrade to nova-3/flux | 🧪 `voice_interruptible_smoke.yaml` |
| 6.3 | Whisper STT (on-device) | ✅ | `whisper.rn` + GGML download | — | 🚫 Needs: `e2e_local_whisper_stt.yaml` |
| 6.4 | WhisperModelManager | ✅ | `voice/whisperModelManager.ts` | — | 🚫 Needs: `e2e_whisper_download.yaml` |
| 6.5 | useTTS (text-to-speech) | ✅ | `hooks/useTTS.ts` | Uses `expo-speech` | 🚫 Needs: `e2e_builtin_tts_response.yaml` |
| 6.6 | useVAD (voice activity detection) | ✅ | `hooks/useVAD.ts` | Energy-based | 🚫 Tested via voice flow |
| 6.7 | useWakeWord ("Hey Guappa") | ✅ | `hooks/useWakeWord.ts` | Energy + STT keyword | 🚫 Needs: `e2e_wake_word.yaml` — play "Hey Guappa" via BlackHole → verify activation |
| 6.8 | VoiceAmplitude | ✅ | `swarm/audio/VoiceAmplitude.ts` | — | 🧪 `e2e_voice_swarm_emotion.yaml` |
| 6.9 | VoiceScreen UI | ✅ | `screens/tabs/VoiceScreen.tsx` (338 lines) | — | 🧪 `guappa_voice_full_flow.yaml` |
| 6.10 | Streaming TTS | ⚠️ | Sentence-level queue | Not word-level | 🚫 |
| 6.11 | Android `SpeechRecognizer` (built-in) | ❌ | Not implemented | **P1** — zero cost, zero API key | 🚫 Needs: `e2e_android_stt_fallback.yaml` |
| 6.12 | Google ML Kit Speech | ❌ | Not found | — | 🚫 |
| 6.13 | Google Cloud Speech-to-Text | ❌ | Not found | — | 🚫 |
| 6.14 | Android TextToSpeech (built-in) | ✅ | Via `expo-speech` | — | 🧪 Via TTS tests |
| 6.15 | Picovoice Orca TTS | ❌ | Not found | Commercial | 🚫 |
| 6.16 | Kokoro TTS (on-device) | ❌ | Not found | Apache 2.0, best quality/size | 🚫 Needs: `e2e_kokoro_tts.yaml` |
| 6.17 | Piper TTS (on-device) | ❌ | Not found | MIT, 100+ voices | 🚫 |
| 6.18 | ElevenLabs TTS (cloud) | ❌ | Not found | — | 🚫 |
| 6.19 | OpenAI TTS (cloud) | ❌ | Not found | — | 🚫 |
| 6.20 | Speechmatics TTS (cloud) | ❌ | Not found | — | 🚫 |
| 6.21 | Google Cloud TTS | ❌ | Not found | — | 🚫 |
| 6.22 | Picovoice Porcupine wake word | ❌ | Using custom energy-based | — | 🚫 |
| 6.23 | Silero VAD | ❌ | Using expo-av metering | — | 🚫 |
| 6.24 | Audio routing (speaker/earpiece/BT) | ❌ | — | — | 🚫 |
| 6.25 | Audio focus management | ❌ | — | — | 🚫 |
| 6.26 | Bluetooth SCO/A2DP routing | ❌ | — | — | 🚫 |
| 6.27 | STT/TTS engine selection UI | ❌ | Hardcoded | — | 🚫 Needs: `e2e_stt_engine_switch.yaml` |
| 6.28 | SpeechRecognizer as free STT fallback | ❌ | **HIGH PRIORITY** | — | 🚫 Needs: `e2e_android_stt_fallback.yaml` |

### Deepgram Full Product Catalog

| # | Feature | Status | Description | E2E Test |
|---|---------|--------|-------------|----------|
| 6.30 | Flux STT (voice agent) | ❌ | Built-in end-of-turn detection | 🚫 Needs: `e2e_deepgram_flux.yaml` |
| 6.31 | Nova-3 STT | ❌ | 54% WER reduction, 50+ langs | 🚫 Needs: `e2e_deepgram_nova3.yaml` |
| 6.32 | Nova-3 Medical | ❌ | Medical terminology | 🚫 |
| 6.33 | Nova-2 STT | ✅ | Currently used | 🧪 `voice_interruptible_smoke.yaml` |
| 6.36 | Aura-2 TTS | ❌ | Natural streaming TTS | 🚫 Needs: `e2e_deepgram_aura2_tts.yaml` |
| 6.38 | Audio Intelligence (summarize, topics, sentiment) | ❌ | Add-on query params to STT | 🚫 Needs: `e2e_audio_intelligence.yaml` |

---

## Phase 7: Memory & Context

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 7.1 | MemoryManager (5-tier) | ✅ | `memory/MemoryManager.kt` (627 lines) | — | 🧪 `test_scenario_memory_persistence.yaml` |
| 7.2 | Tier 1: Working memory | ✅ | ContextCompactor manages | — | 🧪 `e2e_phase1_memory_store.yaml` |
| 7.3 | Tier 2: Short-term memory | ✅ | Room SessionEntity, MessageEntity | — | 🧪 `e2e_phase4_memory_recall.yaml` |
| 7.4 | Tier 3: Long-term memory (facts) | ✅ | `MemoryFactEntity` | — | 🧪 `e2e_phase4_memory_recall.yaml` |
| 7.5 | Tier 4: Episodic memory | ✅ | `EpisodeEntity` | — | 🚫 Needs: `e2e_episodic_recall.yaml` — "what task did you do yesterday?" |
| 7.6 | Tier 5: Semantic memory (embeddings) | ✅ | `EmbeddingEntity` + `EmbeddingService.kt` | — | 🚫 Needs: `e2e_semantic_search.yaml` — store 10 facts → query by meaning |
| 7.7 | ContextCompactor | ✅ | `memory/ContextCompactor.kt` (343 lines) | — | 🚫 Needs: `e2e_long_conversation_context.yaml` |
| 7.8 | SummarizationService | ✅ | `memory/SummarizationService.kt` | — | 🚫 Tested via context compaction |
| 7.9 | MemoryConsolidationWorker | ✅ | `memory/MemoryConsolidationWorker.kt` | — | 🚫 Needs: `e2e_memory_consolidation.yaml` — add facts → trigger consolidation → verify merged |
| 7.10 | EmbeddingService | ✅ | `memory/EmbeddingService.kt` | — | 🚫 Tested via semantic search |
| 7.11 | MemoryBridge (RN module) | ✅ | `memory/MemoryBridge.kt` | — | 🧪 Via memory screen tests |
| 7.12 | MemoryScreen (UI) | ✅ | `screens/tabs/MemoryScreen.tsx` (363 lines) | — | 🚫 Needs: `e2e_memory_screen_browse.yaml` |
| 7.13 | On-device embedding model | ⚠️ | EmbeddingService exists | May use cloud API | 🚫 Needs: `e2e_offline_embedding.yaml` |
| 7.14 | Recursive summarization | ⚠️ | SummarizationService exists | Verify hierarchical chain | 🚫 |
| 7.15 | Memory export/import | ❌ | Not found | — | 🚫 Needs: `e2e_memory_export_import.yaml` |

---

## Phase 9: Testing & QA

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 9.1 | Unit tests (JUnit + MockK) | ⚠️ | 16 test files | No agent/memory/proactive tests | 🚫 Expand unit test coverage |
| 9.2 | Maestro E2E flows | ✅ | **100+ YAML flows** in `.maestro/` | — | 🧪 Extensive |
| 9.3 | Integration tests (Espresso) | ❌ | Not found | — | 🚫 |
| 9.4 | Performance benchmarks | ❌ | Not found | — | 🚫 Needs: `StartupBenchmark.kt`, `ChatJankBenchmark.kt` |
| 9.5 | Firebase Test Lab config | ❌ | Not found | — | 🚫 |
| 9.6 | CI pipeline (.github/workflows) | ❌ | No workflows directory | — | 🚫 |
| 9.7 | Resilience tests | ⚠️ | Some Maestro flows | No systematic chaos testing | 🚫 |
| 9.8 | UI Automator 2.4 instrumented tests | ❌ | No `androidTest/` directory | — | 🚫 Needs full `androidTest/` setup |
| 9.9 | `testInstrumentationRunner` | ❌ | Missing from `defaultConfig` | — | 🚫 |
| 9.10 | UI Automator Shell | ❌ | Not found | — | 🚫 |
| 9.11 | Macrobenchmark / Baseline Profiles | ❌ | Not found | — | 🚫 Needs: `BaselineProfileGenerator.kt` |

---

## Phase 10: Live Config — Hot Reload

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 10.1 | GuappaConfigStore | ✅ | `config/GuappaConfigStore.kt` (413 lines) | — | 🧪 Config screen Maestro tests |
| 10.2 | ConfigBridge (RN module) | ✅ | `config/ConfigBridge.kt` | — | 🧪 Config screen tests |
| 10.3 | ConfigChangeDispatcher | ✅ | `config/ConfigChangeDispatcher.kt` | — | 🚫 Tested indirectly |
| 10.4 | ProviderHotSwap | ✅ | `config/ProviderHotSwap.kt` | — | 🚫 Needs: `e2e_provider_hot_swap.yaml` — switch provider mid-chat → verify continues |
| 10.5 | ChannelHotSwap | ✅ | `config/ChannelHotSwap.kt` | — | 🚫 |
| 10.6 | ToolHotSwap | ✅ | `config/ToolHotSwap.kt` | — | 🚫 |
| 10.7 | SecurePrefs | ✅ | `config/SecurePrefs.kt` | — | 🚫 Needs: `e2e_api_key_persists.yaml` — set key → restart → verify still there |
| 10.8 | TurboModule (New Architecture) | ❌ | Uses old NativeModule | — | 🚫 |
| 10.9 | VoiceHotSwap | ❌ | Not found | — | 🚫 |
| 10.10 | MemoryHotSwap | ❌ | Not found | — | 🚫 |
| 10.11 | ConfigMigrator | ❌ | Not found | — | 🚫 |
| 10.12 | ConfigValidator | ❌ | Not found | — | 🚫 Needs: `e2e_invalid_config_feedback.yaml` — enter bad API key → verify error message |

---

## Phase 11: World Wide Swarm

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 11.1 | SwarmManager | ✅ | `swarm/SwarmManager.kt` (261 lines) | — | 🚫 Needs: `e2e_swarm_connect.yaml` |
| 11.2 | SwarmConnectorClient | ✅ | `swarm/SwarmConnectorClient.kt` (185 lines) | — | 🚫 Needs: `e2e_swarm_send_message.yaml` |
| 11.3 | SwarmConfig | ✅ | `swarm/SwarmConfig.kt` | — | 🚫 |
| 11.4 | SwarmIdentity (Ed25519, DID) | ✅ | `swarm/SwarmIdentity.kt` | — | 🚫 Needs: `e2e_swarm_identity_gen.yaml` — verify DID generated on swarm screen |
| 11.5 | SwarmChallengeSolver | ✅ | `swarm/SwarmChallengeSolver.kt` | — | 🚫 |
| 11.6–11.12 | SwarmTask, Poller, Executor, Message, Holon, Reputation, PeerInfo | ✅ | All exist | — | 🚫 Needs: `e2e_swarm_task_lifecycle.yaml` |
| 11.13 | GuappaSwarmModule (RN bridge) | ✅ | `swarm/GuappaSwarmModule.kt` | — | 🧪 Swarm screen tests |
| 11.14 | SwarmScreen (UI) | ✅ | `screens/tabs/SwarmScreen.tsx` (1694 lines) | — | 🚫 Needs: `e2e_swarm_screen_tabs.yaml` — navigate identity/peers/feed tabs |
| 11.15 | Embedded connector (Rust) | ❌ | Not found | Phase 11b | 🚫 |
| 11.16 | mDNS local discovery | ❌ | Not found | — | 🚫 |

---

## Phase 12: Android UI

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 12.1 | 5-screen app | ✅ | All 5 screens + RootNavigator | — | 🧪 `smoke_all_screens.yaml` (if exists) |
| 12.2 | FloatingDock | ✅ | `components/dock/FloatingDock.tsx` | Hardcoded colors — needs storm palette | 🧪 `floating_dock_navigation.yaml` (if exists) |
| 12.3 | SideRail (tablet) | ✅ | `components/dock/SideRail.tsx` | Hardcoded colors — needs storm palette | 🚫 Needs: `e2e_tablet_side_rail.yaml` (tablet emulator) |
| 12.4 | Glass design system | ✅ | 15 glass components | **Needs storm palette glass fills** | 🚫 Needs: `e2e_glass_components_visual.yaml` — screenshot each glass variant |
| 12.5 | PlasmaOrb | ✅ | `components/plasma/PlasmaOrb.tsx` | — | 🚫 |
| 12.6 | ChatScreen | ✅ | `screens/tabs/ChatScreen.tsx` (342 lines) | — | 🧪 `live_openrouter_chat.yaml` |
| 12.7 | CommandScreen | ✅ | `screens/tabs/CommandScreen.tsx` (1497 lines) | — | 🚫 Needs: `e2e_command_screen_sections.yaml` |
| 12.8 | ConfigScreen | ✅ | `screens/tabs/ConfigScreen.tsx` (1373 lines) | — | 🧪 Config Maestro tests |
| 12.9 | OnboardingScreen | ✅ | `screens/OnboardingScreen.tsx` + 4 steps | — | 🚫 Needs: `e2e_onboarding_flow.yaml` — fresh install → complete all steps |
| 12.10 | Color system | ✅ | `theme/colors.ts` | **NEEDS STORM PALETTE UPDATE** | 🚫 Needs: `e2e_theme_consistency.yaml` — screenshot all screens |
| 12.11 | Typography | ✅ | `theme/typography.ts` | — | 🚫 |
| 12.12 | Animations | ✅ | `theme/animations.ts` + Reanimated | — | 🚫 |
| 12.13 | Gyroscope parallax | ⚠️ | Camera3D uses Accelerometer | SwarmCanvas only | 🚫 |

---

## Phase 14: Neural Swarm Visualization

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 14.1 | SwarmCanvas (Skia, 420 neurons) | ✅ | `swarm/SwarmCanvas.tsx` (585 lines) | — | 🧪 `e2e_voice_swarm_emotion.yaml` |
| 14.2 | NeuronSystem (3D physics) | ✅ | `swarm/neurons/NeuronSystem.ts` (329 lines) | — | 🚫 |
| 14.3 | Camera3D | ✅ | `swarm/camera/Camera3D.ts` (130 lines) | — | 🚫 |
| 14.4 | SwarmController (state machine) | ✅ | `swarm/SwarmController.ts` (73 lines) | — | 🧪 `e2e_voice_swarm_emotion.yaml` |
| 14.5 | SwarmDirector | ✅ | `swarm/SwarmDirector.ts` (190 lines) | — | 🚫 |
| 14.6 | EmotionPalette (20 emotions) | ✅ | `swarm/emotion/EmotionPalette.ts` (63 lines) | **Needs storm palette update** | 🚫 Needs: `e2e_emotion_palette_colors.yaml` — trigger each emotion → screenshot |
| 14.7 | EmotionBlender | ✅ | `swarm/emotion/EmotionBlender.ts` (41 lines) | — | 🚫 |
| 14.8 | ShapeLibrary | ✅ | `swarm/formations/ShapeLibrary.ts` (620 lines) | — | 🚫 |
| 14.9 | TextRenderer | ✅ | `swarm/formations/TextRenderer.ts` (105 lines) | — | 🚫 |
| 14.10 | HarmonicWaves | ✅ | `swarm/waves/HarmonicWaves.ts` (138 lines) | — | 🚫 |
| 14.11 | VoiceAmplitude | ✅ | `swarm/audio/VoiceAmplitude.ts` (78 lines) | — | 🧪 `e2e_voice_swarm_emotion.yaml` |
| 14.12 | Neural swarm background on all screens | ✅ | RootNavigator: opacity per screen | — | 🚫 |
| 14.13 | Swarm ↔ voice integration | ✅ | VoiceScreen, useVoiceRecording, useTTS | — | 🧪 `e2e_voice_swarm_emotion.yaml` |

---

## Phase 13: Documentation

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 13.1 | Guides | ✅ | 7 guide files in `docs/guides/` | — | 🚫 N/A |
| 13.2 | Reference docs | ❌ | Not found | — | 🚫 N/A |
| 13.3 | Architecture docs | ❌ | Not found | — | 🚫 N/A |
| 13.4 | Development docs | ❌ | Not found | — | 🚫 N/A |
| 13.5 | AGENTS.md | ✅ | Root `AGENTS.md` | — | 🚫 N/A |

---

## Local Inference — Full Inventory

| # | Modality | Status | Engine | E2E Test |
|---|----------|--------|--------|----------|
| L.1 | Text (LLM) — GGUF | ✅ | `llama.rn` + NanoHTTPD | 🚫 Needs: `e2e_local_llm_chat.yaml` |
| L.2 | STT (on-device Whisper) | ✅ | `whisper.rn` GGML | 🚫 Needs: `e2e_local_whisper_stt.yaml` |
| L.3 | STT (free, zero-download) | ❌ | `SpeechRecognizer` | 🚫 Needs: `e2e_android_stt_fallback.yaml` |
| L.4 | TTS (built-in) | ✅ | `expo-speech` | 🚫 Needs: `e2e_builtin_tts_response.yaml` |
| L.5 | TTS (high-quality on-device) | ❌ | Kokoro / Piper | 🚫 |
| L.6 | Embeddings (on-device) | ⚠️ | `EmbeddingService.kt` | 🚫 Needs: `e2e_offline_embedding.yaml` |
| L.7 | Vision (on-device) | ❌ | — | 🚫 |
| L.8 | Image generation (on-device) | ❌ | — | 🚫 |
| L.9 | Text — MediaPipe/LiteRT | ❌ | — | 🚫 |
| L.10 | Text — LiteRT LM | ❌ | — | 🚫 |
| L.11 | Text — ONNX | ❌ | — | 🚫 |
| L.12 | Text — Nexa SDK (NPU) | ❌ | — | 🚫 |
| L.13 | HardwareProbe (SoC detection) | ❌ | — | 🚫 |

---

## Summary — E2E Coverage Stats

| Phase | Total Features | Has E2E | Missing E2E | Coverage |
|-------|---------------|---------|-------------|----------|
| Phase 1: Foundation | 16 | 5 | 11 | 31% |
| Phase 2: Provider Router | 23 | 2 | 21 | 9% |
| Phase 3: Tool Engine | 21 | 4 | 17 | 19% |
| Phase 4: Proactive | 22 | 2 | 20 | 9% |
| Phase 5: Channels | 17 | 2 | 15 | 12% |
| Phase 6: Voice | 28 | 4 | 24 | 14% |
| Phase 7: Memory | 15 | 5 | 10 | 33% |
| Phase 9: Testing | 11 | 1 | 10 | 9% |
| Phase 10: Config | 12 | 2 | 10 | 17% |
| Phase 11: Swarm | 16 | 1 | 15 | 6% |
| Phase 12: UI | 13 | 3 | 10 | 23% |
| Phase 14: Visualization | 13 | 4 | 9 | 31% |
| Local Inference | 13 | 0 | 13 | 0% |
| **Icons & Branding** | **9** | **0** | **9** | **0%** |
| **TOTAL** | **219** | **35** | **184** | **16%** |

---

## 🔴 Critical Gaps — Prioritized

### P0 — Blockers

| # | Gap | Impact | E2E Test Needed |
|---|-----|--------|-----------------|
| 1 | **In-app icons invisible** — `colors.text.tertiary` at 35% opacity, `colors.accent.cyan` = `#1A5C6A` has ~1.5:1 contrast ratio on dark bg. Empty states, status badges, section headers, ChatInputBar actions all nearly invisible | Users can't see UI controls | `e2e_dock_icons_visible.yaml`, `e2e_config_section_icons.yaml`, `e2e_command_empty_states.yaml`, `e2e_chat_input_icons.yaml` |
| 2 | **Dual theme system conflict** — `src/config.ts` uses lime/lavender/violet palette, `src/theme/colors.ts` uses dark storm teal. Components mix both, creating visual inconsistency and icon color mismatches | Incoherent visual language, some icons bright (DeviceScreen) while others invisible (CommandScreen) | `e2e_theme_consistency.yaml` |
| 3 | **Storm palette not applied** — neither theme matches the dramatic night sky target. Config theme is lime/lavender, neural swarm theme is too dark | Visual identity doesn't match design intent | `e2e_storm_palette_visual.yaml` |
| 4 | **All Android icons are placeholders** — default Android robot launcher, generic Expo splash, system notification icons | App looks unbranded | `e2e_icon_launcher_not_default.yaml`, `e2e_notification_icon_check.yaml` |
| 5 | **No CI pipeline** — no GitHub Actions | Can't automate test runs | N/A — infra |
| 6 | **Channel incoming messages missing** — Telegram/Discord/Slack send-only | Can't test bidirectional | `e2e_telegram_receive.yaml` |
| 7 | **Session persistence thin** — 67 lines, no session types | Memory may not survive restarts | `e2e_session_persistence.yaml` |

### P1 — Important

| # | Gap | Impact | E2E Test Needed |
|---|-----|--------|-----------------|
| 8 | OAuth for all subscription providers | Copilot unusable; others need API keys | `e2e_copilot_oauth_flow.yaml` |
| 9 | Android SpeechRecognizer as free STT | Voice requires Deepgram key | `e2e_android_stt_fallback.yaml` |
| 10 | No UI Automator 2.4 tests | Can't test notifications, permissions, multi-window, performance | Full `androidTest/` setup |
| 11 | Deepgram STT on nova-2 (nova-3 available) | 54% worse accuracy | `e2e_deepgram_nova3.yaml` |
| 12 | No session encryption (SQLCipher) | Security concern | `e2e_encrypted_db_check.yaml` |
| 13 | E2E coverage at 16% | Most features untested end-to-end | All tests listed above |

### P2 — Nice to Have

| # | Gap | Impact |
|---|-----|--------|
| 12 | Local inference engines (LiteRT, ONNX, Nexa) | All non-text modalities need cloud |
| 13 | No embedded swarm connector | Requires external server |
| 14 | No token counter | Approximate context management |
| 15 | No channel formatters | Raw text to all channels |
| 16 | No DI framework | Manual wiring |
| 17 | Cloud TTS engines | Quality varies by device |

---

## Execution Plan — Next Steps

### Sprint 1: Branding & Palette (1-2 days)
1. Design and generate Guappa app icon (storm-themed, adaptive)
2. Replace all launcher icons across density buckets
3. Create custom notification icon set (11 vector drawables)
4. Replace all `android.R.drawable.*` references in Kotlin
5. Update `colors.ts` with storm palette
6. Fix all hardcoded colors in FloatingDock, SideRail, RootNavigator
7. Design and replace splash screen logo

### Sprint 2: E2E Test Coverage (2-3 days)
8. Create P0 Maestro E2E flows: session persistence, provider failover, onboarding
9. Set up UI Automator 2.4: dependencies, runner, `androidTest/` directory
10. Create UI Automator tests: permissions, notifications, memory store/recall
11. Create icon regression tests

### Sprint 3: Core Gaps (3-5 days)
12. Implement Android `SpeechRecognizer` as free STT fallback
13. Upgrade Deepgram STT from nova-2 to nova-3
14. Add channel `incoming()` Flow for Telegram (long polling)
15. Set up GitHub Actions CI pipeline

---

## E2E Tests — Master List

### Existing Tests ✅

| File | Coverage |
|------|----------|
| `e2e_full_agent_scenario.yaml` | Memory + call hook + recall |
| `e2e_agent_memory_call_voice.yaml` | Full 5-phase: memory → call → recall → voice |
| `e2e_phase1_memory_store.yaml` | Store fact |
| `e2e_phase2_call_hook_setup.yaml` | Call hook setup |
| `e2e_phase3_call_emulate_verify.yaml` | Call emulation verify |
| `e2e_phase4_memory_recall.yaml` | Memory recall |
| `e2e_phase5_voice_stt_blackhole.yaml` | Voice STT via BlackHole |
| `e2e_call_telegram.sh` | Call → Telegram notification |
| `test_scenario_incoming_call_telegram.yaml` | Call hook → Telegram |
| `test_scenario_memory_persistence.yaml` | Memory store → recall |
| `guappa_voice_full_flow.yaml` | Voice mic tap → listen → stop |
| `voice_interruptible_smoke.yaml` | Voice with Deepgram |
| `e2e_voice_swarm_emotion.yaml` | Swarm emotion on voice |
| `live_openrouter_chat.yaml` | Real API chat |

### Needed Tests 🚫

**P0 — Must Have**

| File | Phase | What It Tests |
|------|-------|---------------|
| `e2e_icon_launcher_not_default.yaml` | Icons | Verify launcher icon is not default Android robot |
| `e2e_notification_icon_check.yaml` | Icons | Trigger notification → verify custom icon |
| `e2e_splash_screen_check.yaml` | Icons | Cold launch → screenshot splash |
| `e2e_storm_palette_visual.yaml` | Palette | Screenshot all screens → verify dark storm aesthetic |
| `e2e_session_persistence.yaml` | Phase 1 | Send message → kill → reopen → verify |
| `e2e_onboarding_flow.yaml` | Phase 12 | Fresh install → complete all 4 steps |
| `AgentMemoryTest.kt` | Phase 7 | UI Automator: store → recall with screenshots |
| `PermissionFlowTest.kt` | Phase 3 | UI Automator: `watchFor(PermissionDialog)` |
| `IncomingCallHookTest.kt` | Phase 4 | UI Automator: call hook + shell emulation |

**P1 — Important**

| File | Phase | What It Tests |
|------|-------|---------------|
| `e2e_local_llm_chat.yaml` | Local | Download GGUF → chat → verify |
| `e2e_local_whisper_stt.yaml` | Voice | Whisper STT on-device |
| `e2e_android_stt_fallback.yaml` | Voice | No API key → SpeechRecognizer fallback |
| `e2e_builtin_tts_response.yaml` | Voice | TTS speaks response |
| `e2e_brave_web_search.yaml` | Tools | Web search tool |
| `e2e_telegram_send_channel.yaml` | Channels | Send to Telegram |
| `e2e_provider_hot_swap.yaml` | Config | Switch provider mid-chat |
| `e2e_api_key_persists.yaml` | Config | Key survives restart |
| `e2e_model_list_fetch.yaml` | Providers | Model dropdown populates |
| `e2e_swarm_connect.yaml` | Swarm | Connect to connector |
| `NotificationTest.kt` | Proactive | UI Automator: notification shade |
| `NotificationReplyTest.kt` | Proactive | UI Automator: inline reply |
| `StartupBenchmark.kt` | Perf | Macrobenchmark: cold/warm/hot TTFD |
| `BaselineProfileGenerator.kt` | Perf | Generate AOT profile |

**P2 — Nice to Have**

| File | Phase | What It Tests |
|------|-------|---------------|
| `e2e_copilot_oauth_flow.yaml` | Providers | GitHub device code OAuth |
| `e2e_appfunctions_control.yaml` | Tools | AppFunctions API |
| `e2e_memory_export_import.yaml` | Memory | Export/import |
| `MultiWindowTest.kt` | UI | Split-screen agent |
| `ChatJankBenchmark.kt` | Perf | Scroll jank measurement |
| `e2e_wake_word.yaml` | Voice | "Hey Guappa" via BlackHole |
| `e2e_geofence_transition.yaml` | Proactive | Location trigger |
| `e2e_emotion_palette_colors.yaml` | Swarm | Screenshot each emotion state |
