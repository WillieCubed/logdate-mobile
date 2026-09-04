package app.logdate.screenshots.components.sync

import androidx.compose.runtime.Composable
import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.screenshots.common.HomeTabRouteFrame
import app.logdate.screenshots.common.RoutePreviewTab
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.sync.SyncPresentation
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineUiState
import app.logdate.ui.timeline.createSemanticTimelineDayUiState
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * Full-screen renders of the Timeline tab with the new sync surface composed in context — chip
 * inside the TopAppBar `actions` slot, banner inside the Scaffold body. Complements the
 * isolated-harness tests in `SyncSurfaceScreenshots.kt` by showing the surface against
 * realistic content so the visual relationship to the timeline reads correctly.
 */

private const val SYNC_PREVIEW_IMAGE_URI = "android.resource://studio.hypertext.logdate/mipmap/ic_launcher"

private val missionStudio = PlaceUiState(id = "place-201", title = "Mission Studio")
private val tartine = PlaceUiState(id = "place-202", title = "Tartine")
private val bridgeHome = PlaceUiState(id = "place-203", title = "Home")

private val bridgeAudioNoteId = Uuid.parse("00000000-0000-0000-0000-000000000203")

private val sampleTimelineDays =
    listOf(
        createSemanticTimelineDayUiState(
            summary = "Sketched the new hand-off card and tested it against three real entries.",
            date = LocalDate(2026, 5, 4),
            moments =
                listOf(
                    MomentUiState(
                        id = "moment-2026-05-04-mission-studio",
                        label = "At Mission Studio",
                        timeOfDay = DaylightPeriod.MIDDAY,
                        media = listOf(MomentMediaUiState(uri = SYNC_PREVIEW_IMAGE_URI)),
                        places = listOf(missionStudio),
                        isHero = true,
                    ),
                    MomentUiState(
                        id = "moment-2026-05-04-tartine",
                        label = "At Tartine",
                        timeOfDay = DaylightPeriod.AFTERNOON,
                        textSnippet = "The crew loved the new card; we kept the photo lockup and tightened the title size.",
                        places = listOf(tartine),
                    ),
                ),
            notes =
                listOf(
                    ImageNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000201"),
                        uri = SYNC_PREVIEW_IMAGE_URI,
                        timestamp = ScreenshotTestData.baseInstant,
                    ),
                    TextNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000202"),
                        text = "The crew loved the new card; we kept the photo lockup and tightened the title size.",
                        timestamp = ScreenshotTestData.baseInstant,
                    ),
                ),
            placesVisited = listOf(missionStudio, tartine),
        ),
        createSemanticTimelineDayUiState(
            summary = "Long ride home; recorded a voice memo on the bridge.",
            date = LocalDate(2026, 5, 3),
            moments =
                listOf(
                    MomentUiState(
                        id = "moment-2026-05-03-bridge",
                        label = "",
                        timeOfDay = DaylightPeriod.GOLDEN_HOUR,
                        audio =
                            MomentAudioUiState(
                                uri = "preview://audio",
                                durationMs = 87_000L,
                                transcript =
                                    "Halfway across the bridge and the wind is doing something to the " +
                                        "microphone, but I wanted to get this down before I forgot it.",
                                noteId = bridgeAudioNoteId,
                            ),
                        isHero = true,
                    ),
                    MomentUiState(
                        id = "moment-2026-05-03-home",
                        label = "At Home",
                        timeOfDay = DaylightPeriod.NIGHT,
                        textSnippet = "Bridge wind made the audio crackle; clipping it tomorrow morning.",
                        places = listOf(bridgeHome),
                    ),
                ),
            notes =
                listOf(
                    AudioNoteUiState(
                        noteId = bridgeAudioNoteId,
                        uri = "preview://audio",
                        timestamp = ScreenshotTestData.baseInstant,
                        duration = 87_000L,
                    ),
                    TextNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000204"),
                        text = "Bridge wind made the audio crackle; clipping it tomorrow morning.",
                        timestamp = ScreenshotTestData.baseInstant,
                    ),
                ),
            placesVisited = listOf(bridgeHome),
        ),
    )

@Composable
private fun TimelineWithSync(presentation: SyncPresentation) {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.TIMELINE) {
            TimelinePane(
                uiState = TimelineUiState(items = sampleTimelineDays),
                onNewEntry = {},
                onShareMemory = { _ -> },
                onOpenDay = {},
                onSearchClick = {},
                onProfileClick = {},
                onHistoryClick = {},
                syncPresentation = presentation,
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_NoSyncActivity_SignedOut_or_Healthy() {
    // The "default" view: no chip, no banner. SyncPresentation.Hidden composes nothing.
    TimelineWithSync(SyncPresentation.Hidden)
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_Syncing_3_items() {
    // Active sync run. Chip in the TopAppBar shows "Syncing 3" with the rotating glyph.
    TimelineWithSync(SyncPresentation.Syncing(pendingCount = 3))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_Pending_12_items_offline_or_backoff() {
    // Items queued but not currently syncing. Tonal "12 waiting" pill in the TopAppBar.
    TimelineWithSync(SyncPresentation.Pending(pendingCount = 12))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_NetworkError_with_pending() {
    // Quiet failure — chip with cloud-off glyph, no banner.
    TimelineWithSync(SyncPresentation.NetworkError(pendingCount = 4))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_AuthError_promotes_banner() {
    // Auth lapsed — banner under the TopAppBar with a primary "Sign in" action.
    TimelineWithSync(SyncPresentation.AuthError)
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_StorageError_with_pending() {
    // Quota hit — banner with "Manage" + "Dismiss".
    TimelineWithSync(SyncPresentation.StorageError(pendingCount = 7))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_ConflictError_review_required() {
    // Conflicts present — tonal-tertiary banner with "Review".
    TimelineWithSync(SyncPresentation.ConflictError(conflictCount = 9))
}
