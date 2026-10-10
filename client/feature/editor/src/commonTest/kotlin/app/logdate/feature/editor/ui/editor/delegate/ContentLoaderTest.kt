package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.notes.FetchEntryUseCase
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalContentRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalNotesRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [ContentLoader.loadEntry]: the entry's block, and whether the editor should say it came from the watch. */
class ContentLoaderTest {
    private val notes = FakeJournalNotesRepository()
    private val loader =
        ContentLoader(
            fetchEntryUseCase = FetchEntryUseCase(notes),
            getDefaultSelectedJournals = GetDefaultSelectedJournalsUseCase(notes, FakeJournalContentRepository()),
        )
    private val at = Instant.parse("2026-10-10T15:42:00Z")

    @Test
    fun `an entry captured on the watch loads marked`() =
        runTest {
            val note = JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at, capturedOn = CaptureDevice.WATCH)
            notes.seedExternally(note)

            val loaded = loader.loadEntry(note.uid).getOrThrow()

            assertEquals(note.uid, loaded.block.id)
            assertTrue(loaded.capturedOnWatch)
        }

    @Test
    fun `an entry from the phone loads unmarked`() =
        runTest {
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Typed")
            notes.seedExternally(note)

            assertFalse(loader.loadEntry(note.uid).getOrThrow().capturedOnWatch)
        }

    @Test
    fun `a missing entry fails to load`() =
        runTest {
            assertTrue(loader.loadEntry(Uuid.random()).isFailure)
        }
}
