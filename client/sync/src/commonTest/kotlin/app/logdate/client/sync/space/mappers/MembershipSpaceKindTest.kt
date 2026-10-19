package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceItemChanges
import app.logdate.client.sync.space.SpacePartChange
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MembershipSpaceKindTest {
    private val journalId = Uuid.parse("5b2c0d6e-8f41-4a7b-9c3d-2e1f0a9b8c7d")
    private val entryId = Uuid.parse("0b8f3c1e-6a2d-4e9f-8c7b-1d2e3f4a5b6c")
    private val content = LinkedContent()
    private val kind = membershipSpaceKind(content)
    private val outboxId = "$journalId::$entryId"
    private val early = Instant.parse("2026-10-12T07:41:09Z")
    private val late = Instant.parse("2026-10-13T07:41:09Z")

    @Test
    fun `a membership is the one record of its kind and belongs to the pair its key names`() {
        assertEquals(EntityType.ASSOCIATION, kind.entityType)
        assertEquals(listOf("app.logdate.journal.membership"), kind.parts.map { it.collection })
        assertEquals(outboxId, kind.main.ownerOf("$journalId:$entryId".uppercase()))
        assertEquals(listOf("$journalId:$entryId"), kind.main.rkeysOf(outboxId))
    }

    @Test
    fun `the pair in the outbox is found when the entry is in the journal`() =
        runTest {
            content.journalsOf[entryId] = mutableListOf(Journal(id = journalId, title = "Lake"))

            assertEquals(JournalMembership(journalId, entryId), kind.local.load(outboxId))
            assertNull(kind.local.load("$journalId::${Uuid.random()}"))
            assertNull(kind.local.load("not a pair"))
        }

    @Test
    fun `a membership from another device is added without queueing an upload`() =
        runTest {
            kind.local.replaceLocal(JournalMembership(journalId, entryId, early))

            assertEquals(listOf(entryId to journalId), content.addedFromSync)
        }

    @Test
    fun `the version of the newest change is the version of the membership`() {
        val written = SpacePartChange.Written("$journalId:$entryId", JournalMembership(journalId, entryId, early), seq = 7)
        val changes = SpaceItemChanges<JournalMembership>(outboxId, mapOf("app.logdate.journal.membership" to listOf(written)))

        assertEquals(JournalMembership(journalId, entryId, early, syncVersion = 7), kind.assemble(changes, null))
    }

    @Test
    fun `two copies settle on the earlier time of adding`() {
        val resolver = kind.local.conflictResolver
        val earlier = JournalMembership(journalId, entryId, early)
        val later = JournalMembership(journalId, entryId, late)
        val unknown = JournalMembership(journalId, entryId)

        assertEquals(
            earlier,
            assertIs<ConflictResolution.KeepLocal<JournalMembership>>(resolver.resolve(earlier, later, early, late)).value,
        )
        assertEquals(
            earlier,
            assertIs<ConflictResolution.KeepRemote<JournalMembership>>(resolver.resolve(later, earlier, late, early)).value,
        )
        assertEquals(
            earlier,
            assertIs<ConflictResolution.KeepRemote<JournalMembership>>(resolver.resolve(unknown, earlier, early, early)).value,
        )
        assertEquals(
            earlier,
            assertIs<ConflictResolution.KeepLocal<JournalMembership>>(resolver.resolve(earlier, unknown, early, early)).value,
        )
    }

    private class LinkedContent : SyncableJournalContentRepository {
        val journalsOf = mutableMapOf<Uuid, MutableList<Journal>>()
        val addedFromSync = mutableListOf<Pair<Uuid, Uuid>>()

        override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = MutableStateFlow(journalsOf[contentId].orEmpty())

        override suspend fun addContentToJournalFromSync(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            addedFromSync += contentId to journalId
        }

        override suspend fun removeContentFromJournalFromSync(
            contentId: Uuid,
            journalId: Uuid,
        ) = Unit

        override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override suspend fun addContentToJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) = Unit

        override suspend fun removeContentFromJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) = Unit

        override suspend fun addContentToJournals(
            contentId: Uuid,
            journalIds: List<Uuid>,
        ) = Unit

        override suspend fun removeContentFromAllJournals(contentId: Uuid) = Unit

        override fun observeJournalsForContents(contentIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> = flowOf(emptyMap())
    }
}
