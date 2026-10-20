package app.logdate.client.domain.assistants

import app.logdate.client.repository.assistants.AssistantOperationStatusRequest
import app.logdate.client.repository.assistants.AssistantPrepareEntryRequest
import app.logdate.client.repository.assistants.AssistantSearchRequest
import app.logdate.client.repository.assistants.AssistantSourceReadRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AssistantInputValidationTest {
    private val validation = AssistantInputValidation()
    private val thought = AssistantPrepareEntryRequest("A quiet afternoon.", "thought-1")

    @Test
    fun `parser failures return redacted invalid input instead of throwing submitted links`() {
        listOf(
            "https://private.example:abc/secret",
            "https://private.example:65536/secret",
            "https://private.example/%ZZ-secret",
        ).forEach { url ->
            val result = validation.prepareEntry(thought.copy(sourceUrl = url))
            assertEquals(AssistantInputDecision.Invalid(AssistantInputError.INVALID_SOURCE_URL), result)
            assertEquals(false, result.toString().contains("private"))
            assertEquals(false, result.toString().contains("secret"))
        }
    }

    @Test
    fun `malformed authorities are rejected while international and IPv6 links remain valid`() {
        listOf(
            "https://bücher.example/本",
            "https://例え.テスト/path",
            "https://[::1]:8443/path",
            "https://[2001:db8::1]/path",
            "https://[::ffff:192.0.2.1]/path",
            "https://example.com/path%20with%20spaces",
        ).forEachIndexed { index, url ->
            assertEquals(AssistantInputDecision.Valid, validation.prepareEntry(thought.copy(sourceUrl = url)), "valid URL $index")
        }
        listOf(
            "https://[:::]",
            "https://exa%mple.com",
            "https://:443/path",
            "https://[1:2:3:4:5:6:7:8:9]",
            "https://[2001::db8::1]",
            "https://[::ffff:999.0.2.1]",
            "https://[::1]extra",
            "https://example.com/%0",
            "https://[192.0.2.1::]",
            "https://\"bad\".example",
        ).forEachIndexed { index, url ->
            assertEquals(
                AssistantInputDecision.Invalid(AssistantInputError.INVALID_SOURCE_URL),
                validation.prepareEntry(thought.copy(sourceUrl = url)),
                "invalid URL $index",
            )
        }
    }

    @Test
    fun `query limit counts supplementary Unicode code points`() {
        assertValid(validation.search(AssistantSearchRequest("🌿".repeat(256))))
        assertInvalid(validation.search(AssistantSearchRequest("🌿".repeat(257))))
        assertInvalid(validation.search(AssistantSearchRequest("  ")))
        assertInvalid(validation.search(AssistantSearchRequest("\uD800")))
    }

    @Test
    fun `search accepts one through ten sources and bounded opaque cursors`() {
        listOf(1, 10).forEach { assertValid(validation.search(AssistantSearchRequest("garden", limit = it))) }
        listOf(0, 11).forEach { assertInvalid(validation.search(AssistantSearchRequest("garden", limit = it))) }
        assertValid(validation.search(AssistantSearchRequest("garden", cursor = "a".repeat(512))))
        listOf("", "a".repeat(513), "cursor with spaces").forEach {
            assertInvalid(validation.search(AssistantSearchRequest("garden", cursor = it)))
        }
    }

    @Test
    fun `calendar dates must be real and ordered`() {
        assertValid(validation.search(AssistantSearchRequest("garden", fromDate = "2026-02-01", throughDate = "2026-02-28")))
        assertValid(validation.search(AssistantSearchRequest("garden", fromDate = "2026-02-01", throughDate = "2026-02-01")))
        listOf("2026-02-30", "tomorrow", "2026-13-01", "").forEach {
            assertInvalid(validation.search(AssistantSearchRequest("garden", fromDate = it)))
            assertInvalid(validation.search(AssistantSearchRequest("garden", throughDate = it)))
        }
        assertInvalid(validation.search(AssistantSearchRequest("garden", fromDate = "2026-02-02", throughDate = "2026-02-01")))
    }

    @Test
    fun `draft text and idempotency keys have exact boundaries`() {
        assertValid(validation.prepareEntry(thought.copy(text = "🌿".repeat(20_000))))
        assertInvalid(validation.prepareEntry(thought.copy(text = "🌿".repeat(20_001))))
        assertInvalid(validation.prepareEntry(thought.copy(text = " \n ")))
        assertInvalid(validation.prepareEntry(thought.copy(text = "\uDC00")))
        assertValid(validation.prepareEntry(thought.copy(idempotencyKey = "a")))
        assertValid(validation.prepareEntry(thought.copy(idempotencyKey = "a".repeat(128))))
        listOf("", "a".repeat(129), "\n").forEach {
            assertInvalid(validation.prepareEntry(thought.copy(idempotencyKey = it)))
        }
    }

    @Test
    fun `source links permit only absolute web URLs without credentials`() {
        listOf(null, "https://example.com/a?b=1#c", "http://example.com").forEach {
            assertValid(validation.prepareEntry(thought.copy(sourceUrl = it)))
        }
        listOf(
            "javascript:alert(1)",
            "file:///journal",
            "data:text/plain,secret",
            "/relative",
            "https://",
            "https://user:secret@example.com",
            "https://example.com/\nsecret",
            "https://[bad]",
        ).forEachIndexed {
            index,
            url,
            ->
            assertIs<AssistantInputDecision.Invalid>(validation.prepareEntry(thought.copy(sourceUrl = url)), "source URL case $index")
        }
    }

    @Test
    fun `event times and source page sizes are validated`() {
        assertValid(validation.prepareEntry(thought.copy(eventTime = "2026-10-20T12:00:00Z")))
        assertInvalid(validation.prepareEntry(thought.copy(eventTime = "2026-02-30T12:00:00Z")))
        assertInvalid(validation.prepareEntry(thought.copy(eventTime = "tomorrow")))
        val request = AssistantSourceReadRequest(AssistantAuthorityFixture.otherId)
        listOf(1, 4000).forEach { assertValid(validation.sourceRead(request.copy(limit = it))) }
        listOf(0, 4001).forEach { assertInvalid(validation.sourceRead(request.copy(limit = it))) }
        assertInvalid(validation.sourceRead(request.copy(cursor = "a".repeat(513))))
    }

    @Test
    fun `operation status accepts only bounded opaque identifiers`() {
        assertValid(validation.operationStatus(AssistantOperationStatusRequest("a".repeat(128))))
        listOf("", "a".repeat(129), "operation with spaces", "../other").forEach {
            assertInvalid(validation.operationStatus(AssistantOperationStatusRequest(it)))
        }
    }

    @Test
    fun `input failures contain no submitted text`() {
        val result = validation.prepareEntry(thought.copy(text = "sensitive".repeat(20_001)))
        assertInvalid(result)
        assertEquals(false, result.toString().contains("sensitive"))
    }

    private fun assertValid(result: AssistantInputDecision) = assertEquals(AssistantInputDecision.Valid, result)

    private fun assertInvalid(result: AssistantInputDecision) = assertIs<AssistantInputDecision.Invalid>(result)
}
