package app.logdate.server.identity

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.crypto.EcCurve
import studio.hypertext.atproto.crypto.EcKeySupport
import studio.hypertext.atproto.plc.PlcEncoding
import studio.hypertext.atproto.plc.PlcOperation
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Recovery keys on a server that keeps its own operation log and publishes nothing to the PLC
 * directory, which is how production runs. Registration puts the person's key first, and recovery
 * replaces the server's key only with the person's signature.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)
class RecoveryKeyLifecycleTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `registration chains on the hosted log and lists the recovery key ahead of the server key`() =
        runTest {
            val fixture = Fixture()
            val account = fixture.newAccount()
            val genesis = fixture.operations(account).single()

            val registered = fixture.identity.registerPlcRecoveryKey(account, fixture.recoveryDidKey)

            val stored = fixture.operations(account)
            val update = stored.last()
            assertEquals(2, stored.size)
            assertEquals(PlcEncoding.operationCid(genesis), update.prev)
            assertEquals(listOf(fixture.recoveryDidKey, "did:key:${registered.account.signingKeyPublic}"), update.rotationKeys)
            assertEquals(
                PlcEncoding.operationCid(update),
                fixture.operationRepository
                    .listByDid(registered.account.did!!)
                    .last()
                    .cid,
            )
            assertEquals(fixture.recoveryDidKey, registered.account.plcRecoveryDidKey)
        }

    @Test
    fun `registering the same key again changes nothing and a different key is refused`() =
        runTest {
            val fixture = Fixture()
            val account = fixture.newAccount()
            fixture.identity.registerPlcRecoveryKey(account, fixture.recoveryDidKey)
            val registeredAccount = fixture.accounts.findById(account.id)!!

            val repeated = fixture.identity.registerPlcRecoveryKey(registeredAccount, fixture.recoveryDidKey)
            val other = didKeyFor(fixture.signingKeys.generateKeyPair(EcCurve.P256).publicKeyMultibase)
            val refused =
                assertFailsWith<IdentityLifecycleConflictException> {
                    fixture.identity.registerPlcRecoveryKey(registeredAccount, other)
                }

            assertNull(repeated.plcOperation)
            assertEquals(AtprotoIdentityService.RECOVERY_KEY_ALREADY_SET, refused.code)
            assertEquals(2, fixture.operations(account).size)
        }

    @Test
    fun `registration is refused when the server does not support recovery keys`() =
        runTest {
            val fixture = Fixture(recoveryKeysEnabled = false)
            val account = fixture.newAccount()

            val refused =
                assertFailsWith<IdentityLifecycleConflictException> {
                    fixture.identity.registerPlcRecoveryKey(account, fixture.recoveryDidKey)
                }

            assertEquals(PlcIdentityService.RECOVERY_KEYS_UNAVAILABLE, refused.code)
            assertEquals(1, fixture.operations(account).size)
        }

    @Test
    fun `recovery replaces the server key with one approved by the recovery key`() =
        runTest {
            val fixture = Fixture()
            val account = fixture.newAccount()
            fixture.identity.registerPlcRecoveryKey(account, fixture.recoveryDidKey)
            val registration = fixture.operations(account).last()
            val oldServerKey = fixture.signingKeys.ensureActiveKey(account.id)

            val prepared = fixture.identity.prepareRecovery(fixture.accounts.findById(account.id)!!)
            val recovered =
                fixture.identity.completeRecovery(
                    account = fixture.accounts.findById(account.id)!!,
                    preparedKey = prepared.preparedKey,
                    signature = fixture.signWithRecoveryKey(prepared.signingPayloadBase64Url),
                )

            val recovery = fixture.operations(account).last()
            val newServerKey = fixture.signingKeys.ensureActiveKey(account.id)
            assertEquals(PlcEncoding.operationCid(registration), recovery.prev)
            assertEquals(listOf(fixture.recoveryDidKey, prepared.nextSigningKeyDidKey), recovery.rotationKeys)
            assertEquals(prepared.nextSigningKeyDidKey, recovery.verificationMethods.getValue("atproto"))
            assertTrue(didKeyFor(oldServerKey.publicKeyMultibase) !in recovery.rotationKeys)
            assertNotEquals(oldServerKey.publicKeyMultibase, newServerKey.publicKeyMultibase)
            assertEquals(prepared.nextSigningKeyDidKey, didKeyFor(newServerKey.publicKeyMultibase))
            assertEquals(newServerKey.publicKeyMultibase, recovered.account.signingKeyPublic)
            assertTrue(
                EcKeySupport.verifySha256(
                    publicKey = EcKeySupport.decodePublicKey(fixture.recoveryDidKey.removePrefix("did:key:")).publicKey,
                    curve = EcCurve.P256,
                    payload = PlcEncoding.encodeUnsigned(recovery.unsigned()),
                    signature = decodeBase64Url(requireNotNull(recovery.sig)),
                ),
            )
        }

    @Test
    fun `recovery needs a registered recovery key`() =
        runTest {
            val fixture = Fixture()
            val account = fixture.newAccount()

            val refused = assertFailsWith<IdentityLifecycleConflictException> { fixture.identity.prepareRecovery(account) }

            assertEquals(AtprotoIdentityService.RECOVERY_KEY_NOT_SET, refused.code)
        }

    @Test
    fun `a signature from any other key is refused and nothing changes`() =
        runTest {
            val fixture = Fixture()
            val account = fixture.registeredAccount()
            val prepared = fixture.identity.prepareRecovery(account)
            val stranger = fixture.signingKeys.generateKeyPair(EcCurve.P256)

            val refused =
                assertFailsWith<IdentityLifecycleValidationException> {
                    fixture.identity.completeRecovery(
                        account = account,
                        preparedKey = prepared.preparedKey,
                        signature = sign(stranger.privateKeyPkcs8, prepared.signingPayloadBase64Url),
                    )
                }

            assertEquals(PlcIdentityService.RECOVERY_SIGNATURE_INVALID, refused.code)
            assertEquals(2, fixture.operations(account).size)
        }

    @Test
    fun `a recovery prepared before another change is refused as stale`() =
        runTest {
            val fixture = Fixture()
            val account = fixture.registeredAccount()
            val first = fixture.identity.prepareRecovery(account)
            val second = fixture.identity.prepareRecovery(account)
            fixture.identity.completeRecovery(account, first.preparedKey, fixture.signWithRecoveryKey(first.signingPayloadBase64Url))

            val refused =
                assertFailsWith<IdentityLifecycleConflictException> {
                    fixture.identity.completeRecovery(
                        account = fixture.accounts.findById(account.id)!!,
                        preparedKey = second.preparedKey,
                        signature = fixture.signWithRecoveryKey(second.signingPayloadBase64Url),
                    )
                }

            assertEquals(PlcIdentityService.IDENTITY_CHANGED, refused.code)
            assertEquals(3, fixture.operations(account).size)
        }

    @Test
    fun `a prepared key from another account or an altered one is refused`() =
        runTest {
            val fixture = Fixture()
            val alice = fixture.registeredAccount("alice")
            val bob = fixture.registeredAccount("bob")
            val bobs = fixture.identity.prepareRecovery(bob)

            val crossAccount =
                assertFailsWith<IdentityLifecycleValidationException> {
                    fixture.identity.completeRecovery(alice, bobs.preparedKey, fixture.signWithRecoveryKey(bobs.signingPayloadBase64Url))
                }
            val altered =
                assertFailsWith<IdentityLifecycleValidationException> {
                    fixture.identity.completeRecovery(
                        bob,
                        tamper(bobs.preparedKey),
                        fixture.signWithRecoveryKey(bobs.signingPayloadBase64Url),
                    )
                }

            assertEquals(SigningKeyService.PREPARED_KEY_INVALID, crossAccount.code)
            assertEquals(SigningKeyService.PREPARED_KEY_INVALID, altered.code)
        }

    @Test
    fun `a prepared key expires`() =
        runTest {
            val clock = MutableClock(Instant.parse("2026-10-19T10:00:00Z"))
            val fixture = Fixture(clock = clock)
            val account = fixture.registeredAccount()
            val prepared = fixture.identity.prepareRecovery(account)
            clock.now = clock.now + SigningKeyService.PREPARED_KEY_LIFETIME + 1.minutes

            val refused =
                assertFailsWith<IdentityLifecycleConflictException> {
                    fixture.identity.completeRecovery(
                        account,
                        prepared.preparedKey,
                        fixture.signWithRecoveryKey(prepared.signingPayloadBase64Url),
                    )
                }

            assertEquals(SigningKeyService.PREPARED_KEY_EXPIRED, refused.code)
        }

    @Test
    fun `existing rows without a cid are chained by their computed cid`() =
        runTest {
            val fixture = Fixture()
            val account = fixture.newAccount()
            val genesisRow = fixture.operationRepository.listByAccountId(account.id).single()
            fixture.operationRepository.save(genesisRow.copy(cid = null))

            fixture.identity.registerPlcRecoveryKey(account, fixture.recoveryDidKey)

            assertEquals(PlcEncoding.operationCid(fixture.operations(account).first()), fixture.operations(account).last().prev)
        }

    private inner class Fixture(
        recoveryKeysEnabled: Boolean = true,
        clock: Clock = Clock.System,
    ) {
        val accounts = InMemoryAccountRepository()
        val signingKeys = SigningKeyService(InMemorySigningKeyRepository(), "test-kek", clock = clock)
        val operationRepository = InMemoryHostedPlcOperationRepository()
        private val config =
            AtprotoIdentityConfig(
                handleDomain = "logdate.app",
                pdsServiceEndpoint = "https://logdate.app",
                hostedAccountDidMethod = HostedAccountDidMethod.PLC,
                recoveryKeysEnabled = recoveryKeysEnabled,
            )
        val identity =
            AtprotoIdentityService(
                accountRepository = accounts,
                signingKeyService = signingKeys,
                config = config,
                plcIdentityService =
                    PlcIdentityService(
                        signingKeyService = signingKeys,
                        config = config,
                        hostedPlcOperationRepository = operationRepository,
                    ),
            )
        private val recoveryKey = signingKeys.generateKeyPair(EcCurve.P256)
        val recoveryDidKey = didKeyFor(recoveryKey.publicKeyMultibase)

        suspend fun newAccount(username: String = "alice"): Account =
            identity.ensureIdentity(
                accounts.save(
                    Account(
                        id = Uuid.random(),
                        username = username,
                        displayName = username,
                        createdAt = Clock.System.now(),
                    ),
                ),
            )

        suspend fun registeredAccount(username: String = "alice"): Account =
            identity.registerPlcRecoveryKey(newAccount(username), recoveryDidKey).account

        suspend fun operations(account: Account): List<PlcOperation> =
            operationRepository
                .listByDid(requireNotNull(accounts.findById(account.id)?.did))
                .map { json.decodeFromString(PlcOperation.serializer(), it.operationJson) }

        fun signWithRecoveryKey(payloadBase64Url: String): String = sign(recoveryKey.privateKeyPkcs8, payloadBase64Url)
    }

    private fun sign(
        privateKeyPkcs8: ByteArray,
        payloadBase64Url: String,
    ): String =
        Base64.UrlSafe
            .encode(
                EcKeySupport.signSha256(
                    privateKey = EcKeySupport.decodePrivateKey(privateKeyPkcs8),
                    curve = EcCurve.P256,
                    payload = decodeBase64Url(payloadBase64Url),
                ),
            ).trimEnd('=')

    private fun tamper(token: String): String {
        val characters = token.toCharArray()
        characters[TAMPERED_INDEX] = if (characters[TAMPERED_INDEX] == 'A') 'B' else 'A'
        return characters.concatToString()
    }

    private fun decodeBase64Url(value: String): ByteArray = Base64.UrlSafe.decode(value + "=".repeat((4 - value.length % 4) % 4))

    private companion object {
        private const val TAMPERED_INDEX = 20
    }

    private class MutableClock(
        var now: Instant,
    ) : Clock {
        override fun now(): Instant = now
    }
}
