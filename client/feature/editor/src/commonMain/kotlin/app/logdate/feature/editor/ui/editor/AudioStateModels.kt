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

// Extension functions for the existing AudioBlockUiState from EntryBlockUiState.kt

/**
 * Get the playback state based on the isPlaying property for backward compatibility
 */
val AudioBlockUiState.playbackState: PlaybackState
    get() = if (isPlaying) PlaybackState.PLAYING else PlaybackState.STOPPED

/**
 * For backward compatibility, always return INACTIVE or COMPLETED based on whether there is content
 */
val AudioBlockUiState.recordingState: RecordingState
    get() = if (hasContent()) RecordingState.COMPLETED else RecordingState.INACTIVE

/**
 * Determines if the block is in an active state (either playing or recording)
 */
val AudioBlockUiState.isActive: Boolean
    get() = isPlaying
