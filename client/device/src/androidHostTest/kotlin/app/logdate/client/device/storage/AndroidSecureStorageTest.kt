package app.logdate.client.device.storage

import android.content.SharedPreferences
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
    fun `cache initialization also sanitizes unreadable retained values`() {
        val privateDetail = "synthetic-private-retained-value"
        val prefs =
            mockk<SharedPreferences> {
                every { all } throws IOException(privateDetail)
            }
        val failure = assertFailsWith<SecureStorageInitializationException> { AndroidSecureStorage { prefs } }
        assertNull(failure.cause)
        assertFalse(failure.stackTraceToString().contains(privateDetail))
        verify(exactly = 0) { prefs.edit() }
    }

    @Test
    fun `initialization failure cannot expose private keyset diagnostics`() {
        val privateDetail = "synthetic-private-keyset-value"
        val captured = mutableListOf<String>()
        val sink =
            object : Antilog() {
                override fun performLog(
                    priority: LogLevel,
                    tag: String?,
                    throwable: Throwable?,
                    message: String?,
                ) {
                    captured += message.orEmpty() + throwable?.stackTraceToString().orEmpty()
                }
            }
        Napier.base(sink)
        try {
            val failure =
                assertFailsWith<SecureStorageInitializationException> {
                    AndroidSecureStorage { throw GeneralSecurityException(privateDetail) }
                }
            assertFalse(failure.stackTraceToString().contains(privateDetail))
            assertFalse(captured.joinToString().contains(privateDetail))
        } finally {
            Napier.takeLogarithm(sink)
        }
    }

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
        assertNull(thrown.cause)
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
        assertNull(thrown.cause)
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
        assertNull(thrown.cause)
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
        assertNull(thrown.cause)
    }

    @Test
    fun `an unclassified initialization failure is sanitized without discarding storage`() {
        val exception = IllegalStateException("unrelated failure")

        val thrown =
            assertFailsWith<SecureStorageInitializationException> {
                AndroidSecureStorage(encryptedPrefsFactory = { throw exception })
            }

        assertNull(thrown.cause)
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

    @Test
    fun `a write waits for the encrypted preferences commit`() =
        runTest {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            every { editor.putString("session", "token") } returns editor
            every { editor.commit() } returns true
            val prefs =
                mockk<SharedPreferences> {
                    every { all } returns emptyMap<String, String>()
                    every { edit() } returns editor
                }
            AndroidSecureStorage { prefs }.putString("session", "token")
            verify(exactly = 1) { editor.commit() }
            verify(exactly = 0) { editor.apply() }
        }

    @Test
    fun `a failed encrypted write does not publish the session`() =
        runTest {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            every { editor.putString("session", "token") } returns editor
            every { editor.commit() } returns false
            val prefs =
                mockk<SharedPreferences> {
                    every { all } returns emptyMap<String, String>()
                    every { edit() } returns editor
                }
            val storage = AndroidSecureStorage { prefs }
            assertFailsWith<IOException> { storage.putString("session", "token") }
            assertEquals(emptyMap(), storage.observeAll().first())
        }

    @Test
    fun `removing a key waits for the encrypted preferences commit`() =
        runTest {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            every { editor.remove("session") } returns editor
            every { editor.commit() } returns true
            val prefs =
                mockk<SharedPreferences> {
                    every { all } returns mapOf("session" to "token")
                    every { edit() } returns editor
                }
            val storage = AndroidSecureStorage { prefs }

            storage.remove("session")

            verify(exactly = 1) { editor.commit() }
            verify(exactly = 0) { editor.apply() }
            assertEquals(emptyMap(), storage.observeAll().first())
        }

    @Test
    fun `a failed key removal does not publish an observed removal`() =
        runTest {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            every { editor.remove("session") } returns editor
            every { editor.commit() } returns false
            val prefs =
                mockk<SharedPreferences> {
                    every { all } returns mapOf("session" to "token")
                    every { edit() } returns editor
                }
            val storage = AndroidSecureStorage { prefs }

            assertFailsWith<IOException> { storage.remove("session") }

            assertEquals(mapOf("session" to "token"), storage.observeAll().first())
        }

    @Test
    fun `clearing keys waits for the encrypted preferences commit`() =
        runTest {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            every { editor.clear() } returns editor
            every { editor.commit() } returns true
            val prefs =
                mockk<SharedPreferences> {
                    every { all } returns mapOf("session" to "token")
                    every { edit() } returns editor
                }
            val storage = AndroidSecureStorage { prefs }

            storage.clear()

            verify(exactly = 1) { editor.commit() }
            verify(exactly = 0) { editor.apply() }
            assertEquals(emptyMap(), storage.observeAll().first())
        }

    @Test
    fun `a failed clear does not publish an observed clear`() =
        runTest {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            every { editor.clear() } returns editor
            every { editor.commit() } returns false
            val prefs =
                mockk<SharedPreferences> {
                    every { all } returns mapOf("session" to "token")
                    every { edit() } returns editor
                }
            val storage = AndroidSecureStorage { prefs }

            assertFailsWith<IOException> { storage.clear() }

            assertEquals(mapOf("session" to "token"), storage.observeAll().first())
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
