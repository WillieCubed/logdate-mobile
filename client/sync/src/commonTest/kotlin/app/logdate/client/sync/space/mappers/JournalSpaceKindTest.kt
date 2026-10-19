package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.conflict.DeletionResolution
import app.logdate.client.sync.conflict.JournalConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceItemChanges
import app.logdate.client.sync.space.SpacePartChange
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class JournalSpaceKindTest {
    private val repository = SyncedJournals(FakeJournalRepository())
    private val kind = journalSpaceKind(repository, JournalConflictResolver())
    private val created = Instant.parse("2026-10-12T07:41:09Z")
    private val survivor = Uuid.parse("0b8f3c1e-6a2d-4e9f-8c7b-1d2e3f4a5b6c")
    private val journal =
        Journal(id = Uuid.parse("5b2c0d6e-8f41-4a7b-9c3d-2e1f0a9b8c7d"), title = "Lake trips", created = created, lastUpdated = created)

    @Test
    fun `a journal is the one record of its kind keyed by its id`() {
        assertEquals(EntityType.JOURNAL, kind.entityType)
        assertEquals(listOf("app.logdate.journal.journal"), kind.parts.map { it.collection })
        assertEquals(journal.id.toString(), kind.main.ownerOf(journal.id.toString().uppercase()))
        assertEquals(listOf(journal.id.toString()), kind.main.rkeysOf(journal.id.toString().uppercase()))
    }

    @Test
    fun `a stored journal is loaded by the id in the outbox`() =
        runTest {
            repository.create(journal)

            assertEquals(SpaceJournal(journal), kind.local.load(journal.id.toString()))
            assertNull(kind.local.load(Uuid.random().toString()))
            assertNull(kind.local.load("not an id"))
        }

    @Test
    fun `a journal from another device is created or replaced without queueing an upload`() =
        runTest {
            kind.local.replaceLocal(SpaceJournal(journal))
            kind.local.replaceLocal(SpaceJournal(journal.copy(title = "Lake and river trips")))

            assertEquals(listOf(journal.id), repository.createdFromSync)
            assertEquals(listOf(journal.id), repository.updatedFromSync)
            assertEquals("Lake and river trips", repository.getJournalById(journal.id)?.title)
        }

    @Test
    fun `a merge from another device is applied as a redirect and never stored as a journal`() =
        runTest {
            kind.local.replaceLocal(SpaceJournal(journal, mergedInto = survivor))

            assertEquals(listOf(journal.id to survivor), repository.redirects)
            assertNull(repository.getJournalById(journal.id))
        }

    @Test
    fun `an upload is acknowledged with the version the space gave it`() =
        runTest {
            repository.create(journal)

            kind.local.acknowledgeUpload(SpaceJournal(journal), seq = 31, syncedAt = created)

            assertEquals(31, repository.getJournalById(journal.id)?.syncVersion)
        }

    @Test
    fun `the newest change in the space is the journal and its version`() {
        val written = SpacePartChange.Written(journal.id.toString(), SpaceJournal(journal), seq = 8)
        val changes = SpaceItemChanges<SpaceJournal>(journal.id.toString(), mapOf("app.logdate.journal.journal" to listOf(written)))

        assertEquals(SpaceJournal(journal.copy(syncVersion = 8)), kind.assemble(changes, null))
    }

    @Test
    fun `two journals are settled with the journal conflict resolver`() {
        val resolution =
            kind.local.conflictResolver.resolve(
                SpaceJournal(journal),
                SpaceJournal(journal.copy(title = "Other")),
                created,
                created,
            )

        assertIs<ConflictResolution.RequiresManualResolution<SpaceJournal>>(resolution)
    }

    @Test
    fun `a merge made elsewhere wins over an edit made here and over a deletion made here`() {
        val merged = SpaceJournal(journal, mergedInto = survivor)
        val resolver = kind.local.conflictResolver

        val resolution = resolver.resolve(SpaceJournal(journal.copy(title = "Edited here")), merged, created, created)

        assertEquals(merged, assertIs<ConflictResolution.KeepRemote<SpaceJournal>>(resolution).value)
        assertEquals(DeletionResolution.DELETE, resolver.resolveDeletion(merged, created))
        assertEquals(DeletionResolution.KEEP_EDIT, resolver.resolveDeletion(SpaceJournal(journal), created))
    }

    /** Stores journals like the app and remembers which writes came from sync. */
    private class SyncedJournals(
        private val base: FakeJournalRepository,
    ) : JournalRepository by base,
        SyncableJournalRepository {
        val createdFromSync = mutableListOf<Uuid>()
        val updatedFromSync = mutableListOf<Uuid>()
        val redirects = mutableListOf<Pair<Uuid, Uuid>>()

        override suspend fun createFromSync(journal: Journal) {
            createdFromSync += journal.id
            base.create(journal)
        }

        override suspend fun updateFromSync(journal: Journal) {
            updatedFromSync += journal.id
            base.update(journal)
        }

        override suspend fun deleteFromSync(journalId: Uuid) = base.delete(journalId)

        override suspend fun updateSyncMetadata(
            journalId: Uuid,
            syncVersion: Long,
            syncedAt: Instant,
        ) {
            val stored = base.getJournalById(journalId) ?: return
            base.update(stored.copy(syncVersion = syncVersion))
        }

        override suspend fun applyJournalRedirect(
            sourceId: Uuid,
            destinationId: Uuid,
            expectedScope: app.logdate.client.repository.journals.JournalMergeScope?,
        ) {
            redirects += sourceId to destinationId
        }
    }
}
