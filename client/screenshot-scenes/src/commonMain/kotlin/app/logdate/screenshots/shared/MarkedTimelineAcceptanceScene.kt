@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import app.logdate.client.domain.moments.MarkPhotoSuggestions
import app.logdate.client.media.MediaObject
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.feature.core.main.HomeRouteDestination
import app.logdate.feature.core.main.HomeWorkspaceAccountAction
import app.logdate.feature.core.main.HomeWorkspaceComposition
import app.logdate.feature.core.main.WatchRecordingContent
import app.logdate.feature.core.sync.SyncPresentation
import app.logdate.feature.timeline.ui.marks.MarkedMomentContent
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineUiState
import app.logdate.ui.timeline.createSemanticTimelineDayUiState
import app.logdate.ui.workspace.WorkspaceDestination
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Pending and completed moments share the actual Home timeline, with real readable photo content. */
@Composable
fun MarkedTimelineAcceptanceContent(
    photoUri: String,
    photoPainter: Painter? = null,
) {
    val captured = Instant.parse("2026-05-04T17:35:00Z")
    val mark =
        MarkedMoment(
            Uuid.parse("00000000-0000-0000-0000-000000000009"),
            captured,
            "America/Los_Angeles",
            null,
            null,
            MarkState.OPEN,
            "A quiet moment by the lake",
            emptySet(),
            "fixture-watch",
            captured,
            1,
        )
    HomeWorkspaceComposition(
        destinations =
            HomeRouteDestination.visibleEntries(true).map {
                WorkspaceDestination(it.name, stringResource(it.label), it.selectedIcon, it.unselectedIcon)
            },
        selectedKey = HomeRouteDestination.Timeline.name,
        onSelect = {},
        onCreate = {},
        onSearch = {},
        actions = { HomeWorkspaceAccountAction(SyncPresentation.Hidden, null, {}, {}, {}) },
        status = {
            Column(Modifier.padding(horizontal = Spacing.lg)) {
                WatchRecordingContent(73_000, stopping = false, failed = false, onStop = {})
            }
        },
    ) {
        TimelinePane(
            uiState =
                TimelineUiState(
                    items =
                        listOf(
                            createSemanticTimelineDayUiState(
                                summary = "A morning outside",
                                date = LocalDate(2026, 5, 4),
                                moments =
                                    listOf(
                                        MomentUiState(
                                            id = "patio-coffee",
                                            label = "Coffee on the patio",
                                            textSnippet = "We left our phones inside and watched the neighborhood wake up.",
                                            capturedAt = Instant.parse("2026-05-04T16:50:00Z"),
                                        ),
                                        MomentUiState(
                                            id = "back-home",
                                            label = "Back home",
                                            textSnippet = "I want to make time for mornings like this more often.",
                                            capturedAt = Instant.parse("2026-05-04T19:10:00Z"),
                                        ),
                                    ),
                                pendingMarks = listOf(mark),
                            ),
                            createSemanticTimelineDayUiState(
                                summary = "An evening together",
                                date = LocalDate(2026, 5, 3),
                                moments =
                                    listOf(
                                        MomentUiState(
                                            id = "dinner",
                                            label = "Dinner with friends",
                                            textSnippet = "The conversation ran long after the plates were cleared.",
                                            capturedAt = Instant.parse("2026-05-04T03:15:00Z"),
                                        ),
                                    ),
                            ),
                        ),
                ),
            onNewEntry = {},
            onOpenDay = {},
            pendingMomentContent = { pending ->
                MarkedMomentContent(
                    pending,
                    "Lake trail",
                    pending.caption,
                    {},
                    MarkPhotoSuggestions.Limited(
                        listOf(MediaObject.Image(photoUri, 12_000, "Morning by the lake", captured)),
                    ),
                    busy = false,
                    error = null,
                    onRequestAccess = {},
                    onAttach = {},
                    onSaveCaption = {},
                    onWriteMore = {},
                    onLeave = {},
                    onDelete = {},
                    previewPhotoPainter = photoPainter,
                )
            },
        )
    }
}
