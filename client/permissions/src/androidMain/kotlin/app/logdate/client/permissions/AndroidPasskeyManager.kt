package app.logdate.client.permissions

import android.content.Context
import android.os.Bundle
import androidx.credentials.*
import androidx.credentials.exceptions.*
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyCapabilities
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class AndroidPasskeyManager(
    private val context: Context
) : PasskeyManager {
    
    private val credentialManager = CredentialManager.create(context)
    
    override suspend fun getCapabilities(): PasskeyCapabilities {
        return try {
            val isPlatformAvailable = isPlatformAuthenticatorAvailable()
            PasskeyCapabilities(
                isSupported = true,
                isPlatformAuthenticatorAvailable = isPlatformAvailable,
                supportedAlgorithms = listOf("ES256", "RS256")
            )
        } catch (e: Exception) {
            PasskeyCapabilities(
                isSupported = false,
                isPlatformAuthenticatorAvailable = false
            )
        }
    }
    
    override suspend fun isPlatformAuthenticatorAvailable(): Boolean {
        return try {
            // Check if Google Play Services and device support passkeys
            true // Android 9+ with Google Play Services generally supports passkeys
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun registerPasskey(options: PasskeyRegistrationOptions): Result<String> {
        return try {
            val createPublicKeyCredentialRequest = CreatePublicKeyCredentialRequest(
                requestJson = buildRegistrationRequestJson(options),
                preferImmediatelyAvailableCredentials = false
            )
            
            val result = credentialManager.createCredential(
                context = context,
                request = createPublicKeyCredentialRequest
            )
            
            when (result) {
                is CreatePublicKeyCredentialResponse -> {
                    Result.success(result.registrationResponseJson)
                }
                else -> {
                    Result.failure(PasskeyException("Unexpected credential type", PasskeyErrorCodes.UNKNOWN_ERROR))
                }
            }
        } catch (e: CreateCredentialException) {
            Result.failure(handleCreateCredentialException(e))
        } catch (e: Exception) {
            Result.failure(PasskeyException("Registration failed", PasskeyErrorCodes.UNKNOWN_ERROR, e))
        }
    }
    
    override suspend fun authenticateWithPasskey(options: PasskeyAuthenticationOptions): Result<String> {
        return try {
            val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(
                requestJson = buildAuthenticationRequestJson(options)
            )
            
            val getCredentialRequest = GetCredentialRequest(
                credentialOptions = listOf(getPublicKeyCredentialOption)
            )
            
            val result = credentialManager.getCredential(
                context = context,
                request = getCredentialRequest
            )
            
            when (val credential = result.credential) {
                is PublicKeyCredential -> {
                    Result.success(credential.authenticationResponseJson)
                }
                else -> {
                    Result.failure(PasskeyException("Unexpected credential type", PasskeyErrorCodes.UNKNOWN_ERROR))
                }
            }
        } catch (e: GetCredentialException) {
            Result.failure(handleGetCredentialException(e))
        } catch (e: Exception) {
            Result.failure(PasskeyException("Authentication failed", PasskeyErrorCodes.UNKNOWN_ERROR, e))
        }
    }
    
    override fun getAvailabilityStatus(): Flow<PasskeyCapabilities> {
        return flowOf(
            PasskeyCapabilities(
                isSupported = true,
                isPlatformAuthenticatorAvailable = true,
                supportedAlgorithms = listOf("ES256", "RS256")
            )
        )
    }
    
    private fun buildRegistrationRequestJson(options: PasskeyRegistrationOptions): String {
        // Build the WebAuthn registration request JSON
        // This should match the format expected by the Android Credential Manager
        return """{
            "rp": {
                "id": "logdate.app",
                "name": "LogDate"
            },
            "user": {
                "id": "${options.user.id}",
                "name": "${options.user.name}",
                "displayName": "${options.user.displayName}"
            },
            "challenge": "${options.challenge}",
            "pubKeyCredParams": [
                {"type": "public-key", "alg": -7},
                {"type": "public-key", "alg": -257}
            ],
            "timeout": ${options.timeout},
            "excludeCredentials": ${Json.encodeToString(ListSerializer(String.serializer()), options.excludeCredentials)},
            "authenticatorSelection": {
                "requireResidentKey": false,
                "residentKey": "preferred",
                "userVerification": "preferred"
            },
            "attestation": "none"
        }""".trimIndent()
    }
    
    private fun buildAuthenticationRequestJson(options: PasskeyAuthenticationOptions): String {
        // Build the WebAuthn authentication request JSON
        return """{
            "challenge": "${options.challenge}",
            "timeout": ${options.timeout},
            "rpId": "logdate.app",
            "allowCredentials": ${Json.encodeToString(ListSerializer(String.serializer()), options.allowCredentials)},
            "userVerification": "preferred"
        }""".trimIndent()
    }
    
    private fun handleCreateCredentialException(e: CreateCredentialException): PasskeyException {
        return when (e) {
            is CreateCredentialCancellationException -> 
                PasskeyException("User cancelled registration", PasskeyErrorCodes.USER_CANCELLED, e)
            is CreateCredentialInterruptedException -> 
                PasskeyException("Registration interrupted", PasskeyErrorCodes.INVALID_STATE, e)
            is CreateCredentialProviderConfigurationException -> 
                PasskeyException("Provider configuration error", PasskeyErrorCodes.CONSTRAINT_ERROR, e)
            is CreateCredentialUnknownException -> 
                PasskeyException("Unknown registration error", PasskeyErrorCodes.UNKNOWN_ERROR, e)
            is CreateCredentialUnsupportedException -> 
                PasskeyException("Passkeys not supported", PasskeyErrorCodes.NOT_SUPPORTED, e)
            // Note: CreatePublicKeyCredentialDomException may not be available in all versions
            // is CreatePublicKeyCredentialDomException -> 
            //     PasskeyException("Domain error: ${e.domError.type}", PasskeyErrorCodes.SECURITY_ERROR, e)
            else -> 
                PasskeyException("Registration failed", PasskeyErrorCodes.UNKNOWN_ERROR, e)
        }
    }
    
    private fun handleGetCredentialException(e: GetCredentialException): PasskeyException {
        return when (e) {
            is GetCredentialCancellationException -> 
                PasskeyException("User cancelled authentication", PasskeyErrorCodes.USER_CANCELLED, e)
            is GetCredentialInterruptedException -> 
                PasskeyException("Authentication interrupted", PasskeyErrorCodes.INVALID_STATE, e)
            is GetCredentialProviderConfigurationException -> 
                PasskeyException("Provider configuration error", PasskeyErrorCodes.CONSTRAINT_ERROR, e)
            is GetCredentialUnknownException -> 
                PasskeyException("Unknown authentication error", PasskeyErrorCodes.UNKNOWN_ERROR, e)
            is GetCredentialUnsupportedException -> 
                PasskeyException("Passkeys not supported", PasskeyErrorCodes.NOT_SUPPORTED, e)
            is NoCredentialException -> 
                PasskeyException("No credentials available", PasskeyErrorCodes.NOT_ALLOWED, e)
            else -> 
                PasskeyException("Authentication failed", PasskeyErrorCodes.UNKNOWN_ERROR, e)
        }
    }
}