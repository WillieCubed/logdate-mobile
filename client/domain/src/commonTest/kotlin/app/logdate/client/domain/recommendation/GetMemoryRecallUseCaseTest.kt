package app.logdate.client.domain.recommendation

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GetMemoryRecallUseCaseTest {
    private lateinit var notesRepository: FakeJournalNotesRepository
    private lateinit var useCase: GetMemoryRecallUseCase

    @BeforeTest
    fun setUp() {
        notesRepository = FakeJournalNotesRepository()
        useCase = GetMemoryRecallUseCase(notesRepository)
    }

    @Test
    fun `ON_THIS_DAY returns notes from the exact anniversary day`() =
        runTest {
            val targetDate = currentLocalDate().minus(1, DateTimeUnit.YEAR)
            notesRepository.addTextNote(targetDate, "A day at the beach")

            val result = useCase().first()

            assertNotNull(result)
            assertEquals(targetDate, result.date)
            assertEquals("A day at the beach", result.summary)
            assertEquals(emptyList(), result.mediaUris)
        }

    @Test
    fun `ON_THIS_DAY falls back to the richest day in the anniversary window`() =
        runTest {
            val targetDate = currentLocalDate().minus(1, DateTimeUnit.YEAR)
            val richerDay = targetDate.minus(1, DateTimeUnit.DAY)
            notesRepository.addTextNote(targetDate.plus(2, DateTimeUnit.DAY), "One note")
            notesRepository.addTextNote(richerDay, "First note")
            notesRepository.addTextNote(richerDay, "Second note")

            val result = useCase().first()

            assertNotNull(result)
            assertEquals(richerDay, result.date)
        }

    @Test
    fun `REDISCOVER returns the most recent notable older day`() =
        runTest {
            val today = currentLocalDate()
            val recentNotableDay = today.minus(45, DateTimeUnit.DAY)
            val olderNotableDay = today.minus(90, DateTimeUnit.DAY)
            notesRepository.addImageNote(recentNotableDay, "content://media/recent-photo")
            notesRepository.addTextNote(olderNotableDay, "Older note one")
            notesRepository.addTextNote(olderNotableDay, "Older note two")

            val result = useCase(recallMode = RecallMode.REDISCOVER).first()

            assertNotNull(result)
            assertEquals(recentNotableDay, result.date)
            assertEquals(listOf("content://media/recent-photo"), result.mediaUris)
        }

    @Test
    fun `REDISCOVER ignores notes inside the recent-history window`() =
        runTest {
            val today = currentLocalDate()
            val recentDay = today.minus(10, DateTimeUnit.DAY)
            val olderDay = today.minus(60, DateTimeUnit.DAY)
            notesRepository.addImageNote(recentDay, "content://media/recent-photo")
            notesRepository.addTextNote(olderDay, "Archive note")

            val result = useCase(recallMode = RecallMode.REDISCOVER).first()

            assertNotNull(result)
            assertEquals(olderDay, result.date)
        }

    @Test
    fun `TEXT content type keeps summaries but removes media thumbnails`() =
        runTest {
            val targetDate = currentLocalDate().minus(1, DateTimeUnit.YEAR)
            notesRepository.addTextNote(targetDate, "A written memory")
            notesRepository.addImageNote(targetDate, "content://media/photo")

            val result =
                useCase(
                    contentTypes = setOf(WidgetContentType.TEXT),
                ).first()

            assertNotNull(result)
            assertEquals(targetDate, result.date)
            assertEquals("A written memory", result.summary)
            assertEquals(emptyList(), result.mediaUris)
        }

    @Test
    fun `PHOTOS content type only surfaces photo candidates and media`() =
        runTest {
            val targetDate = currentLocalDate().minus(1, DateTimeUnit.YEAR)
            val photoDay = targetDate.plus(1, DateTimeUnit.DAY)
            notesRepository.addTextNote(targetDate, "Text only")
            notesRepository.addImageNote(photoDay, "content://media/photo")
            notesRepository.addVideoNote(photoDay, "content://media/video")

            val result =
                useCase(
                    contentTypes = setOf(WidgetContentType.PHOTOS),
                ).first()

            assertNotNull(result)
            assertEquals(photoDay, result.date)
            assertEquals("", result.summary)
            assertEquals(
                listOf("content://media/photo", "content://media/video"),
                result.mediaUris,
            )
        }

    @Test
    fun `AUDIO content type surfaces audio-only memories`() =
        runTest {
            val targetDate = currentLocalDate().minus(1, DateTimeUnit.YEAR)
            notesRepository.addAudioNote(targetDate, "content://media/audio")

            val result =
                useCase(
                    contentTypes = setOf(WidgetContentType.AUDIO),
                ).first()

            assertNotNull(result)
            assertEquals(targetDate, result.date)
            assertEquals("", result.summary)
            assertEquals(emptyList(), result.mediaUris)
        }

    @Test
    fun `AUDIO content type on a day with text returns text summary`() =
        runTest {
            val targetDate = currentLocalDate().minus(1, DateTimeUnit.YEAR)
            notesRepository.addTextNote(targetDate, "Today was interesting")
            notesRepository.addAudioNote(targetDate, "content://media/audio")

            val result = useCase().first()

            assertNotNull(result)
            assertEquals(targetDate, result.date)
            assertEquals("Today was interesting", result.summary)
            assertEquals(emptyList(), result.mediaUris)
        }

    @Test
    fun `AUDIO content type picks the richer day in the anniversary window`() =
        runTest {
            // targetDate has no notes so the exact-day check falls through to window scoring
            val targetDate = currentLocalDate().minus(1, DateTimeUnit.YEAR)
            val sparserDay = targetDate.minus(1, DateTimeUnit.DAY)
            val richerDay = targetDate.plus(1, DateTimeUnit.DAY)
            notesRepository.addAudioNote(sparserDay, "content://media/audio-1")
            notesRepository.addAudioNote(richerDay, "content://media/audio-2")
            notesRepository.addAudioNote(richerDay, "content://media/audio-3")

            val result =
                useCase(
                    contentTypes = setOf(WidgetContentType.AUDIO),
                ).first()

            assertNotNull(result)
            assertEquals(richerDay, result.date)
        }

    @Test
    fun `AUDIO content type in REDISCOVER mode surfaces older audio notes`() =
        runTest {
            val today = currentLocalDate()
            val olderDay = today.minus(60, DateTimeUnit.DAY)
            notesRepository.addAudioNote(olderDay, "content://media/audio-old")

            val result =
                useCase(
                    recallMode = RecallMode.REDISCOVER,
                    contentTypes = setOf(WidgetContentType.AUDIO),
                ).first()

            assertNotNull(result)
            assertEquals(olderDay, result.date)
            assertEquals("", result.summary)
            assertEquals(emptyList(), result.mediaUris)
        }

    @Test
    fun `AI provider still takes precedence when enabled`() =
        runTest {
            val aiResult =
                MemoryRecallData(
                    date = LocalDate(2021, 7, 14),
                    summary = "AI-selected memory",
                    mediaUris = listOf("content://media/ai"),
                )
            val aiUseCase =
                GetMemoryRecallUseCase(
                    notesRepository = notesRepository,
                    aiRecallProvider =
                        object : AiRecallProvider {
                            override suspend fun suggestRecall(): MemoryRecallData? = aiResult
                        },
                )

            val result =
                aiUseCase(
                    aiEnabled = true,
                    recallMode = RecallMode.REDISCOVER,
                    contentTypes = setOf(WidgetContentType.PHOTOS),
                ).first()

            assertEquals(aiResult, result)
        }

    private fun currentLocalDate(): LocalDate =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
}

