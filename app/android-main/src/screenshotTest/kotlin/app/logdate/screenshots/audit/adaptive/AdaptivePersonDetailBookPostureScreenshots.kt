package app.logdate.screenshots.audit.adaptive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.core.people.ui.PersonDetailContent
import app.logdate.feature.core.people.ui.PersonDetailViewModel
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.shared.model.Event
import app.logdate.shared.model.Person
import app.logdate.shared.model.PersonOrigin
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

private val sampleInstant = Instant.parse("2026-03-12T09:30:00Z")

private val samplePerson =
    Person(
        uid = Uuid.parse("00000000-0000-0000-0000-000000000128"),
        name = "Maya Hassan",
        aliases = listOf("Maya", "M"),
        relationshipLabel = "Sister",
        notes = "Lives a few blocks over. We usually grab coffee on Sundays.",
        origin = PersonOrigin.MANUAL,
    )

private val sampleLinkedEntries =
    listOf<JournalNote>(
        JournalNote.Text(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000129"),
            creationTimestamp = sampleInstant,
            lastUpdated = sampleInstant,
            content = "Quiet Tuesday. Maya stopped by after work and we reheated leftovers.",
        ),
        JournalNote.Text(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000130"),
            creationTimestamp = sampleInstant,
            lastUpdated = sampleInstant,
            content = "Texted Maya about the dentist appointment. Nothing else going on today.",
        ),
    )

private val sampleLinkedEvents =
    listOf(
        Event(
            id = Uuid.parse("00000000-0000-0000-0000-000000000131"),
            title = "Sunday coffee with Maya",
            startTime = sampleInstant,
            created = sampleInstant,
            lastUpdated = sampleInstant,
        ),
        Event(
            id = Uuid.parse("00000000-0000-0000-0000-000000000132"),
            title = "Grocery run",
            startTime = sampleInstant,
            created = sampleInstant,
            lastUpdated = sampleInstant,
        ),
    )

private val loadedPersonDetailState =
    PersonDetailViewModel.UiState(
        person = samplePerson,
        linkedEntries = sampleLinkedEntries,
        linkedEvents = sampleLinkedEvents,
        isLoading = false,
    )

@PreviewTest
@Preview(name = "Person detail book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A128_PersonDetailBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            PersonDetailContent(
                uiState = loadedPersonDetailState,
                onBack = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
