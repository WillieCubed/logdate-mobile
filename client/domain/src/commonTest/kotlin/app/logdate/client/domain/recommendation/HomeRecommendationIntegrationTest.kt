package app.logdate.client.domain.recommendation

import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Integration tests for the home recommendation pipeline.
 *
 * These tests exercise the full chain from raw repositories through [HasNotesForTodayUseCase]
 * and [FetchMostRecentDraftUseCase] into [GetHomeRecommendationUseCase], verifying that
 * the real UseCases produce the correct [HomeRecommendation] under each combination of
 * underlying data states.
 */
class HomeRecommendationIntegrationTest {
    // Reactive state flows that allow tests to push data changes mid-test
    private val notesFlow = MutableStateFlow(emptyList<JournalNote>())
    private val draftsFlow = MutableStateFlow(emptyList<EntryDraft>())

    private val useCase =
        GetHomeRecommendationUseCase(
            hasNotesForToday = HasNotesForTodayUseCase(ReactiveNotesRepository(notesFlow)),
            fetchMostRecentDraft = FetchMostRecentDraftUseCase(ReactiveDraftRepository(draftsFlow)),
        )

    @Test
    fun `fresh user sees CaptureToday recommendation`() =
        runTest {
            val result = useCase().first()
            assertIs<HomeRecommendation.CaptureToday>(result)
        }

    @Test
    fun `recommendation becomes None after user adds first note`() =
        runTest {
            notesFlow.value = listOf(textNote())

            val result = useCase().first()
            assertIs<HomeRecommendation.None>(result)
        }

    @Test
    fun `recommendation shows CompleteYourDraft when draft exists`() =
        runTest {
            draftsFlow.value = listOf(draftWithText("Hello world"))

            val result = useCase().first()
            assertIs<HomeRecommendation.CompleteYourDraft>(result)
        }

    @Test
    fun `draft recommendation appears even after user has added notes today`() =
        runTest {
            notesFlow.value = listOf(textNote())
            draftsFlow.value = listOf(draftWithText("Unfinished"))

            val result = useCase().first()
            assertIs<HomeRecommendation.CompleteYourDraft>(result)
        }

    @Test
    fun `recommendation transitions correctly across lifecycle states`() =
        runTest {
            val emissions = mutableListOf<HomeRecommendation>()
            val job = launch { useCase().collect { emissions.add(it) } }

            // State 1: no notes, no draft → CaptureToday
            delay(50)
            assertIs<HomeRecommendation.CaptureToday>(emissions.last())

            // State 2: draft created → CompleteYourDraft
            draftsFlow.value = listOf(draftWithText("Started"))
            delay(50)
            assertIs<HomeRecommendation.CompleteYourDraft>(emissions.last())

            // State 3: draft removed, still no notes → CaptureToday
            draftsFlow.value = emptyList()
            delay(50)
            assertIs<HomeRecommendation.CaptureToday>(emissions.last())

            // State 4: note added → None
            notesFlow.value = listOf(textNote())
            delay(50)
            assertIs<HomeRecommendation.None>(emissions.last())

            // State 5: new draft created on same day → CompleteYourDraft again
            draftsFlow.value = listOf(draftWithText("New draft"))
            delay(50)
            assertIs<HomeRecommendation.CompleteYourDraft>(emissions.last())

            job.cancel()
        }

    // --- Helpers ---

    private fun textNote(): JournalNote.Text {
        val now = Clock.System.now()
        return JournalNote.Text(
            uid = Uuid.random(),
            content = "A note",
            creationTimestamp = now,
            lastUpdated = now,
        )
    }

    private fun draftWithText(content: String): EntryDraft {
        val now = Clock.System.now()
        return EntryDraft(
            id = Uuid.random(),
            notes =
                listOf(
                    JournalNote.Text(
                        uid = Uuid.random(),
                        content = content,
                        creationTimestamp = now,
                        lastUpdated = now,
                    ),
                ),
            createdAt = now,
            updatedAt = now,
        )
    }

    // --- Reactive test doubles ---

    private class ReactiveNotesRepository(
        private val notesFlow: MutableStateFlow<List<JournalNote>>,
    ) : JournalNotesRepository {
        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> = notesFlow

        override val allNotesObserved: Flow<List<JournalNote>> = notesFlow

        override fun observeNotesInJournal(journalId: Uuid) = notesFlow

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ) = notesFlow

        override fun observeNotesStream(pageSize: Int) = notesFlow

        override fun observeRecentNotes(limit: Int) = notesFlow

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = null

        override suspend fun create(note: JournalNote): Uuid = note.uid

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) = Unit

        override suspend fun remove(note: JournalNote) = Unit

        override suspend fun removeById(noteId: Uuid) = Unit

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) = Unit
    }

    private class ReactiveDraftRepository(
        private val draftsFlow: MutableStateFlow<List<EntryDraft>>,
    ) : EntryDraftRepository {
        override fun getDrafts(): Flow<List<EntryDraft>> = draftsFlow

        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = throw UnsupportedOperationException()

        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()

        override suspend fun updateDraft(
            uid: Uuid,
            notes: List<JournalNote>,
        ): Uuid = uid

        override suspend fun deleteDraft(uid: Uuid) = Unit
    }
}
