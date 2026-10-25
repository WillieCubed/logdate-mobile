package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticationOptions
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for first-party authenticated AT Protocol identity management APIs.
 */
interface IdentityApiClientContract {
    suspend fun getIdentityStatus(accessToken: String): Result<IdentityStatusDataDto>

    suspend fun getHostedPlcOperations(accessToken: String): Result<List<HostedPlcOperationDataDto>>

    suspend fun exportSigningKey(
        accessToken: String,
        passphrase: String,
    ): Result<ExportSigningKeyDataDto>

    suspend fun rotateSigningKey(
        accessToken: String,
        passphrase: String,
    ): Result<RotateSigningKeyDataDto>

    suspend fun importSigningKey(
        accessToken: String,
        passphrase: String,
        exportedKey: ExportedSigningKeyDto,
    ): Result<ImportSigningKeyDataDto>

    suspend fun prepareRecoverySigningKeyImport(
        accessToken: String,
        passphrase: String,
        exportedKey: ExportedSigningKeyDto,
    ): Result<PrepareRecoverySigningKeyImportDataDto>

    suspend fun completeRecoverySigningKeyImport(
        accessToken: String,
        passphrase: String,
        exportedKey: ExportedSigningKeyDto,
        signature: String,
    ): Result<ImportSigningKeyDataDto>

    /**
     * Registers [recoveryDidKey] on the identity; the server refuses it without a passkey [stepUp]
     * or, in its place, the [stepUpGrant] from a passkey sign-in in the last five minutes.
     */
    suspend fun registerPlcRecoveryKey(
        accessToken: String,
        recoveryDidKey: String,
        stepUp: PasskeyStepUpProofDto? = null,
        stepUpGrant: String? = null,
    ): Result<RegisterPlcRecoveryKeyDataDto>

    /** Puts [recoveryDidKey] in place of the registered recovery key, confirmed by a passkey [stepUp]. */
    suspend fun replacePlcRecoveryKey(
        accessToken: String,
        recoveryDidKey: String,
        stepUp: PasskeyStepUpProofDto,
    ): Result<RegisterPlcRecoveryKeyDataDto>

    /** Asks the server for a challenge that only this account's passkeys can answer, to confirm a recovery-key change. */
    suspend fun beginRecoveryKeyStepUp(accessToken: String): Result<PasskeyAuthenticationOptions>

    /** Asks the server for the operation that replaces its key, for the recovery key to sign. */
    suspend fun prepareIdentityRecovery(accessToken: String): Result<PrepareIdentityRecoveryDataDto>

    /** Sends the recovery key's [signature] with the server's sealed [preparedKey] and a passkey [stepUp] to finish a recovery. */
    suspend fun completeIdentityRecovery(
        accessToken: String,
        preparedKey: String,
        signature: String,
        stepUp: PasskeyStepUpProofDto,
    ): Result<ImportSigningKeyDataDto>
}

