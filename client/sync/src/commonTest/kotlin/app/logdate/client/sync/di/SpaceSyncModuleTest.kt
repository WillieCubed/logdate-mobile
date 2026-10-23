package app.logdate.client.sync.di

import app.logdate.client.database.dao.UserPlaceDao
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.identity.ExistingCanonicalOwner
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableDraftRepository
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.sync.conflict.JournalNoteConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.HISTORY_ACCOUNT
import app.logdate.client.sync.space.InMemoryUserPlaces
import app.logdate.client.sync.space.SpaceHistoryStore
import app.logdate.client.sync.space.SpaceMarks
import app.logdate.client.sync.space.SpaceRecordRegistry
import app.logdate.client.sync.space.mappers.MergedPlace
import app.logdate.client.sync.space.mappers.SpaceDraft
import app.logdate.client.sync.space.mappers.SpaceJournal
import app.logdate.client.sync.test.FakeJournalContentRepository
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.SemanticPlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The real [spaceSyncModule] and [conflictResolverModule], started the way the sync modules include them. */
class SpaceSyncModuleTest {
    private val notes = FakeJournalNotesRepository()
    private val journals = SyncedJournalRepository(FakeJournalRepository())
    private val content = SyncedContentRepository(FakeJournalContentRepository())
    private val marks = SpaceMarks(FakeSyncMetadataService())
    private val history = SpaceHistoryStore()
    private val savedPlaces = InMemoryUserPlaces()
    private val deviceId = Uuid.random()

    private fun startKoin() =
        koinApplication {
            modules(
                module {
                    single<JournalNotesRepository> { notes }
                    single<JournalRepository> { journals }
                    single<JournalContentRepository> { content }
                    single<SyncableMarkedMomentRepository> { marks }
                    single<HistoryRecordStore> { history }
                    single<UserPlaceDao> { savedPlaces }
                    single<SessionStorage> { FakeSessionStorage().apply { currentOrigin = "https://space.test" } }
                    single<CanonicalOwnerProvider> {
                        object : CanonicalOwnerProvider {
                            override suspend fun getCanonicalOwnerId() = HISTORY_ACCOUNT

                            override suspend fun readExistingOwner() = ExistingCanonicalOwner(HISTORY_ACCOUNT, true)

                            override suspend fun hasBoundOwner() = true
                        }
                    }
                    single<LogDateConfigRepository> { DefaultLogDateConfigRepository(initialBackendUrl = "https://space.test") }
                    single<DeviceIdProvider> { FixedDeviceId(deviceId) }
                },
                conflictResolverModule,
                spaceSyncModule,
            )
        }

    @Test
    fun `the registry resolved from the space sync module has the notes kind`() {
        val app = startKoin()
        try {
            val registry = app.koin.get<SpaceRecordRegistry>()

            val kind = assertNotNull(registry.forEntityType(EntityType.NOTE))
            assertEquals(
                setOf(
                    "app.logdate.journal.entry",
                    "app.logdate.journal.transcript",
                    "app.logdate.journal.journal",
                    "app.logdate.journal.membership",
                    "app.logdate.journal.draft",
                    "app.logdate.journal.markedMoment",
                    "app.logdate.place.place",
                    "app.logdate.place.visit",
                    "app.logdate.place.correction",
                    "app.logdate.place.entryLink",
                    "app.logdate.place.journey",
                    "app.logdate.place.sample",
                ),
                registry.collections,
            )
            assertEquals(listOf("app.logdate.journal.entry", "app.logdate.journal.transcript"), kind.parts.map { it.collection })
            assertEquals("app.logdate.journal.entry", kind.main.collection)
            assertIs<JournalNoteConflictResolver>(kind.local.conflictResolver)
        } finally {
            app.close()
        }
    }

