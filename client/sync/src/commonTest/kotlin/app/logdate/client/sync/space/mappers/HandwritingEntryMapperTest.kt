package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class HandwritingEntryMapperTest : NoteEntryMapperFixture() {
    @Test
    fun retainedUnsupportedInkIsNotUploadedAsAnEmptyDocument() {
        val raw = "{\"version\":2,\"strokes\":[{\"futureInk\":true}]}"
        val note =
            JournalNote.Handwriting(
                creationTimestamp = created,
                lastUpdated = updated,
                document = HandwritingDocument(),
                unreadableContent = raw,
            )
        val record = mapper.toRecord(note)
        val block =
            record
                .getValue("blocks")
                .jsonArray
                .single()
                .jsonObject
        assertEquals(raw, block.getValue("document").jsonPrimitive.content)
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(note), record) }
    }

    @Test
    fun `unreadable handwriting stays a mapping failure instead of becoming text`() {
        val note = JournalNote.Handwriting(creationTimestamp = created, lastUpdated = updated, document = HandwritingDocument())
        val record = mapper.toRecord(note)
        for (document in listOf("not JSON", "{\"version\":2}")) {
            val block =
                record
                    .getValue("blocks")
                    .jsonArray
                    .single()
                    .jsonObject
            val unreadable =
                JsonObject(
                    record + ("blocks" to JsonArray(listOf(JsonObject(block + ("document" to JsonPrimitive(document)))))),
                )
            val failure = assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(note), unreadable) }
            assertFalse(failure.retryable)
        }
    }

    @Test
    fun `original handwriting survives personal space records with its capture context`() {
        val note =
            JournalNote.Handwriting(
                creationTimestamp = created,
                lastUpdated = updated,
                document =
                    HandwritingDocument(
                        height = 1700f,
                        strokes =
                            listOf(
                                HandwritingStroke(
                                    Uuid.random(),
                                    "pen",
                                    "#263044",
                                    3.5f,
                                    listOf(HandwritingPoint(12.25f, 1400.5f, 0.2f), HandwritingPoint(400.75f, 1500.25f, 0.9f)),
                                ),
                            ),
                    ),
                location = fullLocation,
                timeZoneId = "Australia/Sydney",
                capturedOn = CaptureDevice.PHONE,
                capturedDuring = CaptureActivity.MOVING,
            )
        val record = mapper.toRecord(note)
        assertValidRecord(mapper.collection, record)
        assertEquals(note, mapper.fromRecord(mapper.rkeyOf(note), record))
        assertEquals(emptyList(), mapper.blobCidsOf(note))
    }

    @Test
    fun handwritingCannotBeUploadedAsAPlainTextSpaceRecord() {
        val note =
            JournalNote.Handwriting(
                creationTimestamp = created,
                lastUpdated = updated,
                document = HandwritingDocument(),
            )

        val block =
            mapper
                .toRecord(note)
                .getValue("blocks")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("app.logdate.journal.entry#handwritingBlock", block.getValue("\$type").jsonPrimitive.content)
        assertFalse("text" in block)
        assertEquals(note.document, HandwritingDocument.decode(block.getValue("document").jsonPrimitive.content))
    }
}
