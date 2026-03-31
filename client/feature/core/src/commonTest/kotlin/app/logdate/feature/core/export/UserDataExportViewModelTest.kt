package app.logdate.feature.core.export

import app.logdate.client.domain.export.ExportCounts
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.export.GetExportCountsUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

private class FakeExportLauncher : ExportLauncher {
    private val _exportProgress = MutableStateFlow(ExportProgressInfo())
    override val exportProgress: StateFlow<ExportProgressInfo> = _exportProgress.asStateFlow()

    val startExportCalls = mutableListOf<ExportOptions>()
    var cancelExportCallCount = 0
        private set
    private var completionCallback: ((String?) -> Unit)? = null

    override fun startExport(options: ExportOptions) {
        startExportCalls.add(options)
    }

    override fun cancelExport() {
        cancelExportCallCount++
    }

    override fun updateProgress(info: ExportProgressInfo) {
        _exportProgress.value = info
    }

    override fun setExportCompletionCallback(callback: (String?) -> Unit) {
        completionCallback = callback
    }

    fun triggerCompletion(path: String?) {
        completionCallback?.invoke(path)
    }

    fun emitProgress(info: ExportProgressInfo) {
        _exportProgress.value = info
    }
}

private class FakeJournalRepository : JournalRepository {
    private val journalsFlow = MutableStateFlow<List<Journal>>(emptyList())
    var testDrafts: List<EditorDraft> = emptyList()

    override val allJournalsObserved: Flow<List<Journal>> = journalsFlow

    override fun observeJournalById(id: Uuid): Flow<Journal> = flowOf(Journal(id = id))

    override suspend fun getJournalById(id: Uuid): Journal? = null

    override suspend fun create(journal: Journal): Uuid = journal.id

    override suspend fun update(journal: Journal) {}

    override suspend fun delete(journalId: Uuid) {}

    override suspend fun saveDraft(draft: EditorDraft) {}

    override suspend fun getLatestDraft(): EditorDraft? = null

    override suspend fun getAllDrafts(): List<EditorDraft> = testDrafts

    override suspend fun getDraft(id: Uuid): EditorDraft? = null

    override suspend fun deleteDraft(id: Uuid) {}
}

private class FakeJournalNotesRepository : JournalNotesRepository {
    private val notesFlow = MutableStateFlow<List<JournalNote>>(emptyList())

    override val allNotesObserved: Flow<List<JournalNote>> = notesFlow

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

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = null

    override suspend fun create(note: JournalNote): Uuid = note.uid

    override suspend fun remove(note: JournalNote) {}

    override suspend fun removeById(noteId: Uuid) {}

    override suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    ) {}

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) {}

    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
}

/**
 * A [GetExportCountsUseCase] wrapper that allows controlling the result in tests.
 *
 * Since [GetExportCountsUseCase] is a final class, this builds a real instance
 * backed by fake repositories that return empty data by default. Tests that need
 * a failure can swap in [FailingGetExportCountsUseCase] instead.
 */
private fun createSuccessfulGetExportCountsUseCase(): GetExportCountsUseCase {
    val journalRepo = FakeJournalRepository()
    val notesRepo = FakeJournalNotesRepository()
    return GetExportCountsUseCase(journalRepo, notesRepo)
}

/**
 * A subclass-like workaround: since [GetExportCountsUseCase] is final, we construct
 * a real one backed by a notes repository whose [allNotesObserved] flow throws,
 * causing the use case's [invoke] to throw.
 */
private fun createFailingGetExportCountsUseCase(): GetExportCountsUseCase {
    val journalRepo = FakeJournalRepository()
    val failingNotesRepo =
        object : JournalNotesRepository {
            override val allNotesObserved: Flow<List<JournalNote>>
                get() = throw RuntimeException("Database error")

            override fun observeNotesInJournal(journalId: Uuid) = throw RuntimeException("Database error")

            override fun observeNotesInRange(
                start: Instant,
                end: Instant,
            ) = throw RuntimeException("Database error")

            override fun observeNotesPage(
                pageSize: Int,
                offset: Int,
            ) = throw RuntimeException("Database error")

            override fun observeNotesStream(pageSize: Int) = throw RuntimeException("Database error")

            override fun observeRecentNotes(limit: Int) = throw RuntimeException("Database error")

            override suspend fun getNoteById(noteId: Uuid): JournalNote? = throw RuntimeException("Database error")

            override suspend fun create(note: JournalNote): Uuid = throw RuntimeException("Database error")

            override suspend fun remove(note: JournalNote) = throw RuntimeException("Database error")

            override suspend fun removeById(noteId: Uuid) = throw RuntimeException("Database error")

            override suspend fun create(
                note: JournalNote,
                journalId: Uuid,
            ) = throw RuntimeException("Database error")

            override suspend fun removeFromJournal(
                noteId: Uuid,
                journalId: Uuid,
            ) = throw RuntimeException("Database error")

            override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = throw RuntimeException("Database error")
        }
    return GetExportCountsUseCase(journalRepo, failingNotesRepo)
}

