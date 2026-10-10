package app.logdate.client.media.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalMediaRefTest {
    @Test
    fun `reference reads as the collection followed by the path`() {
        assertEquals(
            "logdate-media://library/objects/sha256/9f/9f86d081.jpg",
            LocalMediaRef(MediaCollection.Library, "objects/sha256/9f/9f86d081.jpg").toString(),
        )
        assertEquals(
            "logdate-media://recordings/recording_42.m4a",
            LocalMediaRef(MediaCollection.Recordings, "recording_42.m4a").toString(),
        )
    }

    @Test
    fun `characters a URI cannot carry are percent-encoded`() {
        assertEquals(
            "logdate-media://library/1f2e-IMG%200001%20(1).jpg",
            LocalMediaRef(MediaCollection.Library, "1f2e-IMG 0001 (1).jpg").toString(),
        )
        assertEquals(
            "logdate-media://library/caf%C3%A9%20%231%3F%25.jpg",
            LocalMediaRef(MediaCollection.Library, "café #1?%.jpg").toString(),
        )
    }

    @Test
    fun `parsing reverses formatting`() {
        val refs =
            listOf(
                LocalMediaRef(MediaCollection.Library, "objects/sha256/9f/9f86d081.jpg"),
                LocalMediaRef(MediaCollection.Library, "1f2e-IMG 0001 (1).jpg"),
                LocalMediaRef(MediaCollection.Library, "café #1?%.jpg"),
                LocalMediaRef(MediaCollection.Recordings, "recording_42.m4a"),
            )

        refs.forEach { ref -> assertEquals(ref, LocalMediaRef.parse(ref.toString())) }
    }

    @Test
    fun `scheme and collection are matched without regard to case`() {
        assertEquals(
            LocalMediaRef(MediaCollection.Library, "Photo.JPG"),
            LocalMediaRef.parse("LogDate-Media://Library/Photo.JPG"),
        )
    }

    @Test
    fun `unknown collections are refused`() {
        assertNull(LocalMediaRef.parse("logdate-media://cache/thumb.jpg"))
        assertNull(LocalMediaRef.parse("logdate-media:///library/a.jpg"))
    }

    @Test
    fun `paths that are empty or climb out of the collection are refused`() {
        listOf(
            "logdate-media://library",
            "logdate-media://library/",
            "logdate-media://library//a.jpg",
            "logdate-media://library/a.jpg/",
            "logdate-media://library/../a.jpg",
            "logdate-media://library/./a.jpg",
            "logdate-media://library/%2E%2E/a.jpg",
            "logdate-media://library/a%2Fb.jpg",
            "logdate-media://library/a%5Cb.jpg",
            "logdate-media://library/a%00.jpg",
        ).forEach { value -> assertNull(LocalMediaRef.parse(value), value) }
    }

    @Test
    fun `references never carry a query or fragment`() {
        assertNull(LocalMediaRef.parse("logdate-media://library/a.jpg?v=2"))
        assertNull(LocalMediaRef.parse("logdate-media://library/a.jpg#t=3"))
    }

    @Test
    fun `other schemes are not local media references`() {
        assertNull(LocalMediaRef.parse("file:///var/mobile/a.jpg"))
        assertNull(LocalMediaRef.parse("ph://ABC/L0/001"))
        assertNull(LocalMediaRef.parse("https://cloud.logdate.app/media/abc"))
        assertNull(LocalMediaRef.parse("logdate://journal/abc"))
    }

    @Test
    fun `a reference cannot be built from an unsafe path`() {
        listOf("", "/a.jpg", "a//b.jpg", "../a.jpg", "a/./b.jpg", "a\\b.jpg").forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) { LocalMediaRef(MediaCollection.Library, path) }
        }
    }
}
