package app.logdate.client.permissions

import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyCapabilities
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Desktop implementation of PasskeyManager.
 * 
 * Note: Desktop platforms don't natively support WebAuthn/passkeys in the same way
 * as mobile platforms. A complete implementation would require:
 * 1. Integration with system authenticators (Windows Hello, Touch ID, etc.)
 * 2. Browser-based WebAuthn implementation
 * 3. Hardware token support (YubiKey, etc.)
 */
class DesktopPasskeyManager : PasskeyManager {
    
    override suspend fun getCapabilities(): PasskeyCapabilities {
        return PasskeyCapabilities(
            isSupported = false, // Not implemented for desktop yet
            isPlatformAuthenticatorAvailable = false,
            supportedAlgorithms = emptyList()
        )
    }
    
    override suspend fun isPlatformAuthenticatorAvailable(): Boolean {
        return false // Could check for Windows Hello, Touch ID on macOS, etc.
    }
    
    override suspend fun registerPasskey(options: PasskeyRegistrationOptions): Result<String> {
        return Result.failure(
            PasskeyException("Passkeys not supported on desktop platform", PasskeyErrorCodes.NOT_SUPPORTED)
        )
    }
    
    override suspend fun authenticateWithPasskey(options: PasskeyAuthenticationOptions): Result<String> {
        return Result.failure(
            PasskeyException("Passkeys not supported on desktop platform", PasskeyErrorCodes.NOT_SUPPORTED)
        )
    }
    
    override fun getAvailabilityStatus(): Flow<PasskeyCapabilities> {
        return flowOf(
            PasskeyCapabilities(
                isSupported = false,
                isPlatformAuthenticatorAvailable = false,
                supportedAlgorithms = emptyList()
            )
        )
    }
}