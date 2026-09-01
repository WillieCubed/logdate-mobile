package app.logdate.feature.onboarding.ui

import app.logdate.client.media.MediaObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant

class MemorySelectionAspectRatioTest {
    @Test
    fun `aspect ratio is stable for the same media uri`() {
        val image =
            MediaObject.Image(
                uri = "content://media/photo-1",
                name = "photo.jpg",
                size = 1024,
                timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            )
        val video =
            MediaObject.Video(
                uri = "content://media/video-1",
                name = "video.mp4",
                size = 2048,
                timestamp = Instant.parse("2024-01-01T00:00:00Z"),
                duration = Duration.parse("30s"),
            )

        assertEquals(image.getNativeAspectRatio(), image.getNativeAspectRatio())
        assertEquals(video.getNativeAspectRatio(), video.getNativeAspectRatio())
    }
}
