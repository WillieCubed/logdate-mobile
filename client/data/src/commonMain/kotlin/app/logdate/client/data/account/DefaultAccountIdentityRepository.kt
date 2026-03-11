package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.PlcRecoveryKeyManager
import app.logdate.client.networking.ExportedSigningKeyDto
import app.logdate.client.networking.IdentityApiClientContract
import app.logdate.client.repository.account.AccountDerivedPlcRecoveryKey
import app.logdate.client.repository.account.AccountExportedSigningKey
import app.logdate.client.repository.account.AccountHostedPlcOperation
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.AccountIdentityStatus
import app.logdate.client.repository.account.AccountImportedSigningKey
import app.logdate.client.repository.account.AccountRegisteredPlcRecoveryKey
import app.logdate.client.repository.account.AccountRotatedSigningKey
import app.logdate.client.repository.account.ExportedIdentitySigningKeyPayload
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Default authenticated repository for first-party AT Protocol identity management.
 */
@OptIn(ExperimentalEncodingApi::class)
class DefaultAccountIdentityRepository(
    private val apiClient: IdentityApiClientContract,
    private val sessionStorage: SessionStorage,
    private val plcRecoveryKeyManager: PlcRecoveryKeyManager,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        },
) : AccountIdentityRepository {
    override suspend fun getIdentityStatus(): Result<AccountIdentityStatus> =
        runWithAccessToken("load identity status") { token ->
            apiClient.getIdentityStatus(token).map { payload ->
                AccountIdentityStatus(
                    did = payload.did,
                    handle = payload.handle,
                    signingKeyPublicMultibase = payload.signingKeyPublicMultibase,
                    signingKeyDidKey = payload.signingKeyDidKey,
                    plcRecoveryDidKey = payload.plcRecoveryDidKey,
                    plcOperationCount = payload.plcOperationCount,
                )
            }
        }

    override suspend fun getHostedPlcOperations(): Result<List<AccountHostedPlcOperation>> =
        runWithAccessToken("load PLC operation history") { token ->
            apiClient.getHostedPlcOperations(token).map { operations ->
                operations.map { payload ->
                    AccountHostedPlcOperation(
                        did = payload.did,
                        cid = payload.cid,
                        prevCid = payload.prevCid,
                        operationType = payload.operationType,
                        operationJson = payload.operationJson,
                        createdAt = payload.createdAt,
                    )
                }
            }
        }

    override suspend fun exportSigningKey(passphrase: String): Result<AccountExportedSigningKey> =
        runWithAccessToken("export signing key") { token ->
            apiClient.exportSigningKey(token, passphrase).map { payload ->
                AccountExportedSigningKey(
                    did = payload.did,
                    handle = payload.handle,
                    exportedKey = payload.exportedKey.toRepositoryModel(),
                )
            }
        }

    override suspend fun rotateSigningKey(passphrase: String): Result<AccountRotatedSigningKey> =
        runWithAccessToken("rotate signing key") { token ->
            apiClient.rotateSigningKey(token, passphrase).map { payload ->
                AccountRotatedSigningKey(
                    did = payload.did,
                    handle = payload.handle,
                    previousPublicKeyDidKey = payload.previousPublicKeyDidKey,
                    exportedKey = payload.exportedKey.toRepositoryModel(),
                )
            }
        }

    override suspend fun importSigningKey(
        passphrase: String,
        exportedKeyJson: String,
    ): Result<AccountImportedSigningKey> =
        runWithAccessToken("import signing key") { token ->
            val exportedKey =
                runCatching {
                    json.decodeFromString<ExportedIdentitySigningKeyPayload>(exportedKeyJson.trim())
                }.getOrElse { error ->
                    return@runWithAccessToken Result.failure(IllegalArgumentException("Invalid signing key JSON", error))
                }
            apiClient
                .importSigningKey(
                    accessToken = token,
                    passphrase = passphrase,
                    exportedKey = exportedKey.toDto(),
                ).map { payload ->
                    AccountImportedSigningKey(
                        did = payload.did,
                        handle = payload.handle,
                        publicKeyDidKey = payload.publicKeyDidKey,
                    )
                }
        }

    override suspend fun importSigningKeyWithRecovery(
        passphrase: String,
        exportedKeyJson: String,
        recoveryPhrase: String,
    ): Result<AccountImportedSigningKey> =
        runWithAccessToken("import signing key with recovery") { token ->
            val exportedKey =
                runCatching {
                    json.decodeFromString<ExportedIdentitySigningKeyPayload>(exportedKeyJson.trim())
                }.getOrElse { error ->
                    return@runWithAccessToken Result.failure(IllegalArgumentException("Invalid signing key JSON", error))
                }
            val recoveryWords = recoveryPhrase.trim().split(WHITESPACE_REGEX).filter(String::isNotBlank)
            apiClient
                .prepareRecoverySigningKeyImport(
                    accessToken = token,
                    passphrase = passphrase,
                    exportedKey = exportedKey.toDto(),
                ).mapCatching { prepared ->
                    val derivedDidKey = plcRecoveryKeyManager.deriveDidKey(recoveryWords)
                    require(derivedDidKey == prepared.recoveryDidKey) {
                        "Recovery phrase does not match the registered PLC recovery key"
                    }
                    val signature =
                        plcRecoveryKeyManager.signPayload(
                            recoveryPhrase = recoveryWords,
                            payload = decodeBase64Url(prepared.signingPayloadBase64Url),
                        )
                    apiClient
                        .completeRecoverySigningKeyImport(
                            accessToken = token,
                            passphrase = passphrase,
                            exportedKey = exportedKey.toDto(),
                            signature = signature,
                        ).getOrThrow()
                }.map { payload ->
                    AccountImportedSigningKey(
                        did = payload.did,
                        handle = payload.handle,
                        publicKeyDidKey = payload.publicKeyDidKey,
                    )
                }
        }

    override suspend fun derivePlcRecoveryDidKey(recoveryPhrase: String): Result<AccountDerivedPlcRecoveryKey> =
        runCatching {
            val recoveryWords = recoveryPhrase.trim().split(WHITESPACE_REGEX).filter(String::isNotBlank)
            AccountDerivedPlcRecoveryKey(recoveryDidKey = plcRecoveryKeyManager.deriveDidKey(recoveryWords))
        }

    override suspend fun registerPlcRecoveryKey(recoveryDidKey: String): Result<AccountRegisteredPlcRecoveryKey> =
        runWithAccessToken("register PLC recovery key") { token ->
            apiClient.registerPlcRecoveryKey(token, recoveryDidKey).map { payload ->
                AccountRegisteredPlcRecoveryKey(
                    did = payload.did,
                    handle = payload.handle,
                    recoveryDidKey = payload.recoveryDidKey,
                )
            }
        }

    private suspend fun <T> runWithAccessToken(
        operationName: String,
        block: suspend (String) -> Result<T>,
    ): Result<T> {
        val accessToken =
            sessionStorage.getSession()?.accessToken
                ?: return Result.failure(IllegalStateException("No active session"))
        return try {
            block(accessToken)
        } catch (error: Exception) {
            Napier.e("Failed to $operationName", error)
            Result.failure(error)
        }
    }

    private fun decodeBase64Url(value: String): ByteArray {
        val normalized =
            when (value.length % 4) {
                0 -> value
                2 -> "$value=="
                3 -> "$value="
                else -> throw IllegalArgumentException("Invalid base64url payload")
            }
        return Base64.UrlSafe.decode(normalized)
    }
}

private val WHITESPACE_REGEX = Regex("\\s+")

private fun app.logdate.client.networking.ExportedSigningKeyDto.toRepositoryModel(): ExportedIdentitySigningKeyPayload =
    ExportedIdentitySigningKeyPayload(
        algorithm = algorithm,
        publicKeyMultibase = publicKeyMultibase,
        publicKeyDidKey = publicKeyDidKey,
        encryptedPrivateKey = encryptedPrivateKey,
        salt = salt,
        iv = iv,
        kdf = kdf,
        iterations = iterations,
    )

private fun ExportedIdentitySigningKeyPayload.toDto(): ExportedSigningKeyDto =
    ExportedSigningKeyDto(
        algorithm = algorithm,
        publicKeyMultibase = publicKeyMultibase,
        publicKeyDidKey = publicKeyDidKey,
        encryptedPrivateKey = encryptedPrivateKey,
        salt = salt,
        iv = iv,
        kdf = kdf,
        iterations = iterations,
    )
