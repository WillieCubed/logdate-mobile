package app.logdate.client.sync

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.MediaDownloadResponse
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.InMemoryMediaSyncRefStore
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class TranscriptMediaReplacementTest {
    @Test
    fun `same media omitted transcript survives hydration to a different local URI`() =
        download(remoteRef = "https://server.example/media/original", duration = 4000, fileName = "hydrated.m4a", preserve = true)

    @Test
    fun `replaced media cannot inherit transcript when hydration reuses the local URI`() =
        download(remoteRef = "https://server.example/media/replacement", duration = 4000, fileName = "original.m4a", preserve = false)

    @Test
    fun `changed duration with unchanged media URI does not preserve omitted transcript`() =
        download(remoteRef = "https://server.example/media/original", duration = 5000, fileName = "original.m4a", preserve = false)

    private fun download(
        remoteRef: String,
        duration: Long,
        fileName: String,
        preserve: Boolean,
    ) = runTest {
        val media = InMemoryMediaManager()
        val localUri = media.saveMedia(MediaPayload("original.m4a", "audio/mp4", 1, byteArrayOf(1)))
        val document = TranscriptDocument.fromPlainText("Original speech").copy(revision = 3)
        val original =
            JournalNote.Audio(
                mediaRef = localUri,
                durationMs = 4000,
                creationTimestamp = Instant.fromEpochMilliseconds(1),
                lastUpdated = Instant.fromEpochMilliseconds(2),
                syncVersion = 40,
                transcript = document,
            )
        val notes = FakeJournalNotesRepository().apply { create(original) }
        val refs =
            InMemoryMediaSyncRefStore().apply {
                upsert(MediaSyncRef(original.uid.toString(), localUri, "https://server.example/media/original", "original", 2))
            }
        val api =
            FakeCloudApiClient().apply {
                downloadMediaResponse =
                    Result.success(MediaDownloadResponse(original.uid.toString(), fileName, "audio/mp4", 1, byteArrayOf(2), remoteRef))
                getContentChangesResponse =
                    Result.success(
                        ContentChangesResponse(
                            listOf(
                                ContentChange(
                                    original.uid.toString(),
                                    "AUDIO",
                                    mediaUri = remoteRef,
                                    durationMs = duration,
                                    createdAt = 1,
                                    lastUpdated = 99,
                                    serverVersion = 42,
                                ),
                            ),
                            emptyList(),
                            42,
                        ),
                    )
            }
        val manager =
            testDefaultSyncManager(
                cloudContentDataSource = DefaultCloudContentDataSource(api),
                cloudMediaDataSource = DefaultCloudMediaDataSource(api),
                mediaManager = media,
                mediaSyncRefStore = refs,
                journalNotesRepository = notes,
                syncScope = backgroundScope,
            )

        assertTrue(manager.downloadRemoteChanges().success)

        val downloaded = notes.getNoteById(original.uid) as JournalNote.Audio
        assertEquals(if (preserve) document else null, downloaded.transcript)
        assertEquals(duration, downloaded.durationMs)
        assertEquals(remoteRef, refs.get(original.uid)?.remoteUrl)
        assertEquals("file:///tmp/$fileName", downloaded.mediaRef)
    }
}
