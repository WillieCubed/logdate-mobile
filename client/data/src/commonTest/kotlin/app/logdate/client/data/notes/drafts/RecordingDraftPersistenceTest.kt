package app.logdate.client.data.notes.drafts

import app.logdate.client.data.fakes.FakeLocalEntryDraftStore
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class RecordingDraftPersistenceTest {
    @Test
    fun `restart accepts a new target and rejects snapshots from earlier takes`() =
        runTest {
            val repository = OfflineFirstEntryDraftRepository(FakeLocalEntryDraftStore(), backgroundScope)
            val block = Uuid.random()
            val pending = PendingMediaRecord(block, PendingMediaType.AUDIO, Clock.System.now(), filePath = "/first.m4a")
            val draft = repository.createDraft(Uuid.random(), emptyList(), listOf(pending))
            repository.checkpointRecording(block, "/first.m4a", "owner")
            repository.completeRecording(block, "/first.m4a", 10000, "owner", false)
            val replacement = pending.copy(filePath = "/second.m4a", fallbackFilePath = "/first.m4a")
            repository.updateDraft(draft, emptyList(), listOf(replacement))
            assertEquals(
                "/second.m4a",
                repository
                    .getDraft(draft)
                    .first()
                    .getOrThrow()
                    .pendingMedia
                    .single()
                    .filePath,
            )
            assertEquals(draft, repository.checkpointRecording(block, "/second.m4a", "owner"))
            repository.updateDraft(draft, emptyList(), listOf(pending))
            assertEquals(
                "/second.m4a",
                repository
                    .getDraft(draft)
                    .first()
                    .getOrThrow()
                    .pendingMedia
                    .single()
                    .filePath,
            )
            repository.completeRecording(block, "/second.m4a", 20000, "owner", false)
            repository.saveDraftSnapshot(
                repository
                    .getDraft(draft)
                    .first()
                    .getOrThrow()
                    .copy(notes = emptyList(), pendingMedia = listOf(pending)),
            )
            val saved = repository.getDraft(draft).first().getOrThrow()
            assertEquals("/second.m4a", (saved.notes.single() as JournalNote.Audio).mediaRef)
            assertTrue(saved.pendingMedia.isEmpty())
            val third = pending.copy(filePath = "/third.m4a", fallbackFilePath = "/second.m4a")
            repository.saveDraftSnapshot(saved.copy(notes = emptyList(), pendingMedia = listOf(third)))
            assertEquals(
                "/third.m4a",
                repository
                    .getDraft(draft)
                    .first()
                    .getOrThrow()
                    .pendingMedia
                    .single()
                    .filePath,
            )
        }

    @Test
    fun `stale editor snapshot preserves a background completion and its acknowledgement`() =
        runTest {
            val repository = OfflineFirstEntryDraftRepository(FakeLocalEntryDraftStore(), backgroundScope)
            val block = Uuid.random()
            val now = Clock.System.now()
            val draft = repository.createDraft(Uuid.random(), emptyList(), listOf(PendingMediaRecord(block, PendingMediaType.AUDIO, now)))
            repository.checkpointRecording(block, "/audio.m4a", "owner")
            val stale = repository.getDraft(draft).first().getOrThrow()
            repository.completeRecording(block, "/audio.m4a", 10000, "owner", true)
            repository.acknowledgeQuietSave(draft)
            val text = JournalNote.Text(content = "Edited while saving", creationTimestamp = now, lastUpdated = now)
            repository.saveDraftSnapshot(stale.copy(notes = listOf(text)))
            val saved = repository.getDraft(draft).first().getOrThrow()
            assertEquals(listOf(text, saved.notes.last()), saved.notes)
            assertEquals(block, (saved.notes.last() as JournalNote.Audio).uid)
            assertTrue(saved.pendingMedia.isEmpty())
            assertTrue(saved.quietSave!!.acknowledged)
            assertEquals("owner", saved.recordingOwners[block.toString()])
        }

    @Test
    fun `editor recovery preserves ownership after replacing pending audio`() =
        runTest {
            val store = FakeLocalEntryDraftStore()
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            val block = Uuid.random()
            val now = Clock.System.now()
            val draft = repository.createDraft(Uuid.random(), emptyList(), listOf(PendingMediaRecord(block, PendingMediaType.AUDIO, now)))
            repository.checkpointRecording(block, "/audio.m4a", "owner")
            repository.updateDraft(
                draft,
                listOf(
                    JournalNote.Audio(uid = block, mediaRef = "/audio.m4a", durationMs = 10000, creationTimestamp = now, lastUpdated = now),
                ),
                emptyList(),
            )
            val restarted = OfflineFirstEntryDraftRepository(store, backgroundScope)
            assertEquals(draft, restarted.completeRecording(block, "/audio.m4a", 10000, "owner", false))
            assertNull(restarted.completeRecording(block, "/audio.m4a", 10000, "other", false))
        }

    @Test
    fun `manual recovery remains idempotent after restart and refuses another owner`() =
        runTest {
            val store = FakeLocalEntryDraftStore()
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            val block = Uuid.random()
            val draft =
                repository.createDraft(
                    Uuid.random(),
                    emptyList(),
                    listOf(PendingMediaRecord(block, PendingMediaType.AUDIO, Clock.System.now())),
                )
            repository.checkpointRecording(block, "/audio.m4a", "owner")
            assertEquals(draft, repository.completeRecording(block, "/audio.m4a", 10000, "owner", false))
            val restarted = OfflineFirstEntryDraftRepository(store, backgroundScope)
            assertEquals(draft, restarted.completeRecording(block, "/audio.m4a", 10000, "owner", false))
            assertNull(restarted.completeRecording(block, "/audio.m4a", 10000, "other", false))
            assertEquals(
                1,
                restarted
                    .getDraft(draft)
                    .first()
                    .getOrThrow()
                    .notes.size,
            )
        }

    @Test
    fun `a failed save keeps the pending recording available after restart`() =
        runTest {
            val backing = FakeLocalEntryDraftStore()
            var failWrites = false
            val store =
                object : LocalEntryDraftStore by backing {
                    override suspend fun saveDraft(draft: app.logdate.client.repository.journals.EntryDraft) {
                        check(!failWrites) { "Storage unavailable" }
                        backing.saveDraft(draft)
                    }
                }
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            val block = Uuid.random()
            val draft =
                repository.createDraft(
                    Uuid.random(),
                    emptyList(),
                    listOf(PendingMediaRecord(block, PendingMediaType.AUDIO, Clock.System.now())),
                )
            repository.checkpointRecording(block, "/audio.m4a", "owner")
            failWrites = true
            assertFailsWith<IllegalStateException> { repository.completeRecording(block, "/audio.m4a", 10_000, "owner", true) }
            val restarted = OfflineFirstEntryDraftRepository(backing, backgroundScope)
            val retained = restarted.getDraft(draft).first().getOrThrow()
            assertEquals("/audio.m4a", retained.pendingMedia.single().filePath)
            assertNull(retained.quietSave)
            assertEquals(draft, restarted.completeRecording(block, "/audio.m4a", 10_000, "owner", true))
            restarted.acknowledgeQuietSave(draft)
            val reopened = OfflineFirstEntryDraftRepository(backing, backgroundScope).getDraft(draft).first().getOrThrow()
            assertTrue(reopened.quietSave!!.acknowledged)
        }

    @Test
    fun `quiet completion retains text and converts only the owning pending recording`() =
        runTest {
            val store = FakeLocalEntryDraftStore()
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            val block = Uuid.random()
            val text = JournalNote.Text(content = "Before bed", creationTimestamp = Clock.System.now(), lastUpdated = Clock.System.now())
            val pending = PendingMediaRecord(block, PendingMediaType.AUDIO, Clock.System.now(), position = 1)
            val draft = repository.createDraft(Uuid.random(), listOf(text), listOf(pending))
            assertEquals(draft, repository.checkpointRecording(block, "/audio.m4a", "owner"))
            assertEquals(draft, repository.completeRecording(block, "/audio.m4a", 180_000, "owner", true))
            val saved = repository.getDraft(draft).first().getOrThrow()
            assertEquals(text, saved.notes.first())
            assertEquals(block, saved.notes.last().uid)
            assertEquals(180_000L, (saved.notes.last() as JournalNote.Audio).durationMs)
            assertTrue(saved.pendingMedia.isEmpty())
            assertNotNull(saved.quietSave)
            assertEquals(draft, repository.completeRecording(block, "/audio.m4a", 180_000, "owner", true))
            assertEquals(1, repository.getDrafts().first().size)
        }

    @Test
    fun `a different account cannot complete a recording`() =
        runTest {
            val repository = OfflineFirstEntryDraftRepository(FakeLocalEntryDraftStore(), backgroundScope)
            val block = Uuid.random()
            val draft =
                repository.createDraft(
                    Uuid.random(),
                    emptyList(),
                    listOf(PendingMediaRecord(block, PendingMediaType.AUDIO, Clock.System.now())),
                )
            repository.checkpointRecording(block, "/audio.m4a", "owner")
            assertNull(repository.completeRecording(block, "/audio.m4a", 10_000, "other", true))
            assertEquals(
                1,
                repository
                    .getDraft(draft)
                    .first()
                    .getOrThrow()
                    .pendingMedia.size,
            )
        }

    @Test
    fun `stale editor autosave cannot undo a completed recording`() =
        runTest {
            val repository = OfflineFirstEntryDraftRepository(FakeLocalEntryDraftStore(), backgroundScope)
            val block = Uuid.random()
            val pending = PendingMediaRecord(block, PendingMediaType.AUDIO, Clock.System.now())
            val draft = repository.createDraft(Uuid.random(), emptyList(), listOf(pending))
            repository.checkpointRecording(block, "/audio.m4a", "owner")
            repository.completeRecording(block, "/audio.m4a", 10_000, "owner", true)
            repository.updateDraft(draft, emptyList(), listOf(pending))
            val saved = repository.getDraft(draft).first().getOrThrow()
            assertEquals(block, saved.notes.single().uid)
            assertTrue(saved.pendingMedia.isEmpty())
        }
}
