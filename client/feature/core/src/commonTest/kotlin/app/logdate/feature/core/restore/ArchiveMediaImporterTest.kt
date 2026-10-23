package app.logdate.feature.core.restore

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaPayload
import app.logdate.client.media.storage.StoredMediaReferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveMediaImporterTest {
    @Test
    fun `failed incoming import keeps accepted private original available`() =
        runTest {
            val media = InMemoryMediaManager()
            val original = media.saveMedia(MediaPayload("original.jpg", "image/jpeg", 3, byteArrayOf(1, 2, 3)))
            val references = StoredMediaReferences { if (it == original) "logdate-media://library/original.jpg" else it }
            val importer = createArchiveMediaImporter(media, references, { true }, { null })
            assertNull(importer.importMedia("media/corrupt.jpg"))
            assertTrue(importer.isAvailable(original))
        }

    @Test
    fun `file metadata without readable original cannot satisfy recovery`() =
        runTest {
            val media =
                object : InMemoryMediaManager() {
                    override suspend fun exists(mediaId: String) = true

                    override suspend fun openMedia(uri: String): MediaFileSource = error("Synthetic interrupted original")
                }
            val importer = createArchiveMediaImporter(media, StoredMediaReferences.Unchanged, { false }, { null })
            assertFalse(importer.isAvailable("logdate-media://library/missing.jpg"))
            assertEquals(false, importer.containsMedia("media/missing.jpg"))
        }

    @Test
    fun `metadata-only importer retains local availability without copying incoming media`() =
        runTest {
            val media = InMemoryMediaManager()
            val original = media.saveMedia(MediaPayload("original.jpg", "image/jpeg", 1, byteArrayOf(1)))
            val references = StoredMediaReferences { if (it == original) "logdate-media://library/original.jpg" else it }
            var copied = false
            val importer =
                createArchiveMediaImporter(media, references, { true }, {
                    copied = true
                    null
                })
            assertTrue(importer.isAvailable(original))
            assertFalse(copied)
        }
}
