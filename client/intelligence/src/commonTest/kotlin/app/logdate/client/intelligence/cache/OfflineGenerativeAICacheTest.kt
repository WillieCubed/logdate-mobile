package app.logdate.client.intelligence.cache

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class OfflineGenerativeAICacheTest {

    @Test
    fun getEntry_expiresAfterTtl() = runTest {
        val clock = TestClock(Instant.parse("2024-01-01T00:00:00Z"))
        val dataSource = TestLocalDataSource()
        val cache = OfflineGenerativeAICache(
            dataSource = dataSource,
            clock = clock,
            config = AICacheConfig(memoryMaxEntries = 10, memoryMaxBytes = 10_000)
        )
        val request = requestFor("Hello world", ttlSeconds = 5)

        cache.putEntry(request, "summary")
        val fresh = cache.getEntry(request)
        assertNotNull(fresh)

        clock.advance(seconds = 6)
        val expired = cache.getEntry(request)
        assertNull(expired)
    }

    @Test
    fun putEntry_enforcesPersistentMaxEntries() = runTest {
        val clock = TestClock(Instant.parse("2024-01-01T00:00:00Z"))
        val dataSource = TestLocalDataSource()
        val cache = OfflineGenerativeAICache(
            dataSource = dataSource,
            clock = clock,
            config = AICacheConfig(
                memoryMaxEntries = 10,
                memoryMaxBytes = 10_000,
                persistentMaxEntries = 1,
                persistentMaxBytes = 10_000
            )
        )

        val firstRequest = requestFor("First", ttlSeconds = 60)
        val secondRequest = requestFor("Second", ttlSeconds = 60)

        cache.putEntry(firstRequest, "first-content")
        clock.advance(seconds = 1)
        cache.putEntry(secondRequest, "second-content")

        val remaining = dataSource.entries()
        assertEquals(1, remaining.size)
        assertEquals("second-content", remaining.first().content)
    }

    private fun requestFor(inputText: String, ttlSeconds: Long): GenerativeAICacheRequest {
        return GenerativeAICacheRequest(
            contentType = GenerativeAICacheContentType.Summary,
            inputText = inputText,
            providerId = "openai",
            model = "gpt-test",
            promptVersion = "v1",
            schemaVersion = "schema-v1",
            templateId = "summary",
            policy = AICachePolicy(ttlSeconds = ttlSeconds)
        )
    }

    private class TestClock(
        private var current: Instant
    ) : Clock {
        override fun now(): Instant = current

        fun advance(seconds: Long) {
            current = current + seconds.seconds
        }
    }

    private class TestLocalDataSource : AICacheLocalDataSource {
        private val store = mutableMapOf<String, GenerativeAICacheEntry>()

        override fun get(key: String): GenerativeAICacheEntry? = store[key]

        override fun set(key: String, entry: GenerativeAICacheEntry) {
            store[key] = entry
        }

        override fun remove(key: String) {
            store.remove(key)
        }

        override fun entries(): List<GenerativeAICacheEntry> = store.values.toList()

        override fun clear() {
            store.clear()
        }
    }
}
