package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceItemChanges
import app.logdate.client.sync.space.SpacePartChange
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DraftSpaceKindTest {
    private val repository = FakeJournalRepository()
    private val kind = draftSpaceKind(repository)
    private val older = Instant.parse("2026-10-12T07:41:09Z")
    private val newer = Instant.parse("2026-10-13T07:41:09Z")
    private val draft =
        EditorDraft(
            id = Uuid.parse("5b2c0d6e-8f41-4a7b-9c3d-2e1f0a9b8c7d"),
            blocks = listOf(SerializableTextBlock(Uuid.random(), older, content = "Dear diary")),
            createdAt = older,
            lastModifiedAt = newer,
        )

    @Test
    fun `a draft is the one record of its kind keyed by its id`() {
        assertEquals(EntityType.DRAFT, kind.entityType)
        assertEquals(listOf("app.logdate.journal.draft"), kind.parts.map { it.collection })
        assertEquals(draft.id.toString(), kind.main.ownerOf(draft.id.toString().uppercase()))
        assertEquals(listOf(draft.id.toString()), kind.main.rkeysOf(draft.id.toString().uppercase()))
    }

    @Test
    fun `a stored draft is loaded by the id in the outbox`() =
        runTest {
            repository.saveDraft(draft)

            assertEquals(SpaceDraft(draft), kind.local.load(draft.id.toString()))
            assertNull(kind.local.load(Uuid.random().toString()))
            assertNull(kind.local.load("not an id"))
            assertEquals(newer, kind.local.lastUpdatedOf(SpaceDraft(draft)))
        }

    @Test
    fun `a draft from another device replaces the stored one`() =
        runTest {
            repository.saveDraft(draft)
            val edited = draft.copy(blocks = emptyList(), lastModifiedAt = newer)

            kind.local.replaceLocal(SpaceDraft(edited))

            assertEquals(edited, repository.getDraft(draft.id))
        }

    @Test
    fun `the newest change in the space is the draft and its version`() {
        val written = SpacePartChange.Written(draft.id.toString(), SpaceDraft(draft), seq = 12)
        val changes = SpaceItemChanges<SpaceDraft>(draft.id.toString(), mapOf("app.logdate.journal.draft" to listOf(written)))

        assertEquals(SpaceDraft(draft, syncVersion = 12), kind.assemble(changes, null))
    }

    @Test
    fun `the later edit wins when both devices changed the draft`() {
        val here = SpaceDraft(draft.copy(lastModifiedAt = older))
        val there = SpaceDraft(draft.copy(lastModifiedAt = newer))

        val resolution = kind.local.conflictResolver.resolve(here, there, older, newer)

        assertEquals(there, assertIs<ConflictResolution.KeepRemote<SpaceDraft>>(resolution).value)
    }
}
