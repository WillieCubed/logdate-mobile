package app.logdate.feature.editor.ui.state

import androidx.compose.runtime.Immutable

/**
 * Represents the state and callbacks for the audio recording portion of the editor.
 * Acts as a focused bus between the ViewModel and audio editing components.
 *
 * @property onAudioRecordingStarted Callback for when audio recording begins
 * @property onAudioRecordingStopped Callback for when audio recording stops
 * @property onAudioRecordingSaved Callback for when an audio recording is saved
 * @property onCreateAudioBlock Callback to create a new audio block with the URI
 */
@Immutable
data class EditorRecorderState(
    val onAudioRecordingStarted: () -> Unit,
    val onAudioRecordingStopped: () -> Unit,
    val onAudioRecordingSaved: (String) -> Unit,
    val onCreateAudioBlock: (String) -> Unit
)