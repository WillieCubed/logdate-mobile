package app.logdate.client.sync.location

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.ExistingCanonicalOwner
import app.logdate.client.networking.LocationHistoryApiClientContract
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import app.logdate.shared.model.sync.LocationHistoryRecord
import app.logdate.shared.model.sync.LocationHistoryUpload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationOwnerCasingTest {
    private val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
    private val origin = "https://server.test"

    @Test
    fun `history keeps canonical row ownership and the existing session-derived ciphertext binding`() =
        runTest {
            val f = Fixture()
            f.prepare()
            f.rows.items += HistoryRecord("one", "observation", "retained payload", "separate-device")
            assertTrue(f.engine.upload("access").success)
            assertEquals(1, f.uploads.size)
            assertEquals(
                "retained payload",
                f.cipher.decryptString(
                    "location-history:$origin:${raw.lowercase()}:observation:one:v1",
                    f.uploads.single().payload!!,
                ),
            )
            assertEquals(listOf(raw to origin), f.staged)
            f.rows.items.clear()
            // Independently seed the pre-repair wire field ID; a new-only roundtrip would hide AAD changes.
            val oldWire = f.cipher.encryptString("location-history:$origin:${raw.lowercase()}:observation:old:v1", "old wire payload")
            f.page =
                LocationHistoryChangesResponse(
                    listOf(
                        LocationHistoryRecord("old", "observation", oldWire, 1, "other-device", 1, 1, false),
                    ),
                    1,
                    false,
                )
            assertTrue(f.engine.download("access").success)
            assertEquals(raw, f.rows.lastOwner)
            assertEquals(
                "old wire payload",
                f.rows.items
                    .single()
                    .payload,
            )
            assertEquals(raw, f.owner.rawId)
        }

    @Test
    fun `history refuses a changed raw owner before acknowledging an upload`() =
        runTest {
            val f = Fixture()
            f.prepare()
            f.rows.items += HistoryRecord("one", "observation", "retained payload", "separate-device")
            f.afterUpload = { f.owner = ExistingCanonicalOwner(raw.lowercase(), true) }
            assertFalse(f.engine.upload("access").success)
            assertTrue(
                f.rows.items
                    .single()
                    .dirty,
            )
        }

    @Test
    fun `fresh signup can sync its server-confirmed unbound local owner without claiming or rewriting it`() =
        runTest {
            val f = Fixture()
            f.owner = f.owner.copy(isBound = false)
            f.prepare()
            f.rows.items += HistoryRecord("one", "observation", "signup payload", "separate-device")
            assertTrue(f.engine.upload("access").success)
            assertEquals(1, f.uploads.size)
            assertEquals(listOf(raw to origin), f.staged)
            assertEquals(ExistingCanonicalOwner(raw, false), f.owner)
        }

    @Test
    fun `unbound local history cannot be read through another authenticated account`() =
        runTest {
            val f = Fixture()
            f.owner = ExistingCanonicalOwner("b930bf49-d1e9-4a65-b84b-8e74af45012b", false)
            f.prepare()
            f.rows.items += HistoryRecord("one", "observation", "local payload", "separate-device")
            assertFalse(f.engine.upload("access").success)
            assertTrue(f.uploads.isEmpty())
            assertTrue(f.staged.isEmpty())
            assertTrue(
                f.rows.items
                    .single()
                    .dirty,
            )
        }

    private inner class Fixture {
        val crypto = TestCryptoManager()
        val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
        val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
        val sessions = FakeSessionStorage().apply { currentOrigin = origin }
        var owner = ExistingCanonicalOwner(raw, true)
        val provider =
            object : CanonicalOwnerProvider {
                override suspend fun readExistingOwner() = owner

                override suspend fun getCanonicalOwnerId(): String = error("Sync must not mint an owner")

                override suspend fun hasBoundOwner() = owner.isBound
            }
        val rows = MemoryHistoryStore()
        val scoped =
            object : HistoryRecordStore by rows {
                override suspend fun pending(
                    ownerId: String,
                    origin: String,
                    limit: Int,
                ) = if (ownerId == raw) rows.pending(ownerId, origin, limit) else emptyList()
            }
        val uploads = mutableListOf<LocationHistoryUpload>()
        val staged = mutableListOf<Pair<String, String>>()
        var afterUpload: suspend () -> Unit = {}
        var page = LocationHistoryChangesResponse(emptyList(), 0, false)
        val api =
            object : LocationHistoryApiClientContract {
                override suspend fun upload(
                    accessToken: String,
                    records: List<LocationHistoryUpload>,
                ): List<LocationHistoryRecord> {
                    uploads += records
                    afterUpload()
                    return records.map {
                        LocationHistoryRecord(
                            it.id,
                            it.recordType,
                            it.payload,
                            it.payloadSchemaVersion,
                            it.deviceId,
                            it.deviceVersion,
                            1,
                            it.deleted,
                        )
                    }
                }

                override suspend fun changes(
                    accessToken: String,
                    since: Long,
                    limit: Int,
                ) = page
            }
        val engine =
            LocationHistorySyncEngine(
                { api },
                scoped,
                cipher,
                sessions,
                DefaultLogDateConfigRepository(initialBackendUrl = origin),
                { true },
                prepare = { owner, origin ->
                    staged += owner to origin
                    false
                },
                canonicalOwner = provider,
            )

        suspend fun prepare() {
            identity.setupNewIdentity()
            sessions.saveSession(UserSession("access", "refresh", raw.lowercase()))
        }
    }
}
