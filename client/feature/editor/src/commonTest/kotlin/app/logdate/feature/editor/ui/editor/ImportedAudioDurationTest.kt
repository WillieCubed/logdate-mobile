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
import app.logdate.client.media.MediaCleaner
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.repository.journals.JournalNote
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportedAudioDurationTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var journalNotesRepository: FakeJournalNotesRepository
    private lateinit var entryDraftRepository: FakeEntryDraftRepository
    private val deletedPaths = mutableListOf<String>()
    private val mediaCleaner =
        object : MediaCleaner {
            override suspend fun delete(path: String) {
                deletedPaths += path
            }
        }

    private fun buildViewModel(resolver: AudioDurationResolver): EntryEditorViewModel {
        val journalContentRepository = FakeJournalContentRepository()
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
        val deleteEntryDraft = DeleteEntryDraftUseCase(entryDraftRepository, journalNotesRepository, mediaCleaner)

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
            audioDurationResolver = resolver,
        )
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        journalNotesRepository = FakeJournalNotesRepository()
        entryDraftRepository = FakeEntryDraftRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun importedManagedAudioPersistsItsRealDuration() =
        testScope.runTest {
            val viewModel = buildViewModel(AudioDurationResolver { 3_888L })
            viewModel.editorState.first()
            viewModel.setInitialAttachments(listOf("file:///retained/shared.wav"))
            advanceUntilIdle()
            val block =
                viewModel.editorState.value.blocks
                    .filterIsInstance<AudioBlockUiState>()
                    .single()
            assertEquals(3_888L, block.durationMs)
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()
            val saved = assertIs<JournalNote.Audio>(journalNotesRepository.allNotesObserved.first().single())
            assertEquals(3_888L, saved.durationMs)
            assertEquals("file:///retained/shared.wav", saved.mediaRef)
        }

    @Test
    fun retainedZeroDurationAudioIsResolvedBeforePublish() =
        testScope.runTest {
            val viewModel = buildViewModel(AudioDurationResolver { 3_888L })
            viewModel.editorState.first()
            val block = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            viewModel.updateBlock(block.copy(captureState = AudioCaptureState.Ready("file:///retained/shared.wav", 0)))
            advanceUntilIdle()
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()
            val saved = assertIs<JournalNote.Audio>(journalNotesRepository.allNotesObserved.first().single())
            assertEquals(3_888L, saved.durationMs)
        }

    @Test
    fun unresolvedDurationKeepsManagedAudioAndDraftAvailableForRetry() =
        testScope.runTest {
            var available = false
            val viewModel = buildViewModel(AudioDurationResolver { if (available) 3_888L else null })
            viewModel.editorState.first()
            viewModel.setInitialAttachments(listOf("file:///retained/shared.wav"))
            advanceUntilIdle()
            viewModel.persistDraft(viewModel.editorState.value)
            advanceUntilIdle()
            val draftId = (viewModel.editorState.value.draftState as? DraftState.Active)?.id
            assertNotNull(draftId)
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()
            assertTrue(journalNotesRepository.allNotesObserved.first().isEmpty())
            assertNotNull(viewModel.editorState.value.errorMessage)
            assertEquals(draftId, (viewModel.editorState.value.draftState as? DraftState.Active)?.id)
            assertEquals(
                "file:///retained/shared.wav",
                viewModel.editorState.value.blocks
                    .filterIsInstance<AudioBlockUiState>()
                    .single()
                    .uri,
            )
            assertTrue(deletedPaths.isEmpty())
            available = true
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()
            assertEquals(3_888L, assertIs<JournalNote.Audio>(journalNotesRepository.allNotesObserved.first().single()).durationMs)
        }

    @Test
    fun allSharedAttachmentsAreRetainedWhileTheFirstDurationReadIsPending() =
        testScope.runTest {
            val duration = CompletableDeferred<Long>()
            val viewModel = buildViewModel(AudioDurationResolver { duration.await() })
            viewModel.editorState.first()
            viewModel.setInitialAttachments(listOf("file:///retained/first.wav", "file:///retained/second.wav"))
            runCurrent()
            assertEquals(
                2,
                viewModel.editorState.value.blocks
                    .filterIsInstance<AudioBlockUiState>()
                    .size,
            )
            duration.complete(3_888L)
            advanceUntilIdle()
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()
            assertEquals(2, journalNotesRepository.allNotesObserved.first().size)
        }

    @Test
    fun cancelledMetadataReadIsNotTurnedIntoAnUnusableAttachment() =
        testScope.runTest {
            val viewModel = buildViewModel(AudioDurationResolver { throw CancellationException("Editor closed") })
            assertFailsWith<CancellationException> {
                viewModel.resolveAudioDuration(AudioCaptureState.Ready("file:///retained/shared.wav", 0))
            }
            assertTrue(deletedPaths.isEmpty())
        }

    @Test
    fun knownDurationIsPreservedWithoutReadingTheFileAgain() =
        testScope.runTest {
            val viewModel = buildViewModel(AudioDurationResolver { error("Known duration must not be replaced") })
            viewModel.editorState.first()
            val block = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            viewModel.updateBlock(block.copy(captureState = AudioCaptureState.Ready("file:///retained/shared.wav", 7_500)))
            advanceUntilIdle()
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()
            assertEquals(7_500L, assertIs<JournalNote.Audio>(journalNotesRepository.allNotesObserved.first().single()).durationMs)
        }
}
