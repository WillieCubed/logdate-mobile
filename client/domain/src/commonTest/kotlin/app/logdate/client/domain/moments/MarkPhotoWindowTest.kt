package app.logdate.client.domain.moments

import app.logdate.client.media.MediaObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MarkPhotoWindowTest {
    private val at = Instant.parse("2026-11-01T09:00:00Z")

    @Test
    fun `photo window includes both fifteen minute boundaries across DST`() {
        val photos =
            listOf(-16, -15, 0, 15, 16).map { minute ->
                MediaObject.Image("photo$minute", 1, "$minute", at + minute.minutes)
            }
        assertEquals(listOf("photo0", "photo-15", "photo15"), selectMarkPhotos(at, photos).map { it.uri })
    }

    @Test
    fun `suggestions exclude videos and duplicate source URIs`() {
        val photo = MediaObject.Image("photo", 1, "photo", at)
        val video = MediaObject.Video("video", "video", 1, at, 1.minutes)
        assertEquals(listOf(photo), selectMarkPhotos(at, listOf(photo, photo, video)))
    }
}
