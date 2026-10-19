package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.Journal
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class JournalRecordMapperTest {
    private val mapper = JournalRecordMapper()
    private val id = Uuid.parse("5b2c0d6e-8f41-4a7b-9c3d-2e1f0a9b8c7d")
    private val survivor = Uuid.parse("0b8f3c1e-6a2d-4e9f-8c7b-1d2e3f4a5b6c")
    private val created = Instant.parse("2026-10-12T07:41:09.512Z")
    private val updated = Instant.parse("2026-10-13T21:02:00.000000001Z")

    private val journal =
        Journal(
            id = id,
            title = "Lake trips 🏊 日本語",
            description = "Every swim\nthis summer",
            isFavorited = true,
            created = created,
            lastUpdated = updated,
            coverImageUri = uploadedFile(id, 'a', "image/jpeg"),
        )
    private val full = SpaceJournal(journal)

    private fun roundTrip(item: SpaceJournal): SpaceJournal {
        val record = mapper.toRecord(item)
        assertValidRecord(mapper.collection, record)
        return mapper.fromRecord(mapper.rkeyOf(item), record)
    }

    private fun mergedRecord(survivorRef: JsonObject): JsonObject = JsonObject(mapper.toRecord(full) + ("mergedInto" to survivorRef))

    private fun journalRef(rkey: Uuid) =
        buildJsonObject {
            put("collection", "app.logdate.journal.journal")
            put("rkey", rkey.toString())
        }

    @Test
    fun `every field of a journal round-trips`() {
        assertEquals(full, roundTrip(full))
    }

    @Test
    fun `a journal with only its required fields round-trips`() {
        val bare = SpaceJournal(Journal(id = id, title = "", created = created, lastUpdated = created))

        assertEquals(bare, roundTrip(bare))
    }

    @Test
    fun `a journal merged into another round-trips with its survivor`() {
        val merged = SpaceJournal(journal, mergedInto = survivor)

        assertEquals(merged, roundTrip(merged))
        assertEquals(
            survivor.toString(),
            mapper
                .toRecord(merged)
                .getValue("mergedInto")
                .jsonObject
                .getValue("rkey")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `an empty description and an unfavorited journal are left out of the record`() {
        val record = mapper.toRecord(SpaceJournal(journal.copy(description = "", isFavorited = false, coverImageUri = null)))

        assertEquals(setOf("\$type", "title", "createdAt", "updatedAt"), record.keys)
    }

    @Test
    fun `the record is keyed by the lowercase journal id and typed as a journal`() {
        assertEquals("5b2c0d6e-8f41-4a7b-9c3d-2e1f0a9b8c7d", mapper.rkeyOf(full))
        assertEquals("app.logdate.journal.journal", mapper.collection)
        assertEquals(
            "app.logdate.journal.journal",
            mapper
                .toRecord(full)
                .getValue("\$type")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a record key in uppercase names the same journal`() {
        assertEquals(full, mapper.fromRecord(mapper.rkeyOf(full).uppercase(), mapper.toRecord(full)))
    }

    @Test
    fun `times keep their full precision`() {
        assertEquals(
            "2026-10-13T21:02:00.000000001Z",
            mapper
                .toRecord(full)
                .getValue("updatedAt")
                .jsonPrimitive.content,
        )
        assertEquals(updated, roundTrip(full).journal.lastUpdated)
    }

    @Test
    fun `a journal read from a record has no sync version of its own`() {
        assertEquals(0, roundTrip(SpaceJournal(journal.copy(syncVersion = 41))).journal.syncVersion)
    }

    @Test
    fun `a record without updatedAt was last updated when it was created`() {
        val record = JsonObject(mapper.toRecord(full).filterKeys { it != "updatedAt" })

        assertEquals(created, mapper.fromRecord(mapper.rkeyOf(full), record).journal.lastUpdated)
    }

    @Test
    fun `a title and description with a very long text round-trip`() {
        val long = SpaceJournal(journal.copy(title = "é".repeat(100_000), description = "é".repeat(100_000)))

        assertEquals(long, roundTrip(long))
    }

    @Test
    fun `the cover is the uploaded file and its blob is listed`() {
        val record = mapper.toRecord(full)

        assertEquals(listOf(cidWith('a')), mapper.blobCidsOf(full))
        assertEquals(
            "image/jpeg",
            record
                .getValue("cover")
                .jsonObject
                .getValue("mimeType")
                .jsonPrimitive.content,
        )
        assertEquals(emptyList(), mapper.blobCidsOf(SpaceJournal(journal.copy(coverImageUri = null))))
    }

    @Test
    fun `a cover that is not uploaded yet can be tried again later`() {
        val local = SpaceJournal(journal.copy(coverImageUri = "logdate-media://covers/lake.jpg"))

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(local) }
        val listing = assertFailsWith<SpaceRecordMappingException> { mapper.blobCidsOf(local) }

        assertTrue(failure.retryable)
        assertTrue(listing.retryable)
        assertFalse("lake.jpg" in failure.message.orEmpty())
    }

    @Test
    fun `a journal that names itself as its survivor is refused without showing its title`() {
        val record =
            JsonObject(mapper.toRecord(SpaceJournal(journal.copy(title = "Private plans"))) + ("mergedInto" to journalRef(id)))

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), record) }

        assertFalse(failure.retryable)
        assertFalse("Private plans" in failure.message.orEmpty())
    }

    @Test
    fun `a survivor that is not a journal is refused`() {
        val notAJournal =
            buildJsonObject {
                put("collection", "app.logdate.journal.draft")
                put("rkey", survivor.toString())
            }

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), mergedRecord(notAJournal)) }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(
                mapper.rkeyOf(full),
                mergedRecord(
                    buildJsonObject {
                        put("collection", "app.logdate.journal.journal")
                        put("rkey", "not-an-id")
                    },
                ),
            )
        }
    }

    @Test
    fun `a record of another type is refused`() {
        val record = JsonObject(mapper.toRecord(full) + ("\$type" to JsonPrimitive("app.logdate.journal.draft")))

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), record) }
    }

    @Test
    fun `a record without a title is refused`() {
        val record = JsonObject(mapper.toRecord(full).filterKeys { it != "title" })

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), record) }
    }

    @Test
    fun `a key that is not a LogDate id is refused`() {
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("self", mapper.toRecord(full)) }
    }

    @Test
    fun `a time that is not a datetime is refused`() {
        val record = JsonObject(mapper.toRecord(full) + ("createdAt" to JsonPrimitive("yesterday")))

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), record) }
    }

    @Test
    fun `a cover another device wrote reads as the file it names`() {
        val record =
            JsonObject(
                mapper.toRecord(full) +
                    (
                        "cover" to
                            buildJsonObject {
                                put("\$type", "app.logdate.defs#media")
                                putJsonObject("blob") {
                                    put("\$type", "blob")
                                    putJsonObject("ref") { put("\$link", cidWith('b')) }
                                    put("mimeType", "application/octet-stream")
                                    put("size", 65_536)
                                }
                                put("mimeType", "image/png")
                            }
                    ),
            )

        val read = mapper.fromRecord(mapper.rkeyOf(full), record)

        assertEquals(uploadedFile(id, 'b', "image/png", size = null), read.journal.coverImageUri)
        assertNull(read.mergedInto)
    }
}
