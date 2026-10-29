package app.logdate.screenshots.common

import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.createSemanticTimelineDayUiState
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

private const val PREVIEW_IMAGE_URI = "android.resource://studio.hypertext.logdate.debug/mipmap/ic_launcher"

private val blueBottle = PlaceUiState(id = "place-1", title = "Blue Bottle Coffee")
internal val timelineRoutePark = PlaceUiState(id = "place-2", title = "Dolores Park")
private val home = PlaceUiState(id = "place-3", title = "Home")

private val routePhotoNoteId = Uuid.parse("00000000-0000-0000-0000-000000000031")
private val routeNoteId = Uuid.parse("00000000-0000-0000-0000-000000000032")
private val ferryAudioNoteId = Uuid.parse("00000000-0000-0000-0000-000000000033")
private val ferryNoteId = Uuid.parse("00000000-0000-0000-0000-000000000034")

internal val timelineRouteDays =
    listOf(
        createSemanticTimelineDayUiState(
            summary = "Wrapped up the route inventory and started wiring screenshot helpers.",
            date = LocalDate(2025, 2, 20),
            moments =
                listOf(
                    MomentUiState(
                        id = "moment-2025-02-20-blue-bottle",
                        sourceNoteIds = setOf(routePhotoNoteId),
                        label = "At Blue Bottle Coffee",
                        timeOfDay = DaylightPeriod.MORNING,
                        media = listOf(MomentMediaUiState(uri = PREVIEW_IMAGE_URI)),
                        places = listOf(blueBottle),
                        isHero = true,
                    ),
                    MomentUiState(
                        id = "moment-2025-02-20-dolores-park",
                        sourceNoteIds = setOf(routeNoteId),
                        label = "At Dolores Park",
                        timeOfDay = DaylightPeriod.AFTERNOON,
                        textSnippet = "Captured the route shell, then tightened spacing around the new timeline cards.",
                        places = listOf(timelineRoutePark),
                    ),
                ),
            notes =
                listOf(
                    ImageNoteUiState(
                        noteId = routePhotoNoteId,
                        uri = PREVIEW_IMAGE_URI,
                        timestamp = ScreenshotTestData.baseInstant,
                    ),
                    TextNoteUiState(
                        noteId = routeNoteId,
                        text = "Captured the route shell, then tightened spacing around the new timeline cards.",
                        timestamp = ScreenshotTestData.baseInstant,
                    ),
                ),
            placesVisited = listOf(blueBottle, timelineRoutePark),
        ),
        createSemanticTimelineDayUiState(
            summary = "Captured the golden-hour ferry ride home after a long day.",
            date = LocalDate(2025, 2, 19),
            moments =
                listOf(
                    MomentUiState(
                        id = "moment-2025-02-19-ferry",
                        sourceNoteIds = setOf(ferryAudioNoteId),
                        label = "",
                        timeOfDay = DaylightPeriod.GOLDEN_HOUR,
                        audio =
                            MomentAudioUiState(
                                uri = "preview://audio",
                                durationMs = 67_000L,
                                transcript =
                                    "The ferry was nearly empty on the way back, so I recorded this " +
                                        "instead of typing it out. Everything sounds different over water.",
                                noteId = ferryAudioNoteId,
                            ),
                        isHero = true,
                    ),
                    MomentUiState(
                        id = "moment-2025-02-19-home",
                        sourceNoteIds = setOf(ferryNoteId),
                        label = "At Home",
                        timeOfDay = DaylightPeriod.NIGHT,
                        textSnippet = "Kept the end-of-day note short and let the audio carry the texture instead.",
                        places = listOf(home),
                    ),
                ),
            notes =
                listOf(
                    AudioNoteUiState(
                        noteId = ferryAudioNoteId,
                        uri = "preview://audio",
                        timestamp = ScreenshotTestData.baseInstant,
                        duration = 67_000L,
                    ),
                    TextNoteUiState(
                        noteId = ferryNoteId,
                        text = "Kept the end-of-day note short and let the audio carry the texture instead.",
                        timestamp = ScreenshotTestData.baseInstant,
                    ),
                ),
            placesVisited = listOf(home),
        ),
    )
