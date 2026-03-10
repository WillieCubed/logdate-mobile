package app.logdate.client.repository.account

import kotlinx.serialization.Serializable

/**
 * First-party AT Protocol identity management operations for the authenticated account.
 */
interface AccountIdentityRepository {
    suspend fun getIdentityStatus(): Result<AccountIdentityStatus>

    suspend fun getHostedPlcOperations(): Result<List<AccountHostedPlcOperation>>

    suspend fun exportSigningKey(passphrase: String): Result<AccountExportedSigningKey>

    suspend fun rotateSigningKey(passphrase: String): Result<AccountRotatedSigningKey>

    suspend fun importSigningKey(
        passphrase: String,
        exportedKeyJson: String,
    ): Result<AccountImportedSigningKey>

    suspend fun registerPlcRecoveryKey(recoveryDidKey: String): Result<AccountRegisteredPlcRecoveryKey>
}

@Serializable
data class AccountIdentityStatus(
    val did: String,
    val handle: String,
    val signingKeyPublicMultibase: String,
    val signingKeyDidKey: String,
    val plcRecoveryDidKey: String? = null,
    val plcOperationCount: Int = 0,
)

@Serializable
data class AccountHostedPlcOperation(
    val did: String,
    val cid: String? = null,
    val prevCid: String? = null,
    val operationType: String,
    val operationJson: String,
    val createdAt: String,
)

@Serializable
data class ExportedIdentitySigningKeyPayload(
    val algorithm: String,
    val publicKeyMultibase: String,
    val publicKeyDidKey: String,
    val encryptedPrivateKey: String,
    val salt: String,
    val iv: String,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 120_000,
)

@Serializable
data class AccountExportedSigningKey(
    val did: String,
    val handle: String,
    val exportedKey: ExportedIdentitySigningKeyPayload,
)

@Serializable
data class AccountRotatedSigningKey(
    val did: String,
    val handle: String,
    val previousPublicKeyDidKey: String,
    val exportedKey: ExportedIdentitySigningKeyPayload,
)

@Serializable
data class AccountImportedSigningKey(
    val did: String,
    val handle: String,
    val publicKeyDidKey: String,
)

@Serializable
data class AccountRegisteredPlcRecoveryKey(
    val did: String,
    val handle: String,
    val recoveryDidKey: String,
)
