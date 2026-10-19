package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.PlcRecoveryKeyManager
import app.logdate.client.networking.ExportSigningKeyDataDto
import app.logdate.client.networking.ExportedSigningKeyDto
import app.logdate.client.networking.HostedPlcOperationDataDto
import app.logdate.client.networking.IdentityApiClientContract
import app.logdate.client.networking.IdentityApiException
import app.logdate.client.networking.IdentityStatusDataDto
import app.logdate.client.networking.ImportSigningKeyDataDto
import app.logdate.client.networking.PasskeyStepUpProofDto
import app.logdate.client.networking.PrepareIdentityRecoveryDataDto
import app.logdate.client.networking.PrepareRecoverySigningKeyImportDataDto
import app.logdate.client.networking.RegisterPlcRecoveryKeyDataDto
import app.logdate.client.networking.RotateSigningKeyDataDto
import app.logdate.client.repository.account.ExportedIdentitySigningKeyPayload
import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.shared.model.PasskeyAssertionAuthenticatorResponse
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticationOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.plc.PlcEncoding
import studio.hypertext.atproto.plc.PlcOperation
import studio.hypertext.atproto.plc.PlcService
import studio.hypertext.atproto.plc.PlcUnsignedOperation
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validates the management of decentralized account identities within [DefaultAccountIdentityRepository].
 *
 * This suite exercises the cryptographic lifecycle of user identities, including:
 * - Session-aware identity status retrieval
 * - Secure export and import of signing keys using passphrase-based encryption
 * - Client-side derivation and registration of recovery keys, each confirmed with a passkey
 * - The device's check of every operation before the recovery key signs it
 */
class DefaultAccountIdentityRepositoryTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun `getIdentityStatus fails when no session exists`() =
        runTest {
            val repository = repository(FakeIdentityApiClient(), session = null)

            val result = repository.getIdentityStatus()

            assertTrue(result.isFailure)
            assertEquals("No active session", result.exceptionOrNull()?.message)
        }

    @Test
    fun `getIdentityStatus delegates with current access token`() =
        runTest {
            val apiClient = FakeIdentityApiClient()

            val result = repository(apiClient).getIdentityStatus()

            assertTrue(result.isSuccess)
            assertEquals("tk-123", apiClient.lastAccessToken)
            assertEquals("did:plc:alice123", result.getOrThrow().did)
        }

    @Test
    fun `importSigningKey parses exported signing key json before delegating`() =
        runTest {
            val apiClient = FakeIdentityApiClient()

            val result = repository(apiClient).importSigningKey(passphrase = "test-passphrase", exportedKeyJson = exportedKeyJson())

            assertTrue(result.isSuccess)
            assertEquals("test-passphrase", apiClient.lastPassphrase)
            assertEquals("did:key:zPublic", apiClient.lastImportedExportedKey?.publicKeyDidKey)
            assertEquals("did:key:zImported", result.getOrThrow().publicKeyDidKey)
        }

    @Test
    fun `importSigningKeyWithRecovery checks and signs the prepared operation`() =
        runTest {
            val apiClient = FakeIdentityApiClient()
            val keys = FakePlcRecoveryKeyManager()

            val result =
                repository(apiClient, keys = keys).importSigningKeyWithRecovery(
                    passphrase = "test-passphrase",
                    exportedKeyJson = exportedKeyJson(),
                    recoveryPhrase = "one two three four five six seven eight nine ten eleven twelve",
                )

            assertTrue(result.isSuccess)
            assertContentEquals(PlcEncoding.encodeUnsigned(expectedRecovery("did:key:zPublic")), keys.signedPayloads.single())
            assertEquals("signed", apiClient.lastRecoverySignature)
        }

    @Test
    fun `importSigningKeyWithRecovery refuses an operation that removes the recovery key`() =
        runTest {
            val apiClient = FakeIdentityApiClient(prepared = { next -> expectedRecovery(next).copy(rotationKeys = listOf(next)) })
            val keys = FakePlcRecoveryKeyManager()

            val result =
                repository(apiClient, keys = keys).importSigningKeyWithRecovery(
                    passphrase = "test-passphrase",
                    exportedKeyJson = exportedKeyJson(),
                    recoveryPhrase = "one two three four five six seven eight nine ten eleven twelve",
                )

            assertEquals(IdentityRecoveryFailure.UNEXPECTED_CHANGE, result.failure())
            assertTrue(keys.signedPayloads.isEmpty())
        }

    @Test
    fun `derivePlcRecoveryDidKey returns the deterministic client-side did key`() =
        runTest {
            val result =
                repository(FakeIdentityApiClient()).derivePlcRecoveryDidKey(
                    "one two three four five six seven eight nine ten eleven twelve",
                )

            assertTrue(result.isSuccess)
            assertEquals("did:key:zRecovery", result.getOrThrow().recoveryDidKey)
        }

    @Test
    fun `registerRecoveryKey sends only the derived public key with a passkey confirmation`() =
        runTest {
            val apiClient = FakeIdentityApiClient()
            val stepUp = FakeStepUp()

            val result = repository(apiClient, stepUp = stepUp).registerRecoveryKey(PHRASE)

            assertEquals("did:key:zRecovery", apiClient.lastRegisteredRecoveryDidKey)
            assertEquals(STEP_UP, apiClient.lastStepUp)
            assertEquals(1, stepUp.confirmations)
            assertEquals("did:key:zRecovery", result.getOrThrow().recoveryDidKey)
        }

    @Test
    fun `replaceRecoveryKey sends the derived key with a passkey confirmation`() =
        runTest {
            val apiClient = FakeIdentityApiClient()

            val result = repository(apiClient).replaceRecoveryKey(PHRASE)

            assertEquals("did:key:zRecovery", apiClient.lastReplacedRecoveryDidKey)
            assertEquals(STEP_UP, apiClient.lastStepUp)
            assertEquals("did:key:zRecovery", result.getOrThrow().recoveryDidKey)
        }

    @Test
    fun `recoverIdentity signs exactly the prepared operation and returns the sealed key`() =
        runTest {
            val apiClient = FakeIdentityApiClient()
            val keys = FakePlcRecoveryKeyManager()

            val result = repository(apiClient, keys = keys).recoverIdentity(PHRASE)

            assertContentEquals(PlcEncoding.encodeUnsigned(expectedRecovery("did:key:zFresh")), keys.signedPayloads.single())
            assertEquals("sealed-key", apiClient.lastPreparedKey)
            assertEquals("signed", apiClient.lastRecoverySignature)
            assertEquals(STEP_UP, apiClient.lastStepUp)
            assertEquals("did:key:zFresh", result.getOrThrow().publicKeyDidKey)
        }

    @Test
    fun `recoverIdentity refuses to sign an operation that removes this device's key`() =
        runTest {
            assertRefusedToSign { next -> expectedRecovery(next).copy(rotationKeys = listOf(next)) }
        }

    @Test
    fun `recoverIdentity refuses to sign an operation that adds another rotation key`() =
        runTest {
            assertRefusedToSign { next ->
                expectedRecovery(next).copy(rotationKeys = listOf("did:key:zRecovery", next, "did:key:zAttacker"))
            }
        }

    @Test
    fun `recoverIdentity refuses to sign an operation that moves the handle`() =
        runTest {
            assertRefusedToSign { next -> expectedRecovery(next).copy(alsoKnownAs = listOf("at://someone-else.example")) }
        }

    @Test
    fun `recoverIdentity refuses to sign an operation that changes the service`() =
        runTest {
            assertRefusedToSign { next ->
                expectedRecovery(next).copy(
                    services =
                        mapOf(
                            "atproto_pds" to PlcService("AtprotoPersonalDataServer", "https://evil.example"),
                        ),
                )
            }
        }

    @Test
    fun `recoverIdentity refuses to sign an operation that does not follow the latest change`() =
        runTest {
            assertRefusedToSign { next -> expectedRecovery(next).copy(prev = PlcEncoding.operationCid(GENESIS)) }
        }

    @Test
    fun `recoverIdentity refuses when the bytes to sign are not the operation shown`() =
        runTest {
            val other = PlcEncoding.encodeUnsigned(expectedRecovery("did:key:zFresh").copy(rotationKeys = listOf("did:key:zFresh")))
            assertRefusedToSign(payload = Base64.UrlSafe.encode(other).trimEnd('='))
        }

    @Test
    fun `recoverIdentity stops before signing when the phrase does not match the registered key`() =
        runTest {
            val apiClient = FakeIdentityApiClient(preparedRecoveryDidKey = "did:key:zSomeoneElse")

            val failure = repository(apiClient).recoverIdentity(PHRASE).exceptionOrNull()

            assertEquals(IdentityRecoveryFailure.PHRASE_MISMATCH, (failure as IdentityRecoveryException).failure)
            assertEquals(null, apiClient.lastPreparedKey)
        }

    @Test
    fun `a closed passkey prompt sends nothing`() =
        runTest {
            val apiClient = FakeIdentityApiClient()
            val cancelled = FakeStepUp(failure = IdentityRecoveryFailure.STEP_UP_CANCELLED)

            assertEquals(
                IdentityRecoveryFailure.STEP_UP_CANCELLED,
                repository(apiClient, stepUp = cancelled).recoverIdentity(PHRASE).failure(),
            )
            assertEquals(
                IdentityRecoveryFailure.STEP_UP_CANCELLED,
                repository(apiClient, stepUp = cancelled).registerRecoveryKey(PHRASE).failure(),
            )
            assertNull(apiClient.lastPreparedKey)
            assertNull(apiClient.lastRegisteredRecoveryDidKey)
        }

    @Test
    fun `recovery failures carry what the person can act on`() =
        runTest {
            val changed = FakeIdentityApiClient(completeError = IdentityApiException("IDENTITY_CHANGED", "changed"))
            val offline = FakeIdentityApiClient(completeError = IdentityApiException("NETWORK_ERROR", "offline"))
            val rejected = FakeIdentityApiClient(completeError = IdentityApiException("RECOVERY_SIGNATURE_INVALID", "bad"))
            val alreadySet = FakeIdentityApiClient(registerError = IdentityApiException("RECOVERY_KEY_ALREADY_SET", "set"))

            assertEquals(IdentityRecoveryFailure.IDENTITY_CHANGED, repository(changed).recoverIdentity(PHRASE).failure())
            assertEquals(IdentityRecoveryFailure.UNREACHABLE, repository(offline).recoverIdentity(PHRASE).failure())
            assertEquals(IdentityRecoveryFailure.SIGNATURE_REJECTED, repository(rejected).recoverIdentity(PHRASE).failure())
            assertEquals(IdentityRecoveryFailure.ALREADY_SET, repository(alreadySet).registerRecoveryKey(PHRASE).failure())
            assertEquals(IdentityRecoveryFailure.KEY_REMOVED, recoveryFailureFor("RECOVERY_KEY_NOT_ON_IDENTITY"))
            assertEquals(IdentityRecoveryFailure.NOT_AVAILABLE, recoveryFailureFor("RECOVERY_KEYS_UNAVAILABLE"))
            assertEquals(IdentityRecoveryFailure.SERVER_KEY_MISSING, recoveryFailureFor("SERVER_KEY_NOT_ON_IDENTITY"))
            assertEquals(IdentityRecoveryFailure.STEP_UP_FAILED, recoveryFailureFor("STEP_UP_FAILED"))
            assertEquals(IdentityRecoveryFailure.STEP_UP_FAILED, recoveryFailureFor("STEP_UP_REQUIRED"))
            assertEquals(IdentityRecoveryFailure.STEP_UP_UNAVAILABLE, recoveryFailureFor("STEP_UP_UNAVAILABLE"))
            assertEquals(IdentityRecoveryFailure.PASSKEY_REQUIRED, recoveryFailureFor("PASSKEY_REQUIRED"))
            assertEquals(IdentityRecoveryFailure.IDENTITY_CHANGED, recoveryFailureFor("PREPARED_KEY_INVALID"))
            assertEquals(IdentityRecoveryFailure.REFUSED, recoveryFailureFor("SOMETHING_NEW"))
        }

    @Test
    fun `recovery without a session or with an unusable phrase says so`() =
        runTest {
            val signedOut = repository(FakeIdentityApiClient(), session = null)
            val broken =
                repository(
                    FakeIdentityApiClient(),
                    keys = FakePlcRecoveryKeyManager(failure = IllegalArgumentException("Invalid recovery phrase")),
                )

            assertEquals(IdentityRecoveryFailure.SIGNED_OUT, signedOut.registerRecoveryKey(PHRASE).failure())
            assertEquals(IdentityRecoveryFailure.DEVICE_ERROR, broken.registerRecoveryKey(PHRASE).failure())
        }

    private suspend fun assertRefusedToSign(
        payload: String? = null,
        prepared: (String) -> PlcUnsignedOperation = ::expectedRecovery,
    ) {
        val apiClient = FakeIdentityApiClient(prepared = prepared, payloadOverride = payload)
        val keys = FakePlcRecoveryKeyManager()
        val stepUp = FakeStepUp()

        val result = repository(apiClient, keys = keys, stepUp = stepUp).recoverIdentity(PHRASE)

        assertEquals(IdentityRecoveryFailure.UNEXPECTED_CHANGE, result.failure())
        assertTrue(keys.signedPayloads.isEmpty(), "the recovery key signed an operation it should have refused")
        assertEquals(0, stepUp.confirmations)
        assertNull(apiClient.lastPreparedKey)
    }

    private fun repository(
        apiClient: FakeIdentityApiClient,
        session: UserSession? = TEST_SESSION,
        keys: FakePlcRecoveryKeyManager = FakePlcRecoveryKeyManager(),
        stepUp: FakeStepUp = FakeStepUp(),
    ) = DefaultAccountIdentityRepository(
        apiClient = apiClient,
        sessionStorage = FakeSessionStorage(session = session),
        plcRecoveryKeyManager = keys,
        stepUp = stepUp,
        json = json,
    )

    private fun exportedKeyJson(): String =
        json.encodeToString(
            ExportedIdentitySigningKeyPayload(
                algorithm = "P-256",
                publicKeyMultibase = "zPublic",
                publicKeyDidKey = "did:key:zPublic",
                encryptedPrivateKey = "test-ciphertext",
                salt = "test-salt",
                iv = "test-iv",
            ),
        )

    private fun Result<*>.failure(): IdentityRecoveryFailure? = (exceptionOrNull() as? IdentityRecoveryException)?.failure
}

