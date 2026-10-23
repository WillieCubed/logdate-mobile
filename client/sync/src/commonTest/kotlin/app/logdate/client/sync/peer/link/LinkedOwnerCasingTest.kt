package app.logdate.client.sync.peer.link

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityMintPolicy
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.identity.DefaultCanonicalOwnerProvider
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class LinkedOwnerCasingTest {
    @Test
    fun `same-origin relink preserves retained raw ownership and key custody`() =
        runTest {
            val f = Fixture(ORIGIN)
            f.prepare()
            assertNull(f.installer.bindingProblem(f.bundle))
            assertIs<InstallOutcome.Installed>(f.installer.install(f.bundle))
            f.assertRetained()
            for ((from, to) in f.moves) {
                assertEquals(OwnerScope(RAW, ORIGIN), from)
                assertEquals(from, to)
            }
        }

    @Test
    fun `changed-origin relink moves rows to the retained canonical spelling`() =
        runTest {
            val f = Fixture("https://old.test")
            f.prepare()
            assertIs<InstallOutcome.Installed>(f.installer.install(f.bundle))
            assertEquals(listOf(OwnerScope(RAW, "https://old.test") to OwnerScope(RAW, ORIGIN)), f.moves)
            f.assertRetained()
        }

    @Test
    fun `interrupted link preserves recorded raw row target across session casing`() =
        runTest {
            val f = Fixture("https://old.test")
            f.prepare()
            f.failPersistence = true
            assertFailsWith<IllegalStateException> { f.installer.install(f.bundle) }
            assertEquals(1, f.moves.size)
            f.failPersistence = false
            f.session = f.session!!.copy(accountId = RAW)
            assertIs<InstallOutcome.Installed>(f.installer.resume())
            assertEquals(1, f.moves.size)
            f.assertRetained()
            assertEquals(1, f.redeems)
        }

    private class Fixture(
        oldOrigin: String,
    ) {
        val journal = InMemoryKeyValueStorage()
        private val ownerStore = InMemoryKeyValueStorage()
        private val atomicOwner =
            object : KeyValueStorage by ownerStore {
                override suspend fun getStringsOrThrow(keys: Set<String>) = keys.associateWith { ownerStore.values[it] }

                override suspend fun compareAndSetStringsOrThrow(
                    expected: Map<String, String?>,
                    values: Map<String, String?>,
                ): Boolean {
                    if (expected.any { (key, value) -> ownerStore.values[key] != value }) return false
                    for ((key, value) in values) {
                        if (value == null) ownerStore.values.remove(key) else ownerStore.values[key] = value
                    }
                    return true
                }
            }
        val owner = DefaultCanonicalOwnerProvider(atomicOwner)
        val secure = InMemorySecureStorage()
        val crypto = TestCryptoManager()
        val identity = IdentityKeyManager(secure, crypto, mintPolicy = IdentityMintPolicy.NEVER)
        val media = MediaPayloadKeyProvider(secure, crypto, identity, KeyDerivation(crypto))
        val config = DefaultLogDateConfigRepository(initialBackendUrl = oldOrigin)
        val moves = mutableListOf<Pair<OwnerScope, OwnerScope>>()
        var session: UserSession? = null
        var failPersistence = false
        var redeems = 0
        val root = ByteArray(32) { 5 }
        val mediaKey = ByteArray(32) { 9 }
        val bundle =
            LinkBundle(
                mode = LinkMode.CLOUD,
                accountId = RAW.lowercase(),
                serverOrigin = ORIGIN,
                linkCode = "synthetic-code",
                linkCodeExpiresAt = 100_000,
                identityKey = LinkWire.encodeKey(root),
                identityKeyFingerprint = IdentityKeyFingerprint.of(root, crypto),
                mediaKey = LinkWire.encodeKey(mediaKey),
            )
        val installer =
            LinkedDeviceInstaller(
                journal,
                identity,
                media,
                owner,
                config,
                object : OriginSessionVault {
                    override suspend fun read(origin: String) = session

                    override suspend fun write(
                        origin: String,
                        session: UserSession,
                    ) {
                        this@Fixture.session = session
                    }

                    override suspend fun clear(origin: String) {
                        session = null
                    }
                },
                { _, _, _, _ ->
                    redeems++
                    Result.success(DeviceLinkRedeemResponse(RAW.lowercase(), "session", AccountTokens("a", "r")))
                },
                OwnerRowsMover { from, to, _ ->
                    moves += from to to
                    0
                },
                { "separate-device" },
                { "Replacement watch" },
                object : Clock {
                    override fun now() = Instant.fromEpochMilliseconds(1_000)
                },
                { check(!failPersistence) { "Interrupted durable config" } },
            )

        suspend fun prepare() {
            ownerStore.values["identity.canonical_owner_id"] = RAW
            ownerStore.values["identity.canonical_owner_bound"] = "true"
            identity.installAccountKey(RAW, root, mediaKey)
        }

        suspend fun assertRetained() {
            assertEquals(RAW, owner.getCanonicalOwnerId())
            assertEquals(RAW, secure.getString("identity_account_id_v1"))
            assertContentEquals(root, identity.getIdentityKey())
            assertContentEquals(mediaKey, media.getOrCreateKey())
            assertEquals(ORIGIN, config.getCurrentBackendUrl().trimEnd('/'))
            assertEquals(emptyMap(), journal.values)
        }
    }

    private companion object {
        const val RAW = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
        const val ORIGIN = "https://linked.test"
    }
}
