package app.logdate.client.intelligence.cache

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the stability and correctness of the [DefaultAICacheKeyStrategy].
 *
 * This suite ensures that the cache key generation logic is deterministic, producing
 * identical keys for logically equivalent inputs even when they differ in non-functional
 * ways like extra whitespace in the input text.
 */
class AICacheKeyStrategyTest {
    @Test
    fun createKey_withSameInput_producesStableKey() {
        val policy = AICachePolicy(ttlSeconds = 60)
        val input =
            AICacheKeyInput(
                contentType = GenerativeAICacheContentType.Summary,
                inputText = "Hello world",
                providerId = "openai",
                model = "gpt-test",
                promptVersion = "v1",
                schemaVersion = "schema-v1",
                templateId = "summary",
                policy = policy,
            )
        val strategy = DefaultAICacheKeyStrategy()

        val first = strategy.createKey(input)
        val second = strategy.createKey(input)

        assertEquals(first, second)
    }

    @Test
    fun createKey_withNormalizedWhitespace_producesSameKey() {
        val policy = AICachePolicy(ttlSeconds = 60)
        val strategy = DefaultAICacheKeyStrategy()

        val first =
            strategy.createKey(
                AICacheKeyInput(
                    contentType = GenerativeAICacheContentType.Summary,
                    inputText = "Hello   world",
                    providerId = "openai",
                    model = "gpt-test",
                    promptVersion = "v1",
                    schemaVersion = "schema-v1",
                    templateId = "summary",
                    policy = policy,
                ),
            )
        val second =
            strategy.createKey(
                AICacheKeyInput(
                    contentType = GenerativeAICacheContentType.Summary,
                    inputText = "Hello world",
                    providerId = "openai",
                    model = "gpt-test",
                    promptVersion = "v1",
                    schemaVersion = "schema-v1",
                    templateId = "summary",
                    policy = policy,
                ),
            )

        assertEquals(first, second)
    }
}
