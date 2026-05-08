package app.logdate.server.crypto

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptionKeyringTest {
    @Test
    fun `environment keyring default constructor path is invocable`() {
        val missing = kotlin.runCatching { EnvironmentKeyring(System::getenv) }.exceptionOrNull()
        assertTrue(missing is IllegalStateException)
    }

    @Test
    fun `environment keyring loads active key and optional key id`() {
        val keyBytes = ByteArray(32) { index -> index.toByte() }
        val encoded = Base64.getEncoder().encodeToString(keyBytes)
        val env =
            mapOf(
                "SERVER_ENCRYPTION_KEY" to encoded,
                "SERVER_ENCRYPTION_KEY_ID" to "active-key",
            )

        val keyring = EnvironmentKeyring { env[it] }
        val active = keyring.getActiveKey()
        assertEquals("active-key", active.keyId)
        assertTrue(active.keyBytes.contentEquals(keyBytes))
        assertNotNull(keyring.getKey("active-key"))
        assertNull(keyring.getKey("missing"))
    }

    @Test
    fun `environment keyring validates missing malformed and invalid length keys`() {
        val missing = kotlin.runCatching { EnvironmentKeyring { null } }.exceptionOrNull()
        assertTrue(missing is IllegalStateException)

        val malformed =
            kotlin
                .runCatching {
                    EnvironmentKeyring {
                        when (it) {
                            "SERVER_ENCRYPTION_KEY" -> "not-base64"
                            else -> null
                        }
                    }
                }.exceptionOrNull()
        assertTrue(malformed is IllegalArgumentException)
        assertTrue(malformed.message.orEmpty().contains("base64"))

        val invalidLen =
            kotlin
                .runCatching {
                    EnvironmentKeyring {
                        when (it) {
                            "SERVER_ENCRYPTION_KEY" -> Base64.getEncoder().encodeToString(ByteArray(7))
                            else -> null
                        }
                    }
                }.exceptionOrNull()
        assertTrue(invalidLen is IllegalArgumentException)
        assertTrue(invalidLen.message.orEmpty().contains("16, 24, or 32"))
    }

    @Test
    fun `fromEnvironmentOrNull and no-op keyring behavior`() {
        assertNull(EnvironmentKeyring.fromEnvironmentOrNull { null })

        val keyBytes = ByteArray(16) { 1 }
        val encoded = Base64.getEncoder().encodeToString(keyBytes)
        val keyring =
            EnvironmentKeyring.fromEnvironmentOrNull {
                when (it) {
                    "SERVER_ENCRYPTION_KEY" -> encoded
                    else -> null
                }
            }
        assertNotNull(keyring)
        assertEquals("default", keyring.getActiveKey().keyId)

        assertEquals("noop", NoOpKeyring.getActiveKey().keyId)
        assertEquals("noop", NoOpKeyring.getKey("any").keyId)
    }
}
