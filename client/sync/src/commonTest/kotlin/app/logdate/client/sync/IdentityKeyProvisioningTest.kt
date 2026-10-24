package app.logdate.client.sync

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.AccountIdentityBackup
import app.logdate.client.device.crypto.IdentityKeyBackupStore
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.crypto.MediaIdentityBackup
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.JournalChangesResponse
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryIdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.InMemoryUnreadableCloudRecordStore
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.JournalChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
    fun `normal sync cannot mint over media-only native recovery material`() =
        runTest {
            val backup = FakeIdentityKeyBackupStore()
            backup.writeMediaIdentity(MediaIdentityBackup("retained-binding", ByteArray(32) { 8 }))
            val identity = IdentityKeyManager(InMemorySecureStorage(), TestCryptoManager(), backup)
            val manager = testDefaultSyncManager(identityKeyManager = identity, cloudApiClient = fakeCloudApiClient())
            manager.uploadPendingChanges()
            assertFalse(identity.hasIdentityKey())
            assertTrue(backup.hasMediaBackup())
        }

    @Test
    fun `settings recovery selects the recovered identity media keys and can return to the original identity`() =
        runTest {
            val storage = InMemorySecureStorage()
            val crypto = TestCryptoManager()
            val backup = FakeIdentityKeyBackupStore()
            val identity = IdentityKeyManager(storage, crypto, backup)
            val originalPhrase = identity.setupNewIdentity()
            val media = MediaPayloadKeyProvider(storage, crypto, identity, KeyDerivation(crypto))
            val originalMedia = media.getOrCreateKey()
            val recover =
                RecoverIdentityUseCase(
                    identity,
                    fakeSyncMetadataService(),
                    media,
                    InMemoryIdentityRecoveryNeededStore(),
                    InMemoryUnreadableCloudRecordStore(),
                    proof = RecoveryPhraseAccountProof { "test-account-id" },
                    sessionStorage = fakeSessionStorage(),
                )
            val recoveredPhrase = List(12) { "recovered-$it" }
            assertTrue(recover(recoveredPhrase).isSuccess)
            assertFalse(originalMedia.contentEquals(media.getOrCreateKey()))
            assertTrue(recover(originalPhrase.words).isSuccess)
            assertTrue(originalMedia.contentEquals(media.getOrCreateKey()))
        }

    @Test
    fun `normal sync backfills a retained phrase-less identity on upgrade`() =
        runTest {
            val storage = InMemorySecureStorage()
            val root = ByteArray(32) { 7 }
            storage.putBytes("identity_key_v1", root)
            val backup = FakeIdentityKeyBackupStore()
            val identity = IdentityKeyManager(storage, TestCryptoManager(), backup)
            val manager = testDefaultSyncManager(identityKeyManager = identity, cloudApiClient = fakeCloudApiClient())
            manager.uploadPendingChanges()
            assertTrue(root.contentEquals(backup.readAccountIdentity()?.key))
        }

    @Test
    fun `draft only accounts and failed draft checks never mint a different identity`() =
        runTest {
            val draft =
                app.logdate.shared.model.sync.DraftChange(
                    id = "00000000-0000-4000-8000-000000000001",
                    content = "encrypted",
                    blockTypes = emptyList(),
                    journalIds = emptyList(),
                    createdAt = 1,
                    lastUpdated = 1,
                    deviceId = app.logdate.shared.model.sync.DeviceId.UNKNOWN,
                    serverVersion = 1,
                )
            for (response in listOf(
                Result.success(
                    app.logdate.shared.model.sync
                        .DraftChangesResponse(listOf(draft)),
                ),
                Result.success(
                    app.logdate.shared.model.sync
                        .DraftChangesResponse(listOf(draft.copy(isDeleted = true))),
                ),
                Result.failure(IllegalStateException("unavailable")),
            )) {
                val identity = IdentityKeyManager(InMemorySecureStorage(), TestCryptoManager())
                val api = fakeCloudApiClient { getDraftChangesResponse = response }
                testDefaultSyncManager(identityKeyManager = identity, cloudApiClient = api).uploadPendingChanges()
                assertFalse(identity.hasIdentityKey())
            }
        }

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
            val unreadableStore = InMemoryUnreadableCloudRecordStore()
            unreadableStore.record(EntityType.NOTE, listOf(kotlin.uuid.Uuid.random()))
            val useCase =
                RecoverIdentityUseCase(
                    identityKeyManager,
                    metadata,
                    mediaKeyProvider,
                    recoveryStore,
                    unreadableStore,
                    proof = RecoveryPhraseAccountProof { "test-account-id" },
                    sessionStorage = fakeSessionStorage(),
                )

            val result = useCase((1..12).map { "recovered-$it" })

            assertTrue(result.isSuccess)
            assertFalse(recoveryStore.isNeeded())
            assertNull(metadata.getLastSyncTime(EntityType.JOURNAL))
            assertEquals(1, unreadableStore.count(), "Recovering a key does not prove unreadable content recovered")
        }

    @Test
    fun `unproven phrase leaves the installed key and recovery pause untouched`() =
        runTest {
            val storage = InMemorySecureStorage()
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(storage, crypto)
            val original = identity.setupNewIdentity()
            val originalKey = identity.getIdentityKey()
            val media = MediaPayloadKeyProvider(storage, crypto, identity, KeyDerivation(crypto))
            val originalMedia = media.getOrCreateKey()
            val recovery = InMemoryIdentityRecoveryNeededStore().apply { setNeeded(true) }
            val metadata = fakeSyncMetadataService()
            val syncTime = Instant.fromEpochMilliseconds(500)
            metadata.updateLastSyncTime(EntityType.JOURNAL, syncTime)
            val useCase =
                RecoverIdentityUseCase(
                    identity,
                    metadata,
                    media,
                    recovery,
                    InMemoryUnreadableCloudRecordStore(),
                    proof = RecoveryPhraseAccountProof { throw RecoveryPhraseVerificationException() },
                    sessionStorage = fakeSessionStorage(),
                )

            assertTrue(useCase(List(12) { "different-$it" }).isFailure)
            assertContentEquals(originalKey, identity.getIdentityKey())
            assertContentEquals(originalMedia, media.getOrCreateKey())
            assertEquals(original.words, identity.getStoredRecoveryPhrase()?.words)
            assertTrue(recovery.isNeeded())
            assertEquals(syncTime, metadata.getLastSyncTime(EntityType.JOURNAL))
        }

    @Test
    fun `account switch while phrase proof runs cannot install its key`() =
        runTest {
            val storage = InMemorySecureStorage()
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(storage, crypto)
            identity.setupNewIdentity()
            val originalKey = identity.getIdentityKey()
            val session = fakeSessionStorage()
            val recovery = InMemoryIdentityRecoveryNeededStore().apply { setNeeded(true) }
            val media = MediaPayloadKeyProvider(storage, crypto, identity, KeyDerivation(crypto))
            val useCase =
                RecoverIdentityUseCase(
                    identity,
                    fakeSyncMetadataService(),
                    media,
                    recovery,
                    InMemoryUnreadableCloudRecordStore(),
                    proof =
                        RecoveryPhraseAccountProof {
                            session.saveSession(UserSession("different-token", "different-refresh", "other-account"))
                            "test-account-id"
                        },
                    sessionStorage = session,
                )

            assertTrue(useCase(List(12) { "different-$it" }).isFailure)
            assertContentEquals(originalKey, identity.getIdentityKey())
            assertTrue(recovery.isNeeded())
        }

    @Test
    fun `account switch during native backup write leaves recovery paused`() =
        runTest {
            val session = fakeSessionStorage()
            val backing = FakeIdentityKeyBackupStore()
            var switchOnWrite = false
            val backup =
                object : IdentityKeyBackupStore by backing {
                    override suspend fun writePhrase(phrase: String) {
                        backing.writePhrase(phrase)
                        if (switchOnWrite) session.saveSession(UserSession("other-token", "other-refresh", "other-account"))
                    }
                }
            val storage = InMemorySecureStorage()
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(storage, crypto, backup)
            identity.setupNewIdentity()
            val recovery = InMemoryIdentityRecoveryNeededStore().apply { setNeeded(true) }
            val metadata = fakeSyncMetadataService()
            val syncTime = Instant.fromEpochMilliseconds(500)
            metadata.updateLastSyncTime(EntityType.JOURNAL, syncTime)
            switchOnWrite = true
            val useCase =
                RecoverIdentityUseCase(
                    identity,
                    metadata,
                    MediaPayloadKeyProvider(storage, crypto, identity, KeyDerivation(crypto)),
                    recovery,
                    InMemoryUnreadableCloudRecordStore(),
                    proof = RecoveryPhraseAccountProof { "test-account-id" },
                    sessionStorage = session,
                )

            val error = useCase(List(12) { "replacement-$it" }).exceptionOrNull()
            assertEquals(RecoveryPhraseVerificationReason.UNAVAILABLE, (error as RecoveryPhraseVerificationException).reason)
            assertTrue(recovery.isNeeded())
            assertEquals(syncTime, metadata.getLastSyncTime(EntityType.JOURNAL))
            assertFalse(identity.canUseForAccount("other-account"))
        }

    @Test
    fun `retained journal owner conflict is not reported as invalid mnemonic`() =
        runTest {
            val storage = InMemorySecureStorage()
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(storage, crypto)
            identity.setupNewIdentity()
            identity.bindToAccount("another-account")
            val originalKey = identity.getIdentityKey()
            val media = MediaPayloadKeyProvider(storage, crypto, identity, KeyDerivation(crypto))
            val useCase =
                RecoverIdentityUseCase(
                    identity,
                    fakeSyncMetadataService(),
                    media,
                    InMemoryIdentityRecoveryNeededStore(),
                    InMemoryUnreadableCloudRecordStore(),
                    proof = RecoveryPhraseAccountProof { "test-account-id" },
                    sessionStorage = fakeSessionStorage(),
                )

            val error = useCase(List(12) { "replacement-$it" }).exceptionOrNull()
            assertEquals(RecoveryPhraseVerificationReason.MISMATCH, (error as RecoveryPhraseVerificationException).reason)
            assertContentEquals(originalKey, identity.getIdentityKey())
        }

    @Test
    fun `a usable identity backup restores silently and resets sync state`() =
        runTest {
            val recoveryPhrase = (1..12).map { "backup-word-$it" }
            val backupStore = FakeIdentityKeyBackupStore(initialPhrase = recoveryPhrase.joinToString(" "))
            val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), TestCryptoManager(), backupStore)
            // Deliberately shaped exactly like the "account already has cloud data" scenario that
            // would otherwise pause sync with NEEDS_RECOVERY_PHRASE -- the backup restore must be
            // checked, and must win, before that decision is ever made.
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
            val metadata = fakeSyncMetadataService()
            metadata.updateLastSyncTime(EntityType.JOURNAL, Instant.fromEpochMilliseconds(500))
            val mediaSecureStorage = InMemorySecureStorage()
            val cryptoManager = TestCryptoManager()
            val mediaKeyProvider =
                MediaPayloadKeyProvider(mediaSecureStorage, cryptoManager, identityKeyManager, KeyDerivation(cryptoManager))
            // Seed a stale cached media key, the way a real device would have one before its local
            // state (and thus this cache) was wiped by the reinstall that also emptied SecureStorage.
            mediaSecureStorage.putBytes(MEDIA_PAYLOAD_KEY_STORAGE_KEY, ByteArray(32) { it.toByte() })
            val manager =
                testDefaultSyncManager(
                    identityKeyManager = identityKeyManager,
                    mediaPayloadKeyProvider = mediaKeyProvider,
                    cloudApiClient = api,
                    identityRecoveryNeededStore = recoveryStore,
                    syncMetadataService = metadata,
                )

            // uploadPendingChanges alone -- not fullSync -- so a real download phase never runs
            // and re-stamps the JOURNAL cursor afterwards; this isolates provisionIdentityKey's
            // own reset from that unrelated, expected side effect of a normal download completing.
            manager.uploadPendingChanges()

            assertTrue(identityKeyManager.hasIdentityKey(), "The backed-up phrase should have restored a key")
            assertEquals(
                recoveryPhrase,
                identityKeyManager.getStoredRecoveryPhrase()?.words,
                "The restored key must come from the backup phrase, not a freshly minted one",
            )
            assertFalse(
                recoveryStore.isNeeded(),
                "A usable backup must win over pausing for NEEDS_RECOVERY_PHRASE, even though the account has cloud data",
            )
            assertNull(metadata.getLastSyncTime(EntityType.JOURNAL), "Download cursors must reset after a silent restore")
            assertNull(
                mediaSecureStorage.getBytes(MEDIA_PAYLOAD_KEY_STORAGE_KEY),
                "The stale cached media key must be forgotten after a silent restore",
            )
        }
}

/** Mirrors the private key [MediaPayloadKeyProvider] stores its cached key under. */
private const val MEDIA_PAYLOAD_KEY_STORAGE_KEY = "media_payload_key_v1"

private class FakeIdentityKeyBackupStore(
    initialPhrase: String? = null,
) : IdentityKeyBackupStore {
    private var phrase: String? = initialPhrase
    private var account: AccountIdentityBackup? = null
    private val media = mutableMapOf<String, MediaIdentityBackup>()

    override suspend fun readMediaIdentity(identityBinding: String) = media[identityBinding]

    override suspend fun hasMediaBackup() = media.isNotEmpty()

    override suspend fun writeMediaIdentity(identity: MediaIdentityBackup) {
        media[identity.identityBinding] = identity
    }

    override suspend fun readAccountIdentity() = account

    override suspend fun writeAccountIdentity(identity: AccountIdentityBackup) {
        account = identity
        phrase = null
    }

    override suspend fun readPhrase(): String? = phrase

    override suspend fun writePhrase(phrase: String) {
        this.phrase = phrase
        account = null
    }

    override suspend fun clear() {
        phrase = null
        account = null
        media.clear()
    }
}