private val PHRASE = List(12) { "word${it + 1}" }

private val TEST_SESSION =
    UserSession(
        accessToken = "tk-123",
        refreshToken = "rf-123",
        accountId = "account-123",
    )

private val SERVICES = mapOf("atproto_pds" to PlcService(type = "AtprotoPersonalDataServer", endpoint = "https://logdate.app"))

private val GENESIS =
    PlcOperation(
        sig = "genesis-signature",
        prev = null,
        services = SERVICES,
        alsoKnownAs = listOf("at://alice.logdate.app"),
        rotationKeys = listOf("did:key:zServer"),
        verificationMethods = mapOf("atproto" to "did:key:zServer"),
    )

/** The registration: the recovery key first, then the server's key. */
private val HEAD =
    PlcOperation(
        sig = "registration-signature",
        prev = PlcEncoding.operationCid(GENESIS),
        services = SERVICES,
        alsoKnownAs = listOf("at://alice.logdate.app"),
        rotationKeys = listOf("did:key:zRecovery", "did:key:zServer"),
        verificationMethods = mapOf("atproto" to "did:key:zServer"),
    )

/** What a recovery that installs [next] must look like. */
private fun expectedRecovery(next: String): PlcUnsignedOperation =
    PlcUnsignedOperation(
        prev = PlcEncoding.operationCid(HEAD),
        services = SERVICES,
        alsoKnownAs = listOf("at://alice.logdate.app"),
        rotationKeys = listOf("did:key:zRecovery", next),
        verificationMethods = mapOf("atproto" to next),
    )

