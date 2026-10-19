package app.logdate.client.sync.di

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.conflict.JournalNoteConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceRecordRegistry
import app.logdate.client.sync.test.FakeJournalNotesRepository
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

/** The real [spaceSyncModule] and [conflictResolverModule], started the way the sync modules include them. */
class SpaceSyncModuleTest {
    private val notes = FakeJournalNotesRepository()

    private fun startKoin() =
        koinApplication {
            modules(
                module { single<JournalNotesRepository> { notes } },
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
            assertEquals(setOf("app.logdate.journal.entry", "app.logdate.journal.transcript"), registry.collections)
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

    private val created = Instant.parse("2026-10-01T09:00:00Z")
}
