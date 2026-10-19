package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MembershipRecordMapperTest {
    private val mapper = MembershipRecordMapper()
    private val journalId = Uuid.parse("5b2c0d6e-8f41-4a7b-9c3d-2e1f0a9b8c7d")
    private val entryId = Uuid.parse("0b8f3c1e-6a2d-4e9f-8c7b-1d2e3f4a5b6c")
    private val added = Instant.parse("2026-10-12T07:41:09.512000001Z")

    private fun roundTrip(membership: JournalMembership): JournalMembership {
        val record = mapper.toRecord(membership)
        assertValidRecord(mapper.collection, record)
        return mapper.fromRecord(mapper.rkeyOf(membership), record)
    }

    @Test
    fun `a membership with a time of adding round-trips`() {
        val membership = JournalMembership(journalId, entryId, createdAt = added)

        assertEquals(membership, roundTrip(membership))
        assertEquals(
            "2026-10-12T07:41:09.512000001Z",
            mapper
                .toRecord(membership)
                .getValue("createdAt")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a membership without a time leaves createdAt out so every device writes the same record`() {
        val membership = JournalMembership(journalId, entryId)

        assertEquals(membership, roundTrip(membership))
        assertEquals(setOf("\$type", "journal", "entry"), mapper.toRecord(membership).keys)
        assertEquals(mapper.toRecord(membership), mapper.toRecord(JournalMembership(journalId, entryId, syncVersion = 12)))
    }

    @Test
    fun `the key is the journal id and the entry id joined by a colon`() {
        assertEquals("$journalId:$entryId", mapper.rkeyOf(JournalMembership(journalId, entryId)))
        assertEquals("app.logdate.journal.membership", mapper.collection)
    }

    @Test
    fun `the record refers to the journal and the entry by their keys`() {
        val record = mapper.toRecord(JournalMembership(journalId, entryId))

        assertEquals(
            "app.logdate.journal.journal",
            record
                .getValue("journal")
                .jsonObject
                .getValue("collection")
                .jsonPrimitive.content,
        )
        assertEquals(
            journalId.toString(),
            record
                .getValue("journal")
                .jsonObject
                .getValue("rkey")
                .jsonPrimitive.content,
        )
        assertEquals(
            "app.logdate.journal.entry",
            record
                .getValue("entry")
                .jsonObject
                .getValue("collection")
                .jsonPrimitive.content,
        )
        assertEquals(
            entryId.toString(),
            record
                .getValue("entry")
                .jsonObject
                .getValue("rkey")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a key in uppercase names the same membership`() {
        val membership = JournalMembership(journalId, entryId, added)

        assertEquals(membership, mapper.fromRecord(mapper.rkeyOf(membership).uppercase(), mapper.toRecord(membership)))
    }

    @Test
    fun `a membership whose key names another pair is refused`() {
        val record = mapper.toRecord(JournalMembership(journalId, entryId))
        val other = MembershipRecordMapper.rkeyFor(journalId, Uuid.random())

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(other, record) }
    }

    @Test
    fun `a membership that names something other than a journal and an entry is refused`() {
        val notAJournal =
            JsonObject(
                mapper.toRecord(JournalMembership(journalId, entryId)) +
                    (
                        "journal" to
                            buildJsonObject {
                                put("collection", "app.logdate.journal.draft")
                                put("rkey", journalId.toString())
                            }
                    ),
            )

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("$journalId:$entryId", notAJournal) }

        assertFalse(failure.retryable)
    }

    @Test
    fun `a time that is not a datetime is refused`() {
        val record = JsonObject(mapper.toRecord(JournalMembership(journalId, entryId)) + ("createdAt" to JsonPrimitive("soon")))

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("$journalId:$entryId", record) }
    }

    @Test
    fun `a record without an entry is refused`() {
        val record = JsonObject(mapper.toRecord(JournalMembership(journalId, entryId)).filterKeys { it != "entry" })

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("$journalId:$entryId", record) }
    }

    @Test
    fun `a key that is not two LogDate ids is refused`() {
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord("not-a-key", mapper.toRecord(JournalMembership(journalId, entryId)))
        }
    }
}
