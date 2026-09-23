package app.logdate.feature.onboarding.ui

import androidx.lifecycle.SavedStateHandle
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.MediaPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Exercises the [MemorySelectionViewModel] to ensure smooth media curation during onboarding.
 *
 * This suite validates the reactive UI state management for:
 * - Fetching and displaying personal media (memories) for user selection
 * - Falling back to recent media when requested date ranges are empty
 * - Curating memories using a local or generative AI client
 * - Recovering gracefully from media access or loading failures
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemorySelectionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeMediaManager: FakeMediaManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeMediaManager = FakeMediaManager()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh memories falls back to recent media when date range is empty`() =
        runTest {
            val olderMemories = listOf(sampleImage("older-1"), sampleVideo("older-2"))
            fakeMediaManager.queryMediaByDateFlow = { flowOf(emptyList()) }
            fakeMediaManager.recentMediaFlow = { flowOf(olderMemories) }

            val viewModel = createViewModel()
            viewModel.refreshMemories()
            advanceUntilIdle()

            assertEquals(olderMemories, viewModel.uiState.value.allMemories)
            assertEquals(
                olderMemories.toSet(),
                viewModel.uiState.value.aiCuratedMemories
                    .toSet(),
            )
            assertEquals(false, viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.loadFailed)
        }

    @Test
    fun `refresh memories recovers after load failure`() =
        runTest {
            fakeMediaManager.queryMediaByDateFlow = {
                flow {
                    throw IllegalStateException("Media access denied")
                }
            }

            val viewModel = createViewModel()
            viewModel.refreshMemories()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.loadFailed)
            assertTrue(
                viewModel.uiState.value.allMemories
                    .isEmpty(),
            )

            val recoveredMemories = listOf(sampleImage("recovered-1"))
            fakeMediaManager.queryMediaByDateFlow = { flowOf(emptyList()) }
            fakeMediaManager.recentMediaFlow = { flowOf(recoveredMemories) }

            viewModel.refreshMemories()
            advanceUntilIdle()

            assertEquals(recoveredMemories, viewModel.uiState.value.allMemories)
            assertFalse(viewModel.uiState.value.loadFailed)
            assertEquals(false, viewModel.uiState.value.isLoading)
        }

    @Test
    fun `selections survive a fresh view model over the same saved state, as after process death`() =
        runTest {
            val memories = listOf(sampleImage("keep-1"), sampleImage("keep-2"))
            fakeMediaManager.queryMediaByDateFlow = { flowOf(memories) }
            val savedStateHandle = SavedStateHandle()

            val firstViewModel = createViewModel(savedStateHandle)
            firstViewModel.refreshMemories()
            advanceUntilIdle()
            firstViewModel.toggleMemorySelection(memories[0].uri)

            // Simulates the process dying and a fresh view model being created over the same
            // saved state, rather than a fresh, empty one.
            val secondViewModel = createViewModel(savedStateHandle)

            assertEquals(setOf(memories[0].uri), secondViewModel.uiState.value.selectedMemoryIds)
        }

    @Test
    fun `continuing with selected memories imports them and clears importing state`() =
        runTest {
            val memories = listOf(sampleImage("keep-1"))
            fakeMediaManager.queryMediaByDateFlow = { flowOf(memories) }

            val viewModel = createViewModel()
            viewModel.refreshMemories()
            advanceUntilIdle()
            viewModel.toggleMemorySelection(memories.single().uri)

            val result = viewModel.processSelectedMemories()

            assertTrue(result.isSuccess)
            assertFalse(viewModel.uiState.value.isImporting)
            assertFalse(viewModel.uiState.value.importFailed)
            assertEquals(listOf(memories.single().uri), fakeMediaManager.addedToCollection)
        }

    @Test
    fun `a failed import surfaces importFailed instead of failing silently`() =
        runTest {
            val memories = listOf(sampleImage("broken-1"))
            fakeMediaManager.queryMediaByDateFlow = { flowOf(memories) }
            fakeMediaManager.addToDefaultCollectionError = IllegalStateException("Import failed")

            val viewModel = createViewModel()
            viewModel.refreshMemories()
            advanceUntilIdle()
            viewModel.toggleMemorySelection(memories.single().uri)

            val result = viewModel.processSelectedMemories()

            assertTrue(result.isFailure)
            assertFalse(viewModel.uiState.value.isImporting)
            assertTrue(viewModel.uiState.value.importFailed)
        }

    @Test
    fun `a second concurrent import attempt is rejected instead of double importing`() =
        runTest {
            val memories = listOf(sampleImage("slow-1"))
            fakeMediaManager.queryMediaByDateFlow = { flowOf(memories) }
            fakeMediaManager.addToDefaultCollectionDelay = { delay(1_000) }

            val viewModel = createViewModel()
            viewModel.refreshMemories()
            advanceUntilIdle()
            viewModel.toggleMemorySelection(memories.single().uri)

            val firstImport = async { viewModel.processSelectedMemories() }
            runCurrent()
            assertTrue(viewModel.uiState.value.isImporting)

            val secondImport = viewModel.processSelectedMemories()
            assertTrue(secondImport.isFailure)

            advanceUntilIdle()
            assertTrue(firstImport.await().isSuccess)
            assertEquals(1, fakeMediaManager.addedToCollection.size)
        }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): MemorySelectionViewModel =
        MemorySelectionViewModel(
            mediaManager = fakeMediaManager,
            aiClient = FakeGenerativeAIChatClient(),
            savedStateHandle = savedStateHandle,
        )

    private fun sampleImage(id: String): MediaObject.Image =
        MediaObject.Image(
            uri = "content://media/$id",
            name = "$id.jpg",
            size = 1024,
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
        )

    private fun sampleVideo(id: String): MediaObject.Video =
        MediaObject.Video(
            uri = "content://media/$id",
            name = "$id.mp4",
            size = 2048,
            timestamp = Instant.parse("2024-01-02T00:00:00Z"),
            duration = Duration.parse("30s"),
        )
}

private class FakeMediaManager : MediaManager {
    var queryMediaByDateFlow: () -> Flow<List<MediaObject>> = { flowOf(emptyList()) }
    var recentMediaFlow: () -> Flow<List<MediaObject>> = { flowOf(emptyList()) }
    var addToDefaultCollectionError: Throwable? = null
    var addToDefaultCollectionDelay: suspend () -> Unit = {}
    val addedToCollection = mutableListOf<String>()

    override suspend fun getMedia(uri: String): MediaObject = error("Not used in test")

    override suspend fun deleteOwnedMedia(uri: String): Boolean = false

    override suspend fun exists(mediaId: String): Boolean = false

    override suspend fun getRecentMedia(limit: Int): Flow<List<MediaObject>> = recentMediaFlow()

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> = queryMediaByDateFlow()

    override suspend fun addToDefaultCollection(uri: String) {
        addToDefaultCollectionDelay()
        addToDefaultCollectionError?.let { throw it }
        addedToCollection += uri
    }

    override suspend fun readMedia(uri: String): MediaPayload = error("Not used in test")

    override suspend fun saveMedia(payload: MediaPayload): String = error("Not used in test")

    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String = error("Not used in test")
}

private class FakeGenerativeAIChatClient : GenerativeAIChatClient {
    override val providerId: String = "fake"
    override val defaultModel: String? = "fake-model"

    override suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse> =
        AIResult.Success(GenerativeAIResponse(content = "ok"))
}
