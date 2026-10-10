package app.logdate.client.data.journals

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.data.fakes.FakeDraftRepository
import app.logdate.client.data.fakes.FakeLocalEntryDraftStore
import app.logdate.client.data.fakes.FakeRemoteJournalDataSource
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.data.notes.drafts.OfflineFirstEntryDraftRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.repository.journals.JournalMergeResult
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class JournalMergePersistenceTest {
    @Test
    fun `merge unions raw memberships without requiring downloaded notes and survives restart`() =
        runTest {
            val file = Files.createTempFile("journal-merge", ".db")

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
            var database = open()
            val scope = JournalMergeScope("owner", "https://example.test")

            fun repository() = repository(database, scope)
            try {
                val source = journal("Trips")
                val destination = journal("Life")
                val other = journal("Favorites")
                listOf(source, destination, other).forEach { database.journalDao().create(it) }
                val shared = Uuid.random()
                val incoming = Uuid.random()
                val existing = Uuid.random()
                listOf(
                    source.id to shared,
                    source.id to incoming,
                    destination.id to shared,
                    destination.id to existing,
                    other.id to incoming,
                ).forEach { (journal, note) ->
                    database.journalContentDao().addContentToJournal(JournalContentEntityLink(journal, note))
                }
                val repository = repository()
                val preview = assertNotNull(repository.previewMerge(source.id, destination.id))
                assertEquals(2, preview.sourceCount)
                assertEquals(1, preview.overlapCount)
                assertEquals(3, preview.combinedCount)
                val operationId = Uuid.random()
                assertIs<JournalMergeResult.Merged>(repository.merge(preview, operationId))
                assertNull(database.journalDao().getJournalById(source.id))
                assertEquals(destination, database.journalDao().getJournalById(destination.id))
                assertEquals(
                    setOf(shared, incoming, existing),
                    database
                        .journalContentDao()
                        .getContentForJournal(destination.id)
                        .first()
                        .toSet(),
                )
                assertEquals(listOf(incoming), database.journalContentDao().getContentForJournal(other.id).first())
                database.close()
                database = open()
                assertEquals(destination.id, repository().resolveJournalId(source.id))
                assertEquals(operationId, repository().pendingJournalMerges().single().operationId)
                assertIs<JournalMergeResult.Merged>(repository().merge(preview, operationId))
            } finally {
                database.close()
                Files.deleteIfExists(file)
                Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
                Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
            }
        }

    @Test
    fun `changed membership with same counts requires review again and does not remove source`() =
        runTest {
            val file = Files.createTempFile("journal-review", ".db")
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
            try {
                val source = journal("Same title")
                val destination = journal("Same title")
                listOf(source, destination).forEach { database.journalDao().create(it) }
                val repository = repository(database, JournalMergeScope("owner", "origin"))
                val original = Uuid.random()
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(source.id, original))
                val preview = assertNotNull(repository.previewMerge(source.id, destination.id))
                database.journalContentDao().removeContentFromJournal(source.id, original)
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(source.id, Uuid.random()))
                assertIs<JournalMergeResult.ReviewChanged>(repository.merge(preview, Uuid.random()))
                assertNotNull(database.journalDao().getJournalById(source.id))
                assertTrue(repository.pendingJournalMerges().isEmpty())
                assertNull(repository.previewMerge(source.id, source.id))
            } finally {
                database.close()
                Files.deleteIfExists(file)
            }
        }

    @Test
    fun `transaction failure rolls back memberships source redirect and operation`() =
        runTest {
            withDatabase { database ->
                val source = journal("Source")
                val destination = journal("Destination")
                listOf(source, destination).forEach { database.journalDao().create(it) }
                val note = Uuid.random()
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(source.id, note))
                val delegate = RoomSyncTransactionManager(database)
                val failBeforeCommit =
                    object : SyncTransactionManager {
                        override suspend fun <T> withTransaction(block: suspend () -> T): T =
                            delegate.withTransaction {
                                block()
                                error("Simulated disk failure before commit")
                            }
                    }
                val repo = repository(database, JournalMergeScope("owner", "origin"), failBeforeCommit)
                val preview = repository(database, JournalMergeScope("owner", "origin")).previewMerge(source.id, destination.id)!!
                assertFailsWith<IllegalStateException> { repo.merge(preview, Uuid.random()) }
                assertNotNull(database.journalDao().getJournalById(source.id))
                assertTrue(
                    database
                        .journalContentDao()
                        .getContentForJournal(destination.id)
                        .first()
                        .isEmpty(),
                )
                assertTrue(database.journalMergeDao().all("owner", "origin").isEmpty())
                assertTrue(database.syncMetadataDao().getPendingByType("owner", "origin", "JOURNAL_MERGE").isEmpty())
            }
        }

    @Test
    fun `empty journals missing journals and chained redirects retain destination metadata and scope`() =
        runTest {
            withDatabase { database ->
                val a = journal("Duplicate")
                val b = journal("Duplicate")
                val c = journal("Keep")
                listOf(a, b, c).forEach { database.journalDao().create(it) }
                val repo = repository(database, JournalMergeScope("owner", "origin"))
                assertNull(repo.previewMerge(a.id, Uuid.random()))
                assertNull(repo.previewMerge(Uuid.random(), b.id))
                val first = repo.previewMerge(a.id, b.id)!!
                assertEquals(0, first.combinedCount)
                assertIs<JournalMergeResult.Merged>(repo.merge(first, Uuid.random()))
                assertIs<JournalMergeResult.Merged>(repo.merge(repo.previewMerge(b.id, c.id)!!, Uuid.random()))
                assertEquals(c.id, repo.resolveJournalId(a.id))
                assertEquals(c, database.journalDao().getJournalById(c.id))
                assertEquals(a.id, repository(database, JournalMergeScope("other", "origin")).resolveJournalId(a.id))
                assertEquals(a.id, repository(database, JournalMergeScope("owner", "other-server")).resolveJournalId(a.id))
                assertFailsWith<IllegalStateException> { repo.applyJournalRedirect(c.id, a.id) }
                assertEquals(c.id, repo.resolveJournalId(a.id))
            }
        }

    @Test
    fun `draft selection resolves lazily and remains editable after a merge`() =
        runTest {
            withDatabase { database ->
                val a = journal("Source")
                val b = journal("Keep")
                listOf(a, b).forEach { database.journalDao().create(it) }
                val drafts = FakeDraftRepository()
                val draft = EditorDraft(selectedJournalIds = listOf(a.id, b.id))
                drafts.saveDraft(draft)
                val repo = repository(database, JournalMergeScope("owner", "origin"), drafts = drafts)
                repo.merge(repo.previewMerge(a.id, b.id)!!, Uuid.random())
                val restored = repo.getDraft(draft.id)!!
                assertEquals(listOf(b.id), restored.selectedJournalIds)
                assertEquals(draft.blocks, restored.blocks)
                assertEquals(draft.lastModifiedAt, restored.lastModifiedAt)
                repo.saveDraft(draft)
                assertEquals(listOf(b.id), drafts.getDraft(draft.id)!!.selectedJournalIds)
            }
        }

    @Test
    fun `version 51 migration preserves existing journals and memberships`() =
        runTest {
            val file = Files.createTempFile("merge-migration", ".db")
            val source = journal("Existing")
            val content = Uuid.random()
            getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString())).let { database ->
                database.journalDao().create(source)
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(source.id, content))
                database.close()
            }
            BundledSQLiteDriver().open(file.toString()).use { connection ->
                connection.execSQL("DROP TABLE journal_merges")
                connection.execSQL("PRAGMA user_version = 51")
            }
            val reopened = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
            try {
                assertEquals(source, reopened.journalDao().getJournalById(source.id))
                assertEquals(listOf(content), reopened.journalContentDao().getContentForJournal(source.id).first())
                assertTrue(reopened.journalMergeDao().all("owner", "origin").isEmpty())
            } finally {
                reopened.close()
                Files.deleteIfExists(file)
            }
        }

    @Test
    fun `consented first account adoption moves merge records without crossing servers`() =
        runTest {
            withDatabase { database ->
                val a = journal("Offline source")
                val b = journal("Keep")
                listOf(a, b).forEach { database.journalDao().create(it) }
                val original = repository(database, JournalMergeScope("offline-owner", "origin"))
                val result = assertIs<JournalMergeResult.Merged>(original.merge(original.previewMerge(a.id, b.id)!!, Uuid.random()))
                database.historyOwnerAdoptionDao().adopt("offline-owner", "account-owner", "other-server", "device") { it }
                assertEquals(b.id, original.resolveJournalId(a.id))
                database.historyOwnerAdoptionDao().adopt("offline-owner", "account-owner", "origin", "device") { it }
                val adopted = repository(database, JournalMergeScope("account-owner", "origin"))
                assertEquals(b.id, adopted.resolveJournalId(a.id))
                assertEquals(result.operation.operationId, adopted.pendingJournalMerges().single().operationId)
                assertEquals(1, database.syncMetadataDao().getPendingByType("account-owner", "origin", "JOURNAL_MERGE").size)
            }
        }

    @Test
    fun `destination recovery persists original request until confirmed and survives restart aliases`() =
        runTest {
            withDatabase { database ->
                val a = journal("From")
                val b = journal("Deleted later")
                val c = journal("Keep")
                listOf(a, b, c).forEach { database.journalDao().create(it) }
                val repo = repository(database, JournalMergeScope("owner", "origin"))
                val old = assertIs<JournalMergeResult.Merged>(repo.merge(repo.previewMerge(a.id, b.id)!!, Uuid.random())).operation
                repo.markJournalMergeNeedsDestination(old)
                assertEquals(listOf(old.operationId), repo.observeJournalMergeIssues().first().map { it.operationId })
                val preview = repo.previewPendingMerge(old.operationId, c.id)!!
                val updated =
                    assertIs<JournalMergeResult.Merged>(
                        repo.retargetPendingMerge(old.operationId, preview, Uuid.random()),
                    ).operation
                assertEquals(c.id, repo.resolveJournalId(a.id))
                assertEquals(updated.operationId, repo.getJournalMerge(old.operationId)?.operationId)
                assertTrue(repo.observeJournalMergeIssues().first().isEmpty())
                assertEquals(listOf(updated.operationId), repo.pendingJournalMerges().map { it.operationId })
            }
        }

    @Test
    fun `response from previous account cannot write a redirect in the current scope`() =
        runTest {
            withDatabase { database ->
                val previous = JournalMergeScope("previous", "origin")
                val current = JournalMergeScope("current", "origin")
                val source = journal("From")
                val destination = journal("Keep")
                listOf(source, destination).forEach { database.journalDao().create(it) }
                val repo = repository(database, current)
                assertFailsWith<IllegalStateException> {
                    repo.applyJournalRedirect(source.id, destination.id, previous)
                }
                assertNotNull(database.journalDao().getJournalById(source.id))
                assertTrue(database.journalMergeDao().all(current.ownerId, current.serverOrigin).isEmpty())
            }
        }

    @Test
    fun `deleted destination recovery preserves later pending memberships without changing submitted request`() =
        runTest {
            withDatabase { database ->
                val scope = JournalMergeScope("owner", "origin")
                val a = journal("Source")
                val b = journal("Deleted target")
                val c = journal("New target")
                listOf(a, b, c).forEach { database.journalDao().create(it) }
                val original = Uuid.random()
                val later = Uuid.random()
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(a.id, original))
                val repo = repository(database, scope)
                val operation = assertIs<JournalMergeResult.Merged>(repo.merge(repo.previewMerge(a.id, b.id)!!, Uuid.random())).operation
                database.journalContentDao().addContentToJournal(JournalContentEntityLink(b.id, later))
                database.syncMetadataDao().insertPending(
                    PendingUploadEntity(
                        scope.ownerId,
                        scope.serverOrigin,
                        "ASSOCIATION",
                        AssociationPendingKey(b.id, later).toPendingId(),
                        "CREATE",
                        2000,
                    ),
                )
                repo.markJournalMergeNeedsDestination(operation)
                database.syncMetadataDao().deletePending(
                    scope.ownerId,
                    scope.serverOrigin,
                    "ASSOCIATION",
                    AssociationPendingKey(b.id, later).toPendingId(),
                )
                repo.deleteFromSync(b.id)
                assertEquals(setOf(original), repo.pendingJournalMerges().single().contentIds)
                val preview = repo.previewPendingMerge(operation.operationId, c.id)!!
                assertEquals(setOf(original, later), preview.sourceContentIds)
                val recovered =
                    assertIs<JournalMergeResult.Merged>(
                        repo.retargetPendingMerge(operation.operationId, preview, Uuid.random()),
                    ).operation
                assertEquals(setOf(original, later), recovered.contentIds)
                assertEquals(
                    setOf(original, later),
                    database
                        .journalContentDao()
                        .getContentForJournal(c.id)
                        .first()
                        .toSet(),
                )
                assertTrue(
                    database
                        .syncMetadataDao()
                        .getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION")
                        .none { it.entityId == AssociationPendingKey(b.id, later).toPendingId() },
                )
            }
        }

    @Test
    fun `entry draft storage stays intact while restored and newly saved selections follow chained merges`() =
        runTest {
            withDatabase { database ->
                val a = journal("A")
                val b = journal("B")
                val c = journal("C")
                listOf(a, b, c).forEach { database.journalDao().create(it) }
                val repo = repository(database, JournalMergeScope("owner", "origin"))
                val store = FakeLocalEntryDraftStore()
                val drafts = OfflineFirstEntryDraftRepository(store, backgroundScope, repo)
                val id = Uuid.random()
                drafts.createDraft(id, emptyList(), emptyList(), listOf(a.id, c.id))
                val saved = store.getDraft(id)!!
                repo.merge(repo.previewMerge(a.id, b.id)!!, Uuid.random())
                repo.merge(repo.previewMerge(b.id, c.id)!!, Uuid.random())
                assertEquals(
                    listOf(c.id),
                    drafts
                        .getDraft(id)
                        .first()
                        .getOrThrow()
                        .selectedJournalIds,
                )
                assertEquals(saved, store.getDraft(id))
                drafts.updateDraft(id, emptyList(), emptyList(), listOf(a.id, b.id, c.id))
                assertEquals(listOf(c.id), store.getDraft(id)!!.selectedJournalIds)
                assertEquals(saved.createdAt, store.getDraft(id)!!.createdAt)
            }
        }

    @Test
    fun `merge recovery observation switches backend scope without reopening`() =
        runTest {
            withDatabase { database ->
                val backend = kotlinx.coroutines.flow.MutableStateFlow("first")
                val a = journal("A")
                val b = journal("B")
                listOf(a, b).forEach { database.journalDao().create(it) }
                val repo =
                    OfflineFirstJournalRepository(
                        database.journalDao(),
                        FakeRemoteJournalDataSource(),
                        FakeDraftRepository(),
                        syncMetadataService = FakeSyncMetadataService(),
                        database = database,
                        mergeTransactionManager = RoomSyncTransactionManager(database),
                        currentScope = { JournalMergeScope("owner", backend.value) },
                        mergeScopeChanges = backend,
                    )
                val operation = assertIs<JournalMergeResult.Merged>(repo.merge(repo.previewMerge(a.id, b.id)!!, Uuid.random())).operation
                repo.markJournalMergeNeedsDestination(operation)
                val initial = kotlinx.coroutines.CompletableDeferred<Unit>()
                val changed = kotlinx.coroutines.CompletableDeferred<List<app.logdate.client.repository.journals.JournalMergeOperation>>()
                val observer =
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                        repo.observeJournalMergeIssues().collect { issues ->
                            if (!initial.isCompleted) {
                                assertEquals(1, issues.size)
                                initial.complete(Unit)
                            } else {
                                changed.complete(issues)
                            }
                        }
                    }
                try {
                    initial.await()
                    backend.value = "second"
                    val next =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            kotlinx.coroutines.withTimeout(1000) { changed.await() }
                        }
                    assertTrue(next.isEmpty())
                } finally {
                    observer.cancel()
                }
            }
        }

    private suspend fun withDatabase(block: suspend (LogDateDatabase) -> Unit) {
        val file = Files.createTempFile("journal-merge", ".db")
        val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
        try {
            block(database)
        } finally {
            database.close()
            Files.deleteIfExists(file)
        }
    }

    private fun repository(
        database: LogDateDatabase,
        scope: JournalMergeScope,
        transactionManager: SyncTransactionManager = RoomSyncTransactionManager(database),
        drafts: FakeDraftRepository = FakeDraftRepository(),
    ) = OfflineFirstJournalRepository(
        journalDao = database.journalDao(),
        remoteDataSource = FakeRemoteJournalDataSource(),
        draftRepository = drafts,
        syncMetadataService = FakeSyncMetadataService(),
        database = database,
        mergeTransactionManager = transactionManager,
        currentScope = { scope },
    )

    private fun journal(title: String) =
        JournalEntity(
            title = title,
            description = "Keep $title",
            created = Instant.fromEpochMilliseconds(1000),
            lastUpdated = Instant.fromEpochMilliseconds(2000),
            coverImageUri = "/covers/$title.jpg",
        )
}
