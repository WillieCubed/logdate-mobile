package app.logdate.client.data.notes.drafts

import app.logdate.client.data.fakes.FakeLocalEntryDraftStore
import app.logdate.client.media.audio.RecordingDraftSession
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class RecordingDraftSessionTest {
    @Test
    fun `prepare persists the file before recording can start`() =
        runTest {
            val repository = OfflineFirstEntryDraftRepository(FakeLocalEntryDraftStore(), backgroundScope)
            val block = Uuid.random()
            val draft =
                repository.createDraft(
                    Uuid.random(),
                    emptyList(),
                    listOf(PendingMediaRecord(block, PendingMediaType.AUDIO, Clock.System.now())),
                )
            val session = RecordingDraftSession(repository) { "owner" }
            session.prepare(block, "/recording.m4a")
            val saved =
                repository
                    .getDraft(draft)
                    .first()
                    .getOrThrow()
                    .pendingMedia
                    .single()
            assertEquals("/recording.m4a", saved.filePath)
            assertEquals("owner", saved.recordingOwnerId)
        }

    @Test
    fun `switching accounts leaves the original pending file untouched`() =
        runTest {
            val repository = OfflineFirstEntryDraftRepository(FakeLocalEntryDraftStore(), backgroundScope)
            val block = Uuid.random()
            val draft =
                repository.createDraft(
                    Uuid.random(),
                    emptyList(),
                    listOf(PendingMediaRecord(block, PendingMediaType.AUDIO, Clock.System.now())),
                )
            var owner = "original"
            val session = RecordingDraftSession(repository) { owner }
            session.prepare(block, "/recording.m4a")
            owner = "other"
            assertFailsWith<IllegalStateException> { session.complete("/recording.m4a", 1000, true) }
            assertEquals(
                "original",
                repository
                    .getDraft(draft)
                    .first()
                    .getOrThrow()
                    .pendingMedia
                    .single()
                    .recordingOwnerId,
            )
        }
}
