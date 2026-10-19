package app.logdate.client.sync.di

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.conflict.JournalNoteConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceRecordRegistry
import app.logdate.client.sync.space.mappers.SpaceJournal
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The real [spaceSyncModule] and [conflictResolverModule], started the way the sync modules include them. */
class SpaceSyncModuleTest {
    private val notes = FakeJournalNotesRepository()
    private val journals = SyncedJournalRepository(FakeJournalRepository())

    private fun startKoin() =
        koinApplication {
            modules(
                module {
                    single<JournalNotesRepository> { notes }
                    single<JournalRepository> { journals }
                },
                conflictResolverModule,
                spaceSyncModule,
            )
        }

    @Test
    fun `the registry resolved from the space sync module has the notes kind`() {
        val app = startKoin()
        try {
            val registry = app.koin.get<SpaceRecordRegistry>()

            val kind = assertNotNull(registry.forEntityType(EntityType.NOTE))
            assertEquals(
                setOf("app.logdate.journal.entry", "app.logdate.journal.transcript", "app.logdate.journal.journal"),
                registry.collections,
            )
            assertEquals(listOf("app.logdate.journal.entry", "app.logdate.journal.transcript"), kind.parts.map { it.collection })
            assertEquals("app.logdate.journal.entry", kind.main.collection)
            assertIs<JournalNoteConflictResolver>(kind.local.conflictResolver)
        } finally {
            app.close()
        }
    }

    @Test
    fun `the notes kind reads the repository the app uses`() =
        runTest {
            val app = startKoin()
            try {
                val note = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "By the river")
                notes.create(note)

                val kind = app.koin.get<SpaceRecordRegistry>().forEntityType(EntityType.NOTE)!!

                assertEquals(note, kind.local.load(note.uid.toString()))
            } finally {
                app.close()
            }
        }

    @Test
    fun `the registry resolved from the space sync module has the journals kind`() {
        val app = startKoin()
        try {
            val kind = assertNotNull(app.koin.get<SpaceRecordRegistry>().forEntityType(EntityType.JOURNAL))

            assertEquals(listOf("app.logdate.journal.journal"), kind.parts.map { it.collection })
            assertEquals("app.logdate.journal.journal", kind.main.collection)
        } finally {
            app.close()
        }
    }

    @Test
    fun `the journals kind reads the repository the app uses`() =
        runTest {
            val app = startKoin()
            try {
                val journal = Journal(title = "By the river", created = created, lastUpdated = created)
                journals.create(journal)

                val kind = app.koin.get<SpaceRecordRegistry>().forEntityType(EntityType.JOURNAL)!!

                assertEquals(SpaceJournal(journal), kind.local.load(journal.id.toString()))
            } finally {
                app.close()
            }
        }

    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private class SyncedJournalRepository(
        private val base: FakeJournalRepository,
    ) : JournalRepository by base,
        SyncableJournalRepository {
        override suspend fun createFromSync(journal: Journal) {
            base.create(journal)
        }

        override suspend fun updateFromSync(journal: Journal) = base.update(journal)

        override suspend fun deleteFromSync(journalId: Uuid) = base.delete(journalId)

        override suspend fun updateSyncMetadata(
            journalId: Uuid,
            syncVersion: Long,
            syncedAt: Instant,
        ) = Unit
    }
}
