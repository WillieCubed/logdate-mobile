package app.logdate.feature.onboarding.ui


data class AudioRecorderUiState(
    val currentTranscription: TranscriptionState = TranscriptionState(),
    val isRecording: Boolean = false,
    val recordedAudio: String? = null,
)

data class TranscriptionState(
    val transcription: String = "",
)