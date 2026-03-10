package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
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

    suspend fun registerPlcRecoveryKey(
        accessToken: String,
        recoveryDidKey: String,
    ): Result<RegisterPlcRecoveryKeyDataDto>
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

    override suspend fun registerPlcRecoveryKey(
        accessToken: String,
        recoveryDidKey: String,
    ): Result<RegisterPlcRecoveryKeyDataDto> =
        request(
            request = { token ->
                post("${getBaseUrl()}/identity/plc/recovery-key") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(RegisterPlcRecoveryKeyRequestDto(recoveryDidKey = recoveryDidKey))
                }
            },
            success = { payload -> json.decodeFromString<RegisterPlcRecoveryKeyResponseDto>(payload).data },
            failureMessage = "Failed to register PLC recovery key",
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
data class RegisterPlcRecoveryKeyRequestDto(
    val recoveryDidKey: String,
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
data class ImportSigningKeyDataDto(
    val did: String,
    val handle: String,
    val publicKeyDidKey: String,
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
