package app.logdate.client.sync.recovery

import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.dao.sync.DownloadInboxDao
import app.logdate.client.database.entities.sync.DownloadCheckpointEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.cloud.DefaultCloudMomentDataSource
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.crypto.MarkedMomentPayloadCodec
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.MomentChange
import app.logdate.shared.model.sync.MomentChangesResponse
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomMomentDownloadInboxTest {
    @Test
    fun `encrypted moments remain recoverable across database restart and wrong identity decoding`() =
        runTest {
            val path = Files.createTempFile("logdate-moment-inbox", ".db")
            Files.delete(path)

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            var database = open()
            try {
                val scope = { DownloadScope("owner", "server") }

                fun inbox() = DownloadInbox(database.downloadInboxDao(), RoomSyncTransactionManager(database), scope, { 1000 })
                val codec = codec()
                val at = Instant.fromEpochMilliseconds(1)
                val mark =
                    MarkedMoment(Uuid.random(), at, "UTC", null, 72, MarkState.OPEN, "Private capture", emptySet(), "private-watch", at, 7)
                var page =
                    MomentChangesResponse(
                        listOf(MomentChange(mark.id.toString(), 1, 99, DeviceId("watch"), codec.encode(mark), 1, 7)),
                        emptyList(),
                        7,
                    )
                val api =
                    object : FakeCloudApiClient() {
                        override suspend fun getMomentChanges(
                            accessToken: String,
                            since: Long,
                            limit: Int?,
                        ) = Result.success(page)
                    }
                val first = inbox()
                DurableCloudApiClient(api, first).getMomentChanges("token", 0).getOrThrow()
                assertEquals(7L, first.cursor("MOMENT"))
                database.close()
                database = open()
                page = MomentChangesResponse(emptyList(), emptyList(), 7)
                val retained = inbox()
                val durable = DurableCloudApiClient(api, retained)
                val wrongReader = DefaultCloudMomentDataSource(durable, codec())
                val unreadable = wrongReader.changes("token", at).getOrThrow()
                assertEquals(listOf(mark.id), unreadable.unreadable)
                assertTrue(unreadable.changes.isEmpty())
                val row = retained.pending("MOMENT").single()
                assertEquals("WAITING", row.state)
                assertTrue(row.payload.contains("LDSE2:"))
                assertTrue(!row.payload.contains(mark.caption))
                assertTrue(!row.payload.contains(mark.originDeviceId))
                assertEquals(listOf(mark), DefaultCloudMomentDataSource(durable, codec).changes("token", at).getOrThrow().changes)
                retained.applied("MOMENT", mark.id.toString(), 6)
                assertEquals(1, retained.pending("MOMENT").size)
                retained.applied("MOMENT", mark.id.toString(), 7)
                database.close()
                database = open()
                assertTrue(inbox().pending("MOMENT").isEmpty())
                assertEquals(7L, inbox().cursor("MOMENT"))
            } finally {
                database.close()
                cleanup(path)
            }
        }

    @Test
    fun `interruption between moment insert and checkpoint rolls back both on disk`() =
        runTest {
            val path = Files.createTempFile("logdate-moment-rollback", ".db")
            Files.delete(path)

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            var database = open()
            try {
                val dao = database.downloadInboxDao()
                val interrupted =
                    object : DownloadInboxDao by dao {
                        override suspend fun checkpoint(row: DownloadCheckpointEntity) = error("Simulated interruption before checkpoint")
                    }
                val selected = { DownloadScope("owner", "server") }
                val inbox = DownloadInbox(interrupted, RoomSyncTransactionManager(database), selected, { 1000 })
                assertFailsWith<IllegalStateException> {
                    inbox.stage(
                        "MOMENT",
                        7,
                        listOf(WireDownload("capture", 7, false, "LDSE2:opaque")),
                    )
                }
                database.close()
                database = open()
                val restarted = DownloadInbox(database.downloadInboxDao(), RoomSyncTransactionManager(database), selected, { 1000 })
                assertEquals(0L, restarted.cursor("MOMENT"))
                assertTrue(restarted.pending("MOMENT").isEmpty())
            } finally {
                database.close()
                cleanup(path)
            }
        }

    private suspend fun codec(): MarkedMomentPayloadCodec {
        val crypto = DesktopCryptoManager()
        val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
        identity.setupNewIdentity()
        return MarkedMomentPayloadCodec(
            SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto),
        )
    }

    private fun cleanup(path: Path) {
        Files.deleteIfExists(path)
        Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
        Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
    }
}
