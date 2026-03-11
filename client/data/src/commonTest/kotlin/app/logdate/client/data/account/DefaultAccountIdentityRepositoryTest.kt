package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.PlcRecoveryKeyManager
import app.logdate.client.networking.ExportSigningKeyDataDto
import app.logdate.client.networking.ExportedSigningKeyDto
import app.logdate.client.networking.HostedPlcOperationDataDto
import app.logdate.client.networking.IdentityApiClientContract
import app.logdate.client.networking.IdentityStatusDataDto
import app.logdate.client.networking.ImportSigningKeyDataDto
import app.logdate.client.networking.PrepareRecoverySigningKeyImportDataDto
import app.logdate.client.networking.RegisterPlcRecoveryKeyDataDto
import app.logdate.client.networking.RotateSigningKeyDataDto
import app.logdate.client.repository.account.ExportedIdentitySigningKeyPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultAccountIdentityRepositoryTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun `getIdentityStatus fails when no session exists`() =
        runTest {
            val repository =
                DefaultAccountIdentityRepository(
                    apiClient = FakeIdentityApiClient(),
                    sessionStorage = FakeSessionStorage(),
                    plcRecoveryKeyManager = FakePlcRecoveryKeyManager(),
                    json = json,
                )

            val result = repository.getIdentityStatus()

            assertTrue(result.isFailure)
            assertEquals("No active session", result.exceptionOrNull()?.message)
        }

    @Test
    fun `getIdentityStatus delegates with current access token`() =
        runTest {
            val apiClient = FakeIdentityApiClient()
            val repository =
                DefaultAccountIdentityRepository(
                    apiClient = apiClient,
                    sessionStorage = FakeSessionStorage(session = TEST_SESSION),
                    plcRecoveryKeyManager = FakePlcRecoveryKeyManager(),
                    json = json,
                )

            val result = repository.getIdentityStatus()

            assertTrue(result.isSuccess)
            assertEquals("tk-123", apiClient.lastAccessToken)
            assertEquals("did:plc:alice123", result.getOrThrow().did)
        }

    @Test
    fun `importSigningKey parses exported signing key json before delegating`() =
        runTest {
            val apiClient = FakeIdentityApiClient()
            val repository =
                DefaultAccountIdentityRepository(
                    apiClient = apiClient,
                    sessionStorage = FakeSessionStorage(session = TEST_SESSION),
                    plcRecoveryKeyManager = FakePlcRecoveryKeyManager(),
                    json = json,
                )
            val exportedKeyJson =
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

            val result =
                repository.importSigningKey(
                    passphrase = "test-passphrase",
                    exportedKeyJson = exportedKeyJson,
                )

            assertTrue(result.isSuccess)
            assertEquals("test-passphrase", apiClient.lastPassphrase)
            assertEquals("did:key:zPublic", apiClient.lastImportedExportedKey?.publicKeyDidKey)
            assertEquals("did:key:zImported", result.getOrThrow().publicKeyDidKey)
        }

    @Test
    fun `importSigningKeyWithRecovery derives the recovery key and signs the prepared payload`() =
        runTest {
            val apiClient = FakeIdentityApiClient()
            val repository =
                DefaultAccountIdentityRepository(
                    apiClient = apiClient,
                    sessionStorage = FakeSessionStorage(session = TEST_SESSION),
                    plcRecoveryKeyManager = FakePlcRecoveryKeyManager(),
                    json = json,
                )
            val exportedKeyJson =
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

            val result =
                repository.importSigningKeyWithRecovery(
                    passphrase = "test-passphrase",
                    exportedKeyJson = exportedKeyJson,
                    recoveryPhrase = "one two three four five six seven eight nine ten eleven twelve",
                )

            assertTrue(result.isSuccess)
            assertEquals("signed:payload", apiClient.lastRecoverySignature)
            assertEquals("did:key:zImported", result.getOrThrow().publicKeyDidKey)
        }

    @Test
    fun `derivePlcRecoveryDidKey returns the deterministic client-side did key`() =
        runTest {
            val repository =
                DefaultAccountIdentityRepository(
                    apiClient = FakeIdentityApiClient(),
                    sessionStorage = FakeSessionStorage(session = TEST_SESSION),
                    plcRecoveryKeyManager = FakePlcRecoveryKeyManager(),
                    json = json,
                )

            val result =
                repository.derivePlcRecoveryDidKey(
                    "one two three four five six seven eight nine ten eleven twelve",
                )

            assertTrue(result.isSuccess)
            assertEquals("did:key:zRecovery", result.getOrThrow().recoveryDidKey)
        }
}

private val TEST_SESSION =
    UserSession(
        accessToken = "tk-123",
        refreshToken = "rf-123",
        accountId = "account-123",
    )

private class FakeSessionStorage(
    session: UserSession? = null,
) : SessionStorage {
    private val sessions = MutableStateFlow(session)

    override fun getSession(): UserSession? = sessions.value

    override fun getSessionFlow(): Flow<UserSession?> = sessions

    override suspend fun hasValidSession(): Boolean = sessions.value != null

    override fun saveSession(session: UserSession) {
        sessions.value = session
    }

    override fun clearSession() {
        sessions.value = null
    }
}

private class FakeIdentityApiClient : IdentityApiClientContract {
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
                signingKeyPublicMultibase = "zPublic",
                signingKeyDidKey = "did:key:zPublic",
                plcRecoveryDidKey = "did:key:zRecovery",
                plcOperationCount = 2,
            ),
        )
    }

    override suspend fun getHostedPlcOperations(accessToken: String): Result<List<HostedPlcOperationDataDto>> {
        lastAccessToken = accessToken
        return Result.success(
            listOf(
                HostedPlcOperationDataDto(
                    did = "did:plc:alice123",
                    cid = "cid-1",
                    prevCid = null,
                    operationType = "plc_operation",
                    operationJson = "{}",
                    createdAt = "2026-03-10T00:00:00Z",
                ),
            ),
        )
    }

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
        return Result.success(
            PrepareRecoverySigningKeyImportDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                recoveryDidKey = "did:key:zRecovery",
                nextPublicKeyDidKey = "did:key:zImported",
                unsignedOperationJson = """{"type":"plc_operation"}""",
                signingPayloadBase64Url = "cGF5bG9hZA",
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
                publicKeyDidKey = "did:key:zImported",
            ),
        )
    }

    override suspend fun registerPlcRecoveryKey(
        accessToken: String,
        recoveryDidKey: String,
    ): Result<RegisterPlcRecoveryKeyDataDto> {
        lastAccessToken = accessToken
        return Result.success(
            RegisterPlcRecoveryKeyDataDto(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                recoveryDidKey = recoveryDidKey,
            ),
        )
    }
}

private class FakePlcRecoveryKeyManager : PlcRecoveryKeyManager {
    override suspend fun deriveDidKey(recoveryPhrase: List<String>): String = "did:key:zRecovery"

    override suspend fun signPayload(
        recoveryPhrase: List<String>,
        payload: ByteArray,
    ): String = "signed:${payload.decodeToString()}"
}
