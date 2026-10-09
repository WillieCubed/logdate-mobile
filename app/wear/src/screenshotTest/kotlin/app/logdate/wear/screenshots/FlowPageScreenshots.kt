package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowUiState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** Every state of Home's moment list, on the graphite theme. */
class FlowPageScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_FlowLoading() {
        FlowScene(FlowUiState.Loading)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_FlowEmpty() {
        FlowScene(FlowUiState.Ready(timeZone = flowZone, moments = emptyList()))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_FlowMoments() {
        FlowScene(FlowUiState.Ready(timeZone = flowZone, moments = recentMoments))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_FlowFailed() {
        FlowScene(FlowUiState.Failed)
    }
}

private val flowZone = TimeZone.currentSystemDefault()

private fun moment(
    day: Int,
    hour: Int,
): Instant = LocalDateTime(2026, 10, day, hour, 15).toInstant(flowZone)

private val recentMoments =
    listOf(
        JournalNote.Audio(mediaRef = "audio/walk.m4a", durationMs = 42_000, creationTimestamp = moment(3, 16), lastUpdated = moment(3, 16)),
        JournalNote.Text(creationTimestamp = moment(3, 9), lastUpdated = moment(3, 9), content = "Coffee before anyone else was up"),
        JournalNote.Image(mediaRef = "photos/lake.jpg", creationTimestamp = moment(2, 18), lastUpdated = moment(2, 18)),
        JournalNote.Text(creationTimestamp = moment(1, 21), lastUpdated = moment(1, 21), content = "Long call with Mum about the trip"),
    )

@Composable
private fun FlowScene(state: FlowUiState) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = { TimeText(timeSource = FlowTimeSource) }) {
            FlowPage(state = state, timeSource = FlowTimeSource)
        }
    }
}

/** A fixed clock reading, so the time text renders the same on every run. */
private object FlowTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
