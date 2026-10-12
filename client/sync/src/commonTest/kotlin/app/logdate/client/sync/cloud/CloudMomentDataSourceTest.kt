package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.crypto.MarkedMomentPayloadCodec
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.MomentChange
import app.logdate.shared.model.sync.MomentChangesResponse
import app.logdate.shared.model.sync.MomentDeletion
import app.logdate.shared.model.sync.MomentUpdateRequest
import app.logdate.shared.model.sync.MomentUpdateResponse
import app.logdate.shared.model.sync.MomentUploadRequest
import app.logdate.shared.model.sync.VersionConstraint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class CloudMomentDataSourceTest {
    private val id = Uuid.parse("10000000-0000-0000-0000-000000000001")
    private val mark =
        MarkedMoment(
            id,
            Instant.parse("2026-10-12T12:00:00.123456789Z"),
            "UTC",
            null,
            null,
            MarkState.DISMISSED,
            "Private caption",
            emptySet(),
            "watch-origin",
            Instant.parse("2026-10-12T12:01:00.123456789Z"),
            7,
        )
    private val device = DeviceId("uploading-phone")

    @Test
    fun `create and update encrypt snapshots and retain precise clocks and version constraints`() =
        runTest {
            val codec = codec()
            val api = Api()
            val source = DefaultCloudMomentDataSource(api, codec)
            assertIs<ConditionalCreateResult.AlreadyExists>(source.createIfAbsent("token", mark, device).getOrThrow())
            val created = api.created!!
            assertEquals(device, created.deviceId)
            assertEquals(mark.capturedAt.toEpochMilliseconds(), created.capturedAt)
            assertEquals(mark, codec.decode(id, created.schema, 7, created.payload))
            assertEquals(8, source.update("token", mark, device).getOrThrow().serverVersion)
            assertEquals(VersionConstraint.Known(7), api.updated!!.versionConstraint)
            assertEquals(mark, codec.decode(id, 1, 7, api.updated!!.payload))
        }

    @Test
    fun `unversioned snapshots cannot issue unconditional updates`() =
        runTest {
            val api = Api()
            val source = DefaultCloudMomentDataSource(api, codec())
            assertTrue(source.update("token", mark.copy(syncVersion = 0), device).isFailure)
            assertEquals(null, api.updated)
        }

    @Test
    fun `read uses encrypted clocks rather than server write time and delete uses stable identity`() =
        runTest {
            val codec = codec()
            val api = Api().apply { single = change(codec.encode(mark)) }
            val source = DefaultCloudMomentDataSource(api, codec)
            assertEquals(mark.copy(syncVersion = 9), source.get("token", id).getOrThrow())
            source.delete("token", id).getOrThrow()
            assertEquals(id.toString(), api.deleted)
        }

    @Test
    fun `mixed pages preserve good records and durable failure identities`() =
        runTest {
            val codec = codec()
            val wrongAccount = codec("other")
            val corruptId = Uuid.parse("20000000-0000-0000-0000-000000000001")
            val futureId = Uuid.parse("30000000-0000-0000-0000-000000000001")
            val wrongId = Uuid.parse("40000000-0000-0000-0000-000000000001")
            val api =
                Api().apply {
                    page =
                        MomentChangesResponse(
                            listOf(
                                change(codec.encode(mark)),
                                change("LDSE2:broken").copy(id = corruptId.toString()),
                                change("LDSE2:future").copy(id = futureId.toString(), schema = 2),
                                change(wrongAccount.encode(mark.copy(id = wrongId))).copy(id = wrongId.toString()),
                            ),
                            listOf(MomentDeletion(corruptId.toString(), 10, 12)),
                            50,
                            true,
                        )
                }
            val result = DefaultCloudMomentDataSource(api, codec).changes("token", Instant.fromEpochMilliseconds(2), 4).getOrThrow()
            assertEquals(listOf(mark.copy(syncVersion = 9)), result.changes)
            assertEquals(listOf(corruptId), result.deletions)
            assertEquals(mapOf(corruptId to 12L), result.deletionVersions)
            assertEquals(mapOf(corruptId to Instant.fromEpochMilliseconds(10)), result.deletionTimestamps)
            assertEquals(listOf(wrongId), result.unreadable)
            assertEquals(mapOf(wrongId to 9L), result.unreadableVersions)
            assertEquals(
                setOf(DiagnosticReason.CORRUPT_PAYLOAD, DiagnosticReason.UNSUPPORTED_FORMAT),
                result.failures.map { it.reason }.toSet(),
            )
            assertEquals(Instant.fromEpochMilliseconds(50), result.lastSyncTimestamp)
            assertTrue(result.hasMore)
            assertEquals(2L to 4, api.query)
        }

    @Test
    fun `foreign single identities and inconsistent capture metadata are rejected`() =
        runTest {
            val codec = codec()
            val api = Api().apply { single = change(codec.encode(mark)).copy(id = Uuid.random().toString()) }
            val source = DefaultCloudMomentDataSource(api, codec)
            assertTrue(source.get("token", id).isFailure)
            api.single = change(codec.encode(mark)).copy(capturedAt = 0)
            assertTrue(source.get("token", id).isFailure)
        }

    @Test
    fun `cancelled pages propagate and bad deletion ids remain failed records`() =
        runTest {
            val api = Api().apply { pageFailure = CancellationException("cancel") }
            val source = DefaultCloudMomentDataSource(api, codec())
            assertFailsWith<CancellationException> { source.changes("token", Instant.fromEpochMilliseconds(0)) }
            api.pageFailure = null
            api.page = MomentChangesResponse(emptyList(), listOf(MomentDeletion("bad id", 1, 5)), 8)
            val page = source.changes("token", Instant.fromEpochMilliseconds(0)).getOrThrow()
            assertTrue(page.deletions.isEmpty())
            assertEquals("bad id", page.failures.single().entityId)
        }

    private fun change(payload: String) = MomentChange(id.toString(), mark.capturedAt.toEpochMilliseconds(), 9999, device, payload, 1, 9)

    private suspend fun codec(seed: String = "moments"): MarkedMomentPayloadCodec {
        val crypto = TestCryptoManager()
        val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
        identity.recoverIdentity((1..12).map { "$seed-$it" })
        return MarkedMomentPayloadCodec(
            SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto),
        )
    }

    private class Api : FakeCloudApiClient() {
        var created: MomentUploadRequest? = null
        var updated: MomentUpdateRequest? = null
        var single: MomentChange? = null
        var deleted: String? = null
        var query: Pair<Long, Int?>? = null
        var page = MomentChangesResponse(emptyList(), emptyList(), 0)
        var pageFailure: Throwable? = null

        override suspend fun createMomentIfAbsent(
            accessToken: String,
            moment: MomentUploadRequest,
        ): Result<ConditionalCreateResult> {
            created = moment
            return Result.success(ConditionalCreateResult.AlreadyExists(7))
        }

        override suspend fun updateMoment(
            accessToken: String,
            momentId: String,
            moment: MomentUpdateRequest,
        ): Result<MomentUpdateResponse> {
            updated = moment
            return Result.success(MomentUpdateResponse(momentId, 8, 20))
        }

        override suspend fun getMoment(
            accessToken: String,
            momentId: String,
        ): Result<MomentChange> = Result.success(single!!)

        override suspend fun deleteMoment(
            accessToken: String,
            momentId: String,
        ): Result<Unit> {
            deleted = momentId
            return Result.success(Unit)
        }

        override suspend fun getMomentChanges(
            accessToken: String,
            since: Long,
            limit: Int?,
        ): Result<MomentChangesResponse> {
            query = since to limit
            return pageFailure?.let { Result.failure(it) } ?: Result.success(page)
        }
    }
}
