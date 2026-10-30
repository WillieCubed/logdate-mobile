package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.sync.SyncTransactionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** A restore applies its database records as one transaction, so a failure never leaves part of an archive behind. */
internal class RestoreAtomicityTest : RestoreArchiveTestFixture() {
    /** Mirrors a database transaction over the fakes: a failure inside restores every fake to where it started. */
    private inner class SnapshotTransactions : SyncTransactionManager {
        var transactions = 0

        override suspend fun <T> withTransaction(block: suspend () -> T): T {
            transactions++
            val journals = journalRepo.existingJournals.toMap()
            val createdJournals = journalRepo.created.toList()
            val notes = notesRepo.existingNotes.toMap()
            val createdNotes = notesRepo.created.toList()
            val links = contentRepo.links.toList()
            return try {
                block()
            } catch (failure: Throwable) {
                journalRepo.existingJournals.apply { clear() }.putAll(journals)
                journalRepo.created.apply { clear() }.addAll(createdJournals)
                notesRepo.existingNotes.apply { clear() }.putAll(notes)
                notesRepo.created.apply { clear() }.addAll(createdNotes)
                contentRepo.links.apply { clear() }.addAll(links)
                throw failure
            }
        }
    }

    private val transactions = SnapshotTransactions()

    private fun atomicUseCase() =
        RestoreUserDataUseCase(
            journalRepo,
            notesRepo,
            contentRepo,
            profileRepo,
            placesRepo,
            locationHistoryRepo,
            RestoreTestOwnerProvider(),
            RestoreTestDeviceIdProvider(),
            transactions,
        )

    private fun archive(noteCount: Int): RestoreBundle {
        val journalId = Uuid.random()
        val noteIds = List(noteCount) { Uuid.random() }
        return buildBundle(
            journals = listOf(testJournal(journalId)),
            notes = noteIds.map { testTextNote(it) },
            relations = noteIds.map { ExportJournalNoteRelation(journalId.toString(), it.toString(), now) },
        )
    }

    @Test
    fun `a restore that fails partway leaves no part of the archive behind`() =
        runTest {
            contentRepo.linkFailure = IllegalStateException("disk full")

            assertFailsWith<IllegalStateException> { atomicUseCase().restore(archive(noteCount = 3)) }

            assertTrue(journalRepo.existingJournals.isEmpty(), "journals from a failed restore were kept")
            assertTrue(notesRepo.existingNotes.isEmpty(), "notes from a failed restore were kept")
            assertTrue(contentRepo.links.isEmpty())
        }

    @Test
    fun `a cancelled restore leaves no part of the archive behind`() =
        runTest {
            contentRepo.linkFailure = CancellationException("restore cancelled")

            assertFailsWith<CancellationException> { atomicUseCase().restore(archive(noteCount = 3)) }

            assertTrue(journalRepo.existingJournals.isEmpty())
            assertTrue(notesRepo.existingNotes.isEmpty())
        }

    @Test
    fun `a successful restore commits every record in one transaction`() =
        runTest {
            val result = atomicUseCase().restore(archive(noteCount = 3))

            assertEquals(1, transactions.transactions)
            assertEquals(1, result.journalsImported)
            assertEquals(3, result.notesImported)
            assertEquals(3, result.journalLinksImported)
        }

    @Test
    fun `existing notes are read in batches instead of one query per note`() =
        runTest {
            atomicUseCase().restore(archive(noteCount = 25))

            assertEquals(0, notesRepo.singleReads)
            assertTrue(notesRepo.batchReads <= 2, "expected at most two batch reads, saw ${notesRepo.batchReads}")
            assertEquals(1, journalRepo.batchReads)
        }
}
