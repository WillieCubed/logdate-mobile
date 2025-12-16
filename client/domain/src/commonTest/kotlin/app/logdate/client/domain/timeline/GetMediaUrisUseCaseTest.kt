package app.logdate.client.domain.timeline

import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetMediaUrisUseCaseTest {

    private lateinit var mockMediaManager: MockMediaManager
    private lateinit var useCase: GetMediaUrisUseCase

    @BeforeTest
    fun setUp() {
        mockMediaManager = MockMediaManager()
        useCase = GetMediaUrisUseCase(mediaManager = mockMediaManager)
    }

    @Test
    fun `invoke should return media URIs for given day`() = runTest {
        // Given
        val testDay = LocalDate(2024, 1, 15)
        val mediaObjects = listOf(
            createImageMediaObject("image1.jpg"),
            createVideoMediaObject("video1.mp4"),
            createImageMediaObject("image2.png")
        )
        mockMediaManager.mediaObjects = mediaObjects
        
        // When
        val result = useCase(testDay).first()
        
        // Then
        assertEquals(3, result.size)
        assertEquals("image1.jpg", result[0])
        assertEquals("video1.mp4", result[1])
        assertEquals("image2.png", result[2])
        assertEquals(1, mockMediaManager.queryMediaByDateCalls.size)
    }

    @Test
    fun `invoke should return empty list when no media exists for day`() = runTest {
        // Given
        val testDay = LocalDate(2024, 1, 15)
        mockMediaManager.mediaObjects = emptyList()
        
        // When
        val result = useCase(testDay).first()
        
        // Then
        assertTrue(result.isEmpty())
        assertEquals(1, mockMediaManager.queryMediaByDateCalls.size)
    }

    @Test
    fun `invoke should query media for correct time range`() = runTest {
        // Given
        val testDay = LocalDate(2024, 1, 15)
        mockMediaManager.mediaObjects = emptyList()
        
        // When
        useCase(testDay).first()
        
        // Then
        assertEquals(1, mockMediaManager.queryMediaByDateCalls.size)
        val queryCall = mockMediaManager.queryMediaByDateCalls.first()
        
        // Start should be beginning of the day
        val expectedStart = testDay.atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
        assertEquals(expectedStart, queryCall.first)
        
        // End should be beginning of next day
        val expectedEnd = testDay.plus(kotlinx.datetime.DatePeriod(days = 1))
            .atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
        assertEquals(expectedEnd, queryCall.second)
    }

    @Test
    fun `invoke should handle single media object`() = runTest {
        // Given
        val testDay = LocalDate(2024, 6, 10)
        val singleMedia = createImageMediaObject("single_photo.jpg")
        mockMediaManager.mediaObjects = listOf(singleMedia)
        
        // When
        val result = useCase(testDay).first()
        
        // Then
        assertEquals(1, result.size)
        assertEquals("single_photo.jpg", result.first())
    }

    @Test
    fun `invoke should handle different media types correctly`() = runTest {
        // Given
        val testDay = LocalDate(2024, 3, 20)
        val mixedMedia = listOf(
            createImageMediaObject("photo.jpg"),
            createVideoMediaObject("movie.mov"),
            createImageMediaObject("screenshot.png"),
            createVideoMediaObject("clip.avi")
        )
        mockMediaManager.mediaObjects = mixedMedia
        
        // When
        val result = useCase(testDay).first()
        
        // Then
        assertEquals(4, result.size)
        assertEquals("photo.jpg", result[0])
        assertEquals("movie.mov", result[1])
        assertEquals("screenshot.png", result[2])
        assertEquals("clip.avi", result[3])
    }

    @Test
    fun `invoke should handle large number of media objects`() = runTest {
        // Given
        val testDay = LocalDate(2024, 12, 25)
        val manyMedia = (1..100).map { i ->
            createImageMediaObject("image_$i.jpg")
        }
        mockMediaManager.mediaObjects = manyMedia
        
        // When
        val result = useCase(testDay).first()
        
        // Then
        assertEquals(100, result.size)
        result.forEachIndexed { index, uri ->
            assertEquals("image_${index + 1}.jpg", uri)
        }
    }

    @Test
    fun `invoke should work with different dates`() = runTest {
        // Given
        val date1 = LocalDate(2024, 1, 1)
        val date2 = LocalDate(2024, 12, 31)
        
        mockMediaManager.mediaObjects = listOf(createImageMediaObject("test.jpg"))
        
        // When
        val result1 = useCase(date1).first()
        val result2 = useCase(date2).first()
        
        // Then
        assertEquals(1, result1.size)
        assertEquals(1, result2.size)
        assertEquals(2, mockMediaManager.queryMediaByDateCalls.size)
        
        // Verify different date ranges were used
        val call1 = mockMediaManager.queryMediaByDateCalls[0]
        val call2 = mockMediaManager.queryMediaByDateCalls[1]
        assertTrue(call1.first != call2.first)
        assertTrue(call1.second != call2.second)
    }

    private fun createImageMediaObject(uri: String) = MediaObject.Image(
        uri = uri,
        size = 1024,
        name = uri,
        timestamp = Clock.System.now()
    )

    private fun createVideoMediaObject(uri: String) = MediaObject.Video(
        name = uri,
        uri = uri,
        size = 2048,
        timestamp = Clock.System.now(),
        duration = 30000 // 30 seconds
    )

    private class MockMediaManager : MediaManager {
        var mediaObjects = emptyList<MediaObject>()
        val queryMediaByDateCalls = mutableListOf<Pair<Instant, Instant>>()

        override suspend fun queryMediaByDate(start: Instant, end: Instant): Flow<List<MediaObject>> {
            queryMediaByDateCalls.add(Pair(start, end))
            return flowOf(mediaObjects)
        }

        override suspend fun getMedia(uri: String): MediaObject = throw NotImplementedError()
        override suspend fun exists(mediaId: String): Boolean = false
        override suspend fun getRecentMedia(): Flow<List<MediaObject>> = flowOf(emptyList())
        override suspend fun addToDefaultCollection(uri: String) = Unit
    }
}