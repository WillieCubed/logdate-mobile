package app.logdate.client.permissions

import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyCapabilities
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.flow.Flow

/**
 * Cross-platform interface for passkey management.
 * Platform-specific implementations handle the actual WebAuthn interactions.
 */
interface PasskeyManager {
    
    /**
     * Check if passkeys are supported on this platform
     */
    suspend fun getCapabilities(): PasskeyCapabilities
    
    /**
     * Check if platform authenticator (built-in biometrics) is available
     */
    suspend fun isPlatformAuthenticatorAvailable(): Boolean
    
    /**
     * Register a new passkey
     * @param options Registration options from the server
     * @return Registration credential response as JSON string
     */
    suspend fun registerPasskey(options: PasskeyRegistrationOptions): Result<String>
    
    /**
     * Authenticate with a passkey
     * @param options Authentication options from the server
     * @return Authentication credential response as JSON string
     */
    suspend fun authenticateWithPasskey(options: PasskeyAuthenticationOptions): Result<String>
    
    /**
     * Get availability status of passkey features
     */
    fun getAvailabilityStatus(): Flow<PasskeyCapabilities>
}

/**
 * Exception thrown when passkey operations fail
 */
class PasskeyException(
    message: String,
    val errorCode: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Common error codes for passkey operations
 */
object PasskeyErrorCodes {
    const val NOT_SUPPORTED = "NOT_SUPPORTED"
    const val NOT_ALLOWED = "NOT_ALLOWED"
    const val TIMEOUT = "TIMEOUT"
    const val USER_CANCELLED = "USER_CANCELLED"
    const val INVALID_STATE = "INVALID_STATE"
    const val CONSTRAINT_ERROR = "CONSTRAINT_ERROR"
    const val SECURITY_ERROR = "SECURITY_ERROR"
    const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
}