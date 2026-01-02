package app.logdate.client.intelligence.cache

import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AICacheEntryCodecTest {

    @Test
    fun decode_withInvalidPayload_returnsNull() {
        val codec = JsonAICacheEntryCodec()

        val decoded = codec.decode("not-json")

        assertNull(decoded)
    }

    @Test
    fun encodeAndDecode_roundTripsEntry() {
        val codec = JsonAICacheEntryCodec()
        val now = Clock.System.now()
        val entry = GenerativeAICacheEntry(
            key = "summary-123",
            content = "content",
            lastUpdated = now,
            metadata = GenerativeAICacheEntryMetadata(
                contentTypeId = GenerativeAICacheContentType.Summary.id,
                providerId = "openai",
                model = "gpt-test",
                promptVersion = "v1",
                schemaVersion = "schema-v1",
                templateId = "summary",
                ttlSeconds = 60,
                expiresAt = now + 60.seconds,
                sourceHash = "hash",
                debugPrefix = "summary-openai",
                contentBytes = 7,
            )
        )

        val encoded = codec.encode(entry)
        val decoded = codec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(entry.key, decoded.key)
        assertEquals(entry.content, decoded.content)
        assertEquals(entry.metadata.contentTypeId, decoded.metadata.contentTypeId)
    }
}
