package app.logdate.client.data.journals

import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RecoveryOutboxScopeTest {
    @Test
    fun `waiting recovery enqueue keeps its captured owner and server after a scope change`() =
        runTest {
            val file = Files.createTempFile("recovery-outbox", ".db")
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
            try {
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                var pause = true
                var owner = "owner-a"
                val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://one.example")
                val provider =
                    object : CanonicalOwnerProvider {
                        override suspend fun getCanonicalOwnerId(): String {
                            if (pause) {
                                pause = false
                                entered.complete(Unit)
                                release.await()
                            }
                            return owner
                        }

                        override suspend fun hasBoundOwner(): Boolean = true
                    }
                val metadata = DatabaseSyncMetadataService(database.syncMetadataDao(), config, provider)
                val blocking = async { metadata.enqueuePending("blocking-operation", EntityType.DRAFT, PendingOperation.UPDATE) }
                entered.await()
                val captured = UploadScope("owner-a", "https://one.example")
                val recovery =
                    async { metadata.enqueuePendingForScope("recovered-draft", EntityType.DRAFT, PendingOperation.DELETE, captured) }
                runCurrent()
                owner = "owner-b"
                config.updateBackendUrl("https://two.example")
                release.complete(Unit)
                blocking.await()
                recovery.await()
                metadata.enqueueRepairForScopeIfAbsent("recovered-draft", EntityType.DRAFT, captured)
                assertEquals(null, database.syncMetadataDao().getPending("owner-b", "https://two.example", "DRAFT", "recovered-draft"))
                assertEquals(
                    "DELETE",
                    database.syncMetadataDao().getPending("owner-a", "https://one.example", "DRAFT", "recovered-draft")?.operation,
                )
                metadata.enqueueRepairForScopeIfAbsent("replay-repair", EntityType.DRAFT, captured)
                assertEquals(null, database.syncMetadataDao().getPending("owner-b", "https://two.example", "DRAFT", "replay-repair"))
                assertEquals(
                    "UPDATE",
                    database.syncMetadataDao().getPending("owner-a", "https://one.example", "DRAFT", "replay-repair")?.operation,
                )
            } finally {
                database.close()
                Files.deleteIfExists(file)
            }
        }
}
