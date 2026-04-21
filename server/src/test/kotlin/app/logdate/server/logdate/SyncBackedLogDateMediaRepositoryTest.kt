package app.logdate.server.logdate

import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.shared.model.sync.DeviceId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for the media repository implementation backed by the
 * synchronization layer.
 *
 * This suite ensures that media metadata—including content linkage, file properties,
 * and encryption state—is correctly managed within the sync-backed store. It verifies
 * that media records can be reliably persisted, retrieved, and deleted while
 * maintaining integrity with the underlying storage system.
 */
class SyncBackedLogDateMediaRepositoryTest {
    @Test
    fun `media metadata round trips through sync-backed repository`() {
        val repository = SyncBackedLogDateMediaRepository(InMemorySyncRepository())
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
                        sizeBytes = 3,
                        data = byteArrayOf(1, 2, 3),
                        storagePath = "users/$userId/media/media-1/photo.jpg",
                        createdAt = 10L,
                        version = 0L,
                        deviceId = DeviceId("device-1"),
                        encryptionVersion = 1,
                        encryptionKeyId = "default",
                        encryptionMode = "SERVER",
                    ),
            )

        val fetched = repository.getMedia(userId, "media-1")
        assertNotNull(fetched)
        assertEquals("entry-1", fetched.contentId)
        assertEquals("photo.jpg", fetched.fileName)
        assertEquals("image/jpeg", fetched.mimeType)
        assertEquals(stored.version, fetched.version)
        assertTrue(fetched.data.contentEquals(byteArrayOf(1, 2, 3)))

        repository.deleteMedia(userId, "media-1", deletedAt = 20L)

        assertNull(repository.getMedia(userId, "media-1"))
    }
}
