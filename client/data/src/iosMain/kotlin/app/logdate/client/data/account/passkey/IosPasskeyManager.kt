package app.logdate.client.data.account.passkey

import app.logdate.client.domain.account.passkey.AuthenticationOptions
import app.logdate.client.domain.account.passkey.PasskeyAuthenticationResult
import app.logdate.client.domain.account.passkey.PasskeyErrorCode
import app.logdate.client.domain.account.passkey.PasskeyException
import app.logdate.client.domain.account.passkey.PasskeyManager
import app.logdate.client.domain.account.passkey.PasskeyRegistrationResult
import app.logdate.client.domain.account.passkey.RegistrationOptions
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationCredential
import platform.AuthenticationServices.ASAuthorizationPresentationContextProviding
import platform.AuthenticationServices.ASAuthorizationSecurityKeyPublicKeyCredentialAssertionRequest
import platform.AuthenticationServices.ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationSecurityKeyPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationSecurityKeyPublicKeyCredentialRegistrationRequest
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * iOS implementation of [PasskeyManager] using the AuthenticationServices framework.
 *
 * This implementation uses the Apple AuthenticationServices framework to handle
 * WebAuthn/passkey operations on iOS devices.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPasskeyManager : PasskeyManager {

    /**
     * Creates a new passkey for the given registration options.
     *
     * @param options The passkey registration options.
     * @return The result of the passkey creation operation.
     */
    override suspend fun createPasskey(options: RegistrationOptions): Result<PasskeyRegistrationResult> {
        return try {
            // Create the credential provider
            val provider = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(options.rpId)
            
            // Create registration request
            val challenge = options.challenge.decodeBase64ToNSData()
            val userId = options.userId.decodeBase64ToNSData()
            val registrationRequest = provider.createCredentialRegistrationRequestWithChallenge(
                challenge,
                displayName = options.displayName,
                name = options.username,
                userID = userId
            )
            
            // Perform the authorization request
            val result = suspendCoroutine { continuation ->
                val delegate = AuthorizationDelegate { credential, error ->
                    if (error != null) {
                        continuation.resumeWithException(mapAuthorizationError(error))
                    } else if (credential != null) {
                        // Process the credential response
                        // In a real implementation, you'd extract the exact fields needed
                        // This is a simplified implementation
                        val registrationResult = PasskeyRegistrationResult(
                            credentialId = "credential_id_placeholder",
                            clientDataJSON = "client_data_placeholder",
                            attestationObject = "attestation_object_placeholder"
                        )
                        continuation.resume(registrationResult)
                    } else {
                        continuation.resumeWithException(
                            PasskeyException(
                                PasskeyErrorCode.UNKNOWN_ERROR,
                                "Unknown error occurred during passkey registration"
                            )
                        )
                    }
                }
                
                val controller = ASAuthorizationController(arrayOf(registrationRequest))
                controller.delegate = delegate
                controller.presentationContextProvider = delegate
                controller.performRequests()
            }
            
            Result.success(result)
        } catch (e: PasskeyException) {
            Napier.e("Error creating passkey", e)
            Result.failure(e)
        } catch (e: Exception) {
            Napier.e("Unexpected error creating passkey", e)
            Result.failure(
                PasskeyException(
                    PasskeyErrorCode.UNKNOWN_ERROR,
                    "Unexpected error: ${e.message}",
                    e
                )
            )
        }
    }
    
    /**
     * Gets a passkey for authentication.
     *
     * @param options The authentication options.
     * @return The result of the passkey retrieval operation.
     */
    override suspend fun getPasskey(options: AuthenticationOptions): Result<PasskeyAuthenticationResult> {
        return try {
            // Create the credential provider
            val provider = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(options.rpId)
            
            // Create authentication request
            val challenge = options.challenge.decodeBase64ToNSData()
            
            // Create credential descriptors for allowed credentials
            val allowCredentials = options.allowCredentials?.map { credentialId ->
                ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor(
                    credentialId.decodeBase64ToNSData()
                )
            }
            
            val assertionRequest = if (allowCredentials != null) {
                provider.createCredentialAssertionRequestWithChallenge(
                    challenge,
                    allowedCredentialDescriptors = allowCredentials
                )
            } else {
                provider.createCredentialAssertionRequestWithChallenge(challenge)
            }
            
            // Perform the authorization request
            val result = suspendCoroutine { continuation ->
                val delegate = AuthorizationDelegate { credential, error ->
                    if (error != null) {
                        continuation.resumeWithException(mapAuthorizationError(error))
                    } else if (credential != null) {
                        // Process the credential response
                        // In a real implementation, you'd extract the exact fields needed
                        // This is a simplified implementation
                        val authenticationResult = PasskeyAuthenticationResult(
                            credentialId = "credential_id_placeholder",
                            clientDataJSON = "client_data_placeholder",
                            authenticatorData = "authenticator_data_placeholder",
                            signature = "signature_placeholder",
                            userHandle = "user_handle_placeholder"
                        )
                        continuation.resume(authenticationResult)
                    } else {
                        continuation.resumeWithException(
                            PasskeyException(
                                PasskeyErrorCode.UNKNOWN_ERROR,
                                "Unknown error occurred during passkey authentication"
                            )
                        )
                    }
                }
                
                val controller = ASAuthorizationController(arrayOf(assertionRequest))
                controller.delegate = delegate
                controller.presentationContextProvider = delegate
                controller.performRequests()
            }
            
            Result.success(result)
        } catch (e: PasskeyException) {
            Napier.e("Error getting passkey", e)
            Result.failure(e)
        } catch (e: Exception) {
            Napier.e("Unexpected error getting passkey", e)
            Result.failure(
                PasskeyException(
                    PasskeyErrorCode.UNKNOWN_ERROR,
                    "Unexpected error: ${e.message}",
                    e
                )
            )
        }
    }
    
    /**
     * Checks if passkey authentication is supported on this device.
     *
     * @return True if passkeys are supported, false otherwise.
     */
    override fun isPasskeySupported(): Boolean {
        // iOS 16+ supports passkeys
        val systemVersion = UIDevice.currentDevice.systemVersion
        val majorVersion = systemVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
        return majorVersion >= 16
    }
    
    /**
     * Maps authorization errors to domain-specific passkey exceptions.
     *
     * @param error The NSError from AuthenticationServices.
     * @return A domain-specific PasskeyException.
     */
    private fun mapAuthorizationError(error: NSError): PasskeyException {
        // In a real implementation, you'd map specific error codes from ASAuthorizationError
        // to appropriate PasskeyErrorCode values
        return when (error.code) {
            1L -> PasskeyException(
                PasskeyErrorCode.USER_CANCELLED,
                "User cancelled the operation",
                null
            )
            else -> PasskeyException(
                PasskeyErrorCode.UNKNOWN_ERROR,
                "Error: ${error.localizedDescription}",
                null
            )
        }
    }
    
    /**
     * Helper class for handling authorization callbacks.
     */
    private class AuthorizationDelegate(
        private val completionHandler: (ASAuthorizationCredential?, NSError?) -> Unit
    ) : NSObject(), ASAuthorizationControllerDelegateProtocol, ASAuthorizationControllerPresentationContextProvidingProtocol {
        
        override fun authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization: ASAuthorizationCredential) {
            completionHandler(didCompleteWithAuthorization, null)
        }
        
        override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
            completionHandler(null, didCompleteWithError)
        }
        
        override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor {
            // Get the key window to present the authorization controller
            val keyWindow = UIApplication.sharedApplication.windows.firstOrNull { it.isKeyWindow }
            return keyWindow as UIWindow
        }
    }
    
    /**
     * Decodes a Base64 string to NSData.
     */
    private fun String.decodeBase64ToNSData(): NSData {
        // In a real implementation, you'd properly decode Base64
        // This is a placeholder
        return NSData.create(length = 0UL)
    }
}