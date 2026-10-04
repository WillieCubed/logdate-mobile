package app.logdate.client.sync.recovery

import app.logdate.client.database.entities.sync.DownloadCheckpointEntity
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.KeyValueFirstSyncEnqueueStore
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.ContentChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacySyncAuditTest {
    @Test
    fun `legacy scan rewinds this scope once across manager recreation without losing queued work`() =
        runTest {
            val db = DownloadInboxTest.Database()
            db.checkpoint(DownloadCheckpointEntity("other-owner", "other-origin", "NOTE", 700))
            val inbox = DownloadInbox(db, db, { DownloadScope("owner", "origin") }, { 1L })
            inbox.stage("NOTE", 500, listOf(WireDownload("saved", 1, false, "saved encrypted wire")))
            val identity = IdentityKeyManager(InMemorySecureStorage(), TestCryptoManager())
            identity.recoverIdentity((1..12).map { "word-$it" })
            val backing = InMemoryKeyValueStorage()
            val storage =
                object : KeyValueStorage by backing {
                    override suspend fun getBoolean(
                        key: String,
                        defaultValue: Boolean,
                    ): Boolean = backing.getString(key)?.toBooleanStrictOrNull() ?: defaultValue

                    override suspend fun putBoolean(
                        key: String,
                        value: Boolean,
                    ) = backing.putString(key, value.toString())
                }
            val cursors = mutableListOf<Long>()
            val api =
                object : CloudApiClient by fakeCloudApiClient() {
                    override suspend fun getContentChanges(
                        accessToken: String,
                        since: Long,
                        limit: Int?,
                    ): Result<ContentChangesResponse> {
                        cursors += since
                        return Result.success(ContentChangesResponse(emptyList(), emptyList(), 600))
                    }
                }

            fun manager() =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    identityKeyManager = identity,
                    firstSyncEnqueueStore = KeyValueFirstSyncEnqueueStore(storage),
                    downloadInbox = inbox,
                    syncScope = backgroundScope,
                )

            manager().downloadRemoteChanges()
            inbox.stage("NOTE", 600, emptyList())
            manager().downloadRemoteChanges()

            assertEquals(listOf(0L, 600L), cursors)
            assertEquals("saved encrypted wire", inbox.pending(EntityType.NOTE.name).single().payload)
            assertEquals(700L, db.checkpoint("other-owner", "other-origin", "NOTE")?.cursor)
        }
}
