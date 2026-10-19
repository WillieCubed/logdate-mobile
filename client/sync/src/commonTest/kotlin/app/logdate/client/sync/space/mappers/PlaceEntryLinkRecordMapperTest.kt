package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.location.VisitMemoryLink
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PlaceEntryLinkRecordMapperTest {
    private val mapper = PlaceEntryLinkRecordMapper
    private val note = "5a1d3c7e-9b2f-4e6a-8c0d-1f2e3a4b5c6d"

    @Test
    fun `links to every kind of evidence round-trip`() {
        listOf(
            "4b8e0d2a-1c3f-4e5a-9b7c-0d1e2f3a4b5c",
            "3d1f6a2b-7c8e-4f90-a1b2-c3d4e5f6a7b8",
            "derived:journey:account-1:6f1c2b0e-8a4d-4f3e-9b21-5c7d8e9f0a1b:sample-a:sample-b",
            "evidence with spaces / slashes and ünïcödé " + "x".repeat(400),
        ).forEach { evidence ->
            val link = VisitMemoryLink(note, evidence)
            assertEquals(link, mapper.roundTrip(link))
        }
    }

    @Test
    fun `a link keeps when it was made`() {
        val link = VisitMemoryLink(note, "abc", createdAt = kotlin.time.Instant.parse("2026-10-19T12:00:00Z"))

        assertEquals(link, mapper.roundTrip(link))
    }

    @Test
    fun `the key is the entry and the SHA-256 of the evidence id`() {
        val link = VisitMemoryLink(note, "abc")
        assertEquals("$note:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", mapper.rkeyOf(link))
        assertEquals(mapper.rkeyOf(link), mapper.rkeyOf(link.copy()))
        assertNotEquals(mapper.rkeyOf(link), mapper.rkeyOf(link.copy(targetEvidenceId = "abd")))
    }

    @Test
    fun `the record refers to the entry`() {
        val record = mapper.toRecord(VisitMemoryLink(note, "abc"))
        val entry = record.getValue("entry").jsonObject
        assertEquals("app.logdate.journal.entry", entry["collection"]?.jsonPrimitive?.content)
        assertEquals(note, entry["rkey"]?.jsonPrimitive?.content)
        assertEquals("abc", record["evidenceId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a record stored under another key is refused`() {
        val link = VisitMemoryLink(note, "abc")
        val record = mapper.toRecord(link)
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("random-id", record) }
        val moved = JsonObject(record + ("evidenceId" to JsonPrimitive("abd")))
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(link), moved) }
    }
}
