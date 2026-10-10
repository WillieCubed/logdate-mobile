package app.logdate.client.image

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Data Saver only shrinks images fetched over the network; every spelling of a local file is left alone. */
class DataSaverLocalSourceTest {
    @Test
    fun `every spelling of a local file is a local source`() {
        listOf(
            "logdate-media://library/IMG%200001.jpg",
            "file:///data/user/0/studio.hypertext.logdate/files/media/a.jpg",
            "file:/data/user/0/studio.hypertext.logdate/files/media/a.jpg",
            "/data/user/0/studio.hypertext.logdate/files/media/a.jpg",
            "content://media/external/images/media/1",
            "android.resource://studio.hypertext.logdate/drawable/icon",
        ).forEach { source -> assertTrue(source.isLocalMediaSource(), source) }
    }

    @Test
    fun `network and unknown sources are not local`() {
        listOf("https://cloud.logdate.app/media/abc", "http://example.test/a.jpg", "ph://ABC/L0/001", "media/a.jpg", "").forEach { source ->
            assertFalse(source.isLocalMediaSource(), source)
        }
        assertFalse(null.isLocalMediaSource())
    }
}
