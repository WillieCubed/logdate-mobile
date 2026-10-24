package app.logdate.client.data.restore

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRecoveryRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class RetainedPendingCameraDraftAcceptanceTest {
    @Test
    fun `recovered camera path is rewritten in note and pending media references`() =
        runTest {
            val sourceId = Uuid.random()
            val now = Clock.System.now()
            val sourcePath = "/data/user/0/studio.hypertext.logdate/files/camera-captures/capture.jpg.tmp"
            val recovered = "/data/user/0/studio.hypertext.logdate/files/camera-captures/transfer-capture.jpg.tmp"
            val source =
                EntryDraft(
                    sourceId,
                    listOf(JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = sourcePath)),
                    now,
                    now,
                    pendingMedia = listOf(PendingMediaRecord(Uuid.random(), PendingMediaType.CAMERA_PHOTO, now, filePath = sourcePath)),
                )
            val drafts = FakeRecoveryDrafts(source)

            val acceptedId =
                assertNotNull(RetainedPendingCameraDraftAcceptance(drafts).accept("incoming-generation", source, sourcePath, recovered))
            val imported = drafts.rows.getValue(acceptedId)

            assertEquals(recovered, (imported.notes.single() as JournalNote.Image).mediaRef)
            assertEquals(recovered, imported.pendingMedia.single().filePath)
        }

    @Test
    fun `occupied transfer keeps working draft and accepts one stable recovered camera draft`() =
        runTest {
            val sourceId = Uuid.random()
            val blockId = Uuid.random()
            val now = Clock.System.now()
            val sourcePath = "/data/user/0/studio.hypertext.logdate/files/camera-captures/capture.jpg.tmp"
            val source =
                EntryDraft(
                    sourceId,
                    emptyList(),
                    now,
                    now,
                    pendingMedia = listOf(PendingMediaRecord(blockId, PendingMediaType.CAMERA_PHOTO, now, filePath = sourcePath)),
                )
            val working = source.copy(notes = listOf(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Mine")))
            val drafts = FakeRecoveryDrafts(working)
            val acceptance = RetainedPendingCameraDraftAcceptance(drafts)
            val recovered = "logdate-media://library/objects/sha256/aa/${"a".repeat(64)}.jpg"

            val first = assertNotNull(acceptance.accept("incoming-generation", source, sourcePath, recovered))
            val second = assertNotNull(acceptance.accept("incoming-generation", source, sourcePath, recovered))

            assertNotEquals(sourceId, first)
            assertEquals(first, second)
            assertEquals(2, drafts.rows.size)
            assertEquals(working, drafts.rows.getValue(sourceId))
            assertEquals(
                recovered,
                drafts.rows
                    .getValue(first)
                    .pendingMedia
                    .single()
                    .filePath,
            )
        }

    @Test
    fun `repeated callback completes without overwriting a recovered draft the user edited`() =
        runTest {
            val sourceId = Uuid.random()
            val blockId = Uuid.random()
            val now = Clock.System.now()
            val sourcePath = "/data/user/0/studio.hypertext.logdate/files/camera-captures/capture.jpg.tmp"
            val source =
                EntryDraft(
                    sourceId,
                    emptyList(),
                    now,
                    now,
                    pendingMedia = listOf(PendingMediaRecord(blockId, PendingMediaType.CAMERA_PHOTO, now, filePath = sourcePath)),
                )
            val drafts = FakeRecoveryDrafts(source)
            val acceptance = RetainedPendingCameraDraftAcceptance(drafts)
            val recovered = "logdate-media://library/objects/sha256/aa/${"a".repeat(64)}.jpg"
            val acceptedId = assertNotNull(acceptance.accept("incoming-generation", source, sourcePath, recovered))
            val edited =
                drafts.rows
                    .getValue(
                        acceptedId,
                    ).copy(notes = listOf(JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Mine")))
            drafts.rows[acceptedId] = edited

            assertEquals(acceptedId, acceptance.accept("incoming-generation", source, sourcePath, recovered))
            assertEquals(2, drafts.rows.size)
            assertEquals(edited, drafts.rows.getValue(acceptedId))
        }

    @Test
    fun `replayed callback does not resurrect a user deleted recovered draft`() =
        runTest {
            val sourceId = Uuid.random()
            val blockId = Uuid.random()
            val now = Clock.System.now()
            val sourcePath = "/data/user/0/studio.hypertext.logdate/files/camera-captures/capture.jpg.tmp"
            val source =
                EntryDraft(
                    sourceId,
                    emptyList(),
                    now,
                    now,
                    pendingMedia = listOf(PendingMediaRecord(blockId, PendingMediaType.CAMERA_PHOTO, now, filePath = sourcePath)),
                )
            val drafts = FakeRecoveryDrafts(source)
            val recovered = "logdate-media://library/objects/sha256/aa/${"a".repeat(64)}.jpg"
            val acceptance = RetainedPendingCameraDraftAcceptance(drafts)
            val acceptedId = assertNotNull(acceptance.accept("incoming-generation", source, sourcePath, recovered))
            drafts.rows.remove(acceptedId)

            assertEquals(acceptedId, acceptance.accept("incoming-generation", source, sourcePath, recovered))
            assertEquals(1, drafts.rows.size)
            assertTrue(acceptedId !in drafts.rows)
        }

    private class FakeRecoveryDrafts(
        initial: EntryDraft,
    ) : EntryDraftRecoveryRepository {
        val rows = mutableMapOf(initial.id to initial)
        private val transferMarkers = mutableSetOf<String>()

        override suspend fun getDraftSnapshotsForRecovery(): List<EntryDraft> = rows.values.toList()

        override suspend fun compareAndSetDraftForRecovery(
            expected: EntryDraft,
            replacement: EntryDraft,
            canCommit: () -> Boolean,
        ): Boolean = false

        override suspend fun insertDraftIfAbsentForRecovery(
            draft: EntryDraft,
            canCommit: () -> Boolean,
            scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
        ): Boolean {
            if (!canCommit() || rows.containsKey(draft.id)) return false
            val inserted =
                scopeFence {
                    if (!canCommit() || rows.containsKey(draft.id)) return@scopeFence false
                    rows[draft.id] = draft
                    true
                }
            return inserted
        }

        override suspend fun isNativeTransferDraftAdmitted(
            draftId: Uuid,
            transferMarker: String,
        ): Boolean = transferMarker == draftId.toString() && transferMarker in transferMarkers

        override suspend fun insertNativeTransferDraftIfAbsent(
            draft: EntryDraft,
            transferMarker: String,
            canCommit: () -> Boolean,
            scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
        ): Boolean {
            if (isNativeTransferDraftAdmitted(draft.id, transferMarker)) return true
            val inserted = insertDraftIfAbsentForRecovery(draft, canCommit, scopeFence)
            if (inserted) transferMarkers += transferMarker
            return inserted
        }
    }
}
