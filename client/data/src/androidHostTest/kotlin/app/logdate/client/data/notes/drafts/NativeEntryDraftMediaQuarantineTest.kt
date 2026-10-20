package app.logdate.client.data.notes.drafts

import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryEditBase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NativeEntryDraftMediaQuarantineTest {
    @Test
    fun `current draft external identities and edit base are quarantined without changing metadata`() =
        runTest {
            val store = AndroidLocalEntryDraftStore(RecoveryPreferences())
            val original = draft()
            store.saveDraft(original)
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            val service = NativeEntryDraftMediaQuarantine(repository)
            assertTrue(service.run({ true }, ::quarantine))
            val updated = repository.getDraft(original.id).first().getOrThrow()
            val image = original.notes.first() as JournalNote.Image
            val pending = original.pendingMedia.single()
            val expected =
                original.copy(
                    notes = listOf(image.copy(mediaRef = requireNotNull(quarantine(image.mediaRef))), original.notes.last()),
                    editBase = original.editBase?.copy(original = image.copy(mediaRef = requireNotNull(quarantine(image.mediaRef)))),
                    pendingMedia = listOf(pending.copy(filePath = quarantine(requireNotNull(pending.filePath)))),
                )
            assertEquals(expected, updated)
            assertEquals(expected, store.getDraft(original.id))
            assertTrue(service.run({ true }, ::quarantine))
            assertEquals(expected, store.getDraft(original.id))
        }

    @Test
    fun `later save is retained and quarantine retries its new snapshot`() =
        runTest {
            val store = AndroidLocalEntryDraftStore(RecoveryPreferences())
            val original = draft()
            store.saveDraft(original)
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            val service = NativeEntryDraftMediaQuarantine(repository)
            val entered = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            val recovery =
                async {
                    service.run({ true }) { reference ->
                        entered.complete(Unit)
                        resume.await()
                        quarantine(reference)
                    }
                }
            assertTrue(
                select {
                    entered.onAwait { true }
                    recovery.onAwait { false }
                },
                "Quarantine must inspect the retained snapshot",
            )
            val edited = original.copy(notes = listOf((original.notes.first() as JournalNote.Image).copy(caption = "Later caption")))
            repository.saveDraftSnapshot(edited)
            val acceptedEdit = repository.getDraft(original.id).first().getOrThrow()
            resume.complete(Unit)
            assertFalse(recovery.await())
            assertEquals(acceptedEdit, store.getDraft(original.id))
            assertTrue(service.run({ true }, ::quarantine))
            val repaired = repository.getDraft(original.id).first().getOrThrow()
            assertEquals(acceptedEdit.updatedAt, repaired.updatedAt)
            assertEquals("Later caption", (repaired.notes.single() as JournalNote.Image).caption)
        }

    @Test
    fun `cancellation retains the original snapshot instead of acknowledging quarantine`() =
        runTest {
            val store = AndroidLocalEntryDraftStore(RecoveryPreferences())
            val original = draft()
            store.saveDraft(original)
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            assertFailsWith<CancellationException> {
                NativeEntryDraftMediaQuarantine(repository).run({ true }) { throw CancellationException("Synthetic interruption") }
            }
            assertEquals(original, store.getDraft(original.id))
            assertEquals(original, repository.getDraft(original.id).first().getOrThrow())
        }

    private fun quarantine(reference: String): String? =
        reference.takeIf(UnavailableMediaReferences::requiresNativeQuarantine)?.let(UnavailableMediaReferences::nativeRestore)

    private fun draft(): EntryDraft {
        val time = Instant.fromEpochMilliseconds(1234)
        val image =
            JournalNote.Image(
                creationTimestamp = time,
                lastUpdated = time,
                mediaRef = "content://media/external/images/media/42",
                caption = "Retain caption",
            )
        val owned =
            JournalNote.Audio(
                creationTimestamp = time,
                lastUpdated = time,
                mediaRef = "file:///data/user/0/app/files/audio/owned.m4a",
                durationMs = 1234,
            )
        return EntryDraft(
            id = Uuid.random(),
            notes = listOf(image, owned),
            createdAt = time,
            updatedAt = time,
            selectedJournalIds = listOf(Uuid.random()),
            markId = "retained mark",
            editBase = EntryEditBase(image, null),
            pendingMedia =
                listOf(
                    PendingMediaRecord(
                        Uuid.random(),
                        PendingMediaType.AUDIO,
                        time,
                        "content://source/recording",
                        fallbackFilePath = owned.mediaRef,
                        position = 1,
                        caption = "Pending caption",
                        transcription = "Pending transcript",
                    ),
                ),
        )
    }
}
