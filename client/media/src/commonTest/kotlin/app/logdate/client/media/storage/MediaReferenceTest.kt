package app.logdate.client.media.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaReferenceTest {
    @Test
    fun `LogDate media references are owned media`() {
        assertEquals(
            MediaReference.Owned(LocalMediaRef(MediaCollection.Recordings, "recording_42.m4a")),
            MediaReference.parse("logdate-media://recordings/recording_42.m4a"),
        )
    }

    @Test
    fun `file URIs and absolute paths are local files`() {
        assertEquals(
            MediaReference.LocalFile("/data/user/0/app/files/media/a.jpg"),
            MediaReference.parse("file:/data/user/0/app/files/media/a.jpg"),
        )
        assertEquals(
            MediaReference.LocalFile("/data/user/0/app/files/media/a.jpg"),
            MediaReference.parse("file:///data/user/0/app/files/media/a.jpg"),
        )
        assertEquals(
            MediaReference.LocalFile("/var/mobile/a.jpg"),
            MediaReference.parse("file://localhost/var/mobile/a.jpg"),
        )
        assertEquals(
            MediaReference.LocalFile("/var/mobile/a b.jpg"),
            MediaReference.parse("/var/mobile/a b.jpg"),
        )
    }

    @Test
    fun `Windows file URIs and paths are local files written with forward slashes`() {
        assertEquals(
            MediaReference.LocalFile("C:/Users/me/.logdate/media/a.jpg"),
            MediaReference.parse("file:///C:/Users/me/.logdate/media/a.jpg"),
        )
        assertEquals(
            MediaReference.LocalFile("C:/Users/me/.logdate/media/a.jpg"),
            MediaReference.parse("C:/Users/me/.logdate/media/a.jpg"),
        )
        assertEquals(
            MediaReference.LocalFile("C:/Users/me/.logdate/media/a.jpg"),
            MediaReference.parse("C:\\Users\\me\\.logdate\\media\\a.jpg"),
        )
    }

    @Test
    fun `percent-encoded file URIs keep their written spelling as an alternate`() {
        assertEquals(
            MediaReference.LocalFile("/var/mobile/IMG 0001.jpg", alternatePath = "/var/mobile/IMG%200001.jpg"),
            MediaReference.parse("file:///var/mobile/IMG%200001.jpg"),
        )
    }

    @Test
    fun `unencoded file URIs are read as written`() {
        assertEquals(
            MediaReference.LocalFile("/var/mobile/IMG 0001.jpg"),
            MediaReference.parse("file:///var/mobile/IMG 0001.jpg"),
        )
        assertEquals(
            MediaReference.LocalFile("/var/mobile/100%.jpg"),
            MediaReference.parse("file:///var/mobile/100%.jpg"),
        )
    }

    @Test
    fun `everything else is external`() {
        listOf(
            "ph://ABC/L0/001",
            "content://media/external/images/media/1",
            "https://cloud.logdate.app/media/abc",
            "media/abc.jpg",
            "file://otherhost/a.jpg",
            "logdate-media://cache/a.jpg",
            "",
        ).forEach { value -> assertEquals(MediaReference.External(value), MediaReference.parse(value), value) }
    }
}
