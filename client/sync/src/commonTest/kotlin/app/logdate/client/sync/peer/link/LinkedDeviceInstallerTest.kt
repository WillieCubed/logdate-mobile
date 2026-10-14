package app.logdate.client.sync.peer.link

import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityMintPolicy
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [LinkedDeviceInstaller]: a phone's account bundle becomes this device's keys, session, owner
 * and server, every refusal is named, and a stop after the code is spent finishes on resume.
 */
class LinkedDeviceInstallerTest {
    private val crypto = TestCryptoManager()
    private val identity = IdentityKeyManager(InMemorySecureStorage(), crypto, mintPolicy = IdentityMintPolicy.NEVER)
    private val mediaKeys = MediaPayloadKeyProvider(InMemorySecureStorage(), crypto, identity, KeyDerivation(crypto))
    private val owner = FakeOwner(LOCAL_OWNER)
    private val config = DefaultLogDateConfigRepository(initialBackendUrl = DEFAULT_ORIGIN)
    private val vault = FakeVault()
    private val journal = InMemoryKeyValueStorage()
    private val moves = mutableListOf<Triple<OwnerScope, OwnerScope, String>>()
    private var moveFails = false
    private var persistenceFails = false
    private var persistenceCalls = 0
    private var redeemCalls = 0
    private var redeemResult: Result<DeviceLinkRedeemResponse> =
        Result.success(DeviceLinkRedeemResponse(ACCOUNT, "session", AccountTokens("access", "refresh")))
    private var nowMs = 1_000L
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
        }
    private val identityKey = ByteArray(32) { 0x31 }
    private val mediaKey = ByteArray(32) { 0x42 }

    private val installer =
        LinkedDeviceInstaller(
            journal = journal,
            identityKeys = identity,
            mediaKeys = mediaKeys,
            owner = owner,
            config = config,
            sessions = vault,
            redeemer = { _, _, _, _ ->
                redeemCalls++
                redeemResult
            },
            mover =
                OwnerRowsMover { from, to, device ->
                    check(!moveFails) { "The database is busy" }
                    moves += Triple(from, to, device)
                    0
                },
            deviceId = { "device" },
            deviceName = { "Pixel Watch 3" },
            clock = clock,
            persistConfiguration = {
                persistenceCalls++
                check(!persistenceFails) { "configuration was not saved" }
            },
        )

    @Test
    fun `an account bundle installs the keys the session the owner and the server`() =
        runTest {
            val outcome = installer.install(bundle())

            assertEquals(InstallOutcome.Installed(ACCOUNT, LINKED_ORIGIN, leftBehind = 0, sessionId = "session"), outcome)
            assertContentEquals(identityKey, identity.getIdentityKey())
            assertContentEquals(mediaKey, mediaKeys.getOrCreateKey())
            assertEquals(UserSession("access", "refresh", ACCOUNT), vault.sessions[LINKED_ORIGIN])
            assertEquals(ACCOUNT, owner.id)
            assertEquals(LINKED_ORIGIN, config.getCurrentBackendUrl().trimEnd('/'))
            assertEquals(listOf(Triple(OwnerScope(LOCAL_OWNER, DEFAULT_ORIGIN), OwnerScope(ACCOUNT, LINKED_ORIGIN), "device")), moves)
            assertTrue(journal.values.isEmpty())
        }

    @Test
    fun `an expired code is refused before anything changes`() =
        runTest {
            nowMs = 200_000

            assertEquals(InstallOutcome.Failed(LinkProblem.LINK_CODE_EXPIRED), installer.install(bundle()))
            assertFalse(identity.hasIdentityKey())
            assertTrue(vault.sessions.isEmpty())
        }

    @Test
    fun `a device that belongs to another account is refused`() =
        runTest {
            owner.bound = true

            assertEquals(LinkProblem.BOUND_TO_ANOTHER_ACCOUNT, installer.bindingProblem(bundle()))
            assertEquals(InstallOutcome.Failed(LinkProblem.BOUND_TO_ANOTHER_ACCOUNT), installer.install(bundle()))
            assertFalse(identity.hasIdentityKey())
        }

    @Test
    fun `a bundle without its keys is incomplete`() =
        runTest {
            assertEquals(LinkProblem.INCOMPLETE_BUNDLE, installer.bindingProblem(bundle().copy(identityKey = null)))
        }

    @Test
    fun `keys that do not match their fingerprint are refused`() =
        runTest {
            assertEquals(
                InstallOutcome.Failed(LinkProblem.KEY_CONFLICT),
                installer.install(bundle().copy(identityKeyFingerprint = "wrong")),
            )
            assertTrue(vault.sessions.isEmpty())
        }

    @Test
    fun `a spent code leaves the keys installed and asks for a new link`() =
        runTest {
            redeemResult = Result.failure(DeviceLinkApiException(400, DeviceLinkErrorCodes.LINK_CODE_INVALID))

            assertEquals(InstallOutcome.Failed(LinkProblem.LINK_CODE_INVALID), installer.install(bundle()))
            assertTrue(identity.hasIdentityKey())
            assertTrue(vault.sessions.isEmpty())
            assertEquals(LOCAL_OWNER, owner.id)
            assertTrue(journal.values.isEmpty())
        }

    @Test
    fun `an account with no room for another device is reported as such`() =
        runTest {
            redeemResult = Result.failure(DeviceLinkApiException(409, DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED))

            assertEquals(InstallOutcome.Failed(LinkProblem.DEVICE_LIMIT_REACHED), installer.install(bundle()))
        }

    @Test
    fun `no answer from the server is a network problem`() =
        runTest {
            redeemResult = Result.failure(IllegalStateException("offline"))

            assertEquals(InstallOutcome.Failed(LinkProblem.NETWORK), installer.install(bundle()))
        }

    @Test
    fun `an install that stopped after the session finishes on resume`() =
        runTest {
            moveFails = true
            assertEquals(InstallOutcome.Failed(LinkProblem.INTERRUPTED), installer.install(bundle()))
            assertTrue(journal.values.isNotEmpty())
            moveFails = false

            assertEquals(InstallOutcome.Installed(ACCOUNT, LINKED_ORIGIN, leftBehind = 0, sessionId = "session"), installer.resume())
            assertEquals(ACCOUNT, owner.id)
            assertTrue(journal.values.isEmpty())
        }

    @Test
    fun `an install that stopped before the session asks for a new link`() =
        runTest {
            journal.putString(
                "linked_device.install_journal.v1",
                """{"step":"KEYS_INSTALLED","accountId":"$ACCOUNT","origin":"$LINKED_ORIGIN",""" +
                    """"oldOwner":"$LOCAL_OWNER","oldOrigin":"$DEFAULT_ORIGIN"}""",
            )

            assertEquals(InstallOutcome.Failed(LinkProblem.INTERRUPTED), installer.resume())
            assertTrue(journal.values.isEmpty())
            assertEquals(LOCAL_OWNER, owner.id)
        }

    @Test
    fun `resume with nothing started does nothing`() =
        runTest {
            assertNull(installer.resume())
        }

    @Test
    fun `preflight refuses a mismatched fingerprint without installing keys`() =
        runTest {
            assertEquals(LinkProblem.KEY_CONFLICT, installer.bindingProblem(bundle().copy(identityKeyFingerprint = "wrong")))
            assertFalse(identity.hasIdentityKey())
            assertTrue(journal.values.isEmpty())
        }

    @Test
    fun `preflight refuses a different installed identity`() =
        runTest {
            val other = ByteArray(32) { 0x53 }
            identity.installLinkedIdentityKey(other, IdentityKeyFingerprint.of(other, crypto))
            assertEquals(LinkProblem.KEY_CONFLICT, installer.bindingProblem(bundle()))
            assertContentEquals(other, identity.getIdentityKey())
        }

    @Test
    fun `blank account origin code or fingerprint is incomplete`() =
        runTest {
            val invalid =
                listOf(
                    bundle().copy(accountId = ""),
                    bundle().copy(serverOrigin = "  "),
                    bundle().copy(linkCode = ""),
                    bundle().copy(identityKeyFingerprint = ""),
                )
            for (candidate in invalid) assertEquals(LinkProblem.INCOMPLETE_BUNDLE, installer.bindingProblem(candidate))
            assertFalse(identity.hasIdentityKey())
        }

    @Test
    fun `wrong length identity is refused at preflight`() =
        runTest {
            assertEquals(
                LinkProblem.KEY_CONFLICT,
                installer.bindingProblem(bundle().copy(identityKey = LinkWire.encodeKey(ByteArray(1)))),
            )
        }

    @Test
    fun `resume refuses missing saved session before moving captures`() =
        runTest {
            moveFails = true
            installer.install(bundle())
            moveFails = false
            vault.sessions.clear()
            assertEquals(InstallOutcome.Failed(LinkProblem.SESSION_REVOKED), installer.resume())
            assertTrue(moves.isEmpty())
            assertEquals(LOCAL_OWNER, owner.id)
        }

    @Test
    fun `resume refuses another accounts session without clearing it`() =
        runTest {
            moveFails = true
            installer.install(bundle())
            moveFails = false
            val other = UserSession("other", "other-refresh", "another-account")
            vault.sessions[LINKED_ORIGIN] = other
            assertEquals(InstallOutcome.Failed(LinkProblem.SESSION_REVOKED), installer.resume())
            assertEquals(other, vault.sessions[LINKED_ORIGIN])
            assertTrue(moves.isEmpty())
        }

    @Test
    fun `resume refuses an owner changed since setup before moving captures`() =
        runTest {
            moveFails = true
            installer.install(bundle())
            moveFails = false
            owner.id = "another-account"
            owner.bound = true
            assertEquals(InstallOutcome.Failed(LinkProblem.BOUND_TO_ANOTHER_ACCOUNT), installer.resume())
            assertTrue(moves.isEmpty())
            assertEquals("another-account", owner.id)
        }

    @Test
    fun `a saved session before journal advancement resumes without spending another code`() =
        runTest {
            vault.stopAfterWrite = true
            assertFailsWith<CancellationException> { installer.install(bundle()) }
            vault.stopAfterWrite = false
            assertEquals(InstallOutcome.Installed(ACCOUNT, LINKED_ORIGIN, 0), installer.resume())
            assertEquals(ACCOUNT, owner.id)
        }

    @Test
    fun `another install finishes the pending adoption before replacing its journal`() =
        runTest {
            moveFails = true
            installer.install(bundle())
            moveFails = false
            assertEquals(InstallOutcome.Installed(ACCOUNT, LINKED_ORIGIN, 0, sessionId = "session"), installer.install(bundle()))
            assertEquals(1, redeemCalls)
            assertEquals(1, moves.size)
        }

    @Test
    fun `empty session tokens are refused before moving captures`() =
        runTest {
            redeemResult = Result.success(DeviceLinkRedeemResponse(ACCOUNT, "session", AccountTokens("", "")))
            assertEquals(InstallOutcome.Failed(LinkProblem.SERVER_REFUSED), installer.install(bundle()))
            assertTrue(vault.sessions.isEmpty())
            assertTrue(moves.isEmpty())
        }

    @Test
    fun `cancellation returned by redeemer preserves recovery journal`() =
        runTest {
            redeemResult = Result.failure(CancellationException("stopped"))
            assertFailsWith<CancellationException> { installer.install(bundle()) }
            assertTrue(journal.values.isNotEmpty())
        }

    @Test
    fun `finishing pending setup cannot report success for a different account bundle`() =
        runTest {
            moveFails = true
            installer.install(bundle())
            moveFails = false
            assertEquals(
                InstallOutcome.Failed(LinkProblem.BOUND_TO_ANOTHER_ACCOUNT),
                installer.install(bundle().copy(accountId = "another-account")),
            )
            assertEquals(ACCOUNT, owner.id)
            assertEquals(1, redeemCalls)
        }

    @Test
    fun `setup keeps its journal until configuration reaches durable storage`() =
        runTest {
            persistenceFails = true
            assertFailsWith<IllegalStateException> { installer.install(bundle()) }
            assertTrue(journal.values.isNotEmpty())
            persistenceFails = false
            assertEquals(InstallOutcome.Installed(ACCOUNT, LINKED_ORIGIN, 0, sessionId = "session"), installer.resume())
            assertEquals(2, persistenceCalls)
            assertEquals(1, moves.size)
            assertTrue(journal.values.isEmpty())
        }

    private fun bundle() =
        LinkBundle(
            mode = LinkMode.CLOUD,
            accountId = ACCOUNT,
            serverOrigin = "$LINKED_ORIGIN/",
            linkCode = "code",
            linkCodeExpiresAt = 100_000,
            identityKey = LinkWire.encodeKey(identityKey),
            identityKeyFingerprint = IdentityKeyFingerprint.of(identityKey, crypto),
            mediaKey = LinkWire.encodeKey(mediaKey),
        )

    private class FakeOwner(
        var id: String,
    ) : CanonicalOwnerProvider {
        var bound = false

        override suspend fun getCanonicalOwnerId(): String = id

        override suspend fun hasBoundOwner(): Boolean = bound

        override suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean {
            if (bound) return id == remoteOwnerId
            id = remoteOwnerId
            bound = true
            return true
        }
    }

    private class FakeVault : OriginSessionVault {
        val sessions = mutableMapOf<String, UserSession>()
        var stopAfterWrite = false

        override suspend fun read(origin: String): UserSession? = sessions[origin]

        override suspend fun write(
            origin: String,
            session: UserSession,
        ) {
            sessions[origin] = session
            if (stopAfterWrite) throw CancellationException("process stopped after session save")
        }

        override suspend fun clear(origin: String) {
            sessions.remove(origin)
        }
    }

    private companion object {
        const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"
        const val LOCAL_OWNER = "5f0c3a52-0000-4000-8000-000000000002"
        const val DEFAULT_ORIGIN = "https://default.test"
        const val LINKED_ORIGIN = "https://linked.test"
    }
}
