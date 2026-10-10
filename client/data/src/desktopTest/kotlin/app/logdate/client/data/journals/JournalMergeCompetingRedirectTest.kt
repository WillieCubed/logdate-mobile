package app.logdate.client.data.journals

import androidx.room.Room
import app.logdate.client.data.fakes.FakeDraftRepository
import app.logdate.client.data.fakes.FakeRemoteJournalDataSource
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.repository.journals.JournalMergeResult
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class JournalMergeCompetingRedirectTest {
    private val scope = JournalMergeScope("owner", "origin")

    @Test
    fun `competing remote redirect preserves captured and late items and settles the superseded request`() =
        runTest {
            withDatabase { database, repo ->
                val a = journal("Source")
                val b = journal("Local choice")
                val c = journal("Remote survivor")
                listOf(a, b, c).forEach { database.journalDao().create(it) }
                val incoming = Uuid.random()
                val overlap = Uuid.random()
                val ownedByB = Uuid.random()
                val late = Uuid.random()
                listOf(a.id to incoming, a.id to overlap, b.id to overlap, b.id to ownedByB).forEach { (journal, note) ->
                    database.journalContentDao().addContentToJournal(JournalContentEntityLink(journal, note))
                }
                val preview = repo.previewMerge(a.id, b.id)!!
                val operation = assertIs<JournalMergeResult.Merged>(repo.merge(preview, Uuid.random())).operation
                addPending(database, b.id, late)
                addPending(database, b.id, ownedByB)
                repo.applyJournalRedirect(a.id, c.id, scope)
                assertEquals(setOf(incoming, overlap, late, ownedByB), contents(database, c.id))
                assertTrue(contents(database, b.id).containsAll(setOf(overlap, ownedByB)))
                assertTrue(repo.pendingJournalMerges().isEmpty())
                assertTrue(database.syncMetadataDao().getPendingByType(scope.ownerId, scope.serverOrigin, "JOURNAL_MERGE").isEmpty())
                assertTrue(
                    database
                        .syncMetadataDao()
                        .getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION")
                        .map { it.entityId }
                        .containsAll(
                            listOf(AssociationPendingKey(b.id, late).toPendingId(), AssociationPendingKey(b.id, ownedByB).toPendingId()),
                        ),
                )
                assertEquals(c.id, repo.getJournalMerge(operation.operationId)?.destinationId)
                assertEquals(c.id, assertIs<JournalMergeResult.Merged>(repo.merge(preview, operation.operationId)).operation.destinationId)
                database.journalContentDao().removeContentFromJournal(c.id, incoming)
                repo.reconcileJournalRedirects(scope)
                assertEquals(setOf(overlap, late, ownedByB), contents(database, c.id))
            }
        }

    @Test
    fun `competing redirect retains memberships until its remote survivor downloads`() =
        runTest {
            withDatabase { database, repo ->
                val a = journal("Source")
                val b = journal("Local choice")
                val c = journal("Remote survivor")
                listOf(a, b).forEach { database.journalDao().create(it) }
                val incoming = Uuid.random()
                val late = Uuid.random()
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(a.id, incoming))
                val operation = assertIs<JournalMergeResult.Merged>(repo.merge(repo.previewMerge(a.id, b.id)!!, Uuid.random())).operation
                addPending(database, b.id, late)
                repo.deleteFromSync(b.id)
                repo.applyJournalRedirect(a.id, c.id, scope)
                assertTrue(database.syncMetadataDao().getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION").isEmpty())
                assertTrue(repo.pendingJournalMerges().isEmpty())
                assertEquals(c.id, repo.getJournalMerge(operation.operationId)?.destinationId)
                database.journalDao().create(c)
                repo.reconcileJournalRedirects(scope)
                assertEquals(setOf(incoming, late), contents(database, c.id))
            }
        }

    @Test
    fun `same canonical destination leaves the original pending request stable after a lost response`() =
        runTest {
            withDatabase { database, repo ->
                val a = journal("Source")
                val b = journal("Intermediate")
                val c = journal("Survivor")
                listOf(a, b, c).forEach { database.journalDao().create(it) }
                val operation = assertIs<JournalMergeResult.Merged>(repo.merge(repo.previewMerge(a.id, b.id)!!, Uuid.random())).operation
                repo.applyJournalRedirect(b.id, c.id, scope)
                repo.applyJournalRedirect(a.id, c.id, scope)
                assertEquals(operation, repo.pendingJournalMerges().single())
                assertEquals(1, database.syncMetadataDao().getPendingByType(scope.ownerId, scope.serverOrigin, "JOURNAL_MERGE").size)
                assertEquals(c.id, repo.resolveJournalId(a.id))
            }
        }

    @Test
    fun `source union retires a preceding destination removal without undoing later removals`() =
        runTest {
            withDatabase { database, repo ->
                val a = journal("Source")
                val b = journal("Destination")
                listOf(a, b).forEach { database.journalDao().create(it) }
                val note = Uuid.random()
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(a.id, note))
                val removal =
                    PendingUploadEntity(
                        scope.ownerId,
                        scope.serverOrigin,
                        "ASSOCIATION",
                        AssociationPendingKey(b.id, note).toPendingId(),
                        "DELETE",
                        1L,
                    )
                database.syncMetadataDao().insertPending(removal)
                val operation = assertIs<JournalMergeResult.Merged>(repo.merge(repo.previewMerge(a.id, b.id)!!, Uuid.random())).operation
                assertEquals(setOf(note), contents(database, b.id))
                assertTrue(database.syncMetadataDao().getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION").isEmpty())
                repo.markJournalMergeNeedsDestination(operation)
                database.journalContentDao().removeContentFromJournal(b.id, note)
                database.syncMetadataDao().insertPending(removal.copy(operationId = Uuid.random().toString()))
                repo.applyJournalRedirect(a.id, b.id, scope)
                assertTrue(contents(database, b.id).isEmpty())
                repo.markJournalMergeSynced(operation)
                repo.reconcileJournalRedirects(scope)
                assertTrue(contents(database, b.id).isEmpty())
                assertEquals(
                    "DELETE",
                    database
                        .syncMetadataDao()
                        .getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION")
                        .single()
                        .operation,
                )
            }
        }

    private fun journal(title: String) =
        JournalEntity(
            title = title,
            description = "",
            created = Instant.fromEpochMilliseconds(1L),
            lastUpdated = Instant.fromEpochMilliseconds(2L),
        )

    private suspend fun addPending(
        database: LogDateDatabase,
        journal: Uuid,
        note: Uuid,
    ) {
        database.journalContentDao().addContentToJournal(JournalContentEntityLink(journal, note))
        database.syncMetadataDao().insertPending(
            PendingUploadEntity(
                scope.ownerId,
                scope.serverOrigin,
                "ASSOCIATION",
                AssociationPendingKey(journal, note).toPendingId(),
                "CREATE",
                1L,
            ),
        )
    }

    private suspend fun contents(
        database: LogDateDatabase,
        journal: Uuid,
    ) = database
        .journalContentDao()
        .getContentForJournal(journal)
        .first()
        .toSet()

    private suspend fun withDatabase(block: suspend (LogDateDatabase, OfflineFirstJournalRepository) -> Unit) {
        val file = Files.createTempFile("journal-competing-merge", ".db")
        val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
        try {
            val repository =
                OfflineFirstJournalRepository(
                    journalDao = database.journalDao(),
                    remoteDataSource = FakeRemoteJournalDataSource(),
                    draftRepository = FakeDraftRepository(),
                    syncMetadataService = FakeSyncMetadataService(),
                    database = database,
                    mergeTransactionManager = RoomSyncTransactionManager(database),
                    currentScope = { scope },
                )
            block(database, repository)
        } finally {
            database.close()
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
        }
    }
}
