# Audio Memory Experience Design

## Problem Statement

The current audio entry experience in LogDate feels MVP-ish. It functions as a voice recorder rather than a first-class memory medium. The implementation uses basic `MediaPlayer` on Android, has generic waveform visualization (evenly-spaced bars, not actual audio shape), and lacks the craft and attention to detail that would make audio memories feel special.

Audio should not be a substitute for text - it should be its own distinct experience for preserving memories.

## Design Philosophy

### Audio as Expandable Time Capsule

Each audio memory is a **distinct object** - not an inline media player, but a tangible thing with its own identity. The critical interaction is **expansion**: every audio memory can always be expanded to a larger, more immersive view.

However, context matters. Sometimes you want the full time-capsule experience; sometimes you just need to hear a quick recording. The design must support both without forcing one mode.

### Unique Identity from Context, Not Content

Each audio memory should look and feel unique, but that uniqueness comes from **contextual metadata** - when and where it was recorded - not from user customization or content analysis.

Why this choice:
- Location and time are already captured automatically
- Creates visual variety without user effort
- The memory visually "belongs" to its moment
- A beach recording should feel different than a coffee shop recording

### Restraint Over Flash

The design should be confident and polished, not trying to impress. The waveform building in real-time during recording is expressive enough - it doesn't need additional decoration.

**Specific constraints:**
- Never scale content during transitions. Only change container size; content reflows naturally. Scaling distorts and feels cheap.
- No pulsing rings, animated decorations, or gratuitous effects.

## Three-State Model

### State 1: Collapsed

Compact inline block within the journal entry.

**Shows:**
- Mini waveform (actual audio shape, stylized)
- Duration
- Contextual color tint (derived from time/location)

**Behavior:**
- Tap → Spatial expansion
- Long-press → Surface elevation

### State 2: Expanded

Audio-forward view with controls and supporting context. **Two variants to implement and test:**

#### Variant A: Spatial Expansion

The block grows in-place within the journal flow. Feels like unfolding a physical object.

**Characteristics:**
- Container grows; waveform and controls reflow to fit
- Stays inline with surrounding content
- Good for quick review, staying in the editing flow
- Controls animate in (staggered fade + slide from bottom)
- Corner radius transitions from pill to rounded rect

**Transition:** ~350ms, spring physics (medium damping, low stiffness)

**Exit:** Tap outside or swipe down → collapse

#### Variant B: Surface Elevation

The block lifts and floats above content. Like picking up a photograph to look closer.

**Characteristics:**
- Card floats with elevated shadow
- Scrim dims background (40-60% opacity)
- Slight background blur if performance allows
- Creates separation from journal content
- Better for focused listening

**Transition:** ~300ms, shadow grows, scrim fades in

**Exit:** Swipe down → collapse or return to spatial

#### Why Two Variants

We don't know which feels right until we build and test both. They may serve different use cases:
- Spatial for quick inline interaction
- Elevation for focused listening

Or one may clearly win. Build both, test in practice, decide based on how they feel.

#### Expanded View Contents

Both variants show:
- Full waveform (stylized bezier rendering of actual audio shape)
- Play/pause button with morph animation
- Scrubbing via waveform drag
- Progress indicator (filled portion of waveform)
- Context bar: timestamp, location chip, duration
- Transcription (if available)

**From expanded, user can:**
- Swipe up (or dedicated button) → Immersive mode
- Swipe down / tap outside → Collapse

### State 3: Immersive

Near full-screen, minimal chrome. For when you want to just *be* in the audio.

**Characteristics:**
- Edge-to-edge waveform
- Controls hidden by default, appear on tap, auto-hide after 3s
- Context completely hidden (or minimal status bar)
- Background transitions to solid color from contextual palette
- System bars dim/hide

**Transition:** ~400ms, controls fade out, waveform expands to edges

**Exit:** Swipe down → back to elevated state

### State Progression

```
Collapsed ──tap──→ Spatial Expansion ──swipe up──→ Immersive
    ↑                     │                            │
    └─────────────────────┴────────swipe down──────────┘

Collapsed ─long press─→ Surface Elevation ──swipe up──→ Immersive
                              │                            │
                              └───────swipe down───────────┘
```

