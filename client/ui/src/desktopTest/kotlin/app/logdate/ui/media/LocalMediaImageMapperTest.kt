package app.logdate.ui.media

import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import coil3.PlatformContext
import coil3.request.Options
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalMediaImageMapperTest {
    private val directories =
        object : MediaDirectories {
            override fun directory(collection: MediaCollection): String = "/install/new/${collection.id}"

            override fun pathInCurrentInstall(path: String): String? = path.replace("/install/old/", "/install/new/").takeIf { it != path }
        }
    private val options = Options(PlatformContext.INSTANCE)

    @Test
    fun `media references load from their file`() {
        val mapper = LocalMediaImageMapper(MediaFileResolver(directories) { false })

        assertEquals("/install/new/library/IMG 0001.jpg".toPath(), mapper.map("logdate-media://library/IMG%200001.jpg", options))
    }

    @Test
    fun `file URIs in every spelling load from their file`() {
        val mapper = LocalMediaImageMapper(MediaFileResolver(directories) { false })

        assertEquals("/install/new/library/a b.jpg".toPath(), mapper.map("file:/install/new/library/a%20b.jpg", options))
        assertEquals("/install/new/library/a b.jpg".toPath(), mapper.map("file:///install/new/library/a b.jpg", options))
        assertEquals("/install/new/library/a.jpg".toPath(), mapper.map("/install/new/library/a.jpg", options))
    }

    @Test
    fun `files from an earlier install load from this install`() {
        val mapper = LocalMediaImageMapper(MediaFileResolver(directories) { it == "/install/new/library/a.jpg" })

        assertEquals("/install/new/library/a.jpg".toPath(), mapper.map("file:///install/old/library/a.jpg", options))
    }

    @Test
    fun `other URIs are left to the image loader`() {
        val mapper = LocalMediaImageMapper(MediaFileResolver(directories) { true })

        assertNull(mapper.map("https://cloud.logdate.app/media/abc", options))
        assertNull(mapper.map("content://media/external/images/media/1", options))
        assertNull(mapper.map("ph://ABC/L0/001", options))
    }
}
