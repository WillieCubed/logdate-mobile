package app.logdate.wear.presentation.mood

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.capture.Feeling
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for the Wear OS Mood Check-in state machine and business logic.
 *
 * This class validates the [MoodCheckInViewModel], ensuring it correctly manages the
 * multi-step capture flow (mood selection -> optional voice prompt -> success).
 * It verifies that journal notes are persisted with appropriate mood-specific
 * metadata (tags) and that the view model emits the correct navigation and
 * UI events during the check-in lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodCheckInViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var notesRepository: JournalNotesRepository
    private lateinit var dataLayerClient: WearDataLayerClient
    private lateinit var locationCaptureCoordinator: WearLocationCaptureCoordinator
    private lateinit var viewModel: MoodCheckInViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        notesRepository = mockk(relaxed = true)
        dataLayerClient = mockk(relaxed = true)
        locationCaptureCoordinator = mockk(relaxed = true)
        coEvery { notesRepository.create(any()) } returns Uuid.random()
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        viewModel = MoodCheckInViewModel(notesRepository, dataLayerClient, locationCaptureCoordinator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is SELECT_MOOD with no selection`() =
        runTest {
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(MoodCheckInStep.SELECT_MOOD, state.step)
                assertNull(state.selectedMood)
            }
        }

    @Test
    fun `selectMood transitions to VOICE_PROMPT`() =
        runTest {
            viewModel.uiState.test {
                skipItems(1) // initial state
                viewModel.selectMood(Feeling.GOOD)
                val state = awaitItem()
                assertEquals(MoodCheckInStep.VOICE_PROMPT, state.step)
                assertEquals(Feeling.GOOD, state.selectedMood)
            }
        }

    @Test
    fun `skipVoiceAttachment saves the check-in in the stored note format`() =
        runTest {
            viewModel.selectMood(Feeling.GREAT)
            viewModel.skipVoiceAttachment()

            coVerify {
                notesRepository.create(
                    match { note -> note is JournalNote.Text && note.content == "#mood:great Feeling great" },
                )
            }
        }

    @Test
    fun `skipVoiceAttachment attaches current watch location to mood note`() =
        runTest {
            val capturedLocation =
                NoteLocation(
                    coordinates =
                        NoteCoordinates(
                            latitude = 37.7749,
                            longitude = -122.4194,
                            altitude = 14.0,
                        ),
                )
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns capturedLocation

            viewModel.selectMood(Feeling.GREAT)
            viewModel.skipVoiceAttachment()

            coVerify {
                notesRepository.create(
                    match { note ->
                        note is JournalNote.Text && note.location == capturedLocation
                    },
                )
            }
        }

    @Test
    fun `skipVoiceAttachment transitions to SAVED`() =
        runTest {
            viewModel.selectMood(Feeling.OK)

            viewModel.uiState.test {
                skipItems(1) // current state
                viewModel.skipVoiceAttachment()
                val savedState = awaitItem()
                assertEquals(MoodCheckInStep.SAVED, savedState.step)
            }
        }

    @Test
    fun `skipVoiceAttachment emits NavigateBack event`() =
        runTest {
            viewModel.selectMood(Feeling.SAD)

            viewModel.events.test {
                viewModel.skipVoiceAttachment()
                assertEquals(MoodCheckInEvent.NavigateBack, awaitItem())
            }
        }

    @Test
    fun `attachVoice saves the mood, then opens the recorder`() =
        runTest {
            viewModel.selectMood(Feeling.ROUGH)

            viewModel.events.test {
                viewModel.attachVoice()
                assertEquals(MoodCheckInEvent.NavigateToVoiceNote, awaitItem())
            }

            coVerify(exactly = 1) {
                notesRepository.create(
                    match { note -> note is JournalNote.Text && note.content == "#mood:rough Feeling rough" },
                )
            }
        }

    @Test
    fun `attachVoice stays on the prompt when the mood cannot be saved`() =
        runTest {
            coEvery { notesRepository.create(any()) } throws IllegalStateException("The database is closed")
            viewModel.selectMood(Feeling.GOOD)

            viewModel.events.test {
                viewModel.attachVoice()
                expectNoEvents()
            }

            coVerify(exactly = 1) { notesRepository.create(any()) }
            val state = viewModel.uiState.value
            assertEquals(MoodCheckInStep.VOICE_PROMPT, state.step)
            assertFalse(state.isSaving)
            assertFalse(state.isSaved)
            assertNull(state.saveFeedback)
        }

    @Test
    fun `a failed save after Open recorder is reported on the prompt`() =
        runTest {
            coEvery { notesRepository.create(any()) } throws IllegalStateException("The database is closed")
            viewModel.selectMood(Feeling.GOOD)

            viewModel.attachVoice()

            assertTrue(viewModel.uiState.value.saveFailed)
        }

    @Test
    fun `a failed save after Skip is reported on the prompt`() =
        runTest {
            coEvery { notesRepository.create(any()) } throws IllegalStateException("The database is closed")
            viewModel.selectMood(Feeling.GOOD)

            viewModel.skipVoiceAttachment()

            assertTrue(viewModel.uiState.value.saveFailed)
        }

    @Test
    fun `a retry after a failed save clears the failure and saves`() =
        runTest {
            coEvery { notesRepository.create(any()) } throws IllegalStateException("The database is closed") andThen Uuid.random()
            viewModel.selectMood(Feeling.GOOD)
            viewModel.attachVoice()

            viewModel.events.test {
                viewModel.attachVoice()
                assertEquals(MoodCheckInEvent.NavigateToVoiceNote, awaitItem())
            }

            assertFalse(viewModel.uiState.value.saveFailed)
        }

    @Test
    fun `tapping Open recorder twice while the save is pending saves one mood`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { notesRepository.create(any()) } coAnswers
                {
                    gate.await()
                    Uuid.random()
                }
            viewModel.selectMood(Feeling.GREAT)

            viewModel.events.test {
                viewModel.attachVoice()
                viewModel.attachVoice()
                gate.complete(Unit)

                assertEquals(MoodCheckInEvent.NavigateToVoiceNote, awaitItem())
                expectNoEvents()
            }

            coVerify(exactly = 1) { notesRepository.create(any()) }
        }

    @Test
    fun `tapping Skip while Open recorder is saving saves one mood and opens the recorder once`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { notesRepository.create(any()) } coAnswers
                {
                    gate.await()
                    Uuid.random()
                }
            viewModel.selectMood(Feeling.GREAT)

            viewModel.events.test {
                viewModel.attachVoice()
                viewModel.skipVoiceAttachment()
                gate.complete(Unit)

                assertEquals(MoodCheckInEvent.NavigateToVoiceNote, awaitItem())
                expectNoEvents()
            }

            coVerify(exactly = 1) { notesRepository.create(any()) }
            assertEquals(MoodCheckInStep.VOICE_PROMPT, viewModel.uiState.value.step)
        }

    @Test
    fun `tapping Open recorder while Skip is saving saves one mood and navigates back once`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { notesRepository.create(any()) } coAnswers
                {
                    gate.await()
                    Uuid.random()
                }
            viewModel.selectMood(Feeling.GREAT)

            viewModel.events.test {
                viewModel.skipVoiceAttachment()
                viewModel.attachVoice()
                gate.complete(Unit)

                assertEquals(MoodCheckInEvent.NavigateBack, awaitItem())
                expectNoEvents()
            }

            coVerify(exactly = 1) { notesRepository.create(any()) }
        }

    @Test
    fun `attachVoice does nothing without mood selection`() =
        runTest {
            viewModel.attachVoice()
            coVerify(exactly = 0) { notesRepository.create(any()) }
        }

    @Test
    fun `skipVoiceAttachment does nothing without mood selection`() =
        runTest {
            viewModel.skipVoiceAttachment()
            coVerify(exactly = 0) { notesRepository.create(any()) }
        }

    @Test
    fun `every feeling is saved under its own tag`() =
        runTest {
            for (feeling in Feeling.entries) {
                val repo: JournalNotesRepository = mockk(relaxed = true)
                val dlc: WearDataLayerClient = mockk(relaxed = true)
                coEvery { repo.create(any()) } returns Uuid.random()
                coEvery { dlc.isPhoneConnected(any()) } returns false
                val locationCoordinator: WearLocationCaptureCoordinator = mockk(relaxed = true)
                coEvery { locationCoordinator.captureForJournalEntry() } returns null
                val vm = MoodCheckInViewModel(repo, dlc, locationCoordinator)

                vm.selectMood(feeling)
                vm.skipVoiceAttachment()

                coVerify {
                    repo.create(
                        match { note ->
                            note is JournalNote.Text && note.content == "#mood:${feeling.tag} Feeling ${feeling.tag}"
                        },
                    )
                }
            }
        }
}
