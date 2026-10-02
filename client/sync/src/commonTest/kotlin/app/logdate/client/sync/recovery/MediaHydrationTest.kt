package app.logdate.client.sync.recovery

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.MediaDownloadResponse
import app.logdate.client.sync.test.InMemoryMediaSyncRefStore
import app.logdate.client.sync.test.fakeCloudApiClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MediaHydrationTest {
    @Test
    fun `server binary download URL resolves the media id rather than binary suffix`() =
        runTest {
            val noteId = Uuid.random()
            val mediaId = "remote-media-id"
            val remoteUrl = "https://sync.example/api/v1/media/$mediaId/binary"
            val api =
                fakeCloudApiClient {
                    downloadMediaResponse =
                        Result.success(
                            MediaDownloadResponse(noteId.toString(), "image.png", "image/png", 3, byteArrayOf(1, 2, 3), remoteUrl),
                        )
                }
            val transfer = SyncMediaTransfer(InMemoryMediaManager(), InMemoryMediaSyncRefStore(), DefaultCloudMediaDataSource(api))
            val note = JournalNote.Image(noteId, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1), remoteUrl)

            transfer.downloadIfNeeded("token", note)

            assertEquals(listOf("remote-media-id"), api.downloadMediaCalls.map { it.second })
        }

    @Test
    fun `failed media fetch cannot count a remote URL as a locally recovered file`() =
        runTest {
            val api = fakeCloudApiClient { downloadMediaResponse = Result.failure(IllegalStateException("private-network-detail")) }
            val transfer = SyncMediaTransfer(InMemoryMediaManager(), InMemoryMediaSyncRefStore(), DefaultCloudMediaDataSource(api))
            val note =
                JournalNote.Image(
                    Uuid.random(),
                    Instant.fromEpochMilliseconds(1),
                    Instant.fromEpochMilliseconds(1),
                    "https://private-host/media/image",
                )
            assertTrue(runCatching { transfer.downloadIfNeeded("token", note) }.isFailure)
        }
}
