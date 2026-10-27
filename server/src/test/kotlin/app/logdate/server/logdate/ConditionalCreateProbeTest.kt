package app.logdate.server.logdate

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.StaleWriteException
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A create-only upload that finds its ID taken must leave no trace: no repo commit, no new server
 * version, nothing in the change feed. A create after a delete must not bring the record back.
 */
@OptIn(ExperimentalUuidApi::class)
class ConditionalCreateProbeTest {
    private class Fixture(
        val repository: LogDateCollectionsRepository,
        val userId: UUID,
        private val blockStore: InMemoryRepoBlockStore?,
        private val repoDid: AtprotoDid?,
    ) {
        suspend fun commits(): Int = blockStore?.listCommits(requireNotNull(repoDid))?.getOrThrow()?.size ?: 0
    }

    private val accountRepository = InMemoryAccountRepository()
    private val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
    private val identityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = signingKeyService,
            config = AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test"),
        )
    private var accounts = 0

    @Test
    fun `a duplicate create makes no commit and leaves the version and change feed alone`() =
        runTest {
            bothStores { store ->
                val first = store.repository.createEntryIfAbsent(store.userId, entry("e1", "first"), respectTombstones = true)
                val version = first.createdRecord().version
                val commits = store.commits()

                val duplicate = store.repository.createEntryIfAbsent(store.userId, entry("e1", "replayed"), respectTombstones = true)

                assertEquals(ConditionalCreate.Exists(version), duplicate)
                assertEquals(commits, store.commits())
                assertEquals(LogDateRecordState.Live(version), store.repository.entryState(store.userId, "e1"))
                val feed = store.repository.entryChanges(store.userId, since = version, limit = 10)
                assertTrue(feed.changes.isEmpty() && feed.deletions.isEmpty(), "the duplicate must not reach the change feed")
                assertEquals("first", store.repository.getEntry(store.userId, "e1")?.content)
            }
        }

    @Test
    fun `a strict create after a delete answers deleted and writes nothing`() =
        runTest {
            bothStores { store ->
                store.repository.createEntryIfAbsent(store.userId, entry("e2", "first"), respectTombstones = true).createdRecord()
                store.repository.deleteEntry(store.userId, "e2", deletedAt = 42L)
                val tombstone = assertIs<LogDateRecordState.Deleted>(store.repository.entryState(store.userId, "e2"))
                assertEquals(42L, tombstone.deletedAt)
                val commits = store.commits()

                val outcome = store.repository.createEntryIfAbsent(store.userId, entry("e2", "resurrected"), respectTombstones = true)

                assertEquals(ConditionalCreate.Deleted(42L, tombstone.serverVersion), outcome)
                assertEquals(commits, store.commits())
                assertNull(store.repository.getEntry(store.userId, "e2"))
                assertEquals(LogDateRecordState.Absent, store.repository.entryState(store.userId, "never-written"))
            }
        }

    @Test
    fun `a legacy create cannot recreate a deleted entry`() =
        runTest {
            bothStores { store ->
                store.repository.createEntryIfAbsent(store.userId, entry("e3", "first"), respectTombstones = false).createdRecord()
                store.repository.deleteEntry(store.userId, "e3", deletedAt = 43L)

                assertFailsWith<StaleWriteException> {
                    store.repository.createEntryIfAbsent(store.userId, entry("e3", "again"), respectTombstones = false)
                }
                assertIs<LogDateRecordState.Deleted>(store.repository.entryState(store.userId, "e3"))
            }
        }

    @Test
    fun `journals follow the same rules`() =
        runTest {
            bothStores { store ->
                val first = store.repository.createJournalIfAbsent(store.userId, journal("j1", "Walks"), respectTombstones = true)
                val version = first.createdRecord().version
                val duplicate = store.repository.createJournalIfAbsent(store.userId, journal("j1", "Other"), respectTombstones = true)
                assertEquals(ConditionalCreate.Exists(version), duplicate)
                assertEquals("Walks", store.repository.getJournal(store.userId, "j1")?.title)

                store.repository.deleteJournal(store.userId, "j1", deletedAt = 44L)
                val outcome = store.repository.createJournalIfAbsent(store.userId, journal("j1", "Back"), respectTombstones = true)
                assertEquals(44L, assertIs<ConditionalCreate.Deleted>(outcome).deletedAt)
                assertIs<LogDateRecordState.Deleted>(store.repository.journalState(store.userId, "j1"))
            }
        }

    @Test
    fun `strict link creation skips live and deleted links`() =
        runTest {
            bothStores { store ->
                val first = store.repository.createAssociationsIfAbsent(store.userId, listOf(link("a"), link("b")))
                assertEquals(listOf("a", "b"), first.created.map { it.entryId })
                store.repository.deleteAssociations(store.userId, listOf(LogDateAssociationRef("j", "b")), deletedAt = 50L)

                val second = store.repository.createAssociationsIfAbsent(store.userId, listOf(link("a"), link("b"), link("c")))

                assertEquals(listOf("c"), second.created.map { it.entryId })
                assertEquals(listOf(LogDateAssociationRef("j", "a")), second.existing)
                assertEquals(listOf("b"), second.deleted.map { it.association.entryId })
                assertEquals(
                    setOf("a", "c"),
                    store.repository
                        .listAssociations(store.userId)
                        .map { it.entryId }
                        .toSet(),
                )
            }
        }

    private suspend fun bothStores(block: suspend (Fixture) -> Unit) {
        accounts += 1
        val account =
            identityService.ensureIdentity(
                accountRepository.save(
                    Account(id = Uuid.random(), username = "probe_$accounts", displayName = "Probe", createdAt = Clock.System.now()),
                ),
            )
        val blockStore = InMemoryRepoBlockStore()
        val repoBacked =
            RepoBackedLogDateCollectionsRepository(
                accountRepository = accountRepository,
                identityService = identityService,
                signingKeyService = signingKeyService,
                blockStore = blockStore,
                metadataStore = InMemoryLogDateCollectionsMetadataStore(),
            )
        block(Fixture(repoBacked, account.id.toJavaUUID(), blockStore, AtprotoDid.require(requireNotNull(account.did))))
        block(Fixture(SyncBackedLogDateCollectionsRepository(InMemorySyncRepository()), Uuid.random().toJavaUUID(), null, null))
    }

    private fun <T> ConditionalCreate<T>.createdRecord(): T =
        when (this) {
            is ConditionalCreate.Created -> record
            else -> fail("expected a create, got $this")
        }

    private fun entry(
        id: String,
        content: String,
    ) = LogDateEntry(
        id = id,
        type = "TEXT",
        content = content,
        mediaUri = null,
        durationMs = 0L,
        createdAt = 10L,
        lastUpdated = 10L,
        version = 0L,
        deviceId = DeviceId("pixel-watch-3"),
    )

    private fun journal(
        id: String,
        title: String,
    ) = LogDateJournal(
        id = id,
        title = title,
        description = "",
        createdAt = 10L,
        lastUpdated = 10L,
        version = 0L,
        deviceId = DeviceId("pixel-watch-3"),
    )

    private fun link(entryId: String) =
        LogDateAssociation(journalId = "j", entryId = entryId, createdAt = 10L, version = 0L, deviceId = DeviceId("pixel-watch-3"))
}
