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
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Applying a merge that another device made, where the surviving journal already holds some of the entries. */
class JournalRedirectDuplicateTest {
    private val scope = JournalMergeScope("owner", "https://example.test")

    private fun journal(title: String) =
        JournalEntity(
            title = title,
            description = "",
            created = Instant.fromEpochMilliseconds(1000),
            lastUpdated = Instant.fromEpochMilliseconds(1000),
        )

    private suspend fun withRepository(block: suspend (OfflineFirstJournalRepository, LogDateDatabase) -> Unit) {
        val file = Files.createTempFile("journal-redirect", ".db")
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
            block(repository, database)
        } finally {
            database.close()
            Files.deleteIfExists(file)
        }
    }

    private suspend fun contentOf(
        database: LogDateDatabase,
        journal: JournalEntity,
    ) = database
        .journalContentDao()
        .getContentForJournal(journal.id)
        .first()
        .toSet()

    @Test
    fun `an entry the survivor already holds is not added again and the merge repeats safely`() =
        runTest {
            withRepository { repository, database ->
                val source = journal("Lake")
                val survivor = journal("River")
                listOf(source, survivor).forEach { database.journalDao().create(it) }
                val both = Uuid.random()
                val onlyHere = Uuid.random()
                listOf(source.id to both, source.id to onlyHere, survivor.id to both).forEach { (journal, entry) ->
                    database.journalContentDao().addContentToJournal(JournalContentEntityLink(journal, entry))
                }

                repository.applyJournalRedirect(source.id, survivor.id)
                repository.applyJournalRedirect(source.id, survivor.id)
                repository.reconcileJournalRedirects()

                assertNull(database.journalDao().getJournalById(source.id))
                assertEquals(setOf(both, onlyHere), contentOf(database, survivor))
            }
        }

    @Test
    fun `a link already queued to upload for the survivor does not stop the merge`() =
        runTest {
            withRepository { repository, database ->
                val source = journal("Lake")
                val survivor = journal("River")
                listOf(source, survivor).forEach { database.journalDao().create(it) }
                val queuedCreate = Uuid.random()
                val queuedDelete = Uuid.random()
                listOf(queuedCreate, queuedDelete).forEach {
                    database.journalContentDao().addContentToJournal(JournalContentEntityLink(source.id, it))
                }
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(survivor.id, queuedCreate))
                listOf(queuedCreate to "CREATE", queuedDelete to "DELETE").forEach { (entry, operation) ->
                    database.syncMetadataDao().insertPending(
                        PendingUploadEntity(
                            scope.ownerId,
                            scope.serverOrigin,
                            "ASSOCIATION",
                            AssociationPendingKey(survivor.id, entry).toPendingId(),
                            operation,
                            1,
                        ),
                    )
                }

                repository.applyJournalRedirect(source.id, survivor.id)
                repository.applyJournalRedirect(source.id, survivor.id)

                assertEquals(setOf(queuedCreate, queuedDelete), contentOf(database, survivor))
            }
        }

    @Test
    fun `a merge into a survivor that has not arrived waits and finishes when it does`() =
        runTest {
            withRepository { repository, database ->
                val source = journal("Lake")
                val survivor = journal("River")
                database.journalDao().create(source)
                val entry = Uuid.random()
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(source.id, entry))

                repository.applyJournalRedirect(source.id, survivor.id)
                assertEquals(setOf(entry), contentOf(database, source))

                database.journalDao().create(survivor)
                repository.reconcileJournalRedirects()

                assertNull(database.journalDao().getJournalById(source.id))
                assertEquals(setOf(entry), contentOf(database, survivor))
            }
        }
}
