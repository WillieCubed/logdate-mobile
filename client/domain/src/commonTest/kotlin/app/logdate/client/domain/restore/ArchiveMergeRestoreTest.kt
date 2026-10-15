package app.logdate.client.domain.restore

import app.logdate.client.domain.restore.IntegrityCategory
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class ArchiveMergeRestoreTest : RestoreArchiveTestFixture() {
    @Test
    fun `MERGE_KEEP_NEWEST skips journal when existing is newer`() =
        runTest {
            val journalId = Uuid.random()
            val newerTimestamp = now
            val olderTimestamp = now - 7.days

            journalRepo.existingJournals[journalId] = testJournal(journalId, lastUpdated = newerTimestamp)

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId, lastUpdated = olderTimestamp)),
                )

            val result = useCase.restore(bundle, RestoreOptions(strategy = RestoreStrategy.MERGE_KEEP_NEWEST))

            assertEquals(0, result.journalsImported)
            assertTrue(journalRepo.created.isEmpty())
            assertTrue(journalRepo.updated.isEmpty())
        }

    @Test
    fun `MERGE_KEEP_NEWEST overwrites journal when incoming is newer`() =
        runTest {
            val journalId = Uuid.random()
            val olderTimestamp = now - 7.days
            val newerTimestamp = now

            journalRepo.existingJournals[journalId] = testJournal(journalId, lastUpdated = olderTimestamp)

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId, title = "Updated", lastUpdated = newerTimestamp)),
                )

            val result = useCase.restore(bundle, RestoreOptions(strategy = RestoreStrategy.MERGE_KEEP_NEWEST))

            assertEquals(1, result.journalsImported)
            assertEquals("Updated", journalRepo.updated.first().title)
        }

    @Test
    fun `REPLACE_EXISTING always overwrites existing journal`() =
        runTest {
            val journalId = Uuid.random()
            journalRepo.existingJournals[journalId] = testJournal(journalId, lastUpdated = now)

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId, title = "Replaced", lastUpdated = now - 30.days)),
                )

            val result = useCase.restore(bundle, RestoreOptions(strategy = RestoreStrategy.REPLACE_EXISTING))

            assertEquals(1, result.journalsImported)
            assertEquals("Replaced", journalRepo.updated.first().title)
        }

    @Test
    fun `MERGE_KEEP_NEWEST skips note when existing is newer`() =
        runTest {
            val noteId = Uuid.random()
            notesRepo.existingNotes[noteId] =
                JournalNote.Text(
                    uid = noteId,
                    creationTimestamp = now,
                    lastUpdated = now,
                    content = "Local version",
                )

            val bundle =
                buildBundle(
                    notes = listOf(testTextNote(noteId, updatedAt = now - 7.days)),
                )

            val result = useCase.restore(bundle)

            assertEquals(0, result.notesImported)
        }

    @Test
    fun `MERGE_KEEP_NEWEST overwrites note when incoming is newer`() =
        runTest {
            val noteId = Uuid.random()
            notesRepo.existingNotes[noteId] =
                JournalNote.Text(
                    uid = noteId,
                    creationTimestamp = now - 14.days,
                    lastUpdated = now - 7.days,
                    content = "Old version",
                )

            val bundle =
                buildBundle(
                    notes = listOf(testTextNote(noteId, content = "New version", updatedAt = now)),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.notesImported)
        }

    @Test
    fun `overwriting a note with a copy that has no time zone keeps the recorded zone`() =
        runTest {
            val noteId = Uuid.random()
            notesRepo.existingNotes[noteId] =
                JournalNote.Text(
                    uid = noteId,
                    creationTimestamp = now - 14.days,
                    lastUpdated = now - 7.days,
                    content = "Old version",
                    timeZoneId = "Europe/Paris",
                )

            val bundle =
                buildBundle(
                    notes = listOf(testTextNote(noteId, content = "New version", updatedAt = now)),
                )

            useCase.restore(bundle)

            val stored = notesRepo.existingNotes.getValue(noteId)
            assertEquals("New version", (stored as JournalNote.Text).content)
            assertEquals("Europe/Paris", stored.timeZoneId)
        }

    // endregion

    // region Error handling
}
