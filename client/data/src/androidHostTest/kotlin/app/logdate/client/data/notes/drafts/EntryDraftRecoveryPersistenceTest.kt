package app.logdate.client.data.notes.drafts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class EntryDraftRecoveryPersistenceTest {
    @Test
    fun `recovery updates durable snapshot and initialized repository cache without changing timestamps`() =
        runTest {
            val store = AndroidLocalEntryDraftStore(RecoveryPreferences())
            val original = draft()
            store.saveDraft(original)
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            assertEquals(listOf(original), repository.getDraftSnapshotsForRecovery())
            val replacement = quarantine(original)

            assertTrue(repository.compareAndSetDraftForRecovery(original, replacement) { true })

            assertEquals(replacement, store.getDraft(original.id))
            assertEquals(replacement, repository.getDraft(original.id).first().getOrThrow())
            assertEquals(original.createdAt, replacement.createdAt)
            assertEquals(original.updatedAt, replacement.updatedAt)
            assertEquals(listOf(replacement), OfflineFirstEntryDraftRepository(store, backgroundScope).getDraftSnapshotsForRecovery())
        }

    @Test
    fun `later edit and deletion win over captured recovery snapshot`() =
        runTest {
            val store = AndroidLocalEntryDraftStore(RecoveryPreferences())
            val original = draft()
            store.saveDraft(original)
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            repository.getDraftSnapshotsForRecovery()
            val edited = (original.notes.single() as JournalNote.Image).copy(caption = "A later edit")
            repository.updateDraft(original.id, listOf(edited))
            assertFalse(repository.compareAndSetDraftForRecovery(original, quarantine(original)) { true })
            assertEquals("A later edit", (store.getDraft(original.id)?.notes?.single() as JournalNote.Image).caption)
            repository.deleteDraft(original.id)
            assertFalse(repository.compareAndSetDraftForRecovery(original, quarantine(original)) { true })
            assertEquals(emptyList(), repository.getDraftSnapshotsForRecovery())
            assertEquals(null, store.getDraft(original.id))
        }

    @Test
    fun `authority is checked inside the platform write transaction`() =
        runTest {
            val preferences = RecoveryPreferences()
            val store = AndroidLocalEntryDraftStore(preferences)
            val original = draft()
            store.saveDraft(original)
            var current = true
            preferences.beforeUpdate = { current = false }

            assertFalse(store.compareAndSetForRecovery(original, quarantine(original)) { current })

            assertEquals(original, store.getDraft(original.id))
        }

    @Test
    fun `storage failure leaves both persisted draft and repository cache unchanged`() =
        runTest {
            val preferences = RecoveryPreferences()
            val store = AndroidLocalEntryDraftStore(preferences)
            val original = draft()
            store.saveDraft(original)
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            repository.getDraftSnapshotsForRecovery()
            preferences.beforeUpdate = { throw IllegalStateException("Synthetic storage unavailable") }

            assertFailsWith<IllegalStateException> {
                repository.compareAndSetDraftForRecovery(original, quarantine(original)) { true }
            }

            assertEquals(original, store.getDraft(original.id))
            assertEquals(original, repository.getDraft(original.id).first().getOrThrow())
        }

    @Test
    fun `platform snapshot check rejects an out of band replacement`() =
        runTest {
            val store = AndroidLocalEntryDraftStore(RecoveryPreferences())
            val original = draft()
            val edited = original.copy(markId = "retained-edit")
            store.saveDraft(edited)
            assertFalse(store.compareAndSetForRecovery(original, quarantine(original)) { true })
            assertEquals(edited, store.getDraft(original.id))
        }

    @Test
    fun `native transfer tombstone survives edits and deletion`() =
        runTest {
            val store = AndroidLocalEntryDraftStore(RecoveryPreferences())
            val original = draft()
            val transferMarker = original.id.toString()

            assertTrue(store.insertForNativeTransferRecovery(original, transferMarker) { true })
            assertTrue(store.isNativeTransferRecoveryAdmitted(original.id, transferMarker))
            store.saveDraft(original.copy(markId = "later user edit"))
            assertTrue(store.isNativeTransferRecoveryAdmitted(original.id, transferMarker))
            assertTrue(store.deleteDraft(original.id))

            assertTrue(store.isNativeTransferRecoveryAdmitted(original.id, transferMarker))
            assertEquals(null, store.getDraft(original.id))
        }

    private fun draft(): EntryDraft {
        val time = Instant.fromEpochMilliseconds(1234)
        return EntryDraft(
            Uuid.random(),
            listOf(
                JournalNote.Image(
                    creationTimestamp = time,
                    lastUpdated = time,
                    mediaRef = "content://media/external/images/media/42",
                    caption = "Keep me",
                ),
            ),
            time,
            time,
            selectedJournalIds = listOf(Uuid.random()),
            markId = "retained-mark",
        )
    }

    private fun quarantine(draft: EntryDraft): EntryDraft {
        val image = draft.notes.single() as JournalNote.Image
        return draft.copy(notes = listOf(image.copy(mediaRef = UnavailableMediaReferences.nativeRestore(image.mediaRef))))
    }
}

internal class RecoveryPreferences : DataStore<Preferences> {
    private val values = MutableStateFlow(emptyPreferences())
    var beforeUpdate: () -> Unit = {}
    var afterUpdate: () -> Unit = {}
    override val data: Flow<Preferences> = values

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        beforeUpdate()
        return transform(values.value).also {
            values.value = it
            afterUpdate()
        }
    }
}
