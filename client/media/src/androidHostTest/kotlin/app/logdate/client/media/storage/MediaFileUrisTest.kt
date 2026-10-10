package app.logdate.client.media.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaFileUrisTest {
    private val filesDir = Files.createTempDirectory("logdate-uris").toFile().canonicalFile
    private val mediaFiles = MediaFileResolver(AndroidMediaDirectories(filesDir))

    @AfterTest
    fun cleanUp() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `a reference becomes an encoded file URI`() {
        assertEquals("file://${File(filesDir, "media/a b.jpg").toURI().rawPath}", mediaFiles.fileUri("logdate-media://library/a%20b.jpg"))
        assertEquals("file://${filesDir.toURI().rawPath}media/a%20b.jpg", mediaFiles.playableUri("logdate-media://library/a%20b.jpg"))
    }

    @Test
    fun `strings that are not local files are left as they are`() {
        assertNull(mediaFiles.fileUri("https://cloud.logdate.app/media/abc"))
        assertEquals("https://cloud.logdate.app/media/abc", mediaFiles.playableUri("https://cloud.logdate.app/media/abc"))
        assertEquals("content://media/external/images/media/1", mediaFiles.playableUri("content://media/external/images/media/1"))
    }
}
