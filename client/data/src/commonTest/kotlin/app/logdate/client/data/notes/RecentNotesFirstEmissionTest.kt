package app.logdate.client.data.notes

import app.logdate.client.data.fakes.FakeAudioNoteDao
import app.logdate.client.data.fakes.FakeImageNoteDao
import app.logdate.client.data.fakes.FakeJournalContentDao
import app.logdate.client.data.fakes.FakeMediaCaptionDao
import app.logdate.client.data.fakes.FakeTextNoteDao
import app.logdate.client.data.fakes.FakeVideoNoteDao
import app.logdate.client.database.dao.MediaCaptionDao
import app.logdate.client.database.entities.MediaCaptionEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NotePlace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class RecentNotesFirstEmissionTest {
    @Test
    fun `recent local entries appear before place and caption indexes`() =
        runTest {
            val texts = FakeTextNoteDao()
            repeat(827) { index ->
                val created = Instant.fromEpochMilliseconds(index * 60_000L)
                texts.addNote(TextNoteEntity(content = "Saved entry $index", created = created, lastUpdated = created))
            }
            val captions =
                object : MediaCaptionDao by FakeMediaCaptionDao() {
                    override fun observeAll(): Flow<List<MediaCaptionEntity>> = flow { awaitCancellation() }
                }
            val places =
                object : NotePlaceResolver {
                    override suspend fun get(placeId: Uuid): NotePlace? = null

                    override fun observeAll(): Flow<Map<Uuid, NotePlace>> = flow { awaitCancellation() }
                }
            val queries =
                JournalNoteQueries(
                    texts,
                    FakeImageNoteDao(),
                    FakeAudioNoteDao(),
                    FakeVideoNoteDao(),
                    FakeJournalContentDao(),
                    captions,
                    places,
                    null,
                )
            val observed = mutableListOf<List<JournalNote>>()
            backgroundScope.launch { queries.observeRecentNotes(50).collect { observed += it } }

            runCurrent()

            assertEquals(50, observed.lastOrNull()?.size)
            assertEquals("Saved entry 826", (observed.lastOrNull()?.first() as? JournalNote.Text)?.content)
        }
}
