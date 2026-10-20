package app.logdate.client.device.storage

import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyBackupStore
import app.logdate.client.device.crypto.IdentityKeyManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeferredSecureStorageTest {
    @Test
    fun `unreadable initialization cannot mint or overwrite an identity`() =
        runTest {
            val backup = mockk<IdentityKeyBackupStore>(relaxed = true)
            val crypto = mockk<CryptoManager>(relaxed = true)
            val storage = DeferredSecureStorage { throw IOException("Synthetic unreadable retained keyset") }
            assertFailsWith<IOException> { IdentityKeyManager(storage, crypto, backup).ensureIdentityKey() }
            coVerify(exactly = 0) { backup.writePhrase(any()) }
            coVerify(exactly = 0) { backup.writeAccountIdentity(any()) }
            coVerify(exactly = 0) { backup.writeMediaIdentity(any()) }
        }

    @Test
    fun `cancelled initialization remains cancellation for direct reads and observers`() =
        runTest {
            val cancelled = CancellationException("Synthetic cancelled initialization")
            val storage = DeferredSecureStorage { throw cancelled }
            assertSame(cancelled, assertFailsWith<CancellationException> { storage.getString("identity") })
            assertSame(cancelled, assertFailsWith<CancellationException> { storage.observeString("identity").first() })
        }

    @Test
    fun `construction does not open unreadable storage and reads remain explicit failures`() =
        runTest {
            var attempts = 0
            val storage =
                DeferredSecureStorage {
                    attempts++
                    throw IOException("Synthetic unreadable key storage")
                }
            assertEquals(0, attempts)
            assertFailsWith<IOException> { storage.getString("identity_key_v1") }
            assertFailsWith<IOException> { storage.putString("identity_key_v1", "replacement") }
            assertEquals(2, attempts)
        }

    @Test
    fun `identity observation waits without claiming absence and reconnects after a successful read`() =
        runTest {
            val backing = mockk<SecureStorage>()
            coEvery { backing.getString("identity") } returns "retained"
            every { backing.observeString("identity") } returns flowOf("retained")
            every { backing.observeAll() } returns flowOf(mapOf("identity" to "retained"))
            var available = false
            var attempts = 0
            val storage =
                DeferredSecureStorage {
                    attempts++
                    if (!available) throw IOException("Synthetic transient storage failure")
                    backing
                }
            val identityUpdates = storage.observeString("identity")
            assertEquals(0, attempts, "Observations are cold")
            val identity = async { identityUpdates.first() }
            val all = async { storage.observeAll().first() }
            runCurrent()
            assertFalse(identity.isCompleted)
            assertFalse(all.isCompleted)
            available = true
            assertEquals("retained", storage.getString("identity"))
            assertEquals("retained", identity.await())
            assertEquals(mapOf("identity" to "retained"), all.await())
            val completedAttempts = attempts
            assertEquals("retained", storage.getString("identity"))
            assertEquals(completedAttempts, attempts, "A successfully opened store is reused")
            coVerify(exactly = 0) { backing.putString(any(), any()) }
            coVerify(exactly = 0) { backing.clear() }
        }
}
