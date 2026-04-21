package app.logdate.wear.presentation.mood

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import kotlin.test.assertNull
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
                viewModel.selectMood(MoodOption.GOOD)
                val state = awaitItem()
                assertEquals(MoodCheckInStep.VOICE_PROMPT, state.step)
                assertEquals(MoodOption.GOOD, state.selectedMood)
            }
        }

    @Test
    fun `skipVoiceAttachment saves note with mood tag`() =
        runTest {
            viewModel.selectMood(MoodOption.GREAT)
            viewModel.skipVoiceAttachment()

            coVerify {
                notesRepository.create(
                    match { note ->
                        note is JournalNote.Text && note.content.startsWith("#mood:great")
                    },
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

            viewModel.selectMood(MoodOption.GREAT)
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
            viewModel.selectMood(MoodOption.OK)

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
            viewModel.selectMood(MoodOption.SAD)

            viewModel.events.test {
                viewModel.skipVoiceAttachment()
                assertEquals(MoodCheckInEvent.NavigateBack, awaitItem())
            }
        }

    @Test
    fun `attachVoice emits NavigateToVoiceNote event`() =
        runTest {
            viewModel.selectMood(MoodOption.ROUGH)

            viewModel.events.test {
                viewModel.attachVoice()
                assertEquals(MoodCheckInEvent.NavigateToVoiceNote, awaitItem())
            }
        }

    @Test
    fun `skipVoiceAttachment does nothing without mood selection`() =
        runTest {
            viewModel.skipVoiceAttachment()
            coVerify(exactly = 0) { notesRepository.create(any()) }
        }

    @Test
    fun `each mood option produces correct tag prefix`() =
        runTest {
            for (mood in MoodOption.entries) {
                val repo: JournalNotesRepository = mockk(relaxed = true)
                val dlc: WearDataLayerClient = mockk(relaxed = true)
                coEvery { repo.create(any()) } returns Uuid.random()
                coEvery { dlc.isPhoneConnected(any()) } returns false
                val locationCoordinator: WearLocationCaptureCoordinator = mockk(relaxed = true)
                coEvery { locationCoordinator.captureForJournalEntry() } returns null
                val vm = MoodCheckInViewModel(repo, dlc, locationCoordinator)

                vm.selectMood(mood)
                vm.skipVoiceAttachment()

                coVerify {
                    repo.create(
                        match { note ->
                            note is JournalNote.Text && note.content.contains("#mood:${mood.tag}")
                        },
                    )
                }
            }
        }
}
