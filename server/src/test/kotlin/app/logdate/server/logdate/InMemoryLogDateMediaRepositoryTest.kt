package app.logdate.server.logdate

import app.logdate.shared.model.sync.DeviceId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [InMemoryLogDateMediaRepository], verifying the storage, retrieval,
 * and tombstoning of media assets within a user-scoped context.
 */
class InMemoryLogDateMediaRepositoryTest {
    @Test
    fun `media repository stores fetches and tombstones user-scoped media`() {
        val repository = InMemoryLogDateMediaRepository()
        val userId = UUID.randomUUID()

        val stored =
            repository.upsertMedia(
                userId = userId,
                media =
                    LogDateMedia(
                        mediaId = "media-1",
                        contentId = "entry-1",
                        userId = userId,
                        fileName = "photo.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 3L,
                        data = byteArrayOf(1, 2, 3),
                        storagePath = null,
                        createdAt = 10L,
                        version = 0L,
                        deviceId = DeviceId("device-1"),
                        encryptionVersion = 1,
                        encryptionKeyId = "default",
                        encryptionMode = "SERVER",
                    ),
            )

        val fetched = repository.getMedia(userId, "media-1")
        val otherUserLookup = repository.getMedia(UUID.randomUUID(), "media-1")

        assertNotNull(fetched)
        assertEquals("entry-1", fetched.contentId)
        assertTrue(stored.version > 0L)
        assertNull(otherUserLookup)

        repository.deleteMedia(userId, "media-1", deletedAt = 20L)

        assertNull(repository.getMedia(userId, "media-1"))
    }

    @Test
    fun `media value equality includes binary data and encryption metadata`() {
        val userId = UUID.randomUUID()
        val media =
            LogDateMedia(
                mediaId = "media-1",
                contentId = "entry-1",
                userId = userId,
                fileName = "photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 3L,
                data = byteArrayOf(1, 2, 3),
                storagePath = "media/media-1",
                createdAt = 10L,
                version = 1L,
                deviceId = DeviceId("device-1"),
                encryptionVersion = 1,
                encryptionKeyId = "default",
                encryptionMode = "SERVER",
            )

        assertEquals(media, media.copy(data = byteArrayOf(1, 2, 3)))
        assertEquals(media.hashCode(), media.copy(data = byteArrayOf(1, 2, 3)).hashCode())
        assertNotEquals(media, media.copy(data = byteArrayOf(9, 9, 9)))
        assertNotEquals(media, media.copy(encryptionMode = "CLIENT"))
    }
}
