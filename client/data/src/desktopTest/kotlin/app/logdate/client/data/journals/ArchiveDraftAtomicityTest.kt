package app.logdate.client.data.journals

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class ArchiveDraftAtomicityTest {
    @Test
    fun `interruption after preferences commit repairs only the rolled back upload intent`() =
        runTest {
            val directory = Files.createTempDirectory("archive-draft-atomicity-")
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(directory.resolve("draft.db").toString()))
            try {
                val scope = JournalMergeScope("owner", "https://example.test")
                val provider =
                    object : CanonicalOwnerProvider {
                        override suspend fun getCanonicalOwnerId() = scope.ownerId

                        override suspend fun hasBoundOwner() = true
                    }
                val metadata =
                    DatabaseSyncMetadataService(
                        database.syncMetadataDao(),
                        DefaultLogDateConfigRepository(initialBackendUrl = scope.serverOrigin),
                        provider,
                    )
                val transactions = RoomSyncTransactionManager(database)
                val preferences =
                    object : DataStore<Preferences> {
                        override val data = MutableStateFlow(emptyPreferences())

                        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                            transform(data.value).also { data.value = it }
                    }
                val drafts =
                    LocalFirstDraftRepository(
                        DataStoreKeyValueStorage(preferences),
                        Json,
                        currentScope = { DraftStorageScope(scope.ownerId, scope.serverOrigin) },
                    )
                val interrupted =
                    object : SyncTransactionManager {
                        override suspend fun <T> withTransaction(block: suspend () -> T): T =
                            transactions.withTransaction {
                                block()
                                throw CancellationException("Process stops before Room commit")
                            }
                    }
                val first = JournalDraftPersistence(drafts, metadata, { it }, {}, interrupted)
                val recoveryScope = first.captureScope { scope }
                val draft = EditorDraft()
                val recoveryId = Uuid.random().toString()
                assertFailsWith<CancellationException> {
                    first.compareAndSet(draft.id, null, draft, recoveryScope, recoveryId = recoveryId)
                }
                assertEquals(draft, drafts.getDraftForRestore(draft.id))
                assertEquals(0, metadata.getPendingCount())
                val restarted = JournalDraftPersistence(drafts, metadata, { it }, {}, transactions)
                restarted.ensureRecoveryQueued(draft.id, recoveryScope)
                assertEquals(1, metadata.getPendingCount())
                assertFalse(
                    restarted.compareAndSet(draft.id, draft, draft.copy(blocks = emptyList()), recoveryScope, recoveryId = recoveryId),
                )
                assertEquals(draft, drafts.getDraftForRestore(draft.id))
                assertEquals(1, metadata.getPendingCount())
            } finally {
                database.close()
                directory.toFile().deleteRecursively()
            }
        }
}