## Visual Identity System

### Waveform Rendering

**Current state:** Evenly-spaced vertical bars. Generic, doesn't show actual audio shape.

**Target state:** Actual audio amplitude data, rendered with stylized curves.

**Approach:**
1. Extract amplitude samples from audio file (200-500 points) on save
2. Normalize to 0.0-1.0 range
3. Render using quadratic bezier curves (smoothed, not jagged)
4. Vertical gradient fill using contextual palette (darker at base, lighter at peaks)

**During playback:**
- Filled portion (played) has higher saturation/brightness
- Subtle glow on peaks near playhead
- Playhead: vertical line with soft glow, slight overshoot animation when scrubbing stops

**Why actual audio shape matters:** Each recording looks distinct because it *is* distinct. The waveform becomes a visual fingerprint of that specific memory.

### Color Derivation

Colors are computed from metadata on save and cached with the audio entry.

#### Time-of-Day Palette (Base)

Palettes are relative to **sunrise and sunset** at the recording location, not fixed clock times. This means a morning recording in winter looks like morning, even if it's 8am.

| Time Period | Definition | Palette Character |
|-------------|------------|-------------------|
| Dawn | Sunrise ± 30min | Soft pink/orange, transitional |
| Morning | Sunrise+30min → Midpoint to solar noon | Warm amber tones, soft morning light |
| Midday | Around solar noon (±2hrs) | Bright, clear, neutral tones |
| Afternoon | Midpoint from solar noon → Sunset-1hr | Warm but flattening, relaxed feel |
| Golden hour | Sunset-1hr → Sunset | Deep orange, golden warmth |
| Evening | Sunset → Sunset+2hrs | Purple/blue transition, settling |
| Night | Sunset+2hrs → Sunrise-30min | Deep blues, muted, intimate tones |

Requires location coordinates + date → sunrise/sunset calculation. Use existing algorithm or library.

The memory visually evokes *when* it happened - tied to actual daylight, not arbitrary clock times.

#### Location Type (Accent Modifier)

| Location Type | Accent Treatment |
|---------------|------------------|
| Nature/outdoors | Green accents woven in |
| Urban/city | Cool gray/blue tones |
| Home | Warm neutrals, comfortable feel |
| Transit | Muted, transient feel |

The memory visually evokes *where* it happened.

#### Palette Structure

Each palette provides:
- Waveform gradient (start/end colors)
- Played portion fill color
- Accent color (playhead, UI elements)
- Immersive mode background

## Interaction Design

### Scrubbing: Magnetic Moments

**Goal:** Intelligent assistance without restricting control. The waveform has "texture" you can feel.

#### Segment Detection

On audio save, analyze amplitude to detect interesting points:
- **Speech onset**: Amplitude rise after silence (someone starts talking)
- **Significant pauses**: Silence gaps > 500ms
- **Volume peaks**: Notably loud moments

Store as timestamp array with segment type.

#### Scrubbing Behavior

| Action | Result |
|--------|--------|
| Drag playhead | Moves continuously, magnetic snap near segments |
| Drag past snap point | Gentle resistance, then releases (not hard stops) |
| Tap on waveform | Jump directly to that position (no snapping) |
| Release during drag | Slight overshoot animation, settles |

#### Haptic Feedback (Android 16+ APIs)

| Moment | Haptic |
|--------|--------|
| Touch down on waveform | Standard tap feedback |
| Dragging | Subtle continuous texture |
| Crossing snap point | Distinct tick |
| Snapping to segment | Slightly stronger tick |
| Releasing | Settling feedback |

The haptics should feel playful but subtle - communicating texture without being annoying or distracting.

**Target APIs:**
- `VibrationEffect.Composition` for building custom haptic sequences
- `VibrationEffect.startComposition()` with chained `.addPrimitive()` calls
- Primitives: `PRIMITIVE_TICK`, `PRIMITIVE_LOW_TICK`, `PRIMITIVE_CLICK`, `PRIMITIVE_SLOW_RISE`, `PRIMITIVE_QUICK_FALL`
- Scale parameter (0.0-1.0) for intensity control per primitive
- `HapticFeedbackConstants.GESTURE_START`, `GESTURE_END` for gesture lifecycle
- Android 16's `HapticFeedbackConstants.DRAG_START`, `SEGMENT_TICK`, `SEGMENT_FREQUENT_TICK` for scrubbing
- Check `Vibrator.areAllPrimitivesSupported()` before using advanced compositions
- Fallback to `VibrationEffect.createOneShot()` or `HapticFeedbackConstants.CLOCK_TICK` on older devices

