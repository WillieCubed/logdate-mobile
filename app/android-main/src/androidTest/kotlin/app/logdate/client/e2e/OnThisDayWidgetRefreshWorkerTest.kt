package app.logdate.client.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.domain.recommendation.AmbientPromptTime
import app.logdate.client.domain.recommendation.GetMemoryRecallUseCase
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.feature.widgets.OnThisDayWidgetRefreshWorker
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Instrumented test for the widget refresh worker.
 *
 * Uses [TestListenableWorkerBuilder] and a fake notes repository to verify
 * the worker produces the correct result under different conditions.
 */
@RunWith(AndroidJUnit4::class)
class OnThisDayWidgetRefreshWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        stopKoin() // Ensure clean Koin state
    }

    @After
    fun teardown() {
        stopKoin()
    }

    @Test
    fun workerSucceeds_withNoEntries() = runTest {
        setupKoin(
            notesForDay = emptyList(),
            contextualRecommendationsEnabled = true,
        )

        val worker = TestListenableWorkerBuilder<OnThisDayWidgetRefreshWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun workerSucceeds_whenRecommendationsDisabled() = runTest {
        setupKoin(
            notesForDay = emptyList(),
            contextualRecommendationsEnabled = false,
        )

        val worker = TestListenableWorkerBuilder<OnThisDayWidgetRefreshWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun workerSucceeds_withEntryOneYearAgo() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val oneYearAgo = today.minus(1, DateTimeUnit.YEAR)
        val timestamp = oneYearAgo.atStartOfDayIn(TimeZone.currentSystemDefault())

        setupKoin(
            notesForDay = listOf(
                JournalNote.Text(
                    uid = Uuid.random(),
                    content = "A wonderful day at the beach",
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    location = NoteLocation(),
                ),
            ),
            contextualRecommendationsEnabled = true,
        )

        val worker = TestListenableWorkerBuilder<OnThisDayWidgetRefreshWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun workerSucceeds_withArchiveMode() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val archiveDay = today.minus(45, DateTimeUnit.DAY)
        val timestamp = archiveDay.atStartOfDayIn(TimeZone.currentSystemDefault())

        setupKoin(
            notesForDay = listOf(
                JournalNote.Image(
                    uid = Uuid.random(),
                    mediaRef = "content://media/archive-photo",
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    location = NoteLocation(),
                ),
            ),
            contextualRecommendationsEnabled = true,
            recallMode = RecallMode.REDISCOVER,
        )

        val worker = TestListenableWorkerBuilder<OnThisDayWidgetRefreshWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun workerSucceeds_withPhotosOnlyContentFilter() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val oneYearAgo = today.minus(1, DateTimeUnit.YEAR)
        val timestamp = oneYearAgo.atStartOfDayIn(TimeZone.currentSystemDefault())

        setupKoin(
            notesForDay = listOf(
                JournalNote.Text(
                    uid = Uuid.random(),
                    content = "Text note",
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    location = NoteLocation(),
                ),
                JournalNote.Image(
                    uid = Uuid.random(),
                    mediaRef = "content://media/photo-only",
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    location = NoteLocation(),
                ),
            ),
            contextualRecommendationsEnabled = true,
            widgetContentTypes = setOf(WidgetContentType.PHOTOS),
        )

        val worker = TestListenableWorkerBuilder<OnThisDayWidgetRefreshWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    private fun setupKoin(
        notesForDay: List<JournalNote>,
        contextualRecommendationsEnabled: Boolean,
        recallMode: RecallMode = RecallMode.ON_THIS_DAY,
        widgetContentTypes: Set<WidgetContentType> = WidgetContentType.ALL,
    ) {
        val fakeRepo = FakeJournalNotesRepository(notesForDay)
        val getMemoryRecall = GetMemoryRecallUseCase(fakeRepo)
        val settings = FakeMemoriesSettingsRepository(
            MemoriesSettings(
                contextualRecommendationsEnabled = contextualRecommendationsEnabled,
                recallMode = recallMode,
                widgetContentTypes = widgetContentTypes,
            ),
        )

        startKoin {
            modules(
                module {
                    factory { getMemoryRecall }
                    single<MemoriesSettingsRepository> { settings }
                    single<JournalNotesRepository> { fakeRepo }
                },
            )
        }
    }
}

private class FakeMemoriesSettingsRepository(
    private val settings: MemoriesSettings,
) : MemoriesSettingsRepository {
    override suspend fun getSettings(): MemoriesSettings = settings
    override fun observeSettings(): Flow<MemoriesSettings> = flowOf(settings)
    override suspend fun updateSettings(settings: MemoriesSettings) {}
    override suspend fun setContextualRecommendationsEnabled(enabled: Boolean) {}
    override suspend fun setAmbientPromptsEnabled(enabled: Boolean) {}
    override suspend fun setCaptureNudgesEnabled(enabled: Boolean) {}
    override suspend fun setDraftRescueEnabled(enabled: Boolean) {}
    override suspend fun setMemoryRecallNotificationsEnabled(enabled: Boolean) {}
    override suspend fun setMorningPromptEnabled(enabled: Boolean) {}
    override suspend fun setEveningPromptEnabled(enabled: Boolean) {}
    override suspend fun setMorningPromptTime(time: AmbientPromptTime) {}
    override suspend fun setEveningPromptTime(time: AmbientPromptTime) {}
    override suspend fun setAiRecallEnabled(enabled: Boolean) {}
    override suspend fun setRecallMode(mode: RecallMode) {}
    override suspend fun setWidgetContentTypes(types: Set<WidgetContentType>) {}
}

private class FakeJournalNotesRepository(
    private val notesForDay: List<JournalNote>,
) : JournalNotesRepository {
    override val allNotesObserved: Flow<List<JournalNote>> = flowOf(notesForDay)
    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())
    override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> = flowOf(notesForDay)
    override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> = flowOf(notesForDay)
    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(notesForDay)
    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(notesForDay)
    override suspend fun getNoteById(noteId: Uuid): JournalNote? = null
    override suspend fun create(note: JournalNote): Uuid = throw UnsupportedOperationException()
    override suspend fun remove(note: JournalNote) {}
    override suspend fun removeById(noteId: Uuid) {}
    override suspend fun create(note: JournalNote, journalId: Uuid) {}
    override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {}
    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
}
