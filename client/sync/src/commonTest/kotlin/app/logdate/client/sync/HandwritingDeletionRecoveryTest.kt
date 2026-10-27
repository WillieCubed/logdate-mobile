package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.conflict.DeletionConflictRecovery
import app.logdate.client.sync.conflict.KeyValueSyncConflictStore
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.FakeJournalContentRepository
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.testSyncTransactionManager
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke
import app.logdate.shared.model.SerializableHandwritingBlock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HandwritingDeletionRecoveryTest {
    @Test
    fun `saved note recovery retains original strokes after restarting recovery storage`() = runTest { verifyNoteRecovery(null) }

    @Test
    fun `saved note recovery retains opaque future handwriting bytes`() =
        runTest { verifyNoteRecovery("{\"version\":2,\"strokes\":[\"unreadable original\"]}") }

    @Test
    fun `draft recovery changes identities without changing the handwriting`() =
        runTest {
            val device = Device()
            val block = SerializableHandwritingBlock(Uuid.random(), at, document = document())
            val draft = EditorDraft(blocks = listOf(block))
            device.journals.saveDraft(draft)
            device.recovery().retain(EntityType.DRAFT, draft.id.toString())
            device.journals.deleteDraft(draft.id)

            val recovery = device.recovery()
            val retained = recovery.list().single()
            assertEquals(draft, retained.deletedEdit?.draft)
            val id = assertNotNull(recovery.saveAsNew(retained.id))
            val restored = assertNotNull(device.journals.getDraft(id))
            val restoredBlock = assertIs<SerializableHandwritingBlock>(restored.blocks.single())
            assertNotEquals(draft.id, id)
            assertNotEquals(block.id, restoredBlock.id)
            assertEquals(block.copy(id = restoredBlock.id), restoredBlock)
            assertEquals(id, device.recovery().saveAsNew(retained.id))
        }

    private suspend fun verifyNoteRecovery(opaque: String?) {
        val device = Device()
        val original =
            JournalNote.Handwriting(
                creationTimestamp = at,
                lastUpdated = at,
                document = document(),
                unreadableContent = opaque,
                syncVersion = 7,
            )
        device.notes.create(original)
        device.recovery().retain(EntityType.NOTE, original.uid.toString())
        device.notes.deleteFromSync(original.uid)

        val recovery = device.recovery()
        val retained = recovery.list().single()
        assertEquals(original, retained.deletedEdit?.note)
        val id = assertNotNull(recovery.saveAsNew(retained.id))
        val restored = assertIs<JournalNote.Handwriting>(device.notes.getNoteById(id))
        assertNotEquals(original.uid, id)
        assertEquals(original.document, restored.document)
        assertEquals(original.unreadableContent, restored.unreadableContent)
        assertEquals(0L, restored.syncVersion)
        assertEquals(id, device.recovery().saveAsNew(retained.id))
    }

    private class Device {
        private val storage = InMemoryKeyValueStorage()
        val notes = FakeJournalNotesRepository()
        val journals = FakeJournalRepository()
        private val contents = FakeJournalContentRepository()

        fun recovery() =
            DeletionConflictRecovery(
                KeyValueSyncConflictStore(storage),
                notes,
                journals,
                contents,
                testSyncTransactionManager(),
                { UploadScope("owner", "https://cloud.example") },
            )
    }

    private fun document() =
        HandwritingDocument(
            strokes =
                listOf(
                    HandwritingStroke(
                        id = Uuid.random(),
                        tool = "pen",
                        color = "#172634",
                        width = 2.5f,
                        points = listOf(HandwritingPoint(11.25f, 42.5f, 0.35f), HandwritingPoint(27.75f, 53.5f, 0.85f)),
                        x = 1.25f,
                        y = 2.5f,
                        rotation = 0.75f,
                        scaleX = 1.5f,
                        scaleY = 0.9f,
                    ),
                ),
        )

    private val at = Instant.parse("2026-10-27T10:00:00Z")
}
