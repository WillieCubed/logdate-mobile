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

    suspend fun importSigningKeyWithRecovery(
        passphrase: String,
        exportedKeyJson: String,
        recoveryPhrase: String,
    ): Result<AccountImportedSigningKey>

    suspend fun derivePlcRecoveryDidKey(recoveryPhrase: String): Result<AccountDerivedPlcRecoveryKey>

    /**
     * Derives the recovery key from [recoveryPhrase] on this device and registers its public half on
     * the identity, ahead of the server's key. Only the public half leaves the device. The person
     * confirms the change with one of their passkeys first.
     *
     * Fails with [IdentityRecoveryException] when the passkey confirmation does not finish, or the
     * server refuses or cannot be reached.
     */
    suspend fun registerRecoveryKey(recoveryPhrase: List<String>): Result<AccountRegisteredPlcRecoveryKey>

    /**
     * Puts the recovery key derived from [recoveryPhrase] in place of the one registered on the
     * identity, for a person who no longer has the phrase the registered key came from. The person
     * confirms the change with one of their passkeys first.
     *
     * Fails with [IdentityRecoveryException] like [registerRecoveryKey].
     */
    suspend fun replaceRecoveryKey(recoveryPhrase: List<String>): Result<AccountRegisteredPlcRecoveryKey>

    /**
     * Replaces the server's key for the identity with a new one, approved by the recovery key
     * derived from [recoveryPhrase]. The phrase and the private key stay on this device; only the
     * signature is sent. Before signing, the device decodes the operation it is asked to sign and
     * refuses it unless it changes exactly the server's key, and the person confirms the change with
     * one of their passkeys.
     *
     * Fails with [IdentityRecoveryException], including [IdentityRecoveryFailure.PHRASE_MISMATCH]
     * when the phrase does not produce the recovery key registered on the identity and
     * [IdentityRecoveryFailure.UNEXPECTED_CHANGE] when the operation asks for more.
     */
    suspend fun recoverIdentity(recoveryPhrase: List<String>): Result<AccountImportedSigningKey>
}

/** Why a recovery-key request failed, in terms a person can act on. */
enum class IdentityRecoveryFailure {
    /** The server does not support recovery keys for this identity. */
    NOT_AVAILABLE,

    /** A different recovery key is already registered on the identity. */
    ALREADY_SET,

    /** No recovery key is registered on the identity. */
    NOT_SET,

    /** The identity's latest change no longer lists the registered recovery key. */
    KEY_REMOVED,

    /** The recovery phrase on this device does not produce the registered recovery key. */
    PHRASE_MISMATCH,

    /** The server did not accept this device's approval, although the phrase matches the registered key. */
    SIGNATURE_REJECTED,

    /** The server asked this device to approve more than replacing its key, so nothing was signed. */
    UNEXPECTED_CHANGE,

    /** The server cannot sign changes for the identity because its own key is no longer listed. */
    SERVER_KEY_MISSING,

    /** The person closed the passkey prompt, so nothing was sent. */
    STEP_UP_CANCELLED,

    /** The passkey confirmation was not accepted, or it expired. */
    STEP_UP_FAILED,

    /** This device or this server cannot confirm a change with a passkey. */
    STEP_UP_UNAVAILABLE,

    /** The account has no passkey to confirm the change with. */
    PASSKEY_REQUIRED,

    /** The identity changed, or the request took too long, while the recovery was in progress. */
    IDENTITY_CHANGED,

    /** The session expired; the person has to sign in again. */
    SIGNED_OUT,

    /** The server could not be reached. */
    UNREACHABLE,

    /** This device could not derive or use the recovery key from its recovery phrase. */
    DEVICE_ERROR,

    /** The server refused the request for another reason. */
    REFUSED,
}

/** A recovery-key request failed for [failure]; [message] carries the technical detail for logs. */
class IdentityRecoveryException(
    val failure: IdentityRecoveryFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

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
data class AccountDerivedPlcRecoveryKey(
    val recoveryDidKey: String,
)

@Serializable
data class AccountRegisteredPlcRecoveryKey(
    val did: String,
    val handle: String,
    val recoveryDidKey: String,
)