    @Test
    fun `the notes kind reads the repository the app uses`() =
        runTest {
            val app = startKoin()
            try {
                val note = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "By the river")
                notes.create(note)

                val kind = app.koin.get<SpaceRecordRegistry>().forEntityType(EntityType.NOTE)!!

                assertEquals(note, kind.local.load(note.uid.toString()))
            } finally {
                app.close()
            }
        }

    @Test
    fun `the registry resolved from the space sync module has the membership and draft kinds`() {
        val app = startKoin()
        try {
            val registry = app.koin.get<SpaceRecordRegistry>()

            val membership = assertNotNull(registry.forEntityType(EntityType.ASSOCIATION))
            val draft = assertNotNull(registry.forEntityType(EntityType.DRAFT))
            assertEquals(listOf("app.logdate.journal.membership"), membership.parts.map { it.collection })
            assertEquals(listOf("app.logdate.journal.draft"), draft.parts.map { it.collection })
        } finally {
            app.close()
        }
    }

    @Test
    fun `the draft kind reads the repository the app uses`() =
        runTest {
            val app = startKoin()
            try {
                val draft =
                    EditorDraft(blocks = listOf(SerializableTextBlock(Uuid.random(), created, content = "Dear diary")), createdAt = created)
                journals.saveDraft(draft)

                val kind = app.koin.get<SpaceRecordRegistry>().forEntityType(EntityType.DRAFT)!!

                assertEquals(SpaceDraft(draft), kind.local.load(draft.id.toString()))
            } finally {
                app.close()
            }
        }

    @Test
    fun `the registry resolved from the space sync module has the journals kind`() {
        val app = startKoin()
        try {
            val kind = assertNotNull(app.koin.get<SpaceRecordRegistry>().forEntityType(EntityType.JOURNAL))

            assertEquals(listOf("app.logdate.journal.journal"), kind.parts.map { it.collection })
            assertEquals("app.logdate.journal.journal", kind.main.collection)
        } finally {
            app.close()
        }
    }

    @Test
    fun `the journals kind reads the repository the app uses`() =
        runTest {
            val app = startKoin()
            try {
                val journal = Journal(title = "By the river", created = created, lastUpdated = created)
                journals.create(journal)

                val kind = app.koin.get<SpaceRecordRegistry>().forEntityType(EntityType.JOURNAL)!!

                assertEquals(SpaceJournal(journal), kind.local.load(journal.id.toString()))
            } finally {
                app.close()
            }
        }

    @Test
    fun `the registry resolved from the space sync module has the marked moment and every location history kind`() {
        val app = startKoin()
        try {
            val registry = app.koin.get<SpaceRecordRegistry>()

            val expected =
                mapOf(
                    EntityType.MOMENT to "app.logdate.journal.markedMoment",
                    EntityType.PLACE to "app.logdate.place.place",
                    EntityType.PLACE_VISIT to "app.logdate.place.visit",
                    EntityType.PLACE_CORRECTION to "app.logdate.place.correction",
                    EntityType.PLACE_ENTRY_LINK to "app.logdate.place.entryLink",
                    EntityType.PLACE_JOURNEY to "app.logdate.place.journey",
                    EntityType.LOCATION_SAMPLES to "app.logdate.place.sample",
                )
            expected.forEach { (type, collection) ->
                val kind = assertNotNull(registry.forEntityType(type), "$type")
                assertEquals(listOf(collection), kind.parts.map { it.collection }, "$type")
            }
        } finally {
            app.close()
        }
    }

    @Test
    fun `the marked moment kind reads the repository the app uses`() =
        runTest {
            val app = startKoin()
            try {
                val mark =
                    MarkedMoment(
                        Uuid.random(),
                        created,
                        "UTC",
                        null,
                        null,
                        MarkState.OPEN,
                        "",
                        emptySet(),
                        "watch",
                        created,
                        0,
                    )
                marks.mark(mark)

                val kind = app.koin.get<SpaceRecordRegistry>().forEntityType(EntityType.MOMENT)!!

                assertEquals(mark, kind.local.load(mark.id.toString()))
            } finally {
                app.close()
            }
        }

    @Test
    fun `the location history kinds read the history records the app uses`() =
        runTest {
            val app = startKoin()
            try {
                val placeId = Uuid.random().toString()
                history.put(
                    HISTORY_ACCOUNT,
                    "https://space.test",
                    HistoryRecord(
                        placeId,
                        "place",
                        Json.encodeToString(HistoryPayload.serializer(), HistoryPayload.Place(SemanticPlace(placeId, "Home", 1.0, 2.0))),
                        deviceId.toString(),
                    ),
                )
                val registry = app.koin.get<SpaceRecordRegistry>()

                val place = registry.forEntityType(EntityType.PLACE)!!.local.load(placeId)

                assertEquals(MergedPlace(placeId, "Home", 1.0, 2.0), place)
            } finally {
                app.close()
            }
        }

    private class FixedDeviceId(
        id: Uuid,
    ) : DeviceIdProvider {
        private val flow = MutableStateFlow(id)

        override fun getDeviceId(): StateFlow<Uuid> = flow

        override suspend fun refreshDeviceId() = Unit
    }

    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private class SyncedContentRepository(
        private val base: FakeJournalContentRepository,
    ) : JournalContentRepository by base,
        SyncableJournalContentRepository {
        override suspend fun addContentToJournalFromSync(
            contentId: Uuid,
            journalId: Uuid,
        ) = base.addContentToJournal(contentId, journalId)

        override suspend fun removeContentFromJournalFromSync(
            contentId: Uuid,
            journalId: Uuid,
        ) = base.removeContentFromJournal(contentId, journalId)
    }

    private class SyncedJournalRepository(
        private val base: FakeJournalRepository,
    ) : JournalRepository by base,
        SyncableDraftRepository by base,
        SyncableJournalRepository {
        override suspend fun createFromSync(journal: Journal) {
            base.create(journal)
        }

        override suspend fun updateFromSync(journal: Journal) = base.update(journal)

        override suspend fun deleteFromSync(journalId: Uuid) = base.delete(journalId)

        override suspend fun updateSyncMetadata(
            journalId: Uuid,
            syncVersion: Long,
            syncedAt: Instant,
        ) = Unit
    }
}
