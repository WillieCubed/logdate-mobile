package app.logdate.feature.rewind.ui.detail

import app.logdate.shared.model.RewindContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RewindVideoFramePanelTest {
    @Test
    fun `video panel keeps its source and identifies the preview as video`() {
        val sourceId = Uuid.random()
        val video =
            RewindContent.Video(
                timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000L),
                sourceId = sourceId,
                uri = "content://media/video/42",
                caption = "At the beach",
                duration = 12.seconds,
            )

        val panel = video.toVideoFramePanel("Tuesday")

        assertEquals(sourceId, panel.sourceId)
        assertEquals(video.uri, panel.imageUri)
        assertTrue(panel.isVideoFrame)
    }
}
