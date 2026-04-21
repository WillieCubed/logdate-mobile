package app.logdate.server.crypto

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [EncryptionKeyring] implementations, focusing on the secure loading
 * and validation of server-side encryption keys.
 *
 * This suite covers:
 * - Loading of AES keys from environment variables with support for explicit
 *   key IDs and versioning.
 * - Strict validation of key material, including base64 decoding integrity
 *   and enforcement of standard AES key lengths (128, 192, or 256 bits).
 * - Proper handling of missing or malformed configuration to prevent insecure
 *   server startup.
 * - Verification of fallback and "no-op" implementations used in development
 *   or non-encrypted environments.
 */
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
