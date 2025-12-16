package app.logdate.client.domain.account.passkey

/**
 * Manager for passkey operations across different platforms.
 *
 * This interface abstracts platform-specific passkey operations like registration
 * and authentication using WebAuthn/passkeys.
 */
interface PasskeyManager {
    /**
     * Creates a new passkey for the given registration options.
     *
     * @param options The passkey registration options.
     * @return The result of the passkey creation operation.
     */
    suspend fun createPasskey(options: RegistrationOptions): Result<PasskeyRegistrationResult>
    
    /**
     * Gets a passkey for authentication.
     *
     * @param options The authentication options.
     * @return The result of the passkey retrieval operation.
     */
    suspend fun getPasskey(options: AuthenticationOptions): Result<PasskeyAuthenticationResult>
    
    /**
     * Checks if passkey authentication is supported on this device.
     *
     * @return True if passkeys are supported, false otherwise.
     */
    fun isPasskeySupported(): Boolean
}

/**
 * Options for passkey registration.
 *
 * @property challenge The challenge from the server.
 * @property rpId The relying party ID.
 * @property rpName The relying party name.
 * @property userId The user ID.
 * @property username The username.
 * @property displayName The user display name.
 * @property timeout The timeout in milliseconds.
 */
data class RegistrationOptions(
    val challenge: String,
    val rpId: String,
    val rpName: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val timeout: Long
)

/**
 * Result of a passkey registration operation.
 *
 * @property credentialId The ID of the created credential.
 * @property clientDataJSON The client data JSON.
 * @property attestationObject The attestation object.
 */
data class PasskeyRegistrationResult(
    val credentialId: String,
    val clientDataJSON: String,
    val attestationObject: String
)

/**
 * Options for passkey authentication.
 *
 * @property challenge The challenge from the server.
 * @property rpId The relying party ID.
 * @property allowCredentials List of allowed credential IDs.
 * @property timeout The timeout in milliseconds.
 */
data class AuthenticationOptions(
    val challenge: String,
    val rpId: String,
    val allowCredentials: List<String>?,
    val timeout: Long
)

/**
 * Result of a passkey authentication operation.
 *
 * @property credentialId The ID of the used credential.
 * @property clientDataJSON The client data JSON.
 * @property authenticatorData The authenticator data.
 * @property signature The signature.
 * @property userHandle The user handle.
 */
data class PasskeyAuthenticationResult(
    val credentialId: String,
    val clientDataJSON: String,
    val authenticatorData: String,
    val signature: String,
    val userHandle: String
)

/**
 * Errors that can occur during passkey operations.
 */
enum class PasskeyErrorCode {
    NOT_SUPPORTED,
    USER_CANCELLED,
    SECURITY_ERROR,
    NETWORK_ERROR,
    TIMEOUT,
    UNKNOWN_ERROR
}

/**
 * Exception thrown for passkey operation errors.
 */
class PasskeyException(
    val errorCode: PasskeyErrorCode,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)