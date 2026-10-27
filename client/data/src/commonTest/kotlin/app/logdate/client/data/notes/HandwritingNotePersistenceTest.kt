package app.logdate.client.data.notes

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HandwritingNotePersistenceTest {
    @Test
    fun originalInkRoundTripsThroughPersistedNoteRecord() {
        val time = Instant.fromEpochMilliseconds(1725000000000)
        val note =
            JournalNote.Handwriting(
                uid = Uuid.random(),
                creationTimestamp = time,
                lastUpdated = time,
                timeZoneId = "America/Los_Angeles",
                document =
                    HandwritingDocument(
                        height = 1920f,
                        strokes =
                            listOf(
                                HandwritingStroke(
                                    Uuid.random(),
                                    "pen",
                                    "#263044",
                                    3.5f,
                                    listOf(HandwritingPoint(12.25f, 1500.75f, 0.31f), HandwritingPoint(200f, 1580f, 0.91f)),
                                ),
                            ),
                    ),
            )
        assertEquals(note, note.toEntity().toModel())
    }

    @Test
    fun originalInkRetainsPortableLocationAndCaptureMetadata() {
        val time = Instant.fromEpochMilliseconds(1000)
        val place = NotePlace(Uuid.random(), "Captured place", 35.0, -115.0)
        val note =
            JournalNote.Handwriting(
                uid = Uuid.random(),
                creationTimestamp = time,
                lastUpdated = time,
                document = HandwritingDocument(),
                location = NoteLocation(NoteCoordinates(35.0, -115.0), place),
                timeZoneId = "America/Los_Angeles",
                capturedOn = CaptureDevice.PHONE,
                capturedDuring = CaptureActivity.MOVING,
            )
        val stored = note.toEntity(linkedPlaceId = null, preserveSourcePlace = true)
        val renamedPlace = place.copy(name = "Renamed place")

        assertEquals(note, stored.toModel(renamedPlace))
        assertEquals(note, stored.toModel())
    }

    @Test
    fun unreadableInkRetainsItsIdentityAndOriginalPayload() {
        val time = Instant.fromEpochMilliseconds(1000)
        val content = """{"version":2,"futureInk":"retain every byte"}"""
        val entity =
            app.logdate.client.database.entities.TextNoteEntity(
                uid = Uuid.random(),
                content = content,
                contentFormat = "handwriting",
                created = time,
                lastUpdated = time,
            )
        val note = entity.toModel() as JournalNote.Handwriting
        assertEquals(entity.uid, note.uid)
        assertEquals(content, note.unreadableContent)
        assertEquals(content, note.toEntity().content)
    }
}
