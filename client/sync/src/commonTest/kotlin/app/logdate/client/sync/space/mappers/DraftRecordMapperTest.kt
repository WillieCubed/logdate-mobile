package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.PhotoPresentation
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DraftRecordMapperTest {
    private val mapper = DraftRecordMapper()
    private val draftId = Uuid.parse("5b2c0d6e-8f41-4a7b-9c3d-2e1f0a9b8c7d")
    private val journalA = Uuid.parse("0b8f3c1e-6a2d-4e9f-8c7b-1d2e3f4a5b6c")
    private val journalB = Uuid.parse("7d9e1f20-3a4b-4c5d-8e6f-0a1b2c3d4e5f")
    private val created = Instant.parse("2026-10-12T07:41:09.512Z")
    private val updated = Instant.parse("2026-10-13T21:02:00.000000001Z")
    private val textId = Uuid.random()
    private val imageId = Uuid.random()
    private val videoId = Uuid.random()
    private val audioId = Uuid.random()
    private val cameraId = Uuid.random()

    private val fullBlocks: List<SerializableEntryBlock> =
        listOf(
            SerializableTextBlock(
                textId,
                created,
                locationLat = -33.86882,
                locationLng = 151.209295,
                altitude = -12.5,
                content = "# A walk\n\nBy the river, with **Ana**. 日本語も。👨‍👩‍👧",
            ),
            SerializableImageBlock(
                imageId,
                updated,
                locationLat = 0.00001,
                locationLng = -179.999999,
                uri = uploadedFile(imageId, 'a', "image/jpeg"),
                caption = "The view from the *top*",
                presentation = PhotoPresentation.Framed,
            ),
            SerializableVideoBlock(
                videoId,
                created,
                locationLat = 89.5,
                locationLng = 0.0,
                altitude = 1.0E10,
                uri = uploadedFile(videoId, 'b', "video/mp4", size = null),
                thumbnailUri = uploadedFile(videoId, 'c', "image/jpeg"),
                caption = "Waves",
            ),
            SerializableAudioBlock(
                audioId,
                updated,
                locationLat = 38.7223,
                locationLng = -9.1393,
                uri = uploadedFile(audioId, 'd', "audio/mp4"),
                duration = 8_200,
                transcription = "Morning thoughts, so far",
            ),
            SerializableCameraBlock(
                cameraId,
                created,
                locationLat = 12.0,
                locationLng = 34.0,
                altitude = 0.0,
                uri = uploadedFile(cameraId, 'e', "video/quicktime"),
            ),
        )

    private val full =
        SpaceDraft(
            EditorDraft(
                id = draftId,
                blocks = fullBlocks,
                selectedJournalIds = listOf(journalB, journalA),
                createdAt = created,
                lastModifiedAt = updated,
            ),
        )

    private fun roundTrip(draft: SpaceDraft): SpaceDraft {
        val record = mapper.toRecord(draft)
        assertValidRecord(mapper.collection, record)
        return mapper.fromRecord(mapper.rkeyOf(draft), record)
    }

    private fun draftWith(vararg blocks: SerializableEntryBlock) =
        SpaceDraft(EditorDraft(draftId, blocks.toList(), emptyList(), created, created))

    private fun recordWithBlock(block: JsonObject): JsonObject = JsonObject(mapper.toRecord(full) + ("blocks" to JsonArray(listOf(block))))

    private fun textBlockRecord(): JsonObject =
        buildJsonObject {
            put("\$type", "app.logdate.journal.draft#textBlock")
            put("id", textId.toString())
            put("createdAt", "2026-10-12T07:41:09.512Z")
            put("text", "hello")
        }

    @Test
    fun `every field of every kind of block round-trips`() {
        assertEquals(full, roundTrip(full))
    }

    @Test
    fun `blocks with only their required fields round-trip`() {
        val bare =
            draftWith(
                SerializableTextBlock(textId, created, content = ""),
                SerializableImageBlock(imageId, created),
                SerializableVideoBlock(videoId, created),
                SerializableAudioBlock(audioId, created),
                SerializableCameraBlock(cameraId, created),
            )

        assertEquals(bare, roundTrip(bare))
    }

    @Test
    fun `a draft with no blocks and no journals round-trips`() {
        val empty = SpaceDraft(EditorDraft(draftId, emptyList(), emptyList(), created, created))

        assertEquals(empty, roundTrip(empty))
        assertFalse("journals" in mapper.toRecord(empty))
    }

    @Test
    fun `the order of blocks and of journals is kept`() {
        val read = roundTrip(full).draft

        assertEquals(fullBlocks.map { it.id }, read.blocks.map { it.id })
        assertEquals(listOf(journalB, journalA), read.selectedJournalIds)
    }

    @Test
    fun `a draft with the most blocks and journals the record allows round-trips`() {
        val blocks = List(200) { SerializableTextBlock(Uuid.random(), created, content = "block $it") }
        val journals = List(100) { Uuid.random() }
        val big = SpaceDraft(EditorDraft(draftId, blocks, journals, created, updated))

        assertEquals(big, roundTrip(big))
    }

    @Test
    fun `empty captions and blocks without a file leave those fields out`() {
        val block =
            mapper
                .toRecord(draftWith(SerializableImageBlock(imageId, created)))
                .getValue("blocks")
                .jsonArray
                .single()
                .jsonObject

        assertEquals(setOf("\$type", "id", "createdAt", "presentation"), block.keys)
    }

    @Test
    fun `the record is keyed by the lowercase draft id and each block by its own id`() {
        val record = mapper.toRecord(full)

        assertEquals(draftId.toString(), mapper.rkeyOf(full))
        assertEquals("app.logdate.journal.draft", record.getValue("\$type").jsonPrimitive.content)
        assertEquals(
            fullBlocks.map { it.id.toString() },
            record.getValue("blocks").jsonArray.map {
                it.jsonObject
                    .getValue("id")
                    .jsonPrimitive.content
            },
        )
    }

    @Test
    fun `a key in uppercase names the same draft`() {
        assertEquals(full, mapper.fromRecord(mapper.rkeyOf(full).uppercase(), mapper.toRecord(full)))
    }

    @Test
    fun `coordinates are written as plain decimal strings that read back exactly`() {
        val geo =
            mapper
                .toRecord(full)
                .getValue("blocks")
                .jsonArray[1]
                .jsonObject
                .getValue("location")
                .jsonObject
                .getValue("geo")
                .jsonObject

        assertEquals("0.00001", geo.getValue("latitude").jsonPrimitive.content)
        assertEquals("-179.999999", geo.getValue("longitude").jsonPrimitive.content)
    }

    @Test
    fun `a block with the largest and smallest coordinates round-trips`() {
        val edge =
            draftWith(
                SerializableTextBlock(textId, created, locationLat = -90.0, locationLng = 180.0, altitude = -11_034.0, content = "deep"),
                SerializableTextBlock(
                    Uuid.random(),
                    created,
                    locationLat = Double.MIN_VALUE,
                    locationLng = -0.0,
                    altitude = Double.MAX_VALUE,
                ),
            )

        assertEquals(edge, roundTrip(edge))
    }

    @Test
    fun `a location the record can't hold refuses the draft`() {
        listOf(
            SerializableTextBlock(textId, created, altitude = 12.0, content = "secret words"),
            SerializableTextBlock(textId, created, locationLat = 12.0, content = "secret words"),
            SerializableTextBlock(textId, created, locationLng = 12.0, content = "secret words"),
            SerializableTextBlock(textId, created, locationLat = Double.NaN, locationLng = 1.0, content = "secret words"),
            SerializableTextBlock(textId, created, locationLat = 1.0, locationLng = Double.POSITIVE_INFINITY, content = "secret words"),
            SerializableTextBlock(textId, created, locationLat = 1.0, locationLng = 1.0, altitude = Double.NaN, content = "secret words"),
        ).forEach { block ->
            val failure = assertFailsWith<SpaceRecordMappingException>("$block") { mapper.toRecord(draftWith(block)) }

            assertFalse(failure.retryable)
            assertFalse("secret words" in failure.message.orEmpty(), "The message shows the block's text")
        }
    }

    @Test
    fun `a negative recording length refuses the draft`() {
        val failure =
            assertFailsWith<SpaceRecordMappingException> {
                mapper.toRecord(
                    draftWith(SerializableAudioBlock(audioId, created, duration = -1)),
                )
            }

        assertFalse(failure.retryable)
    }

    @Test
    fun `the blobs of every file in the draft are listed`() {
        assertEquals(listOf('a', 'b', 'c', 'd', 'e').map(::cidWith), mapper.blobCidsOf(full))
        assertEquals(emptyList(), mapper.blobCidsOf(draftWith(SerializableTextBlock(textId, created, content = "secret words"))))
    }

    @Test
    fun `a file that is not uploaded yet can be tried again later`() {
        val local = draftWith(SerializableImageBlock(imageId, created, uri = "logdate-media://photos/private.jpg"))

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(local) }
        val listing = assertFailsWith<SpaceRecordMappingException> { mapper.blobCidsOf(local) }

        assertTrue(failure.retryable)
        assertTrue(listing.retryable)
        assertFalse("private.jpg" in failure.message.orEmpty())
    }

    @Test
    fun `a photo framing this version doesn't know reads as edge to edge`() {
        val image =
            JsonObject(
                buildJsonObject {
                    put("\$type", "app.logdate.journal.draft#imageBlock")
                    put("id", imageId.toString())
                    put("createdAt", "2026-10-12T07:41:09.512Z")
                    put("presentation", "polaroid")
                },
            )

        val read =
            mapper
                .fromRecord(mapper.rkeyOf(full), recordWithBlock(image))
                .draft.blocks
                .single()

        assertEquals(PhotoPresentation.EdgeToEdge, (read as SerializableImageBlock).presentation)
    }

    @Test
    fun `a kind of block this version doesn't know refuses the record`() {
        val unknown =
            buildJsonObject {
                put("\$type", "app.logdate.journal.draft#sketchBlock")
                put("id", Uuid.random().toString())
                put("createdAt", "2026-10-12T07:41:09.512Z")
            }

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), recordWithBlock(unknown)) }

        assertTrue("sketchBlock" in failure.message.orEmpty())
    }

    @Test
    fun `a block id that is not a LogDate id refuses the record`() {
        val block = JsonObject(textBlockRecord() + ("id" to JsonPrimitive("block-1")))

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), recordWithBlock(block)) }
    }

    @Test
    fun `a block location with a place or an accuracy refuses the record`() {
        val geo =
            buildJsonObject {
                put("latitude", "1.5")
                put("longitude", "2.5")
            }
        val withAccuracy =
            buildJsonObject {
                put("geo", geo)
                put("accuracyCentimeters", 400)
            }
        val withPlace =
            buildJsonObject {
                put("geo", geo)
                putJsonObject("place") {
                    put("collection", "app.logdate.place.place")
                    put("rkey", Uuid.random().toString())
                }
            }

        listOf(withAccuracy, withPlace).forEach { location ->
            val block = JsonObject(textBlockRecord() + ("location" to location))

            assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), recordWithBlock(block)) }
        }
    }

    @Test
    fun `a block location with only a place is read as having no coordinates and refused`() {
        val placeOnly =
            buildJsonObject {
                putJsonObject("place") {
                    put("collection", "app.logdate.place.place")
                    put("rkey", Uuid.random().toString())
                }
            }
        val block = JsonObject(textBlockRecord() + ("location" to placeOnly))

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), recordWithBlock(block)) }
    }

    @Test
    fun `a journal that is not a journal record refuses the record`() {
        val record =
            JsonObject(
                mapper.toRecord(full) +
                    (
                        "journals" to
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("collection", "app.logdate.journal.draft")
                                        put("rkey", journalA.toString())
                                    },
                                ),
                            )
                    ),
            )

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), record) }
    }

    @Test
    fun `a record without updatedAt was last modified when it was created`() {
        val record = JsonObject(mapper.toRecord(full).filterKeys { it != "updatedAt" })

        assertEquals(created, mapper.fromRecord(mapper.rkeyOf(full), record).draft.lastModifiedAt)
    }

    @Test
    fun `a draft read from a record has no sync version of its own`() {
        assertEquals(0, roundTrip(full.copy(syncVersion = 9)).syncVersion)
    }

    @Test
    fun `a record of another type or without blocks or with a bad key is refused`() {
        val record = mapper.toRecord(full)

        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(mapper.rkeyOf(full), JsonObject(record + ("\$type" to JsonPrimitive("app.logdate.journal.journal"))))
        }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(
                mapper.rkeyOf(full),
                JsonObject(record.filterKeys { it != "blocks" }),
            )
        }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("not-an-id", record) }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(mapper.rkeyOf(full), JsonObject(record + ("createdAt" to JsonPrimitive("yesterday"))))
        }
    }
}
