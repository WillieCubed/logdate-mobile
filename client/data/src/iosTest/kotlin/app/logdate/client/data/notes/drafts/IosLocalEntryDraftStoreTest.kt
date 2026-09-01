package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import platform.Foundation.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class IosLocalEntryDraftStoreTest {
    @Test
    fun `save with corrupt index preserves index and does not create draft`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val defaults = NSUserDefaults.standardUserDefaults
            val draft = draftFixture()
            val indexKey = "entry_drafts_index"
            val draftKey = "entry_draft_${draft.id}"
            val corruptIndex = "[invalid index"

            defaults.setValue(corruptIndex, indexKey)
            try {
                assertFailsWith<EntryDraftStorageException> {
                    store.saveDraft(draft)
                }

                assertEquals(corruptIndex, defaults.stringForKey(indexKey))
                assertEquals(null, defaults.stringForKey(draftKey))
            } finally {
                defaults.removeObjectForKey(indexKey)
                defaults.removeObjectForKey(draftKey)
            }
        }

    @Test
    fun `delete with corrupt index preserves index and draft`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val defaults = NSUserDefaults.standardUserDefaults
            val draft = draftFixture()
            val indexKey = "entry_drafts_index"
            val draftKey = "entry_draft_${draft.id}"
            val corruptIndex = "[invalid index"

            store.saveDraft(draft)
            val originalDraft = assertNotNull(defaults.stringForKey(draftKey))
            defaults.setValue(corruptIndex, indexKey)
            try {
                assertFailsWith<EntryDraftStorageException> {
                    store.deleteDraft(draft.id)
                }

                assertEquals(corruptIndex, defaults.stringForKey(indexKey))
                assertEquals(originalDraft, defaults.stringForKey(draftKey))
            } finally {
                defaults.removeObjectForKey(indexKey)
                defaults.removeObjectForKey(draftKey)
            }
        }

    @Test
    fun `save over corrupt target preserves target and index`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val defaults = NSUserDefaults.standardUserDefaults
            val draft = draftFixture()
            val indexKey = "entry_drafts_index"
            val draftKey = "entry_draft_${draft.id}"
            val corruptTarget = "{not a valid draft"
            val index = "[\"${draft.id}\"]"

            defaults.setValue(corruptTarget, draftKey)
            defaults.setValue(index, indexKey)
            try {
                assertFailsWith<EntryDraftStorageException> {
                    store.saveDraft(draft)
                }

                assertEquals(corruptTarget, defaults.stringForKey(draftKey))
                assertEquals(index, defaults.stringForKey(indexKey))
            } finally {
                defaults.removeObjectForKey(indexKey)
                defaults.removeObjectForKey(draftKey)
            }
        }

    @Test
    fun `malformed draft fails closed and preserves stored bytes`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val defaults = NSUserDefaults.standardUserDefaults
            val draftId = Uuid.random()
            val key = "entry_draft_$draftId"
            val malformedJson = "{not valid draft json"

            defaults.setValue(malformedJson, key)
            try {
                assertFailsWith<EntryDraftStorageException> {
                    store.getDraft(draftId)
                }

                assertEquals(malformedJson, defaults.stringForKey(key))
            } finally {
                defaults.removeObjectForKey(key)
            }
        }

    @Test
    fun `malformed draft index fails closed and preserves stored bytes`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val defaults = NSUserDefaults.standardUserDefaults
            val indexKey = "entry_drafts_index"
            val malformedJson = "[not a valid index"

            defaults.setValue(malformedJson, indexKey)
            try {
                assertFailsWith<EntryDraftStorageException> {
                    store.getAllDrafts()
                }

                assertEquals(malformedJson, defaults.stringForKey(indexKey))
            } finally {
                defaults.removeObjectForKey(indexKey)
            }
        }

    @Test
    fun `invalid draft identity fails closed and preserves stored bytes`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val defaults = NSUserDefaults.standardUserDefaults
            val draftId = Uuid.random()
            val key = "entry_draft_$draftId"
            val invalidIdentityJson =
                """{"id":"not-a-uuid","notes":[],"createdAt":0,"updatedAt":0}"""

            defaults.setValue(invalidIdentityJson, key)
            try {
                assertFailsWith<EntryDraftStorageException> {
                    store.getDraft(draftId)
                }

                assertEquals(invalidIdentityJson, defaults.stringForKey(key))
            } finally {
                defaults.removeObjectForKey(key)
            }
        }

    @Test
    fun `invalid draft index identity fails closed and preserves stored bytes`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val defaults = NSUserDefaults.standardUserDefaults
            val indexKey = "entry_drafts_index"
            val invalidIdentityJson = "[\"not-a-uuid\"]"

            defaults.setValue(invalidIdentityJson, indexKey)
            try {
                assertFailsWith<EntryDraftStorageException> {
                    store.getAllDrafts()
                }

                assertEquals(invalidIdentityJson, defaults.stringForKey(indexKey))
            } finally {
                defaults.removeObjectForKey(indexKey)
            }
        }

    @Test
    fun `save draft round trips pending media journal selection and caption`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val now = Clock.System.now()
            val draftId = Uuid.random()
            val journalId = Uuid.random()
            val pending =
                PendingMediaRecord(
                    blockId = Uuid.random(),
                    mediaType = PendingMediaType.AUDIO,
                    createdAt = now,
                    filePath = "/recordings/pending.m4a",
                )
            val draft =
                EntryDraft(
                    id = draftId,
                    notes =
                        listOf(
                            JournalNote.Image(
                                creationTimestamp = now,
                                lastUpdated = now,
                                mediaRef = "image://draft",
                                caption = "Durable caption",
                            ),
                        ),
                    createdAt = now,
                    updatedAt = now,
                    pendingMedia = listOf(pending),
                    selectedJournalIds = listOf(journalId),
                )

            try {
                store.saveDraft(draft)

                val restored = assertNotNull(store.getDraft(draftId))
                assertEquals(listOf(pending), restored.pendingMedia)
                assertEquals(listOf(journalId), restored.selectedJournalIds)
                assertEquals("Durable caption", (restored.notes.single() as JournalNote.Image).caption)
            } finally {
                store.deleteDraft(draftId)
            }
        }

    @Test
    fun `legacy draft without new fields loads with empty defaults`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val defaults = NSUserDefaults.standardUserDefaults
            val draftId = Uuid.random()
            val noteId = Uuid.random()
            val key = "entry_draft_$draftId"
            val legacyJson =
                """{"id":"$draftId","notes":[{"id":"$noteId","type":"TEXT","content":"Legacy","createdAt":0}],"createdAt":0,"updatedAt":0}"""

            defaults.setValue(legacyJson, key)
            try {
                val restored = assertNotNull(store.getDraft(draftId))

                assertTrue(restored.pendingMedia.isEmpty())
                assertTrue(restored.selectedJournalIds.isEmpty())
            } finally {
                defaults.removeObjectForKey(key)
            }
        }

    @Test
    fun `every journal note type round trips update version and location fields`() =
        runTest {
            val store = IosLocalEntryDraftStore()
            val createdAt = Instant.fromEpochMilliseconds(1_725_000_000_000)

            fun location(seed: Int) =
                NoteLocation(
                    coordinates =
                        NoteCoordinates(
                            latitude = 36.0 + seed,
                            longitude = -115.0 - seed,
                            altitude = 500.0 + seed,
                            accuracy = seed.toFloat(),
                        ),
                )
            val notes =
                listOf(
                    JournalNote.Text(
                        creationTimestamp = createdAt,
                        lastUpdated = Instant.fromEpochMilliseconds(1_725_000_001_001),
                        content = "Text fields",
                        syncVersion = 11,
                        location = location(1),
                    ),
                    JournalNote.Image(
                        creationTimestamp = createdAt,
                        lastUpdated = Instant.fromEpochMilliseconds(1_725_000_002_002),
                        mediaRef = "image://fields",
                        caption = "Image fields",
                        syncVersion = 22,
                        location = location(2),
                    ),
                    JournalNote.Video(
                        creationTimestamp = createdAt,
                        lastUpdated = Instant.fromEpochMilliseconds(1_725_000_003_003),
                        mediaRef = "video://fields",
                        caption = "Video fields",
                        syncVersion = 33,
                        location = location(3),
                    ),
                    JournalNote.Audio(
                        creationTimestamp = createdAt,
                        lastUpdated = Instant.fromEpochMilliseconds(1_725_000_004_004),
                        mediaRef = "audio://fields",
                        durationMs = 4_004,
                        syncVersion = 44,
                        location = location(4),
                    ),
                )
            val draftId = Uuid.random()
            val draft =
                EntryDraft(
                    id = draftId,
                    notes = notes,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                )

            try {
                store.saveDraft(draft)

                assertEquals(notes, assertNotNull(store.getDraft(draftId)).notes)
            } finally {
                store.deleteDraft(draftId)
            }
        }

    private fun draftFixture(): EntryDraft {
        val now = Clock.System.now()
        return EntryDraft(
            id = Uuid.random(),
            notes = listOf(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Draft")),
            createdAt = now,
            updatedAt = now,
        )
    }
}
