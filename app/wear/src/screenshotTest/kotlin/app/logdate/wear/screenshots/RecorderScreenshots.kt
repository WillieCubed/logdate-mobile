package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.domain.today.TodaySummary
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.recorder.LiveMode
import app.logdate.wear.v2.recorder.RecorderFailure
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.HandoffState
import app.logdate.wear.v2.today.PhoneCard
import app.logdate.wear.v2.today.TodayControls
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayUiState
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Every recorder state on Today, on the graphite theme screenshots can render. */
class RecorderScreenshots {
    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R01_Holding() {
        RecorderScene(RecorderState.Live(LiveMode.HOLDING, lockProgress = 0.4f, elapsed = 7.seconds, levels = levels))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R02_Locked() {
        RecorderScene(locked)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R03_Paused() {
        RecorderScene(locked.copy(paused = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R04_PausedByAnotherApp() {
        RecorderScene(locked.copy(paused = true, pausedByInterruption = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R05_ConfirmDiscard() {
        RecorderScene(locked.copy(confirmingDiscard = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R06_Saving() {
        RecorderScene(RecorderState.Saving)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R07_SavedWithFeelings() {
        RecorderScene(RecorderState.Saved(noteId, 42.seconds, feelingsOffered = true))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R08_SavedFeelingChosen() {
        RecorderScene(RecorderState.Saved(noteId, 42.seconds, feelingsOffered = false, feeling = Feeling.GOOD))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R09_TooShort() {
        RecorderScene(RecorderState.TooShort)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R10_MicrophoneRefused() {
        RecorderScene(RecorderState.Failed(RecorderFailure.MICROPHONE_DENIED))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R11_MicrophoneOff() {
        RecorderScene(RecorderState.Failed(RecorderFailure.MICROPHONE_BLOCKED))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R12_StorageFull() {
        RecorderScene(RecorderState.Failed(RecorderFailure.NOT_ENOUGH_STORAGE))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R13_RecorderUnavailable() {
        RecorderScene(RecorderState.Failed(RecorderFailure.RECORDER_UNAVAILABLE))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R14_SaveFailed() {
        RecorderScene(RecorderState.Failed(RecorderFailure.SAVE_FAILED))
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun R15_RecordingLost() {
        RecorderScene(RecorderState.Failed(RecorderFailure.RECORDING_LOST))
    }
}

private val levels = List(50) { index -> ((index * 37) % 100) / 100f }
private val locked = RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f, elapsed = 42.seconds, levels = levels)
private val noteId = Uuid.parse("6f1c2b8e-3a1d-4e5f-9a7b-2c4d6e8f0a1b")
private val today =
    TodayUiState.Ready(
        date = LocalDate(2026, 10, 10),
        timeZone = TimeZone.UTC,
        summary = TodaySummary.Empty,
        feeling = null,
        campfire = null,
        phoneCard = PhoneCard.None,
    )

@Composable
private fun RecorderScene(state: RecorderState) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            TodayPage(
                state = today,
                handoff = HandoffState.IDLE,
                onGetPhoneApp = {},
                onHandoffDismissed = {},
                controls = TodayControls(recorder = TodayRecorder(state, PreviewRecorder, PreviewRecorder), onMark = {}, onAdd = {}),
                timeSource = RecorderTimeSource,
            )
        }
    }
}

/** A fixed clock reading, so the time text renders the same on every run. */
private object RecorderTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
