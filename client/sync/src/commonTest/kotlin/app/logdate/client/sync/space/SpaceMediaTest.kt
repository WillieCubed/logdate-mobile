package app.logdate.client.sync.space

import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaIntegrityException
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.cloud.ReferencedMediaDataSource
import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.test.mediaFileSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class SpaceMediaTest {
    private val mediaKey = ByteArray(32) { (it + 1).toByte() }
    private val location = SpaceLocation("https://cloud.test", TEST_SPACE)

    private fun space(home: FakeDataHome) =
        SpaceMediaDataSource(SpaceDataHomes { _, token -> home.signedIn(token) }, AesGcmMediaPayloadCrypto(mediaKey)) { location }

    @Test
    fun `media is chunk-encrypted and streamed to the space and named by its content identifier`() =
        runTest {
            val home = FakeDataHome()
            val photo = ByteArray(150_000) { (it % 251).toByte() }
            val contentId = Uuid.random()

            val upload =
                space(
                    home,
                ).uploadMedia("test-access-token", contentId, mediaFileSource(photo, "photo.jpg", "image/jpeg")).getOrThrow()

            val stored = assertNotNull(home.blobs[upload.mediaId])
            assertEquals("LDCE2", stored.copyOf(5).decodeToString())
            assertFalse(stored.contentEquals(photo))
            val reference = assertNotNull(SpaceMediaReference.parse(upload.downloadUrl))
            assertEquals(SpaceMediaReference(upload.mediaId, contentId, "image/jpeg", stored.size.toLong(), photo.size.toLong()), reference)
        }

    @Test
    fun `media read back through its reference decrypts to the original file`() =
        runTest {
            val home = FakeDataHome()
            val photo = ByteArray(70_000) { (it % 13).toByte() }
            val contentId = Uuid.random()
            val source = space(home)
            val upload = source.uploadMedia("test-access-token", contentId, mediaFileSource(photo, "photo.jpg", "image/jpeg")).getOrThrow()

            val file = source.downloadReferencedMedia("test-access-token", upload.downloadUrl, upload.mediaId).getOrThrow()

            assertEquals(contentId, file.contentId)
            assertEquals("image/jpeg", file.mimeType)
            assertContentEquals(photo, file.data)
        }

    @Test
    fun `a blob that isn't what its content identifier names is refused`() =
        runTest {
            val home = FakeDataHome()
            val source = space(home)
            val upload = source.uploadMedia("test-access-token", Uuid.random(), mediaFileSource(ByteArray(100) { 1 })).getOrThrow()
            home.blobs[upload.mediaId] =
                home.blobs
                    .getValue(upload.mediaId)
                    .copyOf()
                    .also { it[it.size - 1] = (it.last() + 1).toByte() }

            val failure = source.downloadReferencedMedia("test-access-token", upload.downloadUrl, upload.mediaId).exceptionOrNull()

            assertIs<MediaIntegrityException>(failure)
        }

    @Test
    fun `uploads go to the space only while notes sync there and each file is read from where it lives`() =
        runTest {
            val legacy = RecordingMediaSource()
            val spaceSource = RecordingMediaSource()
            var notesInSpace = false
            val routing = SpaceRoutingMediaDataSource(legacy, spaceSource) { notesInSpace }
            val media = mediaFileSource(ByteArray(10))

            routing.uploadMedia("token", Uuid.random(), media)
            notesInSpace = true
            routing.uploadMedia("token", Uuid.random(), media)
            val spaceReference = SpaceMediaReference("bafkreiabc", Uuid.random(), "image/png", 10).format()
            routing.downloadReferencedMedia("token", spaceReference, "bafkreiabc")
            routing.downloadReferencedMedia("token", "https://cloud.test/api/v1/media/m1", "m1")

            assertEquals(listOf("upload", "download:m1"), legacy.calls)
            assertEquals(listOf("upload", "download:bafkreiabc"), spaceSource.calls)
        }

    private class RecordingMediaSource :
        CloudMediaDataSource,
        ReferencedMediaDataSource {
        val calls = mutableListOf<String>()

        override suspend fun uploadMedia(
            accessToken: String,
            contentId: Uuid,
            media: MediaFileSource,
        ): Result<MediaUploadResult> {
            calls += "upload"
            return Result.failure(IllegalStateException("not needed"))
        }

        override suspend fun downloadMedia(
            accessToken: String,
            mediaId: String,
        ): Result<MediaFile> {
            calls += "download:$mediaId"
            return Result.failure(IllegalStateException("not needed"))
        }

        override suspend fun downloadReferencedMedia(
            accessToken: String,
            reference: String,
            mediaId: String,
        ): Result<MediaFile> = downloadMedia(accessToken, mediaId)
    }
}
