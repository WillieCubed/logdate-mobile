package app.logdate.feature.onboarding.ui

import android.net.Uri

data class AudioRecorderUiState(
    val currentTranscription: TranscriptionState = TranscriptionState(),
    val isRecording: Boolean = false,
    val recordedAudio: Uri? = null,
)

data class TranscriptionState(
    val transcription: String = "",
)