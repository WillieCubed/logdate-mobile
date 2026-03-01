# Audio Shared Element Transitions Design

> Addendum to `2026-01-18-audio-memory-experience-design.md`. Replaces the "Three-State Model" and "State Progression" sections.

## Revised State Machine

The original design described four sequential states (tap → tap → tap → long press). In practice, this creates friction. The revised model organizes states into **two interaction groups** with gesture-driven crossings.

### Interaction Groups

**Inline Group** (within the timeline/editor flow):
- `COLLAPSED` - Compact row with mini waveform, duration, play button
- `SPATIAL_EXPANDED` - In-place expansion with full waveform, controls, context

These two states toggle bi-directionally. The transition is automatic on playback start (configurable via `expandOnPlayback: Boolean` parameter) and reverses when playback completes or user taps collapse.

**Overlay Group** (above the timeline, modal):
- `ELEVATED` - Floating preview card with scrim, gesture-initiated
- `IMMERSIVE` - Full-screen experience, edge-to-edge

Elevated is a transient preview state (like Instagram's long-press on grid → post preview). Immersive is the full destination.

### Transition Map

```
              expandOnPlayback
  COLLAPSED ←────────────────→ SPATIAL_EXPANDED
      │                                │
      │ long press             long press│
      │                                │
      └──────────┐      ┌──────────────┘
                 ▼      ▼
              ┌──────────────┐
              │   ELEVATED   │
              │  (preview)   │
              └──────┬───────┘
                     │
              swipe up / tap expand
                     │
                     ▼
              ┌──────────────┐
              │  IMMERSIVE   │
              └──────────────┘

  Also supported:
  - Any inline state → IMMERSIVE directly (via explicit fullscreen action)
  - IMMERSIVE → previous inline state (close)
  - ELEVATED → previous inline state (release / dismiss)
```

### Transition Details

| From | To | Trigger | Animation |
|------|----|---------|-----------|
| COLLAPSED | SPATIAL_EXPANDED | Playback starts (if `expandOnPlayback=true`) | Spring expand, 350ms, container grows in-place |
| SPATIAL_EXPANDED | COLLAPSED | Playback ends / tap collapse | Spring contract, 300ms |
| Any inline | ELEVATED | Long press (hold) | Card lifts with spring, scrim fades in 200ms |
| ELEVATED | Previous inline | Release / tap scrim | Card settles back, scrim fades out |
| ELEVATED | IMMERSIVE | Swipe up / tap expand | Container morphs to fullscreen, 400ms |
| Any inline | IMMERSIVE | Explicit action (fullscreen button) | Container morphs to fullscreen, 400ms |
| IMMERSIVE | Previous inline | Close / swipe down | Reverse morph, 350ms |

### Why Elevated is Gesture-Oriented

The Elevated state serves a specific UX purpose: **quick preview without commitment**. Like Instagram's long-press-to-preview on a grid photo, the user holds to see more detail and releases to dismiss. This is distinct from Immersive, which is a deliberate full-screen experience.

Elevated shows:
- Larger waveform with segment markers
- Full playback controls (skip, scrub)
- Time/location context
- Quick actions (share, add to journal)

But it's inherently transient - optimized for "let me quickly check this" rather than "I want to sit with this memory."

---

## Shared Element Strategy

### Elements That Persist Across Transitions

Three UI elements maintain visual continuity using Compose's `SharedTransitionLayout` / `sharedElement` / `sharedBounds`:

#### 1. Container (`sharedBounds`)

The outer card boundary animates shape and size across states. Uses `sharedBounds` (not `sharedElement`) because the container is a boundary that clips child content.

| State | Shape | Size | Elevation |
|-------|-------|------|-----------|
| COLLAPSED | RoundedCornerShape(16.dp) | Full width, ~56dp height | 0dp |
| SPATIAL_EXPANDED | RoundedCornerShape(16.dp) | Full width, ~280dp height | 4dp |
| ELEVATED | RoundedCornerShape(24.dp) | Width - 48dp padding, auto height | 24dp |
| IMMERSIVE | RoundedCornerShape(0.dp) | Full screen | 0dp |

#### 2. Waveform (`sharedElement`)

The waveform visualization scales and transitions between rendering modes:

| State | Height | Renderer | Detail Level |
|-------|--------|----------|--------------|
| COLLAPSED | 32.dp | AudioWaveformComponent (bars) | 15 bars |
| SPATIAL_EXPANDED | 80.dp | BezierAudioWaveform | Full bezier |
| ELEVATED | 120.dp | BezierAudioWaveform + segments | Full bezier + markers |
| IMMERSIVE | 180.dp | BezierAudioWaveform + segments | Full bezier + markers |

The waveform uses a single shared element key (`"waveform_{blockId}"`) so it morphs continuously between states. In collapsed state, the bar-based waveform crossfades into the bezier waveform during the shared element transition.

#### 3. Play/Pause Button (`sharedElement`)

The `AnimatedPlayPauseButton` with `MorphingPlayPauseIcon` persists across all states:

| State | Size | Position |
|-------|------|----------|
| COLLAPSED | 40.dp | Left of waveform |
| SPATIAL_EXPANDED | 48.dp | Below waveform, left-aligned |
| ELEVATED | 64.dp | Center, below controls |
| IMMERSIVE | 80.dp | Center, auto-hiding |

The button itself handles play/pause icon morphing internally via `MorphingPlayPauseIcon`. The shared element transition handles the position/size change.

---

## Implementation Architecture

### SharedTransitionScope Hoisting

The current `AudioBlockContent.kt` creates its own `SharedTransitionLayout` internally. This won't work for transitions to Elevated/Immersive because those render in different composable scopes (overlay/dialog).

**Solution:** Hoist the `SharedTransitionScope` to a common ancestor:

```kotlin
// In the journal editor or entry screen
SharedTransitionLayout {
    // Timeline content (contains COLLAPSED and SPATIAL_EXPANDED)
    EntryContent(
        sharedTransitionScope = this@SharedTransitionLayout,
        ...
    )

    // Overlay content (contains ELEVATED and IMMERSIVE)
    AnimatedVisibility(visible = expansionState >= ELEVATED) {
        when (expansionState) {
            ELEVATED -> ElevatedAudioCard(
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this@AnimatedVisibility,
                ...
            )
            IMMERSIVE -> ImmersiveAudioScreen(
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this@AnimatedVisibility,
                ...
            )
        }
    }
}
```

### State Machine Implementation

```kotlin
@Stable
class AudioExpansionController(
    initialState: AudioExpansionState = AudioExpansionState.COLLAPSED,
    private val expandOnPlayback: Boolean = true,
) {
    var currentState by mutableStateOf(initialState)
        private set

    // Track the inline state to return to after overlay dismissal
    private var inlineState: AudioExpansionState = initialState

    fun onPlaybackStarted() {
        if (expandOnPlayback && currentState == AudioExpansionState.COLLAPSED) {
            inlineState = AudioExpansionState.SPATIAL_EXPANDED
            currentState = AudioExpansionState.SPATIAL_EXPANDED
        }
    }

    fun onPlaybackCompleted() {
        if (expandOnPlayback && currentState == AudioExpansionState.SPATIAL_EXPANDED) {
            inlineState = AudioExpansionState.COLLAPSED
            currentState = AudioExpansionState.COLLAPSED
        }
    }

    fun onCollapseToggle() {
        when (currentState) {
            AudioExpansionState.COLLAPSED -> {
                inlineState = AudioExpansionState.SPATIAL_EXPANDED
                currentState = AudioExpansionState.SPATIAL_EXPANDED
            }
            AudioExpansionState.SPATIAL_EXPANDED -> {
                inlineState = AudioExpansionState.COLLAPSED
                currentState = AudioExpansionState.COLLAPSED
            }
            else -> {} // No-op in overlay states
        }
    }

    fun onLongPress() {
        // From any inline state → elevated
        if (currentState.isInline) {
            currentState = AudioExpansionState.ELEVATED
        }
    }

    fun onLongPressRelease() {
        // Return to previous inline state
        if (currentState == AudioExpansionState.ELEVATED) {
            currentState = inlineState
        }
    }

    fun onExpandToImmersive() {
        currentState = AudioExpansionState.IMMERSIVE
    }

    fun onDismissOverlay() {
        currentState = inlineState
    }
}

val AudioExpansionState.isInline: Boolean
    get() = this == AudioExpansionState.COLLAPSED ||
            this == AudioExpansionState.SPATIAL_EXPANDED

val AudioExpansionState.isOverlay: Boolean
    get() = this == AudioExpansionState.ELEVATED ||
            this == AudioExpansionState.IMMERSIVE
```

### Long-Press Preview Behavior

The Elevated state uses a hold-to-preview pattern. Implementation approach:

```kotlin
Modifier.pointerInput(Unit) {
    detectTapGestures(
        onLongPress = {
            controller.onLongPress()
            // The elevated card appears as an overlay
        }
    )
}

// On the elevated overlay:
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        // Wait for all pointers to be released
        waitForUpOrCancellation()
        controller.onLongPressRelease()
    }
}
```

For the "hold to preview" feel, the Elevated card should also support:
- Swipe up while holding → transition to IMMERSIVE (keeps the card, removes the release-to-dismiss behavior)
- Tap a "Keep Open" action → stay in ELEVATED without holding

---

## Palette Integration with Shared Elements

All expansion states use the same `AudioPalette` for visual coherence. The palette colors intensify as the state progresses:

| State | Palette Application |
|-------|-------------------|
| COLLAPSED | Subtle tint on waveform background (10% opacity) |
| SPATIAL_EXPANDED | Gradient background (15% opacity), colored waveform |
| ELEVATED | Stronger gradient (20% opacity), colored controls |
| IMMERSIVE | Full immersive background from `palette.immersiveBackground` |

This means the shared element transitions also involve a gradual color intensification, reinforcing the sense of "going deeper" into the memory.

---

## Files to Modify

| File | Change |
|------|--------|
| `AudioExpansionState.kt` | Update KDoc, add `isInline`/`isOverlay` extensions |
| `AudioBlockContent.kt` | Accept `SharedTransitionScope` from parent, remove internal `SharedTransitionLayout` |
| `ElevatedAudioCard.kt` | Accept `SharedTransitionScope`, add shared element keys, implement hold-to-preview |
| `ImmersiveAudioScreen.kt` | Accept `SharedTransitionScope`, add shared element keys |
| `SpatialExpandedAudioBlock.kt` | Accept `SharedTransitionScope`, add shared element keys |

| File | Create |
|------|--------|
| `AudioExpansionController.kt` | State machine with `expandOnPlayback` support |

## Design Constraints (Carried Over)

From the original design doc:
- **Never scale content during transitions.** Only change container size; content reflows naturally.
- **No pulsing rings, animated decorations, or gratuitous effects.**
- The waveform building in real-time during recording is expressive enough.
