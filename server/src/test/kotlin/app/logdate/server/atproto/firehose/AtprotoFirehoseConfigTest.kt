package app.logdate.server.atproto.firehose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The event stream is off unless a deployment turns it on, and relay hosts are only read when it is.
 */
class AtprotoFirehoseConfigTest {
    private fun environment(vararg pairs: Pair<String, String>): (String) -> String? {
        val values = pairs.toMap()
        return { name -> values[name] }
    }

    @Test
    fun `the stream is off and asks no relays when nothing is configured`() {
        val config = AtprotoFirehoseConfig.fromEnvironment(environment())

        assertFalse(config.enabled)
        assertTrue(config.relayHosts.isEmpty())
    }

    @Test
    fun `only the exact value true turns the stream on`() {
        listOf("true", "TRUE", " true ").forEach { value ->
            assertTrue(AtprotoFirehoseConfig.fromEnvironment(environment("ATPROTO_FIREHOSE_ENABLED" to value)).enabled, value)
        }
        listOf("", "false", "1", "yes", "enabled").forEach { value ->
            assertFalse(AtprotoFirehoseConfig.fromEnvironment(environment("ATPROTO_FIREHOSE_ENABLED" to value)).enabled, value)
        }
    }

    @Test
    fun `relay hosts are a comma separated list of hostnames`() {
        val config =
            AtprotoFirehoseConfig.fromEnvironment(
                environment(
                    "ATPROTO_FIREHOSE_ENABLED" to "true",
                    "ATPROTO_RELAY_HOSTS" to " Relay1.Example.com, relay2.example.net ,,relay1.example.com",
                ),
            )

        assertEquals(listOf("relay1.example.com", "relay2.example.net"), config.relayHosts)
    }

    @Test
    fun `relay hosts are ignored while the stream is off`() {
        val config = AtprotoFirehoseConfig.fromEnvironment(environment("ATPROTO_RELAY_HOSTS" to "relay.example.com"))

        assertTrue(config.relayHosts.isEmpty())
    }

    @Test
    fun `a relay host that is not a hostname stops the server at startup`() {
        val invalid = listOf("https://relay.example.com", "relay.example.com/path", "relay", "relay.example.com:443", "-bad.example.com")
        invalid.forEach { value ->
            assertFailsWith<IllegalArgumentException>(value) {
                AtprotoFirehoseConfig.fromEnvironment(
                    environment("ATPROTO_FIREHOSE_ENABLED" to "true", "ATPROTO_RELAY_HOSTS" to value),
                )
            }
        }
    }

    @Test
    fun `the limits have sensible defaults and refuse nonsense`() {
        val config = AtprotoFirehoseConfig()

        assertEquals(7, config.retention.inWholeDays)
        assertTrue(config.maxSubscribers > 0)
        assertFailsWith<IllegalArgumentException> { AtprotoFirehoseConfig(maxSubscribers = 0) }
    }
}
