package app.logdate.feature.editor.ui.audio.expansion

/**
 * Represents the three expansion states for audio memory display.
 *
 * The expansion flow is designed for progressive engagement:
 * 1. COLLAPSED: Minimal view in timeline (waveform + timestamp)
 * 2. SPATIAL_EXPANDED: First tap - spatial expansion with context
 * 3. ELEVATED: Second tap - floats above content with controls
 * 4. IMMERSIVE: Long press from elevated - full screen experience
 */
enum class AudioExpansionState {
    /**
     * Default state in timeline.
     * Shows: condensed waveform, duration, timestamp badge
     */
    COLLAPSED,

    /**
     * First expansion state after tap.
     * Shows: full waveform, play/pause, palette-colored background
     * Spatial: Card lifts slightly, expands horizontally
     */
    SPATIAL_EXPANDED,

    /**
     * Second expansion after tap on SPATIAL_EXPANDED.
     * Shows: Controls, scrubber, segment markers
     * Spatial: Card elevates significantly, dims background
     */
    ELEVATED,

    /**
     * Full-screen immersive mode (long press from ELEVATED).
     * Shows: Large waveform, auto-hiding controls, contextual background
     * Spatial: Fills screen, edge-to-edge palette gradient
     */
    IMMERSIVE
}
