package app.logdate.feature.editor.ui.editor

import kotlin.time.Duration

/**
 * Represents the possible recording states of an audio block
 */
enum class RecordingState {
    /** No recording in progress or completed */
    INACTIVE,
    
    /** Currently recording audio */
    RECORDING,
    
    /** Recording paused but not finalized */
    PAUSED,
    
    /** Recording completed and saved */
    COMPLETED,
    
    /** Recording in process of being saved */
    PROCESSING
}

/**
 * Represents the possible playback states of an audio block
 */
enum class PlaybackState {
    /** Not playing */
    STOPPED,
    
    /** Currently playing */
    PLAYING,
    
    /** Playback paused */
    PAUSED,
    
    /** Playback buffering/loading */
    LOADING
}

// Extension functions for AudioBlockUiState from EntryBlockUiState.kt

/**
 * Returns the recording state based on whether the block has a saved audio URI.
 */
val AudioBlockUiState.recordingState: RecordingState
    get() = if (hasContent()) RecordingState.COMPLETED else RecordingState.INACTIVE
