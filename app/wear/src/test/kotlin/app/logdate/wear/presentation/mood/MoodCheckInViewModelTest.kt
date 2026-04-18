package app.logdate.wear.presentation.mood

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class MoodCheckInViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var notesRepository: JournalNotesRepository
    private lateinit var dataLayerClient: WearDataLayerClient
    private lateinit var viewModel: MoodCheckInViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        notesRepository = mockk(relaxed = true)
        dataLayerClient = mockk(relaxed = true)
        coEvery { notesRepository.create(any()) } returns Uuid.random()
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
        viewModel = MoodCheckInViewModel(notesRepository, dataLayerClient)
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
                val vm = MoodCheckInViewModel(repo, dlc)

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