### Recording Experience

**Principle:** Recording mirrors playback in visual language, but stays focused and minimal during capture.

#### Visual Treatment

- Waveform draws left-to-right in real-time as audio is captured
- Same stylized bezier rendering as playback
- Same contextual color palette (computed from current time/location)
- The memory is being "written" visually as you speak

#### UI During Recording

Minimal, focused - no distractions while capturing a thought:
- Large, clear record/stop control
- Live duration counter
- Real-time waveform (the visualization itself shows you're recording)
- Current context hint (location, time) - subtle, not prominent

**No:** Pulsing rings, animated decorations, flashy effects. The waveform building is expressive enough.

#### On Recording Complete

- Waveform settles with slight bounce (spring animation)
- Contextual color palette applies
- Segment detection runs in background
- Amplitude data extracted and stored

## Technical Direction

### Media3 Migration

**Current:** `android.media.MediaPlayer` - basic, synchronous, limited features

**Target:** `androidx.media3.exoplayer.ExoPlayer`

**Why:**
- Proper audio focus handling (duck, pause for interruptions)
- Better state management
- Gapless playback capability
- More consistent behavior across Android versions

**Scope:** Replace `AndroidAudioPlaybackManager` implementation while keeping the same `AudioPlaybackManager` interface for common code.

### Data Pipeline

On audio save:
```
Audio File → Amplitude Extraction → Normalization → Storage
                    ↓
            Segment Detection → Segment Storage
                    ↓
            Palette Computation → Palette Storage
```

All processing happens once on save. Display is instant from cached data.

### Storage Requirements

New metadata to store per audio entry:
- Waveform amplitude samples (200-500 floats)
- Detected segments (timestamp + type pairs)
- Computed palette colors
- Daylight period classification

### Performance Targets

- Amplitude extraction: <500ms for typical voice memo
- Waveform rendering: 60fps during transitions
- Memory: ~1-2KB additional storage per entry

## Gap Analysis

### Must Build

| Component | Current State |
|-----------|---------------|
| Media3 playback | Using MediaPlayer |
| Amplitude extraction | None |
| Bezier waveform rendering | Vertical bars |
| Segment detection | None |
| Magnetic scrubbing | Basic seek |
| Haptic compositions | None |
| Contextual color system | None |
| Sunrise/sunset calculation | None |
| Surface elevation mode | None |
| Immersive mode | None |
| Audio metadata storage | Minimal |

### Refine Existing

| Component | Current State |
|-----------|---------------|
| Spatial expansion | Uses shared elements - ensure container-only resize |
| Play/pause morph | Basic animation - tune spring curves |
| Recording UI | Has pulsing effects - simplify, remove decoration |

### Key Files

**Modify:**
- `AudioBlockContent.kt` - State management, transitions
- `AudioWaveformComponent.kt` - Replace rendering approach
- `AndroidAudioPlaybackManager.kt` - Media3 migration

**Create:**
- Audio metadata entity + storage
- Amplitude extractor
- Segment detector
- Palette generator
- Haptic controller
- Immersive audio screen

## Out of Scope

Explicitly not building:
- Playback speed controls
- Audio effects/filters
- Frequency spectrum visualization
- Cross-fade between memories
- Audio editing/trimming
- Waveform zoom

## Testing Approach

### Transition Variants

Build both spatial expansion and surface elevation. Evaluate:
- Which feels more natural for quick interactions?
- Which is better for focused listening?
- Do both have a place, or does one clearly win?
- How do they feel on different screen sizes?

### Haptic Tuning

Must test on physical devices:
- Are snap point ticks noticeable but not jarring?
- Is drag texture subtle enough?
- Does it feel playful or annoying?

### Visual Coherence

- Do daylight-based palettes feel distinct but cohesive?
- Do location accents add value without clashing?
- Does it work with system dark/light mode?
