package app.logdate.client.domain.export.archive

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MediaFileNamerTest {
    private val namer = MediaFileNamer(ArchivePathAllocator())
    private val denver = TimeZone.of("America/Denver")

    // 2026-09-18T03:30:05Z is 2026-09-17 21:30:05 in Denver (MDT, UTC-6).
    private val capture = Instant.parse("2026-09-18T03:30:05Z")

    @Test
    fun `a photo is named by its local capture time inside a folder for its year`() {
        val path = namer.nameFor(MediaKind.PHOTO, capture, denver, "jpg")

        assertEquals("media/photos/2026/2026-09-17_21-30-05.jpg", path.value)
    }

    @Test
    fun `each kind of media gets its own folder`() {
        assertEquals("media/videos/2026/2026-09-17_21-30-05.mp4", namer.nameFor(MediaKind.VIDEO, capture, denver, "mp4").value)
        assertEquals("media/audio/2026/2026-09-17_21-30-05.m4a", namer.nameFor(MediaKind.AUDIO, capture, denver, "m4a").value)
    }

    @Test
    fun `the zone decides the local time and the year folder`() {
        val newYearsEve = Instant.parse("2026-12-31T23:30:00Z")

        assertEquals("media/photos/2026/2026-12-31_23-30-00.jpg", namer.nameFor(MediaKind.PHOTO, newYearsEve, TimeZone.UTC, "jpg").value)
        assertEquals(
            "media/photos/2027/2027-01-01_05-00-00.jpg",
            namer.nameFor(MediaKind.PHOTO, newYearsEve, TimeZone.of("Asia/Kolkata"), "jpg").value,
        )
    }

    @Test
    fun `photos taken in the same second get numbered names`() {
        val first = namer.nameFor(MediaKind.PHOTO, capture, denver, "jpg")
        val second = namer.nameFor(MediaKind.PHOTO, capture, denver, "jpg")

        assertEquals("media/photos/2026/2026-09-17_21-30-05.jpg", first.value)
        assertEquals("media/photos/2026/2026-09-17_21-30-05_2.jpg", second.value)
    }

    @Test
    fun `two instants that show the same local time when clocks fall back get different names`() {
        val newYork = TimeZone.of("America/New_York")
        val beforeFallBack = Instant.parse("2026-11-01T05:30:00Z")
        val afterFallBack = Instant.parse("2026-11-01T06:30:00Z")

        val first = namer.nameFor(MediaKind.PHOTO, beforeFallBack, newYork, "jpg")
        val second = namer.nameFor(MediaKind.PHOTO, afterFallBack, newYork, "jpg")

        assertEquals("media/photos/2026/2026-11-01_01-30-00.jpg", first.value)
        assertEquals("media/photos/2026/2026-11-01_01-30-00_2.jpg", second.value)
    }
}
