package app.logdate.screenshots.flows.flow06_rewind

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.rewind.ui.EmptyRewindContent
import app.logdate.feature.rewind.ui.RewindDetailUiState
import app.logdate.feature.rewind.ui.RewindPanelUiState
import app.logdate.feature.rewind.ui.BasicTextRewindPanelUiState
import app.logdate.feature.rewind.ui.NarrativeContextRewindPanelUiState
import app.logdate.feature.rewind.ui.SubtitledRewindPanelUiState
import app.logdate.feature.rewind.ui.BigStatisticRewindPanelUiState
import app.logdate.feature.rewind.ui.TextNoteRewindPanelUiState
import app.logdate.feature.rewind.ui.TransitionRewindPanelUiState
import app.logdate.feature.rewind.ui.RewindPanelBackgroundSpec
import app.logdate.feature.rewind.ui.RewindScreenContent
import app.logdate.feature.rewind.ui.detail.RewindStoryContent
import app.logdate.feature.rewind.ui.overview.RewindHistoryUiState
import app.logdate.feature.rewind.ui.overview.RewindOverviewScreenUiState
import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.baseInstant
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

private val sampleRewindId = Uuid.parse("00000000-0000-0000-0000-000000000020")

private val samplePastRewinds = listOf(
    RewindHistoryUiState(uid = sampleRewindId, title = "Week of Feb 17", label = "Week 8", startDate = LocalDate(2025, 2, 17), endDate = LocalDate(2025, 2, 23)),
    RewindHistoryUiState(uid = Uuid.parse("00000000-0000-0000-0000-000000000021"), title = "Week of Feb 10", label = "Week 7", startDate = LocalDate(2025, 2, 10), endDate = LocalDate(2025, 2, 16)),
)

private val samplePreview = RewindPreviewUiState(
    message = "Your week in review",
    rewindId = sampleRewindId,
    label = "This Week",
    title = "A Week of New Beginnings",
    start = LocalDate(2025, 2, 17),
    end = LocalDate(2025, 2, 23),
    rewindAvailable = true,
)

// ─── Overview States ────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S01_RewindOverviewLoading() {
    ScreenshotTheme {
        RewindScreenContent(
            state = RewindOverviewScreenUiState.Loading,
            onOpenRewind = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S02_RewindOverviewNotReady() {
    ScreenshotTheme {
        RewindScreenContent(
            state = RewindOverviewScreenUiState.NotReady(pastRewinds = emptyList()),
            onOpenRewind = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S03_RewindOverviewNotReadyGenerating() {
    ScreenshotTheme {
        RewindScreenContent(
            state = RewindOverviewScreenUiState.NotReady(
                pastRewinds = samplePastRewinds,
                isGeneratingRewind = true,
            ),
            onOpenRewind = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S04_RewindOverviewReady() {
    ScreenshotTheme {
        RewindScreenContent(
            state = RewindOverviewScreenUiState.Ready(
                pastRewinds = samplePastRewinds,
                mostRecentRewind = samplePreview,
            ),
            onOpenRewind = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun S05_RewindOverviewReadyDark() {
    ScreenshotTheme(darkTheme = true) {
        RewindScreenContent(
            state = RewindOverviewScreenUiState.Ready(
                pastRewinds = samplePastRewinds,
                mostRecentRewind = samplePreview,
            ),
            onOpenRewind = {},
        )
    }
}

// ─── Empty Rewind ───────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S06_RewindOverviewEmpty() {
    ScreenshotTheme {
        EmptyRewindContent(onTouchGrass = {})
    }
}

// ─── Rewind Panels ──────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S07_RewindPanelBasicText() {
    ScreenshotTheme {
        RewindStoryContent(
            panel = BasicTextRewindPanelUiState(
                text = "This week was about finding your rhythm in a new city.",
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S08_RewindPanelSubtitled() {
    ScreenshotTheme {
        RewindStoryContent(
            panel = SubtitledRewindPanelUiState(
                title = "Your Week",
                subtitle = "February 17 – 23, 2025",
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S09_RewindPanelBigStatistic() {
    ScreenshotTheme {
        RewindStoryContent(
            panel = BigStatisticRewindPanelUiState(
                title = "Steps Taken",
                statistic = "47,832",
                units = "steps",
                description = "You were more active than last week",
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S10_RewindPanelTextNote() {
    ScreenshotTheme {
        RewindStoryContent(
            panel = TextNoteRewindPanelUiState(
                sourceId = sampleRewindId,
                timestamp = baseInstant,
                content = "Finally made it to the California coast. The sunset was incredible.",
                dateFormatted = "Feb 19, 2025",
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S11_RewindPanelNarrativeContext() {
    ScreenshotTheme {
        RewindStoryContent(
            panel = NarrativeContextRewindPanelUiState(
                sourceId = sampleRewindId,
                timestamp = baseInstant,
                contextText = "You finally made it to the California coast after months of planning.",
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S12_RewindPanelTransition() {
    ScreenshotTheme {
        RewindStoryContent(
            panel = TransitionRewindPanelUiState(
                sourceId = sampleRewindId,
                timestamp = baseInstant,
                transitionText = "Later that evening...",
            ),
        )
    }
}
