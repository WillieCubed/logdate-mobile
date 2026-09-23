package app.logdate.screenshots.components.sync

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.feature.core.sync.SyncErrorBanner
import app.logdate.feature.core.sync.SyncStatusButton
import app.logdate.screenshots.common.HomeTabRouteFrame
import app.logdate.screenshots.common.RoutePreviewTab
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTestData.COMPACT_PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.streak.CampfireChip
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.CampfireSize
import app.logdate.feature.core.sync.SyncPresentation
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

private const val SYNC_PREVIEW_IMAGE_URI = "android.resource://studio.hypertext.logdate.debug/mipmap/ic_launcher"

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
private fun TimelineWithSync(
    presentation: SyncPresentation,
    campfire: CampfirePresentation? = null,
) {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.TIMELINE) {
            TimelinePane(
                uiState = TimelineUiState(items = sampleTimelineDays),
                onNewEntry = {},
                onShareMemory = { _ -> },
                onOpenDay = {},
                onSearchClick = {},
                onProfileClick = {},
                // Mirrors what HomeScreen puts in these slots.
                statusActions = {
                    SyncStatusButton(presentation = presentation, modifier = Modifier.padding(end = 4.dp))
                    campfire?.let { CampfireChip(presentation = it, onClick = {}, modifier = Modifier.padding(end = 4.dp)) }
                },
                banner = {
                    SyncErrorBanner(
                        presentation = presentation,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    )
                },
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
fun InContext_Syncing_with_progress() {
    // Active sync run. The status button shows the rotating glyph.
    TimelineWithSync(SyncPresentation.Syncing(progressPercent = 60))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_Pending_12_items_offline_or_backoff() {
    // Items queued but not currently syncing. Cloud glyph with a "12" badge in the TopAppBar.
    TimelineWithSync(SyncPresentation.Pending(pendingCount = 12))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_Pending_with_campfire_fits_phone_width() {
    // Every action the bar carries at once on Android: backup status, campfire, search, settings.
    // The text pill this replaced squeezed the title on a phone-width bar. iOS adds a new-entry
    // action that this Android-only matrix doesn't render.
    TimelineWithSync(
        presentation = SyncPresentation.Pending(pendingCount = 264),
        campfire =
            CampfirePresentation(
                phase = CampfirePhase.BURNING,
                loggedToday = true,
                runDays = 12,
                size = CampfireSize.CAMPFIRE,
                longestRunDays = 12,
                totalDaysJournaled = 40,
            ),
    )
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun InContext_NetworkError_with_pending() {
    // Entries waiting after a failed sync: chip, plus a banner that opens the list of them.
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

@PreviewTest
@Preview(name = "Compact Phone (320dp)", showBackground = true, device = COMPACT_PHONE)
@Composable
fun InContext_Pending_with_long_campfire_streak_compact_width() {
    // Worst realistic case at the narrowest currently-supported phone width (iPhone SE, 320dp):
    // a three-digit campfire streak (the chip has no digit cap, unlike the sync badge's "99+")
    // plus a sync badge, together in the same Android bar exercised by
    // InContext_Pending_with_campfire_fits_phone_width above, but at 320dp instead of 411dp.
    TimelineWithSync(
        presentation = SyncPresentation.Pending(pendingCount = 264),
        campfire =
            CampfirePresentation(
                phase = CampfirePhase.BURNING,
                loggedToday = true,
                runDays = 104,
                size = CampfireSize.BEACON,
                longestRunDays = 104,
                totalDaysJournaled = 140,
            ),
    )
}

@PreviewTest
@Preview(name = "Compact Phone (320dp)", showBackground = true, device = COMPACT_PHONE)
@Composable
fun InContext_Pending_with_campfire_at_compact_width() {
    // Same content as InContext_Pending_with_campfire_fits_phone_width above (already verified
    // to fit at the 411dp "Phone" spec), rendered at 320dp to isolate whether width alone,
    // not digit count, is enough to break it.
    TimelineWithSync(
        presentation = SyncPresentation.Pending(pendingCount = 264),
        campfire =
            CampfirePresentation(
                phase = CampfirePhase.BURNING,
                loggedToday = true,
                runDays = 12,
                size = CampfireSize.CAMPFIRE,
                longestRunDays = 12,
                totalDaysJournaled = 40,
            ),
    )
}
