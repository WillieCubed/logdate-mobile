@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.logdate.server.logdate

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.JournalMergeRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

class JournalMergeCollectionsRepositoryTest {
    @Test
    fun `both backends merge remote and offline links and reject source resurrection`() =
        runTest {
            val accounts = InMemoryAccountRepository()
            val keys = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
            val identity =
                AtprotoIdentityService(
                    accounts,
                    keys,
                    AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val account =
                identity.ensureIdentity(
                    accounts.save(
                        Account(id = Uuid.random(), username = "merge-owner", displayName = "Merge owner", createdAt = Clock.System.now()),
                    ),
                )
            val canonical =
                RepoBackedLogDateCollectionsRepository(
                    accounts,
                    identity,
                    keys,
                    InMemoryRepoBlockStore(),
                    InMemoryLogDateCollectionsMetadataStore(),
                )
            for (backend in listOf(canonical, SyncBackedLogDateCollectionsRepository(InMemorySyncRepository()))) {
                val repository = MergeAwareLogDateCollectionsRepository(backend, InMemoryJournalMergeStore())
                val user = account.id.toJavaUUID()
                val source = journal()
                val destination = journal()
                val remoteNote = Uuid.random().toString()
                val offlineNote = Uuid.random().toString()
                repository.upsertJournal(user, source)
                repository.upsertJournal(user, destination)
                repository.upsertAssociations(user, listOf(association(source.id, remoteNote)))
                val request = JournalMergeRequest(Uuid.random().toString(), destination.id, listOf(remoteNote, offlineNote))
                val result = repository.merge(user, source.id, request)
                assertEquals(destination.id, result.destinationId)
                assertEquals(result, repository.merge(user, source.id, request))
                assertNull(repository.getJournal(user, source.id))
                assertEquals(
                    setOf(remoteNote, offlineNote),
                    repository
                        .listAssociations(user)
                        .filter {
                            it.journalId == destination.id
                        }.map { it.entryId }
                        .toSet(),
                )
                assertFailsWith<JournalMergedException> { repository.upsertJournal(user, source) }
                val lateNote = Uuid.random().toString()
                repository.upsertAssociations(user, listOf(association(source.id, lateNote)))
                repository.deleteAssociations(user, listOf(LogDateAssociationRef(source.id, remoteNote)), 900)
                assertEquals(setOf(remoteNote, offlineNote, lateNote), repository.listAssociations(user).map { it.entryId }.toSet())
                assertNotNull(repository.getJournal(user, destination.id))
                assertEquals(
                    destination.id,
                    repository
                        .journalChanges(user, 0, 100)
                        .deletions
                        .single { it.id == source.id }
                        .mergedIntoJournalId,
                )
                assertFailsWith<JournalMergeDestinationMissingException> { repository.merge(UUID.randomUUID(), source.id, request) }
            }
        }

    @Test
    fun `interrupted merge resumes from durable membership snapshot after wrapper restart`() =
        runTest {
            val backend = SyncBackedLogDateCollectionsRepository(InMemorySyncRepository())
            var failDelete = true
            val faulty =
                object : LogDateCollectionsRepository by backend {
                    override suspend fun deleteJournal(
                        userId: UUID,
                        id: String,
                        deletedAt: Long,
                    ) {
                        if (failDelete) error("Interrupted after destination associations")
                        backend.deleteJournal(userId, id, deletedAt)
                    }
                }
            val store = InMemoryJournalMergeStore()
            val repository = MergeAwareLogDateCollectionsRepository(faulty, store)
            val user = UUID.randomUUID()
            val source = journal()
            val destination = journal()
            repository.upsertJournal(user, source)
            repository.upsertJournal(user, destination)
            val note = Uuid.random().toString()
            repository.upsertAssociations(user, listOf(association(source.id, note)))
            val request = JournalMergeRequest(Uuid.random().toString(), destination.id, listOf(note))
            assertFailsWith<IllegalStateException> { repository.merge(user, source.id, request) }
            assertNotNull(backend.getJournal(user, source.id))
            assertEquals(listOf(note), backend.listAssociations(user).filter { it.journalId == destination.id }.map { it.entryId })
            failDelete = false
            val restarted = MergeAwareLogDateCollectionsRepository(faulty, store)
            restarted.merge(user, source.id, request)
            assertNull(restarted.getJournal(user, source.id))
            assertEquals(
                destination.id,
                restarted
                    .journalChanges(user, 0, 100)
                    .deletions
                    .single { it.id == source.id }
                    .mergedIntoJournalId,
            )
        }

    @Test
    fun `operation requests are immutable and competing merges and cycles are rejected`() =
        runTest {
            val repository =
                MergeAwareLogDateCollectionsRepository(
                    SyncBackedLogDateCollectionsRepository(InMemorySyncRepository()),
                    InMemoryJournalMergeStore(),
                )
            val user = UUID.randomUUID()
            val source = journal()
            val destination = journal()
            repository.upsertJournal(user, source)
            repository.upsertJournal(user, destination)
            val request = JournalMergeRequest(Uuid.random().toString(), destination.id, emptyList())
            repository.merge(user, source.id, request)
            assertFailsWith<JournalMergeConflictException> {
                repository.merge(user, source.id, request.copy(contentIds = listOf(Uuid.random().toString())))
            }
            assertFailsWith<JournalMergeConflictException> {
                repository.merge(
                    user,
                    source.id,
                    request.copy(operationId = Uuid.random().toString()),
                )
            }
            assertFailsWith<JournalMergeConflictException> {
                repository.merge(user, destination.id, request.copy(operationId = Uuid.random().toString(), destinationId = source.id))
            }
        }

    @Test
    fun `chains route late additions to survivor and redirects survive tombstone purge`() =
        runTest {
            val repository =
                MergeAwareLogDateCollectionsRepository(
                    SyncBackedLogDateCollectionsRepository(InMemorySyncRepository()),
                    InMemoryJournalMergeStore(),
                )
            val user = UUID.randomUUID()
            val source = journal()
            val middle = journal()
            val survivor = journal().copy(title = "Keep this title", description = "Keep this description")
            listOf(source, middle, survivor).forEach { repository.upsertJournal(user, it) }
            val first = JournalMergeRequest(Uuid.random().toString(), middle.id, emptyList())
            repository.merge(user, source.id, first)
            repository.merge(user, middle.id, JournalMergeRequest(Uuid.random().toString(), survivor.id, emptyList()))
            repository.purgeTombstones(user, Long.MAX_VALUE)
            val note = Uuid.random().toString()
            repository.upsertAssociations(user, listOf(association(source.id, note)))
            repository.deleteJournal(user, source.id, Long.MAX_VALUE)
            assertEquals(survivor.id, repository.listAssociations(user).single().journalId)
            assertEquals(survivor.title, repository.getJournal(user, survivor.id)?.title)
            assertEquals(survivor.description, repository.getJournal(user, survivor.id)?.description)
            assertEquals(survivor.id, assertFailsWith<JournalMergedException> { repository.upsertJournal(user, source) }.destinationId)
            assertEquals(survivor.id, repository.merge(user, source.id, first).destinationId)
        }

    @Test
    fun `offline source creates merge tombstone and missing destination can be retried`() =
        runTest {
            val store = InMemoryJournalMergeStore()
            val repository = MergeAwareLogDateCollectionsRepository(SyncBackedLogDateCollectionsRepository(InMemorySyncRepository()), store)
            val user = UUID.randomUUID()
            val source = journal()
            val destination = journal()
            val note = Uuid.random().toString()
            val request = JournalMergeRequest(Uuid.random().toString(), destination.id, listOf(note))
            assertFailsWith<JournalMergeDestinationMissingException> { repository.merge(user, source.id, request) }
            repository.upsertJournal(user, destination)
            repository.merge(user, source.id, request)
            assertEquals(destination.id, repository.listAssociations(user).single().journalId)
            assertEquals(
                destination.id,
                repository
                    .journalChanges(user, 0, 100)
                    .deletions
                    .single()
                    .mergedIntoJournalId,
            )
            val otherUser = UUID.randomUUID()
            repository.upsertJournal(otherUser, source)
            assertNotNull(repository.getJournal(otherUser, source.id))
        }

    @Test
    fun `missing unstarted destination can be replaced without pinning the source`() =
        runTest {
            val repository =
                MergeAwareLogDateCollectionsRepository(
                    SyncBackedLogDateCollectionsRepository(InMemorySyncRepository()),
                    InMemoryJournalMergeStore(),
                )
            val user = UUID.randomUUID()
            val source = journal()
            val missing = journal()
            val available = journal()
            repository.upsertJournal(user, source)
            repository.upsertJournal(user, available)
            assertFailsWith<JournalMergeDestinationMissingException> {
                repository.merge(user, source.id, JournalMergeRequest(Uuid.random().toString(), missing.id, emptyList()))
            }
            assertNotNull(repository.getJournal(user, source.id))
            assertEquals(
                available.id,
                repository.merge(user, source.id, JournalMergeRequest(Uuid.random().toString(), available.id, emptyList())).destinationId,
            )
        }

    @Test
    fun `merge tombstones and later writes paginate without a shared version or skipped records`() =
        runTest {
            val repository =
                MergeAwareLogDateCollectionsRepository(
                    SyncBackedLogDateCollectionsRepository(InMemorySyncRepository()),
                    InMemoryJournalMergeStore(),
                )
            val user = UUID.randomUUID()
            val source = journal()
            val destination = journal()
            repository.upsertJournal(user, source)
            repository.upsertJournal(user, destination)
            repository.merge(user, source.id, JournalMergeRequest(Uuid.random().toString(), destination.id, emptyList()))
            val later = journal()
            repository.upsertJournal(user, later)
            var cursor = 0L
            val ids = mutableListOf<String>()
            val versions = mutableListOf<Long>()
            repeat(3) {
                val page = repository.journalChanges(user, cursor, 1)
                assertEquals(1, page.changes.size + page.deletions.size)
                ids += page.changes.map { it.id } + page.deletions.map { it.id }
                versions += page.lastTimestamp
                cursor = page.lastTimestamp
            }
            assertEquals(setOf(source.id, destination.id, later.id), ids.toSet())
            assertEquals(3, versions.distinct().size)
            assertFalse(repository.journalChanges(user, cursor, 1).hasMore)
        }

    @Test
    fun `late additions wait for the canonical merge write and then follow its redirect`() =
        runTest {
            val backend = SyncBackedLogDateCollectionsRepository(InMemorySyncRepository())
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val slow =
                object : LogDateCollectionsRepository by backend {
                    override suspend fun upsertAssociations(
                        userId: UUID,
                        associations: List<LogDateAssociation>,
                    ): List<LogDateAssociation> {
                        started.complete(Unit)
                        release.await()
                        return backend.upsertAssociations(userId, associations)
                    }
                }
            val repository = MergeAwareLogDateCollectionsRepository(slow, InMemoryJournalMergeStore())
            val user = UUID.randomUUID()
            val source = journal()
            val destination = journal()
            repository.upsertJournal(user, source)
            repository.upsertJournal(user, destination)
            val first = Uuid.random().toString()
            val late = Uuid.random().toString()
            val merging =
                async { repository.merge(user, source.id, JournalMergeRequest(Uuid.random().toString(), destination.id, listOf(first))) }
            started.await()
            val addition = async { repository.upsertAssociations(user, listOf(association(source.id, late))) }
            runCurrent()
            assertFalse(addition.isCompleted)
            release.complete(Unit)
            merging.await()
            addition.await()
            assertEquals(
                setOf(first, late),
                repository
                    .listAssociations(user)
                    .filter { it.journalId == destination.id }
                    .map { it.entryId }
                    .toSet(),
            )
        }

    @Test
    fun `later journal writes stay visible after a merge cursor ahead of wall clock`() =
        runTest {
            val backend = SyncBackedLogDateCollectionsRepository(InMemorySyncRepository())
            val floor = System.currentTimeMillis() + 60_000L
            val ahead =
                object : LogDateCollectionsRepository by backend {
                    override suspend fun status(userId: UUID): LogDateCollectionsStatus = backend.status(userId).copy(lastTimestamp = floor)
                }
            val repository = MergeAwareLogDateCollectionsRepository(ahead, InMemoryJournalMergeStore())
            val user = UUID.randomUUID()
            val source = journal()
            val destination = journal()
            repository.upsertJournal(user, source)
            repository.upsertJournal(user, destination)
            repository.merge(user, source.id, JournalMergeRequest(Uuid.random().toString(), destination.id, emptyList()))
            val cursor = repository.journalChanges(user, 0, 100).lastTimestamp
            val first = journal()
            val second = journal()
            repository.upsertJournal(user, first)
            repository.upsertJournal(user, second)
            assertEquals(
                setOf(first.id, second.id),
                repository
                    .journalChanges(user, cursor, 100)
                    .changes
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun `completion persistence failure resumes after the source record has already been deleted`() =
        runTest {
            val backend = SyncBackedLogDateCollectionsRepository(InMemorySyncRepository())
            val durable = InMemoryJournalMergeStore()
            var failCompletion = true
            val unavailable =
                object : JournalMergeStore by durable {
                    override suspend fun save(
                        userId: UUID,
                        operation: JournalMergeOperation,
                    ) {
                        if (operation.completed && failCompletion) error("Completion store unavailable")
                        durable.save(userId, operation)
                    }
                }
            val repository = MergeAwareLogDateCollectionsRepository(backend, unavailable)
            val user = UUID.randomUUID()
            val source = journal()
            val destination = journal()
            repository.upsertJournal(user, source)
            repository.upsertJournal(user, destination)
            val remote = Uuid.random().toString()
            val offline = Uuid.random().toString()
            repository.upsertAssociations(user, listOf(association(source.id, remote)))
            val request = JournalMergeRequest(Uuid.random().toString(), destination.id, listOf(offline))
            assertFailsWith<IllegalStateException> { repository.merge(user, source.id, request) }
            assertNull(backend.getJournal(user, source.id))
            failCompletion = false
            val restarted = MergeAwareLogDateCollectionsRepository(backend, unavailable)
            restarted.merge(user, source.id, request)
            assertEquals(
                setOf(remote, offline),
                restarted
                    .listAssociations(user)
                    .filter { it.journalId == destination.id }
                    .map { it.entryId }
                    .toSet(),
            )
            assertEquals(
                destination.id,
                restarted
                    .journalChanges(user, 0, 100)
                    .deletions
                    .single { it.id == source.id }
                    .mergedIntoJournalId,
            )
        }

    private fun journal() = LogDateJournal(Uuid.random().toString(), "Journal", "Description", 1, 1, 0, DeviceId("device"))

    private fun association(
        journal: String,
        content: String,
    ) = LogDateAssociation(journal, content, 1, 0, DeviceId("device"))
}