@OptIn(ExperimentalCoroutinesApi::class)
class UserDataExportViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeExportLauncher: FakeExportLauncher
    private lateinit var viewModel: UserDataExportViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeExportLauncher = FakeExportLauncher()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        getExportCountsUseCase: GetExportCountsUseCase = createSuccessfulGetExportCountsUseCase(),
    ): UserDataExportViewModel =
        UserDataExportViewModel(
            exportLauncher = fakeExportLauncher,
            getExportCountsUseCase = getExportCountsUseCase,
        )

    @Test
    fun `initial state is Idle`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertIs<ExportState.Idle>(viewModel.exportState.value)
        }

    @Test
    fun `showExportOptions transitions to Configuring with default options`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()

            val state = viewModel.exportState.value
            assertIs<ExportState.Configuring>(state)
            assertEquals(ExportOptions(), state.options)
        }

    @Test
    fun `showExportOptions loads counts asynchronously and updates Configuring state`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            advanceUntilIdle()

            val state = viewModel.exportState.value
            assertIs<ExportState.Configuring>(state)
            val counts = state.counts
            // Fake repos return empty lists, so all counts should be 0
            assertEquals(
                ExportCounts(journalCount = 0, noteCount = 0, draftCount = 0, mediaCount = 0),
                counts,
            )
        }

    @Test
    fun `showExportOptions handles count loading failure gracefully`() =
        testScope.runTest {
            viewModel =
                createViewModel(
                    getExportCountsUseCase = createFailingGetExportCountsUseCase(),
                )
            advanceUntilIdle()

            viewModel.showExportOptions()
            advanceUntilIdle()

            val state = viewModel.exportState.value
            assertIs<ExportState.Configuring>(state)
            assertNull(state.counts)
        }

    @Test
    fun `updateExportOptions updates options in Configuring state`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()

            val customOptions =
                ExportOptions(
                    includeJournals = true,
                    includeNotes = false,
                    includeDrafts = false,
                    includeMedia = true,
                    dateRange = ExportDateRange.Last30Days,
                )
            viewModel.updateExportOptions(customOptions)

            val state = viewModel.exportState.value
            assertIs<ExportState.Configuring>(state)
            assertEquals(customOptions, state.options)
        }

    @Test
    fun `updateExportOptions is no-op when not in Configuring state`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val customOptions = ExportOptions(includeNotes = false)
            viewModel.updateExportOptions(customOptions)

            assertIs<ExportState.Idle>(viewModel.exportState.value)
        }

    @Test
    fun `dismissSheet transitions to Idle from Configuring`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            assertIs<ExportState.Configuring>(viewModel.exportState.value)

            viewModel.dismissSheet()
            assertIs<ExportState.Idle>(viewModel.exportState.value)
        }

    @Test
    fun `confirmExport saves options and transitions to Selecting`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            viewModel.confirmExport()

            assertIs<ExportState.Selecting>(viewModel.exportState.value)
        }

    @Test
    fun `confirmExport calls exportLauncher startExport with saved options`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            val customOptions = ExportOptions(includeMedia = false, includeDrafts = false)
            viewModel.updateExportOptions(customOptions)
            viewModel.confirmExport()

            assertEquals(1, fakeExportLauncher.startExportCalls.size)
            assertEquals(customOptions, fakeExportLauncher.startExportCalls.first())
        }

    @Test
    fun `cancelExport calls exportLauncher cancelExport and transitions to Idle`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            viewModel.confirmExport()
            viewModel.cancelExport()

            assertEquals(1, fakeExportLauncher.cancelExportCallCount)
            assertIs<ExportState.Idle>(viewModel.exportState.value)
        }

    @Test
    fun `retryExport transitions to Selecting and calls startExport with last options`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val customOptions = ExportOptions(includeMedia = false)
            viewModel.showExportOptions()
            viewModel.updateExportOptions(customOptions)
            viewModel.confirmExport()

            // Simulate entering Exporting state then failure via null completion
            fakeExportLauncher.emitProgress(
                ExportProgressInfo(isActive = true, progressPercent = 50),
            )
            advanceUntilIdle()
            fakeExportLauncher.triggerCompletion(null)
            assertIs<ExportState.Failed>(viewModel.exportState.value)

            fakeExportLauncher.startExportCalls.clear()
            viewModel.retryExport()

            assertIs<ExportState.Selecting>(viewModel.exportState.value)
            assertEquals(1, fakeExportLauncher.startExportCalls.size)
            assertEquals(customOptions, fakeExportLauncher.startExportCalls.first())
        }

    @Test
    fun `dismissSheet transitions to Idle from Completed`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            viewModel.confirmExport()
            fakeExportLauncher.emitProgress(
                ExportProgressInfo(
                    isActive = false,
                    completedFilePath = "/path/to/export.zip",
                ),
            )
            advanceUntilIdle()

            assertIs<ExportState.Completed>(viewModel.exportState.value)

            viewModel.dismissSheet()
            assertIs<ExportState.Idle>(viewModel.exportState.value)
        }

    @Test
    fun `dismissSheet transitions to Idle from Failed`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            viewModel.confirmExport()

            // Enter Exporting state then trigger failure
            fakeExportLauncher.emitProgress(
                ExportProgressInfo(isActive = true, progressPercent = 10),
            )
            advanceUntilIdle()
            fakeExportLauncher.triggerCompletion(null)

            assertIs<ExportState.Failed>(viewModel.exportState.value)

            viewModel.dismissSheet()
            assertIs<ExportState.Idle>(viewModel.exportState.value)
        }

    @Test
    fun `completion progress with path transitions to Completed with extracted fileName`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            viewModel.confirmExport()
            fakeExportLauncher.emitProgress(
                ExportProgressInfo(
                    isActive = false,
                    completedFilePath = "/storage/exports/logdate-export.zip",
                ),
            )
            advanceUntilIdle()

            val state = viewModel.exportState.value
            assertIs<ExportState.Completed>(state)
            assertEquals("/storage/exports/logdate-export.zip", state.path)
            assertEquals("logdate-export.zip", state.fileName)
        }

    @Test
    fun `completion callback with null during Exporting transitions to Failed`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            viewModel.confirmExport()

            // Enter Exporting state via progress
            fakeExportLauncher.emitProgress(
                ExportProgressInfo(isActive = true, progressPercent = 25),
            )
            advanceUntilIdle()
            assertIs<ExportState.Exporting>(viewModel.exportState.value)

            fakeExportLauncher.triggerCompletion(null)

            val state = viewModel.exportState.value
            assertIs<ExportState.Failed>(state)
            assertEquals("Export was cancelled or failed", state.reason)
            assertTrue(state.canRetry)
        }

    @Test
    fun `completion callback with null during non-Exporting transitions to Idle`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            viewModel.confirmExport()

            // State is Selecting, not Exporting
            assertIs<ExportState.Selecting>(viewModel.exportState.value)

            fakeExportLauncher.triggerCompletion(null)

            assertIs<ExportState.Idle>(viewModel.exportState.value)
        }

    @Test
    fun `progress updates from exportLauncher update state to Exporting`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            fakeExportLauncher.emitProgress(
                ExportProgressInfo(
                    isActive = true,
                    progressPercent = 42,
                    message = "Exporting notes...",
                ),
            )
            advanceUntilIdle()

            val state = viewModel.exportState.value
            assertIs<ExportState.Exporting>(state)
            assertEquals(42, state.progressPercent)
            assertEquals("Exporting notes...", state.message)
        }

    @Test
    fun `progress updates with isActive false are ignored`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()

            fakeExportLauncher.emitProgress(
                ExportProgressInfo(isActive = false, progressPercent = 100, message = "Done"),
            )
            advanceUntilIdle()

            // Should remain in Configuring, not transition to Exporting
            assertIs<ExportState.Configuring>(viewModel.exportState.value)
        }

    @Test
    fun `direct completion via progress channel transitions to Completed`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            viewModel.confirmExport()

            val testStats =
                ExportStats(
                    journalCount = 3,
                    noteCount = 12,
                    draftCount = 2,
                    mediaCount = 5,
                )

            // Simulate worker sending completion directly through progress
            fakeExportLauncher.emitProgress(
                ExportProgressInfo(
                    isActive = false,
                    progressPercent = 100,
                    message = "Export completed",
                    completedFilePath = "/storage/exports/logdate-export.zip",
                    stats = testStats,
                ),
            )
            advanceUntilIdle()

            val state = viewModel.exportState.value
            assertIs<ExportState.Completed>(state)
            assertEquals("/storage/exports/logdate-export.zip", state.path)
            assertEquals("logdate-export.zip", state.fileName)
            assertEquals(testStats, state.stats)
        }

    @Test
    fun `direct completion is not overwritten by late callback`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showExportOptions()
            viewModel.confirmExport()

            // Direct completion fires first
            fakeExportLauncher.emitProgress(
                ExportProgressInfo(
                    isActive = false,
                    completedFilePath = "/storage/exports/logdate-export.zip",
                ),
            )
            advanceUntilIdle()
            assertIs<ExportState.Completed>(viewModel.exportState.value)

            // Late WorkManager callback should be ignored
            fakeExportLauncher.triggerCompletion(null)
            assertIs<ExportState.Completed>(viewModel.exportState.value)
        }

    @Test
    fun `fileName extraction works for file paths`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            fakeExportLauncher.emitProgress(
                ExportProgressInfo(
                    isActive = false,
                    completedFilePath = "/path/to/file.zip",
                ),
            )
            advanceUntilIdle()

            val state = viewModel.exportState.value
            assertIs<ExportState.Completed>(state)
            assertEquals("file.zip", state.fileName)
        }

    @Test
    fun `fileName extraction works for content URIs`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            fakeExportLauncher.emitProgress(
                ExportProgressInfo(
                    isActive = false,
                    completedFilePath = "content://com.android.providers.downloads/doc:logdate-export.zip",
                ),
            )
            advanceUntilIdle()

            val state = viewModel.exportState.value
            assertIs<ExportState.Completed>(state)
            assertEquals("logdate-export.zip", state.fileName)
        }
}
