package app.logdate.feature.editor.ui.audio.expansion

/**
 * Represents the four expansion states for audio memory display.
 *
 * States are organized into two interaction groups:
 *
 * **Inline group** (within the timeline/editor flow):
 * - [COLLAPSED] ↔ [SPATIAL_EXPANDED]: Bi-directional toggle, auto-expands on
 *   playback start (configurable via `expandOnPlayback` parameter).
 *
 * **Overlay group** (modal, above the timeline):
 * - [ELEVATED] → [IMMERSIVE]: One-directional progression.
 *
 * **Cross-group transitions:**
 * - Long press from any inline state → [ELEVATED] (hold-to-preview, like Instagram grid preview)
 * - Release / dismiss from [ELEVATED] → return to previous inline state
 * - Swipe up or explicit action from [ELEVATED] → [IMMERSIVE]
 * - Explicit fullscreen action from any inline state → [IMMERSIVE] directly
 * - Close from [IMMERSIVE] → return to previous inline state
 */
enum class AudioExpansionState {
    /**
     * Default compact state in timeline.
     * Shows: mini waveform, duration, play button.
     */
    COLLAPSED,

    /**
     * In-place expansion within the timeline flow.
     * Shows: full waveform with palette colors, play/pause, context bar.
     * Triggered by playback start (if `expandOnPlayback=true`) or explicit tap.
     */
    SPATIAL_EXPANDED,

    /**
     * Floating preview card above content (overlay).
     * Shows: large waveform with segment markers, full controls, scrubber, quick actions.
     * Triggered by long press from any inline state. Transient: release dismisses.
     */
    ELEVATED,

    /**
     * Full-screen immersive experience (overlay).
     * Shows: edge-to-edge waveform, auto-hiding controls, palette gradient background.
     * Triggered by swipe up from [ELEVATED] or explicit fullscreen action.
     */
    IMMERSIVE,
}

/** Whether this state renders inline within the timeline/editor flow. */
val AudioExpansionState.isInline: Boolean
    get() =
        this == AudioExpansionState.COLLAPSED ||
            this == AudioExpansionState.SPATIAL_EXPANDED

/** Whether this state renders as a modal overlay above the timeline. */
val AudioExpansionState.isOverlay: Boolean
    get() =
        this == AudioExpansionState.ELEVATED ||
            this == AudioExpansionState.IMMERSIVE