class IdentityApiClient(
    private val httpClient: HttpClient,
    private val configRepository: LogDateConfigRepository,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        },
) : IdentityApiClientContract {
    private suspend fun getBaseUrl(): String = configRepository.apiBaseUrl.first()

    override suspend fun getIdentityStatus(accessToken: String): Result<IdentityStatusDataDto> =
        request(
            request = { token ->
                get("${getBaseUrl()}/identity") {
                    header("Authorization", "Bearer $token")
                }
            },
            success = { payload -> json.decodeFromString<IdentityStatusResponseDto>(payload).data },
            failureMessage = "Failed to load identity status",
            accessToken = accessToken,
        )

    override suspend fun getHostedPlcOperations(accessToken: String): Result<List<HostedPlcOperationDataDto>> =
        request(
            request = { token ->
                get("${getBaseUrl()}/identity/plc/operations") {
                    header("Authorization", "Bearer $token")
                }
            },
            success = { payload -> json.decodeFromString<HostedPlcOperationsResponseDto>(payload).data },
            failureMessage = "Failed to load PLC operation history",
            accessToken = accessToken,
        )

    override suspend fun exportSigningKey(
        accessToken: String,
        passphrase: String,
    ): Result<ExportSigningKeyDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/signing-key/export") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(ExportSigningKeyRequestDto(passphrase = passphrase))
                }
            },
            success = { payload -> json.decodeFromString<ExportSigningKeyResponseDto>(payload).data },
            failureMessage = "Failed to export signing key",
            accessToken = accessToken,
        )

    override suspend fun rotateSigningKey(
        accessToken: String,
        passphrase: String,
    ): Result<RotateSigningKeyDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/signing-key/rotate") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(RotateSigningKeyRequestDto(passphrase = passphrase))
                }
            },
            success = { payload -> json.decodeFromString<RotateSigningKeyResponseDto>(payload).data },
            failureMessage = "Failed to rotate signing key",
            accessToken = accessToken,
        )

    override suspend fun importSigningKey(
        accessToken: String,
        passphrase: String,
        exportedKey: ExportedSigningKeyDto,
    ): Result<ImportSigningKeyDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/signing-key/import") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        ImportSigningKeyRequestDto(
                            passphrase = passphrase,
                            exportedKey = exportedKey,
                        ),
                    )
                }
            },
            success = { payload -> json.decodeFromString<ImportSigningKeyResponseDto>(payload).data },
            failureMessage = "Failed to import signing key",
            accessToken = accessToken,
        )

    override suspend fun prepareRecoverySigningKeyImport(
        accessToken: String,
        passphrase: String,
        exportedKey: ExportedSigningKeyDto,
    ): Result<PrepareRecoverySigningKeyImportDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/signing-key/import/recovery/prepare") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        PrepareRecoverySigningKeyImportRequestDto(
                            passphrase = passphrase,
                            exportedKey = exportedKey,
                        ),
                    )
                }
            },
            success = { payload -> json.decodeFromString<PrepareRecoverySigningKeyImportResponseDto>(payload).data },
            failureMessage = "Failed to prepare signing key recovery import",
            accessToken = accessToken,
        )

    override suspend fun completeRecoverySigningKeyImport(
        accessToken: String,
        passphrase: String,
        exportedKey: ExportedSigningKeyDto,
        signature: String,
    ): Result<ImportSigningKeyDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/signing-key/import/recovery/complete") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        CompleteRecoverySigningKeyImportRequestDto(
                            passphrase = passphrase,
                            exportedKey = exportedKey,
                            signature = signature,
                        ),
                    )
                }
            },
            success = { payload -> json.decodeFromString<ImportSigningKeyResponseDto>(payload).data },
            failureMessage = "Failed to complete signing key recovery import",
            accessToken = accessToken,
        )

    override suspend fun registerPlcRecoveryKey(
        accessToken: String,
        recoveryDidKey: String,
        stepUp: PasskeyStepUpProofDto?,
        stepUpGrant: String?,
    ): Result<RegisterPlcRecoveryKeyDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/plc/recovery-key") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(RegisterPlcRecoveryKeyRequestDto(recoveryDidKey = recoveryDidKey, stepUp = stepUp, stepUpGrant = stepUpGrant))
                }
            },
            success = { payload -> json.decodeFromString<RegisterPlcRecoveryKeyResponseDto>(payload).data },
            failureMessage = "Failed to register PLC recovery key",
            accessToken = accessToken,
        )

    override suspend fun replacePlcRecoveryKey(
        accessToken: String,
        recoveryDidKey: String,
        stepUp: PasskeyStepUpProofDto,
    ): Result<RegisterPlcRecoveryKeyDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/plc/recovery-key/replace") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(RegisterPlcRecoveryKeyRequestDto(recoveryDidKey = recoveryDidKey, stepUp = stepUp))
                }
            },
            success = { payload -> json.decodeFromString<RegisterPlcRecoveryKeyResponseDto>(payload).data },
            failureMessage = "Failed to replace the recovery key",
            accessToken = accessToken,
        )

    override suspend fun beginRecoveryKeyStepUp(accessToken: String): Result<PasskeyAuthenticationOptions> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/recovery-key/step-up") {
                    header("Authorization", "Bearer $token")
                }
            },
            success = { payload ->
                val data = json.decodeFromString<RecoveryKeyStepUpResponseDto>(payload).data
                PasskeyAuthenticationOptions(
                    challenge = data.challenge,
                    rpId = data.rpId,
                    allowCredentials = data.allowCredentials.map { it.id },
                    timeout = data.timeout,
                )
            },
            failureMessage = "Failed to begin the passkey confirmation",
            accessToken = accessToken,
        )

    override suspend fun prepareIdentityRecovery(accessToken: String): Result<PrepareIdentityRecoveryDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/recovery-key/recover/prepare") {
                    header("Authorization", "Bearer $token")
                }
            },
            success = { payload -> json.decodeFromString<PrepareIdentityRecoveryResponseDto>(payload).data },
            failureMessage = "Failed to prepare identity recovery",
            accessToken = accessToken,
        )

    override suspend fun completeIdentityRecovery(
        accessToken: String,
        preparedKey: String,
        signature: String,
        stepUp: PasskeyStepUpProofDto,
    ): Result<ImportSigningKeyDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/recovery-key/recover/complete") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CompleteIdentityRecoveryRequestDto(preparedKey = preparedKey, signature = signature, stepUp = stepUp))
                }
            },
            success = { payload -> json.decodeFromString<ImportSigningKeyResponseDto>(payload).data },
            failureMessage = "Failed to complete identity recovery",
            accessToken = accessToken,
        )

    private suspend fun <T> request(
        request: suspend HttpClient.(String) -> HttpResponse,
        success: (String) -> T,
        failureMessage: String,
        accessToken: String,
    ): Result<T> =
        try {
            val response = httpClient.request(accessToken)
            val payload = response.bodyAsText()
            if (response.status.value in 200..299) {
                Result.success(success(payload))
            } else {
                val errorResponse = json.decodeFromString<app.logdate.shared.model.ApiErrorResponse>(payload)
                Result.failure(IdentityApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (error: Exception) {
            Napier.e(failureMessage, error)
            Result.failure(IdentityApiException("NETWORK_ERROR", failureMessage, error))
        }
}

class IdentityApiException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
data class IdentityStatusResponseDto(
    val success: Boolean,
    val data: IdentityStatusDataDto,
)

@Serializable
data class IdentityStatusDataDto(
    val did: String,
    val handle: String,
    val signingKeyPublicMultibase: String,
    val signingKeyDidKey: String,
    val plcRecoveryDidKey: String? = null,
    val plcOperationCount: Int = 0,
)

@Serializable
data class HostedPlcOperationsResponseDto(
    val success: Boolean,
    val data: List<HostedPlcOperationDataDto>,
)

@Serializable
data class HostedPlcOperationDataDto(
    val did: String,
    val cid: String? = null,
    val prevCid: String? = null,
    val operationType: String,
    val operationJson: String,
    val createdAt: String,
)

@Serializable
data class ExportSigningKeyRequestDto(
    val passphrase: String,
)

@Serializable
data class RotateSigningKeyRequestDto(
    val passphrase: String,
)

@Serializable
data class ImportSigningKeyRequestDto(
    val passphrase: String,
    val exportedKey: ExportedSigningKeyDto,
)

@Serializable
data class PrepareRecoverySigningKeyImportRequestDto(
    val passphrase: String,
    val exportedKey: ExportedSigningKeyDto,
)

@Serializable
data class CompleteRecoverySigningKeyImportRequestDto(
    val passphrase: String,
    val exportedKey: ExportedSigningKeyDto,
    val signature: String,
)

@Serializable
data class RegisterPlcRecoveryKeyRequestDto(
    val recoveryDidKey: String,
    val stepUp: PasskeyStepUpProofDto? = null,
    val stepUpGrant: String? = null,
)

@Serializable
data class ExportSigningKeyResponseDto(
    val success: Boolean,
    val data: ExportSigningKeyDataDto,
)

@Serializable
data class ExportSigningKeyDataDto(
    val did: String,
    val handle: String,
    val exportedKey: ExportedSigningKeyDto,
)

@Serializable
data class RotateSigningKeyResponseDto(
    val success: Boolean,
    val data: RotateSigningKeyDataDto,
)

@Serializable
data class RotateSigningKeyDataDto(
    val did: String,
    val handle: String,
    val previousPublicKeyDidKey: String,
    val exportedKey: ExportedSigningKeyDto,
)

@Serializable
data class ImportSigningKeyResponseDto(
    val success: Boolean,
    val data: ImportSigningKeyDataDto,
)

@Serializable
data class PrepareRecoverySigningKeyImportResponseDto(
    val success: Boolean,
    val data: PrepareRecoverySigningKeyImportDataDto,
)

@Serializable
data class ImportSigningKeyDataDto(
    val did: String,
    val handle: String,
    val publicKeyDidKey: String,
)

@Serializable
data class PrepareRecoverySigningKeyImportDataDto(
    val did: String,
    val handle: String,
    val recoveryDidKey: String,
    val nextPublicKeyDidKey: String,
    val unsignedOperationJson: String,
    val signingPayloadBase64Url: String,
)

@Serializable
data class PrepareIdentityRecoveryResponseDto(
    val success: Boolean,
    val data: PrepareIdentityRecoveryDataDto,
)

@Serializable
data class PrepareIdentityRecoveryDataDto(
    val did: String,
    val handle: String,
    val recoveryDidKey: String,
    val nextPublicKeyDidKey: String,
    val unsignedOperationJson: String,
    val signingPayloadBase64Url: String,
    val preparedKey: String,
)

@Serializable
data class CompleteIdentityRecoveryRequestDto(
    val preparedKey: String,
    val signature: String,
    val stepUp: PasskeyStepUpProofDto,
)

/** A passkey assertion over a step-up [challenge], sent with a recovery-key change. */
@Serializable
data class PasskeyStepUpProofDto(
    val challenge: String,
    val credential: PasskeyAssertionResponse,
)

@Serializable
data class RecoveryKeyStepUpResponseDto(
    val success: Boolean,
    val data: RecoveryKeyStepUpDataDto,
)

@Serializable
data class RecoveryKeyStepUpDataDto(
    val challenge: String,
    val rpId: String,
    val allowCredentials: List<StepUpAllowedCredentialDto> = emptyList(),
    val timeout: Long = 300_000,
)

@Serializable
data class StepUpAllowedCredentialDto(
    val id: String,
)

@Serializable
data class RegisterPlcRecoveryKeyResponseDto(
    val success: Boolean,
    val data: RegisterPlcRecoveryKeyDataDto,
)

@Serializable
data class RegisterPlcRecoveryKeyDataDto(
    val did: String,
    val handle: String,
    val recoveryDidKey: String,
)

@Serializable
data class ExportedSigningKeyDto(
    val algorithm: String,
    val publicKeyMultibase: String,
    val publicKeyDidKey: String,
    val encryptedPrivateKey: String,
    val salt: String,
    val iv: String,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 120_000,
)
