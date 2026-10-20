package app.logdate.client.data.notes.drafts

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class EntryDraftRecoveryAcknowledgementTest {
    @Test
    fun `committed repair with cancelled acknowledgement reconciles the cache before retry`() =
        runTest {
            val preferences = RecoveryPreferences()
            val store = AndroidLocalEntryDraftStore(preferences)
            val original = draft()
            store.saveDraft(original)
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            repository.getDraftSnapshotsForRecovery()
            val repaired = repaired(original)
            preferences.afterUpdate = {
                preferences.afterUpdate = {}
                throw CancellationException("Interrupted after durable commit")
            }

            assertFailsWith<CancellationException> { repository.compareAndSetDraftForRecovery(original, repaired) { true } }

            assertEquals(repaired, store.getDraft(original.id))
            assertEquals(listOf(repaired), repository.getDraftSnapshotsForRecovery())
            assertTrue(repository.compareAndSetDraftForRecovery(repaired, repaired) { true })
        }

    @Test
    fun `the same storage exception from write and reread preserves the original failure`() =
        runTest {
            val store = AndroidLocalEntryDraftStore(RecoveryPreferences())
            val original = draft()
            store.saveDraft(original)
            val failure = IllegalStateException("Synthetic retained storage error")
            val failingStore =
                object : LocalEntryDraftStore by store, RecoveryEntryDraftStore {
                    override suspend fun getDraft(id: Uuid): EntryDraft? = throw failure

                    override suspend fun compareAndSetForRecovery(
                        expected: EntryDraft,
                        replacement: EntryDraft,
                        canCommit: () -> Boolean,
                    ): Boolean = throw failure
                }
            val repository = OfflineFirstEntryDraftRepository(failingStore, backgroundScope)
            repository.getDraftSnapshotsForRecovery()

            assertSame(
                failure,
                assertFailsWith<IllegalStateException> {
                    repository.compareAndSetDraftForRecovery(original, repaired(original)) { true }
                },
            )
            assertEquals(listOf(original), repository.getDraftSnapshotsForRecovery())
            assertEquals(original, store.getDraft(original.id))
        }

    @Test
    fun `rejected disk comparison refreshes the affected cached draft`() =
        runTest {
            val store = AndroidLocalEntryDraftStore(RecoveryPreferences())
            val original = draft()
            store.saveDraft(original)
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            repository.getDraftSnapshotsForRecovery()
            val newer = original.copy(markId = "Newer durable edit")
            store.saveDraft(newer)

            assertFalse(repository.compareAndSetDraftForRecovery(original, repaired(original)) { true })

            assertEquals(listOf(newer), repository.getDraftSnapshotsForRecovery())
            assertTrue(repository.compareAndSetDraftForRecovery(newer, repaired(newer)) { true })
        }

    @Test
    fun `automatic repair preserves unknown fields in raw draft and nested records`() =
        runTest {
            val preferences = RecoveryPreferences()
            val store = AndroidLocalEntryDraftStore(preferences)
            val original = draft()
            val raw = Json.parseToJsonElement(Json.encodeToString(original)).jsonObject
            val image =
                raw
                    .getValue("notes")
                    .jsonArray
                    .single()
                    .jsonObject
            val pending =
                raw
                    .getValue("pendingMedia")
                    .jsonArray
                    .single()
                    .jsonObject
            val future = JsonObject(mapOf("unknown" to JsonPrimitive("Keep this metadata")))
            val extended =
                JsonObject(
                    raw +
                        mapOf(
                            "futureDraft" to future,
                            "notes" to JsonArray(listOf(JsonObject(image + ("futureNote" to future)))),
                            "pendingMedia" to JsonArray(listOf(JsonObject(pending + ("futureRecording" to future)))),
                        ),
                )
            val key = stringPreferencesKey(original.id.toString())
            preferences.edit { it[key] = extended.toString() }
            val replacement = repaired(original)

            assertTrue(store.compareAndSetForRecovery(original, replacement) { true })

            val actual = Json.parseToJsonElement(requireNotNull(preferences.data.first()[key])).jsonObject
            assertEquals(future, actual["futureDraft"])
            assertEquals(
                future,
                actual
                    .getValue("notes")
                    .jsonArray
                    .single()
                    .jsonObject["futureNote"],
            )
            assertEquals(
                future,
                actual
                    .getValue("pendingMedia")
                    .jsonArray
                    .single()
                    .jsonObject["futureRecording"],
            )
            assertEquals(replacement, store.getDraft(original.id))
        }

    private fun draft(): EntryDraft {
        val time = Instant.fromEpochMilliseconds(1234)
        return EntryDraft(
            Uuid.random(),
            listOf(JournalNote.Image(creationTimestamp = time, lastUpdated = time, mediaRef = "content://source/42", caption = "Retained")),
            time,
            time,
            pendingMedia = listOf(PendingMediaRecord(Uuid.random(), PendingMediaType.AUDIO, time, filePath = "/excluded/staging.m4a")),
        )
    }

    private fun repaired(draft: EntryDraft): EntryDraft =
        draft.copy(
            notes = draft.notes.map { (it as JournalNote.Image).copy(mediaRef = UnavailableMediaReferences.nativeRestore(it.mediaRef)) },
            pendingMedia =
                draft.pendingMedia.map {
                    it.copy(
                        filePath = UnavailableMediaReferences.nativeRestore(requireNotNull(it.filePath)),
                    )
                },
        )
}
