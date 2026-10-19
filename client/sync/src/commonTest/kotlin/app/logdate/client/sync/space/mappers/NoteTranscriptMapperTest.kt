package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptEngineMetadata
import app.logdate.client.repository.transcription.TranscriptSegment
import app.logdate.client.repository.transcription.TranscriptSoundTag
import app.logdate.client.repository.transcription.TranscriptSource
import app.logdate.client.repository.transcription.TranscriptSpeaker
import app.logdate.client.repository.transcription.TranscriptWord
import app.logdate.client.repository.transcription.TranscriptionStatus
import app.logdate.client.sync.space.SpaceRecordMappingException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NoteTranscriptMapperTest {
    private val mapper = NoteTranscriptMapper()
    private val noteId = Uuid.parse("7d0c9e7a-2f4b-4b8e-9a51-3e6f1c2d4b90")

    private val fullDocument =
        TranscriptDocument(
            revision = 7,
            status = TranscriptDocumentStatus.REFINING,
            language = "pt-BR",
            segments =
                listOf(
                    TranscriptSegment(
                        segmentId = "seg-0",
                        text = "Olá, futuro nós! 👋🏽",
                        startMs = 0,
                        endMs = 2_400,
                        words =
                            listOf(
                                TranscriptWord("Olá,", "olá", 0, 380, confidence = 0.961f, speakerId = "s1"),
                                TranscriptWord("futuro", "futuro", 420, 900, speakerId = null),
                            ),
                        speakerId = "s1",
                        confidence = 0.942f,
                        source = TranscriptSource.CLOUD_REFINEMENT,
                        isFinal = true,
                    ),
                    TranscriptSegment(
                        segmentId = "seg-1",
                        text = "",
                        startMs = 2_600,
                        endMs = 2_600,
                        confidence = 0f,
                        source = TranscriptSource.IMPORTED,
                    ),
                    TranscriptSegment(
                        segmentId = "seg-2",
                        text = "x",
                        startMs = 3_000,
                        endMs = 3_100,
                        confidence = 1f,
                        source = TranscriptSource.LOCAL_LIVE,
                    ),
                    TranscriptSegment(
                        segmentId = "seg-3",
                        text = "y",
                        startMs = 3_200,
                        endMs = 3_300,
                        source = TranscriptSource.CLOUD_LIVE,
                    ),
                    TranscriptSegment(
                        segmentId = "seg-4",
                        text = "z",
                        startMs = 3_400,
                        endMs = 3_500,
                        source = TranscriptSource.LOCAL_REFINEMENT,
                    ),
                ),
            speakers = listOf(TranscriptSpeaker("s1", "Speaker 1"), TranscriptSpeaker("s2", "Ana")),
            engine = TranscriptEngineMetadata(name = "LogDate Cloud", modelId = "speech-large-2", version = "2026.10"),
            soundTags =
                listOf(
                    TranscriptSoundTag("laughter", startMs = 1_200, durationMs = 800, confidence = 0.875f),
                    TranscriptSoundTag("rain", startMs = 5_000),
                ),
            createdAt = Instant.parse("2026-10-12T07:41:09.512Z"),
            updatedAt = Instant.parse("2026-10-13T21:02:00.000000001Z"),
        )

    private fun roundTrip(document: TranscriptDocument): TranscriptDocument {
        val item = NoteTranscript(noteId, document)
        val record = mapper.toRecord(item)
        assertValidRecord(mapper.collection, record)
        return mapper.fromRecord(mapper.rkeyOf(item), record).also { assertEquals(noteId, it.noteId) }.document
    }

    @Test
    fun `every field of a transcript round-trips`() {
        assertEquals(fullDocument, roundTrip(fullDocument))
    }

    @Test
    fun `an empty transcript round-trips`() {
        val empty = TranscriptDocument(engine = null)

        assertEquals(empty, roundTrip(empty))
    }

    @Test
    fun `every status round-trips`() {
        TranscriptDocumentStatus.entries.forEach { status ->
            assertEquals(status, roundTrip(TranscriptDocument(status = status)).status)
        }
    }

    @Test
    fun `statuses follow the lexicon's table of jobs and transcripts`() {
        val firstSegment = listOf(TranscriptSegment("a", "Hello", 0, 900))
        val rows =
            listOf(
                Triple(TranscriptionStatus.PENDING, TranscriptDocument(), "pending"),
                Triple(TranscriptionStatus.IN_PROGRESS, TranscriptDocument(), "inProgress"),
                Triple(TranscriptionStatus.IN_PROGRESS, TranscriptDocument(segments = firstSegment), "inProgress"),
                Triple(TranscriptionStatus.IN_PROGRESS, TranscriptDocument(status = TranscriptDocumentStatus.REFINING), "refining"),
                Triple(TranscriptionStatus.COMPLETED, TranscriptDocument(status = TranscriptDocumentStatus.FINAL), "complete"),
                Triple(TranscriptionStatus.FAILED, TranscriptDocument(status = TranscriptDocumentStatus.FAILED), "failed"),
            )

        rows.forEach { (job, document, status) ->
            val item = NoteTranscript(noteId, document, job)
            val record = mapper.toRecord(item)

            assertValidRecord(mapper.collection, record)
            assertEquals(status, record["status"]?.jsonPrimitive?.content)
            assertEquals(item, mapper.fromRecord(mapper.rkeyOf(item), record), status)
        }
    }

    @Test
    fun `a pending job that already has segments is written as in progress`() {
        val document = TranscriptDocument(segments = listOf(TranscriptSegment("a", "Hello", 0, 900)))

        val record = mapper.toRecord(NoteTranscript(noteId, document, TranscriptionStatus.PENDING))

        assertEquals("inProgress", record["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `without a job a listening document with no segments is waiting and one with segments is running`() {
        assertEquals(TranscriptionStatus.PENDING, NoteTranscript(noteId, TranscriptDocument()).job)
        val running = TranscriptDocument(segments = listOf(TranscriptSegment("a", "Hello", 0, 900)))
        assertEquals(TranscriptionStatus.IN_PROGRESS, NoteTranscript(noteId, running).job)
    }

    @Test
    fun `sound tags and times are written and read back`() {
        val record = mapper.toRecord(NoteTranscript(noteId, fullDocument))

        assertEquals(2, record.getValue("soundTags").jsonArray.size)
        assertEquals(
            875,
            record
                .getValue("soundTags")
                .jsonArray[0]
                .jsonObject
                .getValue("confidence")
                .jsonPrimitive.long,
        )
        assertEquals("2026-10-12T07:41:09.512Z", record["createdAt"]?.jsonPrimitive?.content)
        assertEquals("2026-10-13T21:02:00.000000001Z", record["updatedAt"]?.jsonPrimitive?.content)
        assertEquals(fullDocument, mapper.fromRecord(mapper.rkeyOf(NoteTranscript(noteId, fullDocument)), record).document)
    }

    @Test
    fun `record keys match regardless of case`() {
        val upper = noteId.toString().uppercase()
        val record =
            JsonObject(
                minimalRecord() +
                    (
                        "entry" to
                            buildJsonObject {
                                put("collection", JsonPrimitive("app.logdate.journal.entry"))
                                put("rkey", JsonPrimitive(upper))
                            }
                    ) + ("blockId" to JsonPrimitive(upper)),
            )

        val transcript = mapper.fromRecord("$upper:$upper", record)

        assertEquals(noteId, transcript.noteId)
        assertEquals("$noteId:$noteId", mapper.rkeyOf(transcript))
    }

    @Test
    fun `the record names the note's entry and block and is keyed by both`() {
        val item = NoteTranscript(noteId, fullDocument)
        val record = mapper.toRecord(item)

        assertEquals("$noteId:$noteId", mapper.rkeyOf(item))
        assertEquals(NoteTranscriptMapper.transcriptRkeyOf(noteId), mapper.rkeyOf(item))
        assertEquals("app.logdate.journal.transcript", record["\$type"]?.jsonPrimitive?.content)
        assertEquals(
            buildJsonObject {
                put("collection", JsonPrimitive("app.logdate.journal.entry"))
                put("rkey", JsonPrimitive(noteId.toString()))
            },
            record["entry"],
        )
        assertEquals(noteId.toString(), record["blockId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `confidences are stored in thousandths`() {
        val record = mapper.toRecord(NoteTranscript(noteId, fullDocument))
        val segment = record.getValue("segments").jsonArray[0].jsonObject

        assertEquals(942, segment.getValue("confidence").jsonPrimitive.long)
        assertEquals(
            961,
            segment
                .getValue("words")
                .jsonArray[0]
                .jsonObject
                .getValue("confidence")
                .jsonPrimitive.long,
        )
    }

    @Test
    fun `a confidence between thousandths is rounded to the nearest one`() {
        val document = TranscriptDocument(segments = listOf(TranscriptSegment("a", "b", 0, 1, confidence = 0.12345f)))

        assertEquals(0.123f, roundTrip(document).segments.single().confidence)
    }

    @Test
    fun `a confidence outside 0 to 1 is kept between 0 and 1000`() {
        mapOf(-0.1f to 0f, 1.01f to 1f).forEach { (confidence, stored) ->
            val document = TranscriptDocument(segments = listOf(TranscriptSegment("a", "b", 0, 1, confidence = confidence)))

            assertEquals(stored, roundTrip(document).segments.single().confidence)
        }
    }

    @Test
    fun `a confidence that isn't a number is refused without quoting the words`() {
        val words = listOf(TranscriptWord("secretword", "secretword", 5, 9, confidence = Float.NaN))
        val document = TranscriptDocument(segments = listOf(TranscriptSegment("a", "private text", 0, 10, words = words)))

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(NoteTranscript(noteId, document)) }
        val infinite = TranscriptDocument(segments = listOf(TranscriptSegment("a", "b", 0, 1, confidence = Float.POSITIVE_INFINITY)))

        assertFalse("secretword" in failure.message.orEmpty())
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(NoteTranscript(noteId, infinite)) }
    }

    @Test
    fun `a speaker's color isn't stored`() {
        val document = TranscriptDocument(speakers = listOf(TranscriptSpeaker("s1", "Speaker 1", colorToken = "tertiary")))

        assertNull(roundTrip(document).speakers.single().colorToken)
    }

    @Test
    fun `a transcript that is requested but not started reads as a pending job with a listening transcript`() {
        val record = JsonObject(minimalRecord() + ("status" to JsonPrimitive("pending")))

        val transcript = mapper.fromRecord("$noteId:$noteId", record)

        assertEquals(TranscriptDocumentStatus.LISTENING, transcript.document.status)
        assertEquals(TranscriptionStatus.PENDING, transcript.job)
    }

    @Test
    fun `a record with only the required fields gets the documented defaults`() {
        val segment =
            buildJsonObject {
                put("id", JsonPrimitive("s"))
                put("startMs", JsonPrimitive(0))
                put("endMs", JsonPrimitive(10))
                put("text", JsonPrimitive("hi"))
                put(
                    "words",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("text", JsonPrimitive("Hi"))
                                put("startMs", JsonPrimitive(0))
                                put("endMs", JsonPrimitive(10))
                            },
                        ),
                    ),
                )
            }
        val record = JsonObject(minimalRecord() + ("segments" to JsonArray(listOf(segment))))
        assertValidRecord(mapper.collection, record)

        val document = mapper.fromRecord("$noteId:$noteId", record).document

        assertEquals("en-US", document.language)
        assertEquals(TranscriptSource.IMPORTED, document.segments.single().source)
        assertEquals(false, document.segments.single().isFinal)
        assertEquals(
            "Hi",
            document.segments
                .single()
                .words
                .single()
                .normalizedText,
        )
    }

    @Test
    fun `records that can't be a note's transcript are refused`() {
        val otherNote = Uuid.random()
        val refusals =
            listOf(
                "$noteId" to minimalRecord(),
                "$noteId:$noteId" to JsonObject(minimalRecord() + ("status" to JsonPrimitive("paused"))),
                "$noteId:$noteId" to JsonObject(minimalRecord() + ("blockId" to JsonPrimitive("b4"))),
                "$noteId:$noteId" to JsonObject(minimalRecord() + ("revision" to JsonPrimitive(Int.MAX_VALUE.toLong() + 1))),
                "$otherNote:$otherNote" to minimalRecord(),
                "$noteId:$noteId" to JsonObject(minimalRecord() + ("\$type" to JsonPrimitive("app.logdate.journal.entry"))),
                "$noteId:$noteId" to
                    JsonObject(
                        minimalRecord() +
                            (
                                "segments" to
                                    JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("s"))
                                                put("startMs", JsonPrimitive(10))
                                                put("endMs", JsonPrimitive(5))
                                                put("text", JsonPrimitive("backwards"))
                                            },
                                        ),
                                    )
                            ),
                    ),
            )

        refusals.forEach { (rkey, record) ->
            assertFailsWith<SpaceRecordMappingException>("$rkey $record") { mapper.fromRecord(rkey, record) }
        }
    }

    @Test
    fun `segment and word limits match the lexicon`() {
        val longId = "s".repeat(128)
        val document = TranscriptDocument(segments = listOf(TranscriptSegment(longId, "text", 0, 1)))
        val record = mapper.toRecord(NoteTranscript(noteId, document))

        assertValidRecord(mapper.collection, record)
        val tooLong =
            mapper.toRecord(
                NoteTranscript(noteId, TranscriptDocument(segments = listOf(TranscriptSegment("$longId!", "text", 0, 1)))),
            )
        assertEquals(listOf("segments[0].id: longer than 128 bytes"), lexiconIssues(mapper.collection, tooLong))
    }

    private fun minimalRecord(): JsonObject =
        buildJsonObject {
            put("\$type", JsonPrimitive("app.logdate.journal.transcript"))
            put(
                "entry",
                buildJsonObject {
                    put("collection", JsonPrimitive("app.logdate.journal.entry"))
                    put("rkey", JsonPrimitive(noteId.toString()))
                },
            )
            put("blockId", JsonPrimitive(noteId.toString()))
            put("status", JsonPrimitive("complete"))
            put("revision", JsonPrimitive(0))
        }
}
