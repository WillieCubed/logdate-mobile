package app.logdate.client.repository.assistants

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class AssistantProtocolTest {
    @Test
    fun `wire results bound excerpts and source pages by Unicode code point`() {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val hit = AssistantSearchHit(id, null, "🌿".repeat(280), "logdate://entry/$id")
        AssistantProtocol.encodeSearchResult(AssistantSearchResult(listOf(hit), AssistantCoverage()))
        assertFailsWith<IllegalArgumentException> {
            AssistantProtocol.encodeSearchResult(
                AssistantSearchResult(listOf(hit.copy(excerpt = "🌿".repeat(281))), AssistantCoverage()),
            )
        }
        val page = AssistantSourcePage(id, "🌿".repeat(4000), "logdate://entry/$id", AssistantCoverage())
        AssistantProtocol.encodeSourcePage(page)
        assertFailsWith<IllegalArgumentException> { AssistantProtocol.encodeSourcePage(page.copy(text = "🌿".repeat(4001))) }
    }

    @Test
    fun `wire results bound source count and preserve dates and coverage`() {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val time = kotlin.time.Instant.parse("2026-10-20T12:00:00Z")
        val hit = AssistantSearchHit(id, null, "garden", "logdate://entry/$id", eventTime = time, createdAt = time)
        val result = AssistantSearchResult(List(10) { hit }, AssistantCoverage())
        val encoded = AssistantProtocol.encodeSearchResult(result)
        assertEquals(true, encoded.contains("\"eventTime\":\"2026-10-20T12:00:00Z\""))
        assertEquals(true, encoded.contains("\"createdAt\":\"2026-10-20T12:00:00Z\""))
        assertEquals(true, encoded.contains("\"partial\":true"))
        assertFailsWith<IllegalArgumentException> { AssistantProtocol.encodeSearchResult(result.copy(sources = List(11) { hit })) }
    }

    @Test
    fun `prepare decodes exact literal content without granting approval`() {
        val result =
            AssistantProtocol.decodePrepareEntry(
                """{"text":"Keep this exact thought.\nIncluding this line.","idempotencyKey":"thought-1"}""",
            )
        val request = assertIs<AssistantDecodeResult.Decoded<AssistantPrepareEntryRequest>>(result).value
        assertEquals("Keep this exact thought.\nIncluding this line.", request.text)
        assertEquals("thought-1", request.idempotencyKey)
        assertEquals(null, request.eventTime)
        assertEquals(null, request.sourceUrl)
    }

    @Test
    fun `unknown approval destination credentials and extra fields are rejected`() {
        listOf(
            "\"approved\":true",
            "\"destinationJournalId\":\"private\"",
            "\"credentials\":\"secret\"",
            "\"unexpected\":{}",
        ).forEach { extra ->
            assertIs<AssistantDecodeResult.Invalid>(
                AssistantProtocol.decodePrepareEntry("""{"text":"thought","idempotencyKey":"key",$extra}"""),
            )
        }
    }

    @Test
    fun `strict decoder rejects malformed types missing fields and lenient syntax`() {
        listOf(
            """{"text":123,"idempotencyKey":"key"}""",
            """{"text":"thought"}""",
            """{text:"thought",idempotencyKey:"key"}""",
            """{"text":"thought","idempotencyKey":"key",}""",
        ).forEach { assertIs<AssistantDecodeResult.Invalid>(AssistantProtocol.decodePrepareEntry(it)) }
    }

    @Test
    fun `read and status requests use their own strict contracts`() {
        val id = "00000000-0000-0000-0000-000000000001"
        val search = AssistantProtocol.decodeSearch("""{"query":"garden","limit":10,"fromDate":"2026-01-01"}""")
        assertEquals("garden", assertIs<AssistantDecodeResult.Decoded<AssistantSearchRequest>>(search).value.query)
        val source = AssistantProtocol.decodeSourceRead("""{"sourceId":"$id","limit":4000}""")
        assertEquals(Uuid.parse(id), assertIs<AssistantDecodeResult.Decoded<AssistantSourceReadRequest>>(source).value.sourceId)
        assertIs<AssistantDecodeResult.Invalid>(AssistantProtocol.decodeSourceRead("""{"sourceId":"invalid"}"""))
        assertIs<AssistantDecodeResult.Invalid>(AssistantProtocol.decodeSearch("""{"query":"garden","grantId":"other"}"""))
        assertIs<AssistantDecodeResult.Decoded<AssistantOperationStatusRequest>>(
            AssistantProtocol.decodeOperationStatus("""{"operationId":"own-operation"}"""),
        )
    }

    @Test
    fun `capabilities serialize only to the approved scope strings`() {
        assertEquals("\"memories:read\"", AssistantProtocol.encodeCapability(AssistantCapability.READ_MEMORIES))
        assertEquals("\"entries:add\"", AssistantProtocol.encodeCapability(AssistantCapability.PREPARE_ENTRIES))
    }

    @Test
    fun `pending and saved have distinct serialized states and saved identity`() {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val pending = AssistantProtocol.encodeEntryOperation(AssistantEntryOperation.Pending("operation", "logdate://review/operation"))
        val saved = AssistantProtocol.encodeEntryOperation(AssistantEntryOperation.Saved("operation", id, "logdate://entry/$id"))
        assertEquals(true, pending.contains("\"status\":\"pending\""))
        assertFalse(pending.contains("sourceId"))
        assertEquals(true, saved.contains("\"status\":\"saved\""))
        assertEquals(true, saved.contains(id.toString()))
        listOf(AssistantEntryOperation.Rejected("operation"), AssistantEntryOperation.Expired("operation")).forEach {
            assertFalse(AssistantProtocol.encodeEntryOperation(it).contains("\"status\":\"saved\""))
        }
    }

    @Test
    fun `default diagnostic strings omit query source and draft payloads`() {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val sensitive = "do not log this private body"
        val values =
            listOf(
                AssistantSearchRequest(sensitive, cursor = "private-cursor"),
                AssistantPrepareEntryRequest(sensitive, "private-retry-key", sourceUrl = "https://secret.example"),
                AssistantSearchHit(id, sensitive, sensitive, "logdate://entry/$id"),
                AssistantSourcePage(id, sensitive, "logdate://entry/$id", AssistantCoverage()),
                AssistantDecodeResult.Decoded(AssistantPrepareEntryRequest(sensitive, "private-retry-key")),
            )
        values.forEach {
            assertFalse(it.toString().contains(sensitive))
            assertFalse(it.toString().contains("private-"))
            assertFalse(it.toString().contains("secret.example"))
        }
        val invalid = AssistantProtocol.decodePrepareEntry("""{"text":"$sensitive","approved":true}""")
        assertIs<AssistantDecodeResult.Invalid>(invalid)
        assertFalse(invalid.toString().contains(sensitive))
    }
}
