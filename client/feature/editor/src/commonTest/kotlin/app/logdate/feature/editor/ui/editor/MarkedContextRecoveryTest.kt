package app.logdate.feature.editor.ui.editor

import app.logdate.client.domain.editor.ObserveEditorDataUseCase
import app.logdate.client.domain.editor.SaveEntryUseCase
import app.logdate.client.domain.journals.GetCurrentUserJournalsUseCase
import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.location.LocationRetryWorker
import app.logdate.client.domain.location.LogCurrentLocationUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.FetchEntryUseCase
import app.logdate.client.domain.notes.FetchTodayNotesUseCase
import app.logdate.client.domain.notes.drafts.CleanupExpiredDraftsUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.notes.drafts.GetAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.domain.world.LogLocationUseCase
import app.logdate.feature.editor.ui.editor.delegate.ContentLoader
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import app.logdate.feature.editor.ui.editor.fakes.FakeActivityTimelineRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeClientLocationProvider
import app.logdate.feature.editor.ui.editor.fakes.FakeEntryDraftRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalContentRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalNotesRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeLocationHistoryRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeLocationTrackingSettingsRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeMediaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
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
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MarkedContextRecoveryTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val journalNotesRepository = FakeJournalNotesRepository()
    private val journalContentRepository = FakeJournalContentRepository()
    private val draftStorage = FakeEntryDraftRepository()
    private val draftMarkIds = mutableMapOf<Uuid, String>()
    private val entryDraftRepository =
        object : app.logdate.client.repository.journals.EntryDraftRepository by draftStorage {
            override fun getDrafts() =
                draftStorage.getDrafts().map { drafts ->
                    drafts.map { it.copy(markId = draftMarkIds[it.id]) }
                }

            override fun getDraft(uid: Uuid) =
                draftStorage.getDraft(uid).map { result ->
                    result.map { it.copy(markId = draftMarkIds[it.id]) }
                }
        }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `explicit new entry keeps content and drops authoritative mark context before flow catches up`() =
        testScope.runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()
            val markId = Uuid.random().toString()
            viewModel.initializeMarkContext(markId)
            viewModel.appendTextBlock("Retain this writing")
            advanceUntilIdle()
            val prior = viewModel.editorState.value
            assertEquals(markId, prior.markId)
            viewModel.continueWithoutMarkedMoment()
            val current = viewModel.currentDraftPersistenceState(prior)
            assertEquals(null, current.markId)
            assertEquals(prior.blocks, current.blocks)
            viewModel.initializeMarkContext(markId)
            assertEquals(null, viewModel.currentDraftPersistenceState(prior).markId)
            advanceUntilIdle()
            assertFalse(viewModel.editorState.value.shouldExit)
        }

    @Test
    fun `write more reopens the same mark draft instead of replacing long writing with caption`() =
        testScope.runTest {
            val markId = Uuid.random().toString()
            val draftId = Uuid.random()
            val now =
                kotlin.time.Clock.System
                    .now()
            draftMarkIds[draftId] = markId
            entryDraftRepository.createDraft(
                uid = draftId,
                notes =
                    listOf(
                        app.logdate.client.repository.journals.JournalNote.Text(
                            creationTimestamp = now,
                            lastUpdated = now,
                            content = "Caption and the longer story",
                        ),
                    ),
                pendingMedia = emptyList(),
                selectedJournalIds = emptyList(),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.openMarkedMoment(markId, "Caption")
            advanceUntilIdle()
            assertEquals(draftId, (viewModel.editorState.value.draftState as DraftState.Active).id)
            assertEquals(
                "Caption and the longer story",
                viewModel.editorState.value.blocks
                    .filterIsInstance<TextBlockUiState>()
                    .single()
                    .content,
            )
            assertEquals(markId, viewModel.editorState.value.markId)
        }

    @Test
    fun `new marked editor starts with retained caption and navigation replay never duplicates it`() =
        testScope.runTest {
            val markId = Uuid.random().toString()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.openMarkedMoment(markId, "A quiet morning")
            advanceUntilIdle()
            assertEquals(
                "A quiet morning",
                viewModel.editorState.value.blocks
                    .filterIsInstance<TextBlockUiState>()
                    .single()
                    .content,
            )
            viewModel.openMarkedMoment(markId, "A stale caption")
            advanceUntilIdle()
            assertEquals(
                "A quiet morning",
                viewModel.editorState.value.blocks
                    .filterIsInstance<TextBlockUiState>()
                    .single()
                    .content,
            )
        }

    private fun createViewModel(): EntryEditorViewModel {
        val journalRepository = FakeJournalRepository()

        val locationProvider = FakeClientLocationProvider()
        val activityTimelineRepository = FakeActivityTimelineRepository()
        val locationHistoryRepository = FakeLocationHistoryRepository()
        val locationRetryWorker =
            LocationRetryWorker(
                locationProvider = locationProvider,
                locationHistoryRepository = locationHistoryRepository,
                coroutineScope = testScope.backgroundScope,
            )
        val logLocationUseCase = LogLocationUseCase(locationProvider, activityTimelineRepository)
        val logCurrentLocationUseCase =
            LogCurrentLocationUseCase(
                locationProvider = locationProvider,
                locationHistoryRepository = locationHistoryRepository,
                locationRetryWorker = locationRetryWorker,
                canonicalOwnerProvider = TestCanonicalOwnerProvider(),
                deviceIdProvider = TestDeviceIdProvider(),
            )
        val mediaManager = FakeMediaManager()

        val addNoteUseCase =
            AddNoteUseCase(
                repository = journalNotesRepository,
                journalContentRepository = journalContentRepository,
                logLocationUseCase = logLocationUseCase,
                logCurrentLocationUseCase = logCurrentLocationUseCase,
                settingsRepository = FakeLocationTrackingSettingsRepository(),
                mediaManager = mediaManager,
            )
        val deleteEntryDraft = DeleteEntryDraftUseCase(entryDraftRepository, journalNotesRepository)

        val observeEditorData =
            ObserveEditorDataUseCase(
                fetchTodayNotes = FetchTodayNotesUseCase(journalNotesRepository),
                getCurrentUserJournals = GetCurrentUserJournalsUseCase(journalRepository),
                fetchMostRecentDraft = FetchMostRecentDraftUseCase(entryDraftRepository),
                getAllDrafts = GetAllDraftsUseCase(entryDraftRepository),
            )
        val saveEntryUseCase =
            SaveEntryUseCase(
                addNoteUseCase = addNoteUseCase,
                deleteEntryDraft = deleteEntryDraft,
            )
        val draftManager =
            DraftManager(
                updateEntryDraft = UpdateEntryDraftUseCase(entryDraftRepository),
                createEntryDraft = CreateEntryDraftUseCase(entryDraftRepository),
                fetchEntryDraft = FetchEntryDraftUseCase(entryDraftRepository),
                deleteEntryDraft = deleteEntryDraft,
                deleteAllDraftsUseCase = DeleteAllDraftsUseCase(entryDraftRepository),
                cleanupExpiredDraftsUseCase = CleanupExpiredDraftsUseCase(entryDraftRepository),
            )
        val contentLoader =
            ContentLoader(
                fetchEntryUseCase = FetchEntryUseCase(journalNotesRepository),
                getDefaultSelectedJournals =
                    GetDefaultSelectedJournalsUseCase(
                        journalNotesRepository,
                        journalContentRepository,
                    ),
            )

        return EntryEditorViewModel(
            observeEditorData = observeEditorData,
            saveEntryUseCase = saveEntryUseCase,
            draftManager = draftManager,
            contentLoader = contentLoader,
        )
    }
}
