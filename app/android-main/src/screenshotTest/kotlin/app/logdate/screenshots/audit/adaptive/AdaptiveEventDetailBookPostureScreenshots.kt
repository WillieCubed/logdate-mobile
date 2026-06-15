package app.logdate.screenshots.audit.adaptive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.events.ui.EventDetailActions
import app.logdate.feature.events.ui.EventDetailContent
import app.logdate.feature.events.ui.EventDetailUiState
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.shared.model.Event
import app.logdate.shared.model.Place
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val BOOK_FOLDABLE = "spec:width=1440dp,height=900dp"

private val bookPostureLayoutInfo =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Book,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Vertical,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    FoldableHingeBounds(
                        left = 708.dp,
                        top = 0.dp,
                        right = 732.dp,
                        bottom = 900.dp,
                        width = 24.dp,
                        height = 900.dp,
                    ),
                isSeparating = true,
            ),
    )

private val sampleEventId = Uuid.parse("00000000-0000-0000-0000-000000000122")
private val samplePlaceId = Uuid.parse("00000000-0000-0000-0000-000000000123")
private val sampleNoteOneId = Uuid.parse("00000000-0000-0000-0000-000000000124")
private val sampleNoteTwoId = Uuid.parse("00000000-0000-0000-0000-000000000125")

private val sampleEventStart = Instant.parse("2026-03-04T18:00:00Z")
private val sampleEventEnd = Instant.parse("2026-03-04T19:30:00Z")
private val sampleNoteOneTime = Instant.parse("2026-03-04T18:20:00Z")
private val sampleNoteTwoTime = Instant.parse("2026-03-04T19:05:00Z")

/**
 * Deterministic fake for the event detail editor: an ordinary weeknight book club meetup with a
 * resolved place and two short attached captures. No aspirational vacation content, no random ids.
 */
private val sampleLoadedState =
    EventDetailUiState.Loaded(
        event =
            Event(
                id = sampleEventId,
                title = "Tuesday book club",
                description = "Met at the corner cafe to talk through the last few chapters.",
                startTime = sampleEventStart,
                endTime = sampleEventEnd,
                placeId = samplePlaceId,
                coverImageUri = null,
                created = sampleEventStart,
                lastUpdated = sampleEventEnd,
            ),
        linkedNotes =
            listOf(
                JournalNote.Text(
                    uid = sampleNoteOneId,
                    creationTimestamp = sampleNoteOneTime,
                    lastUpdated = sampleNoteOneTime,
                    content = "Picked the next read: a quiet little mystery novel.",
                ),
                JournalNote.Text(
                    uid = sampleNoteTwoId,
                    creationTimestamp = sampleNoteTwoTime,
                    lastUpdated = sampleNoteTwoTime,
                    content = "Split the bill, walked home in the drizzle.",
                ),
            ),
        availablePlaces =
            listOf(
                Place.UserDefined(
                    id = samplePlaceId,
                    displayName = "Corner Cafe",
                    lat = 47.6062,
                    lng = -122.3321,
                    description = "The usual spot on the corner.",
                ),
            ),
    )

/**
 * No-op [EventDetailActions] for screenshot rendering. The audit only cares about layout, so every
 * callback is a no-op and `delete` ignores its completion handler.
 */
private val noOpEventDetailActions =
    object : EventDetailActions {
        override fun loadEvent(eventId: Uuid) = Unit

        override fun updateTitle(title: String) = Unit

        override fun updateDescription(description: String) = Unit

        override fun updateStartTime(startTime: Instant) = Unit

        override fun updateEndTime(endTime: Instant?) = Unit

        override fun togglePointInTime(pointInTime: Boolean) = Unit

        override fun updatePlace(placeId: Uuid?) = Unit

        override fun updateCoverImage(uri: String?) = Unit

        override fun openPlacePicker() = Unit

        override fun dismissPlacePicker() = Unit

        override fun openAttachSheet() = Unit

        override fun dismissAttachSheet() = Unit

        override fun linkNote(noteId: Uuid) = Unit

        override fun unlinkNote(noteId: Uuid) = Unit

        override fun save() = Unit

        override fun delete(onDeleted: () -> Unit) = Unit
    }

@PreviewTest
@Preview(name = "Event detail book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A122_EventDetailBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            EventDetailContent(
                uiState = sampleLoadedState,
                actions = noOpEventDetailActions,
                onGoBack = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