private val STEP_UP =
    PasskeyStepUpProofDto(
        challenge = "step-up-challenge",
        credential = PasskeyAssertionResponse("cred-1", "cred-1", PasskeyAssertionAuthenticatorResponse("e30", "AA", "AA", "dXNlcg")),
    )

private class FakeStepUp(
    private val failure: IdentityRecoveryFailure? = null,
) : IdentityStepUp {
    var confirmations = 0

    override suspend fun confirm(accessToken: String): PasskeyStepUpProofDto {
        failure?.let { throw IdentityRecoveryException(it, "The passkey prompt did not finish") }
        confirmations++
        return STEP_UP
    }
}

private class FakeSessionStorage(
    session: UserSession? = null,
) : SessionStorage {
    private val sessions = MutableStateFlow(session)

    override fun getSession(): UserSession? = sessions.value

    override fun getSessionFlow(): Flow<UserSession?> = sessions

    override suspend fun hasValidSession(): Boolean = sessions.value != null

    override suspend fun saveSession(session: UserSession) {
        sessions.value = session
    }

    override suspend fun clearSession() {
        sessions.value = null
    }
}

@OptIn(ExperimentalEncodingApi::class)
private class FakeIdentityApiClient(
    private val preparedRecoveryDidKey: String = "did:key:zRecovery",
    private val completeError: IdentityApiException? = null,
    private val registerError: IdentityApiException? = null,
    private val prepared: (String) -> PlcUnsignedOperation = ::expectedRecovery,
    private val payloadOverride: String? = null,
) : IdentityApiClientContract {
    private val plcJson =
        Json {
            encodeDefaults = true
            explicitNulls = true
        }
    var lastRegisteredRecoveryDidKey: String? = null
    var lastReplacedRecoveryDidKey: String? = null
    var lastStepUp: PasskeyStepUpProofDto? = null
    var lastPreparedKey: String? = null
    var lastAccessToken: String? = null
    var lastPassphrase: String? = null
    var lastImportedExportedKey: ExportedSigningKeyDto? = null
    var lastRecoverySignature: String? = null

    override suspend fun getIdentityStatus(accessToken: String): Result<IdentityStatusDataDto> {
        lastAccessToken = accessToken
        return Result.success(
            IdentityStatusDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                signingKeyPublicMultibase = "zServer",
                signingKeyDidKey = "did:key:zServer",
                plcRecoveryDidKey = "did:key:zRecovery",
                plcOperationCount = 2,
            ),
        )
    }

    override suspend fun getHostedPlcOperations(accessToken: String): Result<List<HostedPlcOperationDataDto>> {
        lastAccessToken = accessToken
        return Result.success(listOf(HEAD, GENESIS).map { row(it) })
    }

    private fun row(operation: PlcOperation) =
        HostedPlcOperationDataDto(
            did = "did:plc:alice123",
            cid = PlcEncoding.operationCid(operation),
            prevCid = operation.prev,
            operationType = operation.type,
            operationJson = plcJson.encodeToString(PlcOperation.serializer(), operation),
            createdAt = "2026-03-10T00:00:00Z",
        )

    private fun encode(operation: PlcUnsignedOperation): String = plcJson.encodeToString(PlcUnsignedOperation.serializer(), operation)

    private fun payloadOf(operation: PlcUnsignedOperation): String =
        payloadOverride ?: Base64.UrlSafe.encode(PlcEncoding.encodeUnsigned(operation)).trimEnd('=')

    override suspend fun exportSigningKey(
        accessToken: String,
        passphrase: String,
    ): Result<ExportSigningKeyDataDto> {
        lastAccessToken = accessToken
        lastPassphrase = passphrase
        return Result.success(
            ExportSigningKeyDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                exportedKey =
                    ExportedSigningKeyDto(
                        algorithm = "P-256",
                        publicKeyMultibase = "zPublic",
                        publicKeyDidKey = "did:key:zPublic",
                        encryptedPrivateKey = "test-ciphertext",
                        salt = "test-salt",
                        iv = "test-iv",
                    ),
            ),
        )
    }

    override suspend fun rotateSigningKey(
        accessToken: String,
        passphrase: String,
    ): Result<RotateSigningKeyDataDto> {
        lastAccessToken = accessToken
        lastPassphrase = passphrase
        return Result.success(
            RotateSigningKeyDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                previousPublicKeyDidKey = "did:key:zOld",
                exportedKey =
                    ExportedSigningKeyDto(
                        algorithm = "P-256",
                        publicKeyMultibase = "zNew",
                        publicKeyDidKey = "did:key:zNew",
                        encryptedPrivateKey = "test-ciphertext",
                        salt = "test-salt",
                        iv = "test-iv",
                    ),
            ),
        )
    }

    override suspend fun importSigningKey(
        accessToken: String,
        passphrase: String,
        exportedKey: ExportedSigningKeyDto,
    ): Result<ImportSigningKeyDataDto> {
        lastAccessToken = accessToken
        lastPassphrase = passphrase
        lastImportedExportedKey = exportedKey
        return Result.success(
            ImportSigningKeyDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                publicKeyDidKey = "did:key:zImported",
            ),
        )
    }

    override suspend fun prepareRecoverySigningKeyImport(
        accessToken: String,
        passphrase: String,
        exportedKey: ExportedSigningKeyDto,
    ): Result<PrepareRecoverySigningKeyImportDataDto> {
        lastAccessToken = accessToken
        lastPassphrase = passphrase
        lastImportedExportedKey = exportedKey
        val operation = prepared(exportedKey.publicKeyDidKey)
        return Result.success(
            PrepareRecoverySigningKeyImportDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                recoveryDidKey = "did:key:zRecovery",
                nextPublicKeyDidKey = exportedKey.publicKeyDidKey,
                unsignedOperationJson = encode(operation),
                signingPayloadBase64Url = payloadOf(operation),
            ),
        )
    }

    override suspend fun completeRecoverySigningKeyImport(
        accessToken: String,
        passphrase: String,
        exportedKey: ExportedSigningKeyDto,
        signature: String,
    ): Result<ImportSigningKeyDataDto> {
        lastAccessToken = accessToken
        lastPassphrase = passphrase
        lastImportedExportedKey = exportedKey
        lastRecoverySignature = signature
        return Result.success(
            ImportSigningKeyDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                publicKeyDidKey = exportedKey.publicKeyDidKey,
            ),
        )
    }

    override suspend fun registerPlcRecoveryKey(
        accessToken: String,
        recoveryDidKey: String,
        stepUp: PasskeyStepUpProofDto?,
    ): Result<RegisterPlcRecoveryKeyDataDto> {
        lastAccessToken = accessToken
        lastRegisteredRecoveryDidKey = recoveryDidKey
        lastStepUp = stepUp
        registerError?.let { return Result.failure(it) }
        return Result.success(
            RegisterPlcRecoveryKeyDataDto(did = "did:plc:alice123", handle = "alice.logdate.app", recoveryDidKey = recoveryDidKey),
        )
    }

    override suspend fun replacePlcRecoveryKey(
        accessToken: String,
        recoveryDidKey: String,
        stepUp: PasskeyStepUpProofDto,
    ): Result<RegisterPlcRecoveryKeyDataDto> {
        lastAccessToken = accessToken
        lastReplacedRecoveryDidKey = recoveryDidKey
        lastStepUp = stepUp
        return Result.success(
            RegisterPlcRecoveryKeyDataDto(did = "did:plc:alice123", handle = "alice.logdate.app", recoveryDidKey = recoveryDidKey),
        )
    }

    override suspend fun beginRecoveryKeyStepUp(accessToken: String): Result<PasskeyAuthenticationOptions> =
        Result.success(PasskeyAuthenticationOptions(challenge = "step-up-challenge", rpId = "logdate.app"))

    override suspend fun prepareIdentityRecovery(accessToken: String): Result<PrepareIdentityRecoveryDataDto> {
        lastAccessToken = accessToken
        val operation = prepared("did:key:zFresh")
        return Result.success(
            PrepareIdentityRecoveryDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                recoveryDidKey = preparedRecoveryDidKey,
                nextPublicKeyDidKey = "did:key:zFresh",
                unsignedOperationJson = encode(operation),
                signingPayloadBase64Url = payloadOf(operation),
                preparedKey = "sealed-key",
            ),
        )
    }

    override suspend fun completeIdentityRecovery(
        accessToken: String,
        preparedKey: String,
        signature: String,
        stepUp: PasskeyStepUpProofDto,
    ): Result<ImportSigningKeyDataDto> {
        lastAccessToken = accessToken
        lastPreparedKey = preparedKey
        lastRecoverySignature = signature
        lastStepUp = stepUp
        completeError?.let { return Result.failure(it) }
        return Result.success(
            ImportSigningKeyDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                publicKeyDidKey = "did:key:zFresh",
            ),
        )
    }
}

private class FakePlcRecoveryKeyManager(
    private val failure: Exception? = null,
) : PlcRecoveryKeyManager {
    val signedPayloads = mutableListOf<ByteArray>()

    override suspend fun deriveDidKey(recoveryPhrase: List<String>): String {
        failure?.let { throw it }
        return "did:key:zRecovery"
    }

    override suspend fun signPayload(
        recoveryPhrase: List<String>,
        payload: ByteArray,
    ): String {
        signedPayloads += payload
        return "signed"
    }
}
