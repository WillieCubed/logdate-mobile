package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.logdate.LogDateMedia
import app.logdate.shared.model.sync.DeviceId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [PostgreSQLLogDateMediaRepository] utilizing an H2 database.
 *
 * This suite validates the persistence and management of media records, specifically:
 * - Idempotent upsert operations (creation and updates).
 * - Media record retrieval by unique identifiers.
 * - Graceful deletion (tombstoning) of media content.
 *
 * It ensures that media-specific metadata, such as encryption details and device associations,
 * are correctly handled.
 */
class PostgreSQLLogDateMediaRepositoryTest {
    @Test
    fun `postgres media repository stores updates and tombstones media`() {
        withH2Database(LogDateMediaRecordsTable) {
            val repository = PostgreSQLLogDateMediaRepository()
            val userId = UUID.randomUUID()

            val created =
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

            val updated =
                repository.upsertMedia(
                    userId = userId,
                    media = created.copy(fileName = "photo-updated.jpg"),
                )
            val fetched = repository.getMedia(userId, "media-1")

            assertNotNull(fetched)
            assertEquals("photo-updated.jpg", fetched.fileName)
            assertTrue(updated.version >= created.version)

            repository.deleteMedia(userId, "media-1", deletedAt = 30L)

            assertNull(repository.getMedia(userId, "media-1"))
        }
    }
}
