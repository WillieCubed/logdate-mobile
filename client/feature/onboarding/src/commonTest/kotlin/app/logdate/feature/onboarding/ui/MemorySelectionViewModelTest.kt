package app.logdate.feature.onboarding.ui

import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.MediaPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
    fun refreshMemories_fallsBackToRecentMedia_whenDateRangeIsEmpty() =
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
    fun refreshMemories_recoversAfterLoadFailure() =
        runTest {
            fakeMediaManager.queryMediaByDateFlow = {
                flow {
                    throw SecurityException("Media access denied")
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

    private fun createViewModel(): MemorySelectionViewModel =
        MemorySelectionViewModel(
            mediaManager = fakeMediaManager,
            aiClient = FakeGenerativeAIChatClient(),
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

    override suspend fun getMedia(uri: String): MediaObject = error("Not used in test")

    override suspend fun exists(mediaId: String): Boolean = false

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> = recentMediaFlow()

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> = queryMediaByDateFlow()

    override suspend fun addToDefaultCollection(uri: String) = Unit

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
