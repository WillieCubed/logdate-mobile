package app.logdate.feature.editor.ui.image

import app.logdate.client.media.MediaObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ImagePickerStateTest {
    @Test
    fun testRecentImagePreviewsOnlyReturnsImagesSortedNewestFirst() {
        val oldest = Instant.parse("2024-01-01T00:00:00Z")
        val middle = Instant.parse("2024-02-01T00:00:00Z")
        val newest = Instant.parse("2024-03-01T00:00:00Z")

        val previews =
            recentImagePreviews(
                listOf(
                    MediaObject.Video(
                        name = "clip.mov",
                        uri = "file:///clip.mov",
                        size = 120,
                        timestamp = newest,
                        duration = kotlin.time.Duration.ZERO,
                    ),
                    MediaObject.Image(
                        uri = "file:///old.jpg",
                        size = 12,
                        name = "old.jpg",
                        timestamp = oldest,
                    ),
                    MediaObject.Image(
                        uri = "file:///new.jpg",
                        size = 18,
                        name = "new.jpg",
                        timestamp = newest,
                    ),
                    MediaObject.Image(
                        uri = "file:///middle.jpg",
                        size = 15,
                        name = "middle.jpg",
                        timestamp = middle,
                    ),
                ),
            )

        assertEquals(
            listOf("file:///new.jpg", "file:///middle.jpg", "file:///old.jpg"),
            previews.map { it.uri },
        )
    }

    @Test
    fun testRecentImagePreviewsAppliesLimit() {
        val previews =
            recentImagePreviews(
                media =
                    List(5) { index ->
                        MediaObject.Image(
                            uri = "file:///$index.jpg",
                            size = index,
                            name = "$index.jpg",
                            timestamp = Instant.fromEpochMilliseconds(index.toLong()),
                        )
                    },
                limit = 3,
            )

        assertEquals(
            listOf("file:///4.jpg", "file:///3.jpg", "file:///2.jpg"),
            previews.map { it.uri },
        )
    }
}
