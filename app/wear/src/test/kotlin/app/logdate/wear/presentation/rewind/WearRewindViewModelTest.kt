package app.logdate.wear.presentation.rewind

import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit tests for the Wear OS Rewind summary experience.
 *
 * This class validates the [WearRewindViewModel], which handles the presentation
 * of personal summaries (Rewinds) on Wear. Key behaviors tested include:
 * - Fetching and listing available Rewinds from the repository.
 * - Managing panel-based playback navigation (advancing, rewinding, and exiting).
 * - Filtering content to ensure only Wear-compatible media (text/narrative) is
 *   presented, excluding unsupported types like images or videos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearRewindViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val fixedTime = Instant.fromEpochMilliseconds(1_710_000_000_000)

    private fun createRewind(
        title: String = "Your Week",
        content: List<RewindContent> = emptyList(),
    ): Rewind =
        Rewind(
            uid = Uuid.random(),
            startDate = fixedTime,
            endDate = Instant.fromEpochMilliseconds(fixedTime.toEpochMilliseconds() + 7 * 86_400_000),
            generationDate = fixedTime,
            label = "2024#10",
            title = title,
            content = content,
        )

    private fun createViewModel(rewinds: List<Rewind> = emptyList()): WearRewindViewModel {
        val repository = mockk<RewindRepository>()
        every { repository.getAllRewinds() } returns flowOf(rewinds)
        return WearRewindViewModel(repository)
    }

    // =======================================================================
    // Rewind list
    // =======================================================================

    @Test
    fun `empty state when no rewinds available`() =
        runTest {
            val vm = createViewModel(emptyList())
            val state = vm.rewindListState.first()

            assertTrue(state.rewinds.isEmpty())
        }

    @Test
    fun `shows available rewinds`() =
        runTest {
            val rewinds =
                listOf(
                    createRewind(title = "Week 1"),
                    createRewind(title = "Week 2"),
                )
            val vm = createViewModel(rewinds)
            val state = vm.rewindListState.first()

            assertEquals(2, state.rewinds.size)
        }

    // =======================================================================
    // Panel playback
    // =======================================================================

    @Test
    fun `selecting rewind starts playback at first panel`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "A week of exploration",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                    RewindContent.TextNote(
                        content = "Had a great day",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 60f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            val playback = vm.playbackState.first()

            assertEquals(0, playback?.currentIndex)
            assertEquals(2, playback?.totalPanels)
        }

    @Test
    fun `advance moves to next panel`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "Panel 1",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                    RewindContent.TextNote(
                        content = "Panel 2",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 60f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            vm.advance()
            val playback = vm.playbackState.first()

            assertEquals(1, playback?.currentIndex)
        }

    @Test
    fun `advance at last panel does not overflow`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "Only panel",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            vm.advance()
            val playback = vm.playbackState.first()

            assertEquals(0, playback?.currentIndex)
        }

    @Test
    fun `previous moves back`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "Panel 1",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                    RewindContent.TextNote(
                        content = "Panel 2",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 60f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            vm.advance()
            vm.previous()
            val playback = vm.playbackState.first()

            assertEquals(0, playback?.currentIndex)
        }

    @Test
    fun `previous at first panel stays at first`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "First panel",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            vm.previous()
            val playback = vm.playbackState.first()

            assertEquals(0, playback?.currentIndex)
        }

    @Test
    fun `exit playback clears state`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "Some panel",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            vm.exitPlayback()
            val playback = vm.playbackState.first()

            assertNull(playback)
        }

    // =======================================================================
    // Image filtering for Wear
    // =======================================================================

    @Test
    fun `image panels are filtered out for wear`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "Narrative",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                    RewindContent.Image(
                        uri = "/photo.jpg",
                        caption = "Photo",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 70f,
                    ),
                    RewindContent.TextNote(
                        content = "Text note",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 60f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            val playback = vm.playbackState.first()

            // Image should be filtered out, leaving 2 panels
            assertEquals(2, playback?.totalPanels)
        }

    @Test
    fun `video panels are filtered out for wear`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "Narrative",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                    RewindContent.Video(
                        uri = "/video.mp4",
                        duration = kotlin.time.Duration.parse("5s"),
                        caption = "Video",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 70f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            val playback = vm.playbackState.first()

            // Video should be filtered out, leaving 1 panel
            assertEquals(1, playback?.totalPanels)
        }

    @Test
    fun `isLastPanel is true at final panel`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "Only panel",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            val playback = vm.playbackState.first()

            assertTrue(playback!!.isLastPanel)
        }

    @Test
    fun `isLastPanel is false when more panels remain`() =
        runTest {
            val content =
                listOf(
                    RewindContent.NarrativeContext(
                        contextText = "Panel 1",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 80f,
                    ),
                    RewindContent.TextNote(
                        content = "Panel 2",
                        timestamp = fixedTime,
                        sourceId = Uuid.random(),
                        significanceScore = 60f,
                    ),
                )
            val rewind = createRewind(content = content)
            val vm = createViewModel(listOf(rewind))

            vm.selectRewind(rewind.uid)
            val playback = vm.playbackState.first()

            assertFalse(playback!!.isLastPanel)
        }
}
