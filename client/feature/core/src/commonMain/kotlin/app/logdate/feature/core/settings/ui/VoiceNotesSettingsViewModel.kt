package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.audio.download.ModelDownloadStatus
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.transcription.TranscriptionService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the voice notes settings screen. Combines the live download status
 * of both on-device audio models — Whisper for transcript refinement, CED
 * for ambient sound detection — into a single UI state the screen renders
 * as two reactive rows.
 */
class VoiceNotesSettingsViewModel(
    private val transcriptionService: TranscriptionService,
    private val audioTaggingService: AudioTaggingService,
) : ViewModel() {
    /** UI state for one downloadable on-device model row. */
    data class ModelRowState(
        val status: ModelDownloadStatus,
    )

    data class UiState(
        val transcription: ModelRowState = ModelRowState(ModelDownloadStatus.Idle),
        val tagging: ModelRowState = ModelRowState(ModelDownloadStatus.Idle),
    )

    val uiState: StateFlow<UiState> =
        combine(
            transcriptionService.offlineModelDownloadStatus,
            audioTaggingService.modelDownloadStatus,
        ) { transcription, tagging ->
            UiState(
                transcription = ModelRowState(transcription),
                tagging = ModelRowState(tagging),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(),
        )

    fun downloadTranscriptionModel() {
        transcriptionService.startOfflineModelDownload()
    }

    fun downloadTaggingModel() {
        audioTaggingService.startModelDownload()
    }
}
