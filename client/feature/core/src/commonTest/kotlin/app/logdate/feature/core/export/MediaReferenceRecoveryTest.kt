package app.logdate.feature.core.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaReferenceRecoveryTest {
    @Test
    fun `a doubled extension left by an old import is collapsed`() {
        assertEquals("photo.jpg", MediaReferenceRecovery.withoutDoubledExtension("photo.jpg.jpg"))
        assertEquals("clip.mp4", MediaReferenceRecovery.withoutDoubledExtension("clip.mp4.mp4"))
    }

    @Test
    fun `a normal name is left alone`() {
        assertEquals("photo.jpg", MediaReferenceRecovery.withoutDoubledExtension("photo.jpg"))
        assertEquals("archive.tar.gz", MediaReferenceRecovery.withoutDoubledExtension("archive.tar.gz"))
    }

    @Test
    fun `a recording name is found inside a longer path segment`() {
        assertEquals("recording_1a2b-3c.m4a", MediaReferenceRecovery.recordingFileName("cache_recording_1a2b-3c.m4a"))
    }

    @Test
    fun `a recording name without an extension is given m4a`() {
        assertEquals("recording_abc.m4a", MediaReferenceRecovery.recordingFileName("recording_abc"))
    }

    @Test
    fun `a doubled extension on a recording is collapsed`() {
        assertEquals("recording_abc.m4a", MediaReferenceRecovery.recordingFileName("recording_abc.m4a.m4a"))
    }

    @Test
    fun `a name that is not a recording has no recording file name`() {
        assertNull(MediaReferenceRecovery.recordingFileName("holiday.jpg"))
    }

    @Test
    fun `an app-private media file is matched by the whole name or the base name only`() {
        val keys = MediaReferenceRecovery.matchKeys("IMG_20260101_abc123.jpg")

        assertTrue(MediaReferenceRecovery.matchesAny("IMG_20260101_abc123.jpg", keys))
        assertTrue(MediaReferenceRecovery.matchesAny("IMG_20260101_abc123.jpeg", keys), "the base name matches with another extension")
        assertFalse(MediaReferenceRecovery.matchesAny("unrelated.jpg", keys))
    }

    @Test
    fun `a name that only shares a trailing token is another file`() {
        val keys = MediaReferenceRecovery.matchKeys("IMG_20240101_101010.jpg")

        assertFalse(MediaReferenceRecovery.matchesAny("IMG_20240202_101010.jpg", keys), "the same time of day on another date")
        assertFalse(MediaReferenceRecovery.matchesAny("copy_101010.jpg", keys))
    }

    @Test
    fun `a legacy media store id is the run of digits at the end of the name`() {
        assertEquals("1000025292", MediaReferenceRecovery.legacyMediaStoreId("1000025292"))
        assertEquals("1000025292", MediaReferenceRecovery.legacyMediaStoreId("image_1000025292"))
        assertNull(MediaReferenceRecovery.legacyMediaStoreId("photo.jpg"))
        assertNull(MediaReferenceRecovery.legacyMediaStoreId("12345"), "shorter than a media store id")
    }

    @Test
    fun `the media store collection follows the extension`() {
        assertEquals(MediaReferenceRecovery.Collection.IMAGES, MediaReferenceRecovery.collectionFor("jpg"))
        assertEquals(MediaReferenceRecovery.Collection.VIDEO, MediaReferenceRecovery.collectionFor("MP4"))
        assertEquals(MediaReferenceRecovery.Collection.AUDIO, MediaReferenceRecovery.collectionFor("m4a"))
        assertNull(MediaReferenceRecovery.collectionFor("txt"))
        assertNull(MediaReferenceRecovery.collectionFor(null))
    }
}
