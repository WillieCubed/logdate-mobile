package app.logdate.server.logdate

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RepoBackedLogDateCollectionsRepositoryTest {
    @Test
    fun `entries become canonical repo records while preserving sync change feeds`() =
        runTest {
            val accountRepository = InMemoryAccountRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "alice",
                            displayName = "Alice",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val blockStore = InMemoryRepoBlockStore()
            val repository =
                RepoBackedLogDateCollectionsRepository(
                    accountRepository = accountRepository,
                    identityService = identityService,
                    blockStore = blockStore,
                    metadataStore = InMemoryLogDateCollectionsMetadataStore(),
                )
            val userId = account.id.toJavaUUID()

            val stored =
                repository.upsertEntry(
                    userId = userId,
                    entry =
                        LogDateEntry(
                            id = "entry-1",
                            type = "TEXT",
                            content = "hello",
                            mediaUri = null,
                            durationMs = 0L,
                            createdAt = 10L,
                            lastUpdated = 10L,
                            version = 0L,
                            deviceId = DeviceId("device-a"),
                        ),
                )

            val fetched = repository.getEntry(userId = userId, id = "entry-1")
            val snapshot = repository.listEntries(userId)
            val changes = repository.entryChanges(userId = userId, since = 0L, limit = 20)
            val repoDid = AtprotoDid.require(requireNotNull(account.did))
            val canonicalRecord =
                DefaultRepoEngine(blockStore)
                    .getRecord(entryRecordId(repoDid, "entry-1"))
                    .getOrThrow()

            assertNotNull(fetched)
            assertEquals("hello", fetched.content)
            assertEquals(listOf(fetched), snapshot)
            assertEquals(listOf(fetched), changes.changes)
            assertTrue(stored.version > 0L)
            assertNotNull(canonicalRecord)
            assertEquals("hello", canonicalRecord.value.stringValue("content"))

            val deletedAt = changes.lastTimestamp + 1L
            repository.deleteEntry(userId = userId, id = "entry-1", deletedAt = deletedAt)

            val deleted = repository.entryChanges(userId = userId, since = stored.version, limit = 20)
            val purged = repository.purgeTombstones(userId = userId, olderThan = deletedAt + 1L)

            assertNull(repository.getEntry(userId = userId, id = "entry-1"))
            assertTrue(repository.listEntries(userId).isEmpty())
            assertEquals(listOf(LogDateEntryDeletion(id = "entry-1", deletedAt = deletedAt)), deleted.deletions)
            assertEquals(1, purged.entryPurged)
        }

    @Test
    fun `journals and associations share the same canonical repo did and status counts`() =
        runTest {
            val accountRepository = InMemoryAccountRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "brie",
                            displayName = "Brie",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val blockStore = InMemoryRepoBlockStore()
            val repository =
                RepoBackedLogDateCollectionsRepository(
                    accountRepository = accountRepository,
                    identityService = identityService,
                    blockStore = blockStore,
                    metadataStore = InMemoryLogDateCollectionsMetadataStore(),
                )
            val userId = account.id.toJavaUUID()
            val repoDid = AtprotoDid.require(requireNotNull(account.did))

            val journal =
                repository.upsertJournal(
                    userId = userId,
                    journal =
                        LogDateJournal(
                            id = "journal-1",
                            title = "Travel",
                            description = "Trip notes",
                            createdAt = 20L,
                            lastUpdated = 20L,
                            version = 0L,
                            deviceId = DeviceId("device-b"),
                        ),
                )
            val association =
                repository
                    .upsertAssociations(
                        userId = userId,
                        associations =
                            listOf(
                                LogDateAssociation(
                                    journalId = "journal-1",
                                    entryId = "entry-1",
                                    createdAt = 30L,
                                    version = 0L,
                                    deviceId = DeviceId("device-c"),
                                ),
                            ),
                    ).single()

            val status = repository.status(userId)
            val journalRecord =
                DefaultRepoEngine(blockStore)
                    .getRecord(journalRecordId(repoDid, "journal-1"))
                    .getOrThrow()
            val associationRecord =
                DefaultRepoEngine(blockStore)
                    .getRecord(associationRecordId(repoDid, "journal-1", "entry-1"))
                    .getOrThrow()

            assertTrue(journal.version > 0L)
            assertTrue(association.version > journal.version)
            assertEquals(0, status.entryCount)
            assertEquals(1, status.journalCount)
            assertEquals(1, status.associationCount)
            assertNotNull(journalRecord)
            assertNotNull(associationRecord)
            assertEquals("Travel", journalRecord.value.stringValue("title"))
            assertEquals("entry-1", associationRecord.value.stringValue("contentId"))
        }

    @Test
    fun `missing accounts fall back to a stable synthetic repo did`() =
        runTest {
            val accountRepository = InMemoryAccountRepository()
            val repository =
                RepoBackedLogDateCollectionsRepository(
                    accountRepository = accountRepository,
                    identityService = identityService(accountRepository),
                    blockStore = InMemoryRepoBlockStore(),
                    metadataStore = InMemoryLogDateCollectionsMetadataStore(),
                )
            val userId = UUID.randomUUID()

            val stored =
                repository.upsertEntry(
                    userId = userId,
                    entry =
                        LogDateEntry(
                            id = "entry-fallback",
                            type = "TEXT",
                            content = "fallback",
                            mediaUri = null,
                            durationMs = 0L,
                            createdAt = 1L,
                            lastUpdated = 1L,
                            version = 0L,
                            deviceId = DeviceId("device-fallback"),
                        ),
                )
            val changes = repository.entryChanges(userId = userId, since = 0L, limit = 20)

            assertTrue(stored.version > 0L)
            assertEquals(listOf("entry-fallback"), changes.changes.map(LogDateEntry::id))
        }

    private fun identityService(accountRepository: InMemoryAccountRepository): AtprotoIdentityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
            config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
        )
}