private class FakeJournalNotesRepository : JournalNotesRepository {
    private val notes = mutableListOf<JournalNote>()
    private val timezone = TimeZone.currentSystemDefault()

    override val allNotesObserved: Flow<List<JournalNote>>
        get() = flowOf(notes.toList())

    fun addTextNote(
        day: LocalDate,
        content: String,
    ) {
        val timestamp = day.atStartOfDayIn(timezone)
        notes +=
            JournalNote.Text(
                uid = Uuid.random(),
                content = content,
                creationTimestamp = timestamp,
                lastUpdated = timestamp,
                location = NoteLocation(),
            )
    }

    fun addImageNote(
        day: LocalDate,
        mediaRef: String,
    ) {
        val timestamp = day.atStartOfDayIn(timezone)
        notes +=
            JournalNote.Image(
                uid = Uuid.random(),
                mediaRef = mediaRef,
                creationTimestamp = timestamp,
                lastUpdated = timestamp,
                location = NoteLocation(),
            )
    }

    fun addVideoNote(
        day: LocalDate,
        mediaRef: String,
    ) {
        val timestamp = day.atStartOfDayIn(timezone)
        notes +=
            JournalNote.Video(
                uid = Uuid.random(),
                mediaRef = mediaRef,
                creationTimestamp = timestamp,
                lastUpdated = timestamp,
                location = NoteLocation(),
            )
    }

    fun addAudioNote(
        day: LocalDate,
        mediaRef: String,
    ) {
        val timestamp = day.atStartOfDayIn(timezone)
        notes +=
            JournalNote.Audio(
                uid = Uuid.random(),
                mediaRef = mediaRef,
                creationTimestamp = timestamp,
                lastUpdated = timestamp,
                location = NoteLocation(),
            )
    }

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = notes.firstOrNull { note -> note.uid == noteId }

    override suspend fun create(note: JournalNote): Uuid {
        notes += note
        return note.uid
    }

    override suspend fun remove(note: JournalNote) {
        notes.removeAll { existing -> existing.uid == note.uid }
    }

    override suspend fun removeById(noteId: Uuid) {
        notes.removeAll { existing -> existing.uid == noteId }
    }

    override suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    ) {
        notes += note
    }

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) {}
}
