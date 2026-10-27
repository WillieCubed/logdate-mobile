package app.logdate.wear.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.shared.model.HandwritingDocument
import app.logdate.wear.capture.MomentNoteCodec
import app.logdate.wear.capture.MomentNoteKind
import app.logdate.wear.v2.moments.momentBody
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class HandwritingCompanionTest {
    private val time = Instant.parse("2026-10-27T12:00:00Z")
    private val note =
        JournalNote.Handwriting(
            creationTimestamp = time,
            lastUpdated = time,
            document = HandwritingDocument(),
            unreadableContent = "{\"version\":99,\"original\":\"ink\"}",
            syncVersion = 3,
        )

    @Test
    fun `handwriting is recognized separately from typed text`() {
        assertEquals(MomentNoteKind.HANDWRITING, MomentNoteCodec.classify(note))
        assertNull(momentBody(note))
    }

    @Test
    fun `window fingerprints ignore transport versions and retain original bytes`() {
        assertEquals(windowNoteFingerprint(note), windowNoteFingerprint(note.copy(syncVersion = 10)))
        assertNotEquals(windowNoteFingerprint(note), windowNoteFingerprint(note.copy(unreadableContent = "changed original")))
    }

    @Test
    fun `companion transport preserves unsupported original documents`() {
        val mapper = NoteDataMapper()
        assertEquals(note, mapper.fromDataMap(mapper.toDataMap(note)))
    }
}
