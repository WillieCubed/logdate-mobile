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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GetHomeRecommendationUseCaseTest {
    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var mockDraftRepository: MockEntryDraftRepository
    private lateinit var useCase: GetHomeRecommendationUseCase

    @BeforeTest
    fun setUp() {
        mockNotesRepository = MockJournalNotesRepository()
        mockDraftRepository = MockEntryDraftRepository()
        useCase =
            GetHomeRecommendationUseCase(
                hasNotesForToday = HasNotesForTodayUseCase(mockNotesRepository),
                fetchMostRecentDraft = FetchMostRecentDraftUseCase(mockDraftRepository),
            )
    }

    // --- None ---

    @Test
    fun `returns None when user has notes today and no drafts`() =
        runTest {
            mockNotesRepository.notesForRange = listOf(createTextNote())
            mockDraftRepository.drafts = emptyList()

            val result = useCase().first()

            assertIs<HomeRecommendation.None>(result)
        }

    // --- CaptureToday ---

    @Test
    fun `returns CaptureToday when user has no notes today and no drafts`() =
        runTest {
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = emptyList()

            val result = useCase().first()

            assertIs<HomeRecommendation.CaptureToday>(result)
        }

    @Test
    fun `CaptureToday carries default message`() =
        runTest {
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = emptyList()

            val result = useCase().first() as HomeRecommendation.CaptureToday

            assertEquals("You haven't added any memories today.", result.message)
        }

    // --- CompleteYourDraft ---

    @Test
    fun `returns CompleteYourDraft when a draft exists and no notes today`() =
        runTest {
            val draft = createDraftWithText("Working on something")
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first()

            assertIs<HomeRecommendation.CompleteYourDraft>(result)
        }

    @Test
    fun `CompleteYourDraft draft ID matches the draft`() =
        runTest {
            val draft = createDraftWithText("In progress")
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first() as HomeRecommendation.CompleteYourDraft

            assertEquals(draft.id, result.draftId)
        }

    @Test
    fun `CompleteYourDraft preview comes from first text note in draft`() =
        runTest {
            val draft = createDraftWithText("My draft content")
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first() as HomeRecommendation.CompleteYourDraft

            assertEquals("My draft content", result.notePreview)
        }

    @Test
    fun `CompleteYourDraft preview is null when draft has no text notes`() =
        runTest {
            val draft = createDraftWithAudioOnly()
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first() as HomeRecommendation.CompleteYourDraft

            assertNull(result.notePreview)
        }

    // --- Priority ---

    @Test
    fun `CompleteYourDraft takes priority over CaptureToday when draft exists`() =
        runTest {
            val draft = createDraftWithText("Draft content")
            // No notes today — would normally trigger CaptureToday
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first()

            assertIs<HomeRecommendation.CompleteYourDraft>(result)
        }

    @Test
    fun `CompleteYourDraft takes priority even when user already has notes today`() =
        runTest {
            val draft = createDraftWithText("Draft content")
            // User has notes today — would normally trigger None
            mockNotesRepository.notesForRange = listOf(createTextNote())
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first()

            assertIs<HomeRecommendation.CompleteYourDraft>(result)
        }

    // --- Reactivity ---

    @Test
    fun `emits updated recommendation when notes are added`() =
        runTest {
            val notesFlow = MutableStateFlow(emptyList<JournalNote>())
            mockNotesRepository.observeRangeFlow = notesFlow
            mockDraftRepository.drafts = emptyList()

            val emissions = mutableListOf<HomeRecommendation>()
            val job = launch { useCase().collect { emissions.add(it) } }

            delay(50)
            assertIs<HomeRecommendation.CaptureToday>(emissions[0])

            notesFlow.value = listOf(createTextNote())

            delay(50)
            assertEquals(2, emissions.size)
            assertIs<HomeRecommendation.None>(emissions[1])

            job.cancel()
        }

    @Test
    fun `emits updated recommendation when draft is created`() =
        runTest {
            val draftsFlow = MutableStateFlow(emptyList<EntryDraft>())
            mockDraftRepository.draftsFlow = draftsFlow
            mockNotesRepository.notesForRange = emptyList()

            val emissions = mutableListOf<HomeRecommendation>()
            val job = launch { useCase().collect { emissions.add(it) } }

            delay(50)
            assertIs<HomeRecommendation.CaptureToday>(emissions[0])

            draftsFlow.value = listOf(createDraftWithText("New draft"))

            delay(50)
            assertEquals(2, emissions.size)
            assertIs<HomeRecommendation.CompleteYourDraft>(emissions[1])

            job.cancel()
        }

    @Test
    fun `emits None when draft is deleted and notes exist`() =
        runTest {
            val draft = createDraftWithText("Will be deleted")
            val draftsFlow = MutableStateFlow(listOf(draft))
            mockDraftRepository.draftsFlow = draftsFlow
            mockNotesRepository.notesForRange = listOf(createTextNote())

            val emissions = mutableListOf<HomeRecommendation>()
            val job = launch { useCase().collect { emissions.add(it) } }

            delay(50)
            assertIs<HomeRecommendation.CompleteYourDraft>(emissions[0])

            draftsFlow.value = emptyList()

            delay(50)
            assertEquals(2, emissions.size)
            assertIs<HomeRecommendation.None>(emissions[1])

            job.cancel()
        }

    // --- Helpers ---

    private fun createTextNote(content: String = "Test note") =
        JournalNote.Text(
            uid = Uuid.random(),
            content = content,
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
        )

    private fun createDraftWithText(content: String): EntryDraft {
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

    private fun createDraftWithAudioOnly(): EntryDraft {
        val now = Clock.System.now()
        return EntryDraft(
            id = Uuid.random(),
            notes =
                listOf(
                    JournalNote.Audio(
                        uid = Uuid.random(),
                        mediaRef = "file://audio.m4a",
                        durationMs = 5000,
                        creationTimestamp = now,
                        lastUpdated = now,
                    ),
                ),
            createdAt = now,
            updatedAt = now,
        )
    }

    // --- Mocks ---

    private class MockJournalNotesRepository : JournalNotesRepository {
        var notesForRange = emptyList<JournalNote>()
        var observeRangeFlow: MutableStateFlow<List<JournalNote>>? = null

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> = observeRangeFlow ?: flowOf(notesForRange)

        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ) = flowOf(emptyList<JournalNote>())

        override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())

        override fun observeRecentNotes(limit: Int) = flowOf(emptyList<JournalNote>())

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

    private class MockEntryDraftRepository : EntryDraftRepository {
        var drafts = emptyList<EntryDraft>()
        var draftsFlow: MutableStateFlow<List<EntryDraft>>? = null

        override fun getDrafts(): Flow<List<EntryDraft>> = draftsFlow ?: flowOf(drafts)

        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = flowOf(Result.failure(NoSuchElementException()))

        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()

        override suspend fun updateDraft(
            uid: Uuid,
            notes: List<JournalNote>,
        ): Uuid = uid

        override suspend fun deleteDraft(uid: Uuid) = Unit
    }
}
