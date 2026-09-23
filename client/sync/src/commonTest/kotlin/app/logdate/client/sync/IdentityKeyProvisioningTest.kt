package app.logdate.client.sync

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.JournalChangesResponse
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryIdentityRecoveryNeededStore
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.JournalChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A device can lose its identity key without losing its account -- a reinstall, a restore onto a
 * different phone. Minting a fresh key on the very next sync used to look identical to onboarding
 * a genuinely new device: the account's existing cloud data, encrypted under the old key, silently
 * became unreadable forever, and everything from then on quietly started a second identity.
 */
class IdentityKeyProvisioningTest {
    @Test
    fun `no key plus an account with existing cloud data never mints a new key`() =
        runTest {
            val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), TestCryptoManager())
            val api =
                fakeCloudApiClient {
                    getJournalChangesResponse =
                        Result.success(
                            JournalChangesResponse(
                                changes =
                                    listOf(
                                        JournalChange(
                                            id = "existing-journal",
                                            title = "already-there",
                                            description = "",
                                            createdAt = 1L,
                                            lastUpdated = 1L,
                                            serverVersion = 1L,
                                        ),
                                    ),
                                deletions = listOf(),
                                lastTimestamp = 1L,
                            ),
                        )
                }
            val recoveryStore = InMemoryIdentityRecoveryNeededStore()
            val manager =
                testDefaultSyncManager(
                    identityKeyManager = identityKeyManager,
                    cloudApiClient = api,
                    identityRecoveryNeededStore = recoveryStore,
                )

            manager.fullSync()

            assertFalse(identityKeyManager.hasIdentityKey(), "A key must never be minted when the account already has cloud data")
            assertTrue(recoveryStore.isNeeded(), "Sync should record that this device needs its recovery phrase")
            assertEquals(SyncPausedReason.NEEDS_RECOVERY_PHRASE, manager.getSyncStatus().pausedReason)
        }

    @Test
    fun `no key and a genuinely empty account mints a new key`() =
        runTest {
            val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), TestCryptoManager())
            val api =
                fakeCloudApiClient {
                    getJournalChangesResponse =
                        Result.success(JournalChangesResponse(changes = listOf(), deletions = listOf(), lastTimestamp = 0L))
                    getContentChangesResponse =
                        Result.success(ContentChangesResponse(changes = listOf(), deletions = listOf(), lastTimestamp = 0L))
                }
            val recoveryStore = InMemoryIdentityRecoveryNeededStore()
            val manager =
                testDefaultSyncManager(
                    identityKeyManager = identityKeyManager,
                    cloudApiClient = api,
                    identityRecoveryNeededStore = recoveryStore,
                )

            manager.fullSync()

            assertTrue(identityKeyManager.hasIdentityKey(), "A genuinely new, empty account should still provision its first key")
            assertFalse(recoveryStore.isNeeded())
        }

    @Test
    fun `recovering the identity clears the paused state and resets download cursors`() =
        runTest {
            val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), TestCryptoManager())
            val recoveryStore = InMemoryIdentityRecoveryNeededStore()
            recoveryStore.setNeeded(true)
            val metadata = fakeSyncMetadataService()
            metadata.updateLastSyncTime(EntityType.JOURNAL, Instant.fromEpochMilliseconds(500))
            val cryptoManager = TestCryptoManager()
            val mediaKeyProvider =
                MediaPayloadKeyProvider(InMemorySecureStorage(), cryptoManager, identityKeyManager, KeyDerivation(cryptoManager))
            val useCase = RecoverIdentityUseCase(identityKeyManager, metadata, mediaKeyProvider, recoveryStore)

            val result = useCase((1..12).map { "recovered-$it" })

            assertTrue(result.isSuccess)
            assertFalse(recoveryStore.isNeeded())
            assertNull(metadata.getLastSyncTime(EntityType.JOURNAL))
        }
}
