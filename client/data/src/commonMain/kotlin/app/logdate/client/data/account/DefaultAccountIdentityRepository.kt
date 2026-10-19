package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.PlcRecoveryKeyManager
import app.logdate.client.networking.ExportedSigningKeyDto
import app.logdate.client.networking.IdentityApiClientContract
import app.logdate.client.networking.IdentityApiException
import app.logdate.client.repository.account.AccountDerivedPlcRecoveryKey
import app.logdate.client.repository.account.AccountExportedSigningKey
import app.logdate.client.repository.account.AccountHostedPlcOperation
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.AccountIdentityStatus
import app.logdate.client.repository.account.AccountImportedSigningKey
import app.logdate.client.repository.account.AccountRegisteredPlcRecoveryKey
import app.logdate.client.repository.account.AccountRotatedSigningKey
import app.logdate.client.repository.account.ExportedIdentitySigningKeyPayload
import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Default authenticated repository for first-party AT Protocol identity management.
 *
 * @param stepUp gets the fresh passkey confirmation that every recovery-key change needs
 */
@OptIn(ExperimentalEncodingApi::class)
class DefaultAccountIdentityRepository(
    private val apiClient: IdentityApiClientContract,
    private val sessionStorage: SessionStorage,
    private val plcRecoveryKeyManager: PlcRecoveryKeyManager,
    private val stepUp: IdentityStepUp,
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
                    require(prepared.nextPublicKeyDidKey == exportedKey.publicKeyDidKey) {
                        "The prepared operation installs a different key than the one being imported"
                    }
                    val payload = decodeBase64Url(prepared.signingPayloadBase64Url)
                    RecoveryOperationCheck.requireExpected(
                        unsignedOperationJson = prepared.unsignedOperationJson,
                        nextKey = prepared.nextPublicKeyDidKey,
                        payload = payload,
                        history = apiClient.getHostedPlcOperations(token).getOrThrow(),
                        recoveryDidKey = derivedDidKey,
                        handle = apiClient.getIdentityStatus(token).getOrThrow().handle,
                    )
                    val signature = plcRecoveryKeyManager.signPayload(recoveryPhrase = recoveryWords, payload = payload)
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

    override suspend fun registerRecoveryKey(recoveryPhrase: List<String>): Result<AccountRegisteredPlcRecoveryKey> =
        runRecoveryRequest("register the recovery key") { token ->
            val recoveryDidKey = deriveOnDevice { plcRecoveryKeyManager.deriveDidKey(recoveryPhrase) }
            val payload = apiClient.registerPlcRecoveryKey(token, recoveryDidKey, stepUp.confirm(token)).getOrThrow()
            AccountRegisteredPlcRecoveryKey(did = payload.did, handle = payload.handle, recoveryDidKey = payload.recoveryDidKey)
        }

    override suspend fun replaceRecoveryKey(recoveryPhrase: List<String>): Result<AccountRegisteredPlcRecoveryKey> =
        runRecoveryRequest("replace the recovery key") { token ->
            val recoveryDidKey = deriveOnDevice { plcRecoveryKeyManager.deriveDidKey(recoveryPhrase) }
            val payload = apiClient.replacePlcRecoveryKey(token, recoveryDidKey, stepUp.confirm(token)).getOrThrow()
            AccountRegisteredPlcRecoveryKey(did = payload.did, handle = payload.handle, recoveryDidKey = payload.recoveryDidKey)
        }

    override suspend fun recoverIdentity(recoveryPhrase: List<String>): Result<AccountImportedSigningKey> =
        runRecoveryRequest("recover the identity") { token ->
            val prepared = apiClient.prepareIdentityRecovery(token).getOrThrow()
            val recoveryDidKey = deriveOnDevice { plcRecoveryKeyManager.deriveDidKey(recoveryPhrase) }
            if (recoveryDidKey != prepared.recoveryDidKey) {
                throw IdentityRecoveryException(
                    IdentityRecoveryFailure.PHRASE_MISMATCH,
                    "The recovery phrase on this device does not produce the registered recovery key",
                )
            }
            val payload =
                runCatching { decodeBase64Url(prepared.signingPayloadBase64Url) }.getOrElse {
                    throw IdentityRecoveryException(IdentityRecoveryFailure.UNEXPECTED_CHANGE, "The bytes to sign could not be read", it)
                }
            RecoveryOperationCheck.requireExpected(
                unsignedOperationJson = prepared.unsignedOperationJson,
                nextKey = prepared.nextPublicKeyDidKey,
                payload = payload,
                history = apiClient.getHostedPlcOperations(token).getOrThrow(),
                recoveryDidKey = recoveryDidKey,
                handle = apiClient.getIdentityStatus(token).getOrThrow().handle,
            )
            val signature = deriveOnDevice { plcRecoveryKeyManager.signPayload(recoveryPhrase, payload) }
            val completed = apiClient.completeIdentityRecovery(token, prepared.preparedKey, signature, stepUp.confirm(token)).getOrThrow()
            AccountImportedSigningKey(did = completed.did, handle = completed.handle, publicKeyDidKey = completed.publicKeyDidKey)
        }

    /**
     * Runs a recovery-key request and turns every failure into an [IdentityRecoveryException], so
     * the screen can say what went wrong and what to do about it.
     */
    private suspend fun <T> runRecoveryRequest(
        operationName: String,
        block: suspend (String) -> T,
    ): Result<T> {
        val accessToken =
            sessionStorage.getSession()?.accessToken
                ?: return Result.failure(IdentityRecoveryException(IdentityRecoveryFailure.SIGNED_OUT, "No active session"))
        return try {
            Result.success(block(accessToken))
        } catch (error: CancellationException) {
            throw error
        } catch (error: IdentityRecoveryException) {
            Napier.w("Could not $operationName: ${error.failure}", error)
            Result.failure(error)
        } catch (error: IdentityApiException) {
            val failure = recoveryFailureFor(error.errorCode)
            Napier.w("Could not $operationName: ${error.errorCode}", error)
            Result.failure(IdentityRecoveryException(failure, error.message ?: "Could not $operationName", error))
        } catch (error: Exception) {
            Napier.e("Could not $operationName", error)
            Result.failure(IdentityRecoveryException(IdentityRecoveryFailure.REFUSED, "Could not $operationName", error))
        }
    }

    private suspend fun <T> deriveOnDevice(block: suspend () -> T): T =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IdentityRecoveryException(IdentityRecoveryFailure.DEVICE_ERROR, "Could not use the recovery phrase on this device", error)
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

/** The failure a person sees for a server error [code] from the recovery-key endpoints. */
internal fun recoveryFailureFor(code: String): IdentityRecoveryFailure =
    when (code) {
        "RECOVERY_KEYS_UNAVAILABLE" -> IdentityRecoveryFailure.NOT_AVAILABLE
        "RECOVERY_KEY_ALREADY_SET" -> IdentityRecoveryFailure.ALREADY_SET
        "RECOVERY_KEY_NOT_SET" -> IdentityRecoveryFailure.NOT_SET
        "RECOVERY_KEY_NOT_ON_IDENTITY" -> IdentityRecoveryFailure.KEY_REMOVED
        "RECOVERY_SIGNATURE_INVALID" -> IdentityRecoveryFailure.SIGNATURE_REJECTED
        "SERVER_KEY_NOT_ON_IDENTITY" -> IdentityRecoveryFailure.SERVER_KEY_MISSING
        "IDENTITY_CHANGED", "PREPARED_KEY_EXPIRED", "PREPARED_KEY_INVALID" -> IdentityRecoveryFailure.IDENTITY_CHANGED
        "STEP_UP_REQUIRED", "STEP_UP_FAILED" -> IdentityRecoveryFailure.STEP_UP_FAILED
        "STEP_UP_UNAVAILABLE" -> IdentityRecoveryFailure.STEP_UP_UNAVAILABLE
        "PASSKEY_REQUIRED" -> IdentityRecoveryFailure.PASSKEY_REQUIRED
        "INVALID_TOKEN", "ACCOUNT_NOT_FOUND" -> IdentityRecoveryFailure.SIGNED_OUT
        "NETWORK_ERROR" -> IdentityRecoveryFailure.UNREACHABLE
        else -> IdentityRecoveryFailure.REFUSED
    }

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
