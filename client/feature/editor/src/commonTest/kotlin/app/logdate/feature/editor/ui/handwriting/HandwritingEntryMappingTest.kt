package app.logdate.feature.editor.ui.handwriting

import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.mapper.toDomainBlock
import app.logdate.feature.editor.ui.mapper.toJournalNote
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class HandwritingEntryMappingTest {
    @Test
    fun draftAndSavedEntryMappingRetainsEveryOriginalStroke() {
        val block =
            HandwritingBlockUiState(
                page =
                    HandwritingPage(
                        height = 1800f,
                        strokes =
                            listOf(
                                CanvasElement.Ink(
                                    Uuid.random(),
                                    InkTool.PEN,
                                    "#663399",
                                    4.25f,
                                    listOf(InkPoint(12.25f, 1450.5f, 0.2f), InkPoint(300.5f, 1500.75f, 0.9f)),
                                ),
                            ),
                    ),
            )
        val note = assertNotNull(block.toJournalNote())
        assertEquals(block, note.toDomainBlock())
    }

    @Test
    fun reopeningSyncedInkPreservesCaptureMetadata() {
        val time = kotlin.time.Instant.fromEpochMilliseconds(1725000000000)
        val note =
            app.logdate.client.repository.journals.JournalNote.Handwriting(
                uid = Uuid.random(),
                creationTimestamp = time,
                lastUpdated = time,
                document =
                    HandwritingPage(
                        strokes =
                            listOf(
                                CanvasElement.Ink(Uuid.random(), InkTool.PEN, "#263044", 3.5f, listOf(InkPoint(12f, 24f, 0.4f))),
                            ),
                    ).toDocument(),
                syncVersion = 7,
                capturedOn = app.logdate.client.repository.journals.CaptureDevice.WATCH,
                location =
                    app.logdate.client.repository.journals.NoteLocation(
                        coordinates =
                            app.logdate.client.repository.journals
                                .NoteCoordinates(36.1, -115.1),
                    ),
                timeZoneId = "America/Los_Angeles",
            )
        val reopened = assertNotNull(note.toDomainBlock().toJournalNote())
        assertEquals(note.location, reopened.location)
        assertEquals(note.capturedOn, reopened.capturedOn)
        assertEquals(note.syncVersion, reopened.syncVersion)
    }
}
