package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSegment
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.PhotoPresentation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NoteEntryMediaMapperTest : NoteEntryMapperFixture() {
    @Test
    fun `a media file that isn't uploaded yet is named without its path`() {
        val image =
            JournalNote.Image(
                creationTimestamp = created,
                lastUpdated = created,
                mediaRef = "file:///private/Diary/secret-name.jpg",
            )

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(image) }

        assertFalse("secret-name" in failure.message.orEmpty())
        assertFalse("Diary" in failure.message.orEmpty())
    }

    @Test
    fun `an audio note refers to its transcript in its own record`() {
        val audio =
            (fullNotes[3] as JournalNote.Audio).copy(
                transcript =
                    TranscriptDocument(
                        status = TranscriptDocumentStatus.FINAL,
                        segments = listOf(TranscriptSegment("a", "Hello", 0, 900)),
                    ),
            )
        val transcripts = NoteTranscriptMapper()
        val transcript = NoteTranscript(audio.uid, audio.transcript!!)

        val entry = mapper.toRecord(audio)
        val ref =
            entry
                .getValue("blocks")
                .jsonArray[0]
                .jsonObject
                .getValue("transcript")
                .jsonObject

        assertValidRecord(mapper.collection, entry)
        assertEquals("app.logdate.journal.transcript", ref["collection"]?.jsonPrimitive?.content)
        assertEquals(transcripts.rkeyOf(transcript), ref["rkey"]?.jsonPrimitive?.content)
        val fromEntry = mapper.fromRecord(mapper.rkeyOf(audio), entry) as JournalNote.Audio
        val fromTranscript = transcripts.fromRecord(transcripts.rkeyOf(transcript), transcripts.toRecord(transcript))
        assertNull(fromEntry.transcript)
        assertEquals(audio, fromEntry.copy(transcript = fromTranscript.document))
    }

    @Test
    fun `media blocks carry the uploaded blob and the file's own type`() {
        val image =
            mapper
                .toRecord(fullNotes[1])
                .getValue("blocks")
                .jsonArray[0]
                .jsonObject
        val stored = image.getValue("media").jsonObject

        assertEquals("image/jpeg", stored["mimeType"]?.jsonPrimitive?.content)
        assertEquals("61204", stored["size"]?.jsonPrimitive?.content)
        assertEquals(
            "application/octet-stream",
            stored
                .getValue("blob")
                .jsonObject["mimeType"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(listOf(cidOf("aa")), mapper.blobCidsOf(fullNotes[1]))
        assertEquals(emptyList(), mapper.blobCidsOf(fullNotes[0]))
    }

    @Test
    fun `a note whose file hasn't been uploaded can't become a record`() {
        val image = JournalNote.Image(creationTimestamp = created, lastUpdated = created, mediaRef = "logdate-media://library/new.jpg")

        assertEquals(true, assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(image) }.retryable)
        assertEquals(true, assertFailsWith<SpaceRecordMappingException> { mapper.blobCidsOf(image) }.retryable)
    }

    @Test
    fun `a note whose file no device has becomes an entry without media`() {
        val unavailable =
            listOf(
                "",
                " ",
                "logdate-unavailable://archive/recovery-1/note/original",
                "logdate-unavailable://native/Y29udGVudDovL21lZGlhLzQy",
            )
        val notes =
            fullNotes.filterNot { it is JournalNote.Text }.flatMap { note ->
                unavailable.map { mediaRef ->
                    when (note) {
                        is JournalNote.Handwriting -> note
                        is JournalNote.Image -> note.copy(mediaRef = mediaRef)
                        is JournalNote.Video -> note.copy(mediaRef = mediaRef)
                        is JournalNote.Audio -> note.copy(mediaRef = mediaRef, transcript = TranscriptDocument())
                        is JournalNote.Text -> note
                    }
                }
            }

        notes.forEach { note ->
            val record = mapper.toRecord(note)
            val block = record.getValue("blocks").jsonArray[0].jsonObject
            val missing = "logdate-unavailable://archive/${note.uid}/original"

            assertValidRecord(mapper.collection, record)
            assertFalse("media" in block, "$note")
            assertEquals(emptyList(), mapper.blobCidsOf(note))
            val readBack = mapper.fromRecord(mapper.rkeyOf(note), record)
            assertEquals(note.withMediaRef(missing).withoutTranscript(), readBack)
        }
    }

    @Test
    fun `a note's unavailable file reads back the same each time`() {
        val image = (fullNotes[1] as JournalNote.Image).copy(mediaRef = "")
        val once = mapper.fromRecord(mapper.rkeyOf(image), mapper.toRecord(image))

        assertEquals(once, roundTrip(once))
    }

    @Test
    fun `a missing updatedAt reads as the creation time`() {
        val record = JsonObject(mapper.toRecord(fullNotes[0]) - "updatedAt")

        assertEquals(created, mapper.fromRecord(mapper.rkeyOf(fullNotes[0]), record).lastUpdated)
    }

    @Test
    fun `capture details a newer app records read as unknown`() {
        val record =
            JsonObject(
                mapper.toRecord(fullNotes[0]) +
                    mapOf("capturedOn" to JsonPrimitive("glasses"), "capturedDuring" to JsonPrimitive("swimming")),
            )
        assertValidRecord(mapper.collection, record)

        val note = mapper.fromRecord(mapper.rkeyOf(fullNotes[0]), record)

        assertNull(note.capturedOn)
        assertNull(note.capturedDuring)
    }

    @Test
    fun `a photo framing a newer app adds reads as edge to edge`() {
        val note = fullNotes[1]
        val record = mapper.toRecord(note).withBlock { it + ("presentation" to JsonPrimitive("polaroid")) }

        assertEquals(PhotoPresentation.EdgeToEdge, (mapper.fromRecord(mapper.rkeyOf(note), record) as JournalNote.Image).presentation)
    }

    @Test
    fun `a media file's text alternative and aspect ratio are written back as they arrived`() {
        val note = fullNotes[1] as JournalNote.Image
        val alt = "A lake at dawn, 100% still & quiet 🌅"
        val withAlt = note.copy(mediaRef = uploadedRef(imageId, "aa", "image/jpeg", alt = alt))
        val sent =
            mapper.toRecord(withAlt).withBlock { block ->
                val media = block.getValue("media").jsonObject
                val ratio =
                    buildJsonObject {
                        put("width", JsonPrimitive(4))
                        put("height", JsonPrimitive(3))
                    }
                block + ("media" to JsonObject(media + ("aspectRatio" to ratio)))
            }
        assertValidRecord(mapper.collection, sent)

        val received = mapper.fromRecord(mapper.rkeyOf(note), sent) as JournalNote.Image
        val resent = mapper.toRecord(received)

        assertEquals(sent, resent)
        assertEquals(
            alt,
            resent
                .getValue("blocks")
                .jsonArray[0]
                .jsonObject
                .getValue("media")
                .jsonObject["alt"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun `a video's length survives being read and written again`() {
        val video = fullNotes[2] as JournalNote.Video
        val received = mapper.fromRecord(mapper.rkeyOf(video), mapper.toRecord(video)) as JournalNote.Video

        assertEquals(61_250L, received.durationMs)
        assertEquals(mapper.toRecord(video), mapper.toRecord(received))
    }
}
