package app.logdate.client.domain.moments

import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.PhotoLibraryAccess
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkPhotoSuggestionsTest {
    private val at = Instant.parse("2026-10-12T12:00:00Z")
    private val mark = MarkedMoment(Uuid.random(), at, "UTC", null, null, MarkState.OPEN, "", emptySet(), "watch", at, 0)
    private val media = mockk<MediaManager>()

    @Test
    fun `denied access never queries private photos`() =
        runTest {
            every { media.photoLibraryAccess() } returns PhotoLibraryAccess.DENIED
            assertEquals(
                listOf(MarkPhotoSuggestions.Loading, MarkPhotoSuggestions.NeedsAccess),
                ObserveMarkPhotoSuggestionsUseCase(media)(mark).toList(),
            )
            coVerify(exactly = 0) { media.observeMediaByDate(any(), any()) }
        }

    @Test
    fun `limited access remains explicit when no selected pictures match`() =
        runTest {
            every { media.photoLibraryAccess() } returns PhotoLibraryAccess.LIMITED
            coEvery { media.observeMediaByDate(any(), any()) } returns flowOf(emptyList())
            assertEquals(
                listOf(MarkPhotoSuggestions.Loading, MarkPhotoSuggestions.Limited(emptyList())),
                ObserveMarkPhotoSuggestionsUseCase(media)(mark).toList(),
            )
        }

    @Test
    fun `library changes replace inline suggestions`() =
        runTest {
            val photo = MediaObject.Image("photo", 1, "Photo", at)
            every { media.photoLibraryAccess() } returns PhotoLibraryAccess.FULL
            coEvery { media.observeMediaByDate(any(), any()) } returns flowOf(emptyList(), listOf(photo))
            assertEquals(
                listOf(MarkPhotoSuggestions.Loading, MarkPhotoSuggestions.None, MarkPhotoSuggestions.Photos(listOf(photo))),
                ObserveMarkPhotoSuggestionsUseCase(media)(mark).toList(),
            )
        }
}
