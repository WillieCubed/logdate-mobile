package app.logdate.client.device.storage

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Tests for [AndroidSecureStorage]'s handling of a broken encrypted preferences file.
 *
 * The file this class opens holds the user's identity encryption key and recovery phrase.
 * This suite locks down that a broken read is never treated as "stale and safe to delete":
 * initialization must fail loudly instead, so a transient KeyStore or Tink hiccup can never
 * silently destroy the user's only copy of their key.
 */
class AndroidSecureStorageTest {
    @Test
    fun `GeneralSecurityException during initialization is rethrown, not swallowed and retried`() {
        var attempts = 0
        val exception = GeneralSecurityException("Keystore is temporarily unavailable")

        val thrown =
            assertFailsWith<SecureStorageInitializationException> {
                AndroidSecureStorage(
                    encryptedPrefsFactory = {
                        attempts++
                        throw exception
                    },
                )
            }

        assertEquals(1, attempts, "the broken creation step must not be retried automatically")
        assertSame(exception, thrown.cause)
    }

    @Test
    fun `KeyStoreException during initialization is rethrown, not swallowed and retried`() {
        var attempts = 0
        val exception = KeyStoreException("Keystore entry is corrupt")

        val thrown =
            assertFailsWith<SecureStorageInitializationException> {
                AndroidSecureStorage(
                    encryptedPrefsFactory = {
                        attempts++
                        throw exception
                    },
                )
            }

        assertEquals(1, attempts, "the broken creation step must not be retried automatically")
        assertSame(exception, thrown.cause)
    }

    @Test
    fun `Tink-shaded InvalidProtocolBufferException is rethrown, not swallowed and retried`() {
        var attempts = 0
        val exception = InvalidProtocolBufferException("Corrupt keyset")

        val thrown =
            assertFailsWith<SecureStorageInitializationException> {
                AndroidSecureStorage(
                    encryptedPrefsFactory = {
                        attempts++
                        throw exception
                    },
                )
            }

        assertEquals(1, attempts, "the broken creation step must not be retried automatically")
        assertSame(exception, thrown.cause)
    }

    @Test
    fun `a wrapped InvalidProtocolBufferException cause is also rethrown, not swallowed and retried`() {
        var attempts = 0
        val cause = InvalidProtocolBufferException("Corrupt keyset")
        val wrapper = RuntimeException("Tink internal failure", cause)

        val thrown =
            assertFailsWith<SecureStorageInitializationException> {
                AndroidSecureStorage(
                    encryptedPrefsFactory = {
                        attempts++
                        throw wrapper
                    },
                )
            }

        assertEquals(1, attempts, "the broken creation step must not be retried automatically")
        assertSame(wrapper, thrown.cause)
    }

    @Test
    fun `an exception unrelated to keystore or protobuf corruption is propagated unchanged`() {
        val exception = IllegalStateException("unrelated failure")

        val thrown =
            assertFailsWith<IllegalStateException> {
                AndroidSecureStorage(encryptedPrefsFactory = { throw exception })
            }

        assertSame(exception, thrown)
    }

    @Test
    fun `successful initialization reads values already present in the file`() =
        runTest {
            val prefs =
                mockk<SharedPreferences> {
                    every { all } returns mapOf(IDENTITY_KEY_PREF to "existing-identity-key")
                    every { getString(IDENTITY_KEY_PREF, null) } returns "existing-identity-key"
                }

            val storage = AndroidSecureStorage(encryptedPrefsFactory = { prefs })

            assertEquals("existing-identity-key", storage.getString(IDENTITY_KEY_PREF))
        }

    @Test
    fun `a key already written to the file survives a failed attempt and is readable on retry`() =
        runTest {
            // First attempt hits a transient failure. Construction must fail rather than
            // delete anything, so nothing about the file changes because of this attempt.
            assertFailsWith<SecureStorageInitializationException> {
                AndroidSecureStorage(
                    encryptedPrefsFactory = {
                        throw GeneralSecurityException("Keystore is temporarily unavailable")
                    },
                )
            }

            // A later retry, once the transient failure clears, still finds the identity key
            // that was already written to the file — it was never touched by the failure above.
            val prefs =
                mockk<SharedPreferences> {
                    every { all } returns mapOf(IDENTITY_KEY_PREF to "existing-identity-key")
                    every { getString(IDENTITY_KEY_PREF, null) } returns "existing-identity-key"
                }
            val storage = AndroidSecureStorage(encryptedPrefsFactory = { prefs })

            assertEquals("existing-identity-key", storage.getString(IDENTITY_KEY_PREF))
        }

    /**
     * Stands in for Tink's real `InvalidProtocolBufferException`, which is shaded into an
     * internal package and so can only be matched by class name, not by catching its type.
     */
    private class InvalidProtocolBufferException(
        message: String,
    ) : Exception(message)

    private companion object {
        private const val IDENTITY_KEY_PREF = "identity_key_v1"
    }
}
