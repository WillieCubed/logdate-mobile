package app.logdate.wear.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.presentation.home.WearHomeContent
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.presentation.home.WearHomeUiState
import app.logdate.wear.presentation.recording.RecordingError
import app.logdate.wear.presentation.recording.RecordingPhase
import app.logdate.wear.presentation.recording.RecordingUiState
import app.logdate.wear.presentation.theme.LogDateTheme
import java.util.Random
import com.android.tools.screenshot.PreviewTest

class WearHomeScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_HomeEmpty() {
        OnWatchSurface {
            WearHomeContent(
                homeState =
                    WearHomeUiState(
                        greeting = "Good morning",
                        entryCount = 0,
                        entryCountLabel = "No entries yet",
                    ),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_HomeWithEntries() {
        OnWatchSurface {
            WearHomeContent(
                homeState =
                    WearHomeUiState(
                        greeting = "Good afternoon",
                        entryCount = 5,
                        entryCountLabel = "5 entries today",
                    ),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_HomeSingleEntry() {
        OnWatchSurface {
            WearHomeContent(
                homeState =
                    WearHomeUiState(
                        greeting = "Good evening",
                        entryCount = 1,
                        entryCountLabel = "1 entry today",
                    ),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_HomeGestureHint() {
        HomeWithRecorder(RecordingUiState(showGestureHint = true))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S05_HomeStarting() {
        HomeWithRecorder(RecordingUiState(phase = RecordingPhase.STARTING))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S06_HomeRecordingHold() {
        HomeWithRecorder(
            RecordingUiState(phase = RecordingPhase.RECORDING, recordingDurationMs = 7_000, audioLevels = sampleLevels()),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S16_HomeDiscardConfirm() {
        HomeWithRecorder(
            RecordingUiState(
                phase = RecordingPhase.RECORDING,
                recordingDurationMs = 84_000,
                audioLevels = sampleLevels(),
                isLatched = true,
                confirmingDiscard = true,
            ),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S07_HomeRecordingLatched() {
        HomeWithRecorder(
            RecordingUiState(
                phase = RecordingPhase.RECORDING,
                recordingDurationMs = 84_000,
                audioLevels = sampleLevels(),
                isLatched = true,
            ),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S08_HomePaused() {
        HomeWithRecorder(RecordingUiState(phase = RecordingPhase.PAUSED, recordingDurationMs = 84_000, isLatched = true))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S09_HomePausedByInterruption() {
        HomeWithRecorder(
            RecordingUiState(
                phase = RecordingPhase.PAUSED,
                recordingDurationMs = 84_000,
                isLatched = true,
                pausedByInterruption = true,
            ),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S10_HomeSavedWithUndo() {
        HomeWithRecorder(
            RecordingUiState(
                phase = RecordingPhase.SAVED,
                savedDurationMs = 84_000,
                undoableNoteId = kotlin.uuid.Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                saveFeedback = SaveFeedback.SYNCING_TO_PHONE,
            ),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S11_HomeTooShort() {
        HomeWithRecorder(RecordingUiState(phase = RecordingPhase.TOO_SHORT))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S12_HomeErrorMicrophone() {
        HomeWithRecorder(RecordingUiState(phase = RecordingPhase.ERROR, error = RecordingError.MICROPHONE_PERMISSION_DENIED))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S13_HomeErrorStorage() {
        HomeWithRecorder(RecordingUiState(phase = RecordingPhase.ERROR, error = RecordingError.NOT_ENOUGH_STORAGE))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S14_HomeErrorSaveFailed() {
        HomeWithRecorder(RecordingUiState(phase = RecordingPhase.ERROR, error = RecordingError.SAVE_FAILED))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S15_HomeErrorRecordingLost() {
        HomeWithRecorder(RecordingUiState(phase = RecordingPhase.ERROR, error = RecordingError.RECORDING_LOST))
    }
}

@Composable
private fun HomeWithRecorder(recordingState: RecordingUiState) {
    OnWatchSurface {
        WearHomeContent(
            homeState = WearHomeUiState(greeting = "Good morning"),
            recordingState = recordingState,
        )
    }
}

/** The watch's black background, so the previews show the contrast the screen is designed for. */
@Composable
internal fun OnWatchSurface(content: @Composable () -> Unit) {
    LogDateTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) { content() }
    }
}

/** A fixed, varied level history so the waveform renders the same on every run. */
private fun sampleLevels(): List<Float> {
    val random = Random(7)
    return List(40) { 0.1f + random.nextFloat() * 0.8f }
}
